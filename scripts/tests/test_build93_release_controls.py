"""Build95 identity and authentic Build94 upgrade contract; no signing or release claims."""
from pathlib import Path
import subprocess
import unittest
from unittest.mock import patch

import fetch_code81_upgrade_baseline as baseline
from scripts.tests import test_code81_main_release_controls as main_controls

ROOT = Path(__file__).resolve().parents[2]


class Build94ReleaseControls(unittest.TestCase):
    def test_guardian_job_remains_private_and_requires_android_job_binding(self):
        import xml.etree.ElementTree as ET
        manifest = ET.parse(ROOT / 'app/src/main/AndroidManifest.xml').getroot()
        android = '{http://schemas.android.com/apk/res/android}'
        job, = [service for service in manifest.findall('application/service')
                if service.get(android + 'name') == '.core.guardian.GuardianCloudJob']
        self.assertEqual('false', job.get(android + 'exported'))
        self.assertEqual('android.permission.BIND_JOB_SERVICE', job.get(android + 'permission'))

    def test_single_bracket_shell_conditions_never_embed_boolean_operators(self):
        import yaml
        workflow = yaml.safe_load((ROOT / '.github/workflows/fast-development.yml').read_text())
        for job in workflow['jobs'].values():
            for step in job.get('steps', []):
                self.assertNotRegex(step.get('run', ''), r'(?<!\[)\[[^\[\]\n]*(?:&&|\|\|)[^\[\]\n]*\](?!\])', step.get('name'))

    def test_current_candidate_and_baseline_are_consistent_across_existing_controls(self):
        build = (ROOT / 'app/build.gradle.kts').read_text()
        self.assertRegex(build, r'versionCode\s*=\s*95\b')
        self.assertIn('versionName = "1.7.49"', build)
        configuration = (ROOT / 'scripts/release_configuration1711.sh').read_text()
        for line in ('release_version=1.7.49', 'release_code=95',
                     'baseline_version=1.7.48', 'baseline_code=94',
                     'baseline_sha=46b9c78c15bcbe18d129e28be8677d21c59ca838'):
            self.assertIn(line, configuration.splitlines())
        self.assertEqual(151959239, baseline.BASELINE_SIZE)
        self.assertEqual('165e49a35bafc1134b08116daf3fe62cfcbd7e85df4c433c50a4a0fe14351125', baseline.BASELINE_SHA256)
        self.assertEqual('https://github.com/traynor1987/Private-gallery-/releases/download/v1.7.48/private-gallery-release.apk', baseline.BASELINE_URL)
        fixture = (ROOT / 'docs/release1711/UpgradeFixtureTest.kt').read_text()
        self.assertIn('baselineCode == 94 && candidateCode == 95', fixture)
        self.assertIn('assertEquals(95L,available.release?.versionCode)', fixture)
        self.assertIn('original=94 candidate=95', fixture)
        live = (ROOT / 'docs/build85/verify_live_build84.py').read_text()
        for text in ("installed() == 94", "installed() != 95", "1.7.49 · Build 95 available", "1.7.49 · Up to date", 'baseline_code=94, installed_code=95'):
            self.assertIn(text, live)

    def test_staging_metadata_uses_the_same_candidate_identity_as_signing(self):
        import json
        import sys
        import tempfile
        import yaml

        workflow = yaml.safe_load((ROOT / '.github/workflows/sanctuary-production.yml').read_text())
        stage = next(step['run'] for step in workflow['jobs']['sanctuary-production']['steps']
                     if step.get('name') == 'Stage and verify stable update assets')
        generator = stage.split('python3 - "$stage" <<\'PY\'\n', 1)[1].split('\nPY\n', 1)[0]
        configuration = dict(line.split('=', 1) for line in
                             (ROOT / 'scripts/release_configuration1711.sh').read_text().splitlines()
                             if line.startswith(('release_version=', 'release_code=')))
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            digest = 'a' * 64
            (root / 'private-gallery-release.apk.sha256').write_text(f'{digest}  private-gallery-release.apk\n')
            subprocess.run([sys.executable, '-c', generator, directory], check=True)
            metadata = json.loads((root / 'private-gallery-release.metadata.json').read_text())
            self.assertEqual(configuration['release_version'], metadata['versionName'])
            self.assertEqual(int(configuration['release_code']), metadata['versionCode'])
            self.assertEqual(digest, metadata['apkSha256'])

    def test_retention_preparation_run_remains_bound_to_exact_source_and_main_ancestry(self):
        import tempfile
        from scripts.tests.test_code81_main_release_controls import gate
        with tempfile.TemporaryDirectory() as directory:
            values = main_controls.MainReleaseControls().fixture(directory)
            branch = 'feature/novaos-guardian-fold-acceptance'
            values[0]['runtime_branch'] = branch
            values[2]['head_branch'] = branch
            values[1] += ('Runtime branch: '+branch+'\n').encode()
            values[0]['review_sha256'] = gate.sha(values[1])
            with patch.object(gate, 'runtime_cases', return_value=343) as reports:
                proof = gate.validate(*values)
            reports.assert_called_once()
            self.assertEqual(343, proof['validated_case_count'])
            values[2]['head_sha'] = '2'*40
            with patch.object(gate, 'runtime_cases') as reports, self.assertRaisesRegex(ValueError, 'runtime source/branch/event'):
                gate.validate(*values)
            reports.assert_not_called()
        workflow = (ROOT / '.github/workflows/sanctuary-production.yml').read_text()
        self.assertIn("['git','merge-base','--is-ancestor',marker['source'],head]", workflow)


if __name__ == '__main__':
    unittest.main()
