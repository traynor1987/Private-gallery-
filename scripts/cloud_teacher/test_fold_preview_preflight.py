from contextlib import closing
from pathlib import Path
from tempfile import TemporaryDirectory
from unittest import TestCase
from unittest.mock import patch
import os
import sqlite3

from budget import Budget
from fold_preview_preflight import PreflightRefused, standard_compose_volume, verify_fresh_preview, verify_normal_ledger


class FoldPreviewPreflightTest(TestCase):
    def setUp(self):
        self.temporary = TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.state = self.root / 'state'
        self.state.mkdir()
        self.preview = self.state / 'nova-fold-owner-preview'
        self.relocations = self.root / 'relocations'
        self.relocations.mkdir()

    def verify(self, **options):
        verify_fresh_preview(self.preview, (self.state, self.relocations), **options)

    def ledger(self, cycle='normal-funded-cycle'):
        storage = self.relocations / 'docker' / 'volumes' / 'copied-volume' / '_data'
        storage.mkdir(parents=True, exist_ok=True)
        path = storage / 'Budget.DB'
        return path, Budget(path, cycle, 1_000, 100, 10, clock=lambda: 99)

    def test_new_path_or_empty_private_parent_is_read_only(self):
        self.verify()
        self.assertFalse(self.preview.exists())
        self.preview.mkdir(mode=0o700)
        self.verify()
        self.assertEqual(list(self.preview.iterdir()), [])

    def test_old_or_partial_preparation_blocks_even_without_ledger(self):
        self.preview.mkdir(mode=0o700)
        for name in ('approval-20261125', 'unidentified-backup'):
            with self.subTest(name=name):
                approval = self.preview / name
                approval.mkdir()
                with self.assertRaisesRegex(PreflightRefused, 'Prior or partial state'):
                    self.verify()
                self.assertTrue(approval.is_dir())
                approval.rmdir()

    def test_only_empty_private_original_directories_are_allowed(self):
        original = self.preview / 'approval-20261123'
        self.preview.mkdir(mode=0o700)
        original.mkdir(mode=0o700)
        self.verify()
        ledger = original / 'ledger'
        ledger.mkdir(mode=0o700)
        self.verify()
        marker = ledger / 'budget.db-wal'
        marker.touch()
        with self.assertRaisesRegex(PreflightRefused, 'Prior or partial state'):
            self.verify()
        self.assertTrue(marker.is_file())
        marker.unlink()
        ledger.rmdir()
        ledger.symlink_to(self.relocations, target_is_directory=True)
        with self.assertRaisesRegex(PreflightRefused, 'Prior or partial state'):
            self.verify()

    def test_case_insensitive_relocation_marker_or_link_blocks(self):
        path = self.relocations / 'move'
        path.mkdir()
        prior = path / 'SAVED-APPROVAL_20261123.old'
        prior.symlink_to(self.root / 'now-missing')
        with self.assertRaisesRegex(PreflightRefused, 'prior preview marker'):
            self.verify()
        self.assertTrue(prior.is_symlink())

    def test_canonical_symlink_is_rejected_even_if_target_is_empty(self):
        self.preview.symlink_to(self.relocations, target_is_directory=True)
        with self.assertRaisesRegex(PreflightRefused, 'canonical preview directory is not private'):
            self.verify()

    def test_reviewed_source_worktree_is_not_a_ledger_but_is_still_searched(self):
        source = self.relocations / 'nova-fold-owner-preview'
        (source / '.git').mkdir(parents=True)
        script = source / 'scripts' / 'cloud_teacher' / 'fold-owner-preview.sh'
        script.parent.mkdir(parents=True)
        script.touch()
        workflow = source / '.github' / 'workflows' / 'nova-brain-fold-owner-preview.yml'
        workflow.parent.mkdir(parents=True)
        workflow.touch()
        self.verify()
        (source / 'approval-20261123').mkdir()
        with self.assertRaisesRegex(PreflightRefused, 'prior preview marker'):
            self.verify()

    def test_normal_active_budget_is_recognised_without_modifying_it(self):
        path, budget = self.ledger()
        self.assertTrue(budget.reserve())
        prior = path.read_bytes()
        before = path.stat()
        self.verify()
        after = path.stat()
        self.assertEqual(prior, path.read_bytes())
        self.assertEqual(before.st_mtime_ns, after.st_mtime_ns)
        self.assertEqual(before.st_ctime_ns, after.st_ctime_ns)
        self.assertEqual(list(path.parent.iterdir()), [path])

    def test_prior_index_blocks_even_when_the_moved_ledger_is_empty(self):
        path, _ = self.ledger()
        with closing(sqlite3.connect(path)) as db, db:
            db.execute('CREATE UNIQUE INDEX FOLD_ONE_PER_FIXTURE ON request_reservations(cycle,digest)')
        with self.assertRaisesRegex(PreflightRefused, 'Fold preview index'):
            self.verify()

    def test_prior_approval_cycle_blocks_even_without_the_index(self):
        _, budget = self.ledger('FOLD-OWNER-20261123')
        self.assertTrue(budget.reserve())
        with self.assertRaisesRegex(PreflightRefused, 'possible Fold preview approval') as refused:
            self.verify()
        self.assertNotIn('FOLD-OWNER-20261123', str(refused.exception))

    def test_known_production_empty_ledger_exception_has_exact_mount_scope(self):
        self.assertTrue(standard_compose_volume(Path('/var/lib/docker/volumes/nova-cloud-teacher_state/_data/budget.db')))
        self.assertFalse(standard_compose_volume(Path('/tmp/docker/volumes/nova-cloud-teacher_state/_data/budget.db')))
        self.ledger()
        with self.assertRaisesRegex(PreflightRefused, 'no verifiable origin'):
            self.verify()
        with patch('fold_preview_preflight.standard_compose_volume', return_value=True):
            self.verify()

    def test_real_custom_docker_volumes_are_searched_below_skipped_ancestors(self):
        docker_root = self.relocations / '.cache' / 'custom-engine-store'
        snapshot = docker_root / 'volumes' / 'unrelated-name' / '_data'
        snapshot.mkdir(parents=True)
        (snapshot / 'Budget.DB').write_bytes(b'unclassifiable SQLite')
        self.verify()
        with self.assertRaisesRegex(PreflightRefused, 'Search location: Docker volume or mounted state'):
            self.verify(docker_root=docker_root)

    def test_empty_production_ledger_is_recognised_at_actual_docker_location_once(self):
        docker_root = self.relocations / 'custom-engine-store'
        volume = docker_root / 'volumes' / 'nova-cloud-teacher_state' / '_data'
        volume.mkdir(parents=True)
        ledger = volume / 'budget.db'
        Budget(ledger, 'normal-funded-cycle', 1_000, 100, 10, clock=lambda: 99)
        before = ledger.read_bytes()
        with patch('fold_preview_preflight.verify_normal_ledger', wraps=verify_normal_ledger) as check:
            self.verify(docker_root=docker_root, state_mounts=(volume,))
            check.assert_called_once_with(ledger, docker_root / 'volumes')
        self.assertEqual(before, ledger.read_bytes())

    def test_explicit_mount_inside_skipped_custom_docker_layers_is_checked_privately(self):
        docker_root = self.relocations / 'custom-engine-store'
        hidden_mount = docker_root / 'overlay2' / 'unprinted-mount-name' / 'state'
        hidden_mount.mkdir(parents=True)
        ledger = hidden_mount / 'budget.db'
        budget = Budget(ledger, 'FoLd-Owner-20261123', 1_000, 100, 10, clock=lambda: 99)
        self.assertTrue(budget.reserve())
        self.verify(docker_root=docker_root)
        with self.assertRaisesRegex(PreflightRefused, 'possible Fold preview approval') as refused:
            self.verify(docker_root=docker_root, state_mounts=(hidden_mount,))
        self.assertIn('Search location: Docker volume or mounted state', str(refused.exception))
        self.assertNotIn('unprinted-mount-name', str(refused.exception))

    def test_mount_alias_to_old_approval_is_refused_even_when_empty(self):
        docker_root = self.relocations / 'custom-engine-store'
        old_state = docker_root / 'overlay2' / 'Approval-20261123' / 'ledger'
        old_state.mkdir(parents=True)
        alias = self.relocations / 'unprinted-mount-name'
        alias.symlink_to(old_state, target_is_directory=True)
        with self.assertRaisesRegex(PreflightRefused, 'prior preview marker was found in a Docker state location') as refused:
            self.verify(docker_root=docker_root, state_mounts=(alias,))
        self.assertNotIn('unprinted-mount-name', str(refused.exception))

    def test_unparseable_or_linked_candidate_is_never_treated_as_production(self):
        marker = self.relocations / 'budget-ledger.sqlite3.bak'
        marker.write_text('synthetic unreadable content')
        with self.assertRaisesRegex(PreflightRefused, 'could not be read reliably') as refused:
            self.verify()
        self.assertNotIn('synthetic unreadable content', str(refused.exception))
        marker.unlink()
        marker.symlink_to(self.root / 'dangling')
        with self.assertRaisesRegex(PreflightRefused, 'verifiable regular SQLite'):
            self.verify()

    def test_active_sidecar_is_not_silently_ignored_by_immutable_sqlite(self):
        path, budget = self.ledger()
        self.assertTrue(budget.reserve())
        wal = path.with_name(path.name + '-wal')
        wal.write_bytes(b'active sidecar')
        with self.assertRaisesRegex(PreflightRefused, 'active or unreadable SQLite sidecar'):
            self.verify()
        self.assertEqual(wal.read_bytes(), b'active sidecar')

    def test_unreadable_relocation_root_refuses_with_no_path_to_the_ledger(self):
        real_scan = os.scandir

        def missing_permission(path):
            if path == self.relocations:
                raise PermissionError('secret-in-error')
            return real_scan(path)

        with patch('fold_preview_preflight.os.scandir', side_effect=missing_permission):
            with self.assertRaisesRegex(PreflightRefused, 'could not be read reliably') as refused:
                self.verify()
        self.assertNotIn('secret-in-error', str(refused.exception))
