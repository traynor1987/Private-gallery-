"""Main-branch provenance checks, independent of Android compilation and emulator execution."""
import copy
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import sys
import subprocess

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from scripts.tests import test_upgrade1711_controls as historical
gate = historical.gate


class MainReleaseControls(unittest.TestCase):
    def test_repair_branch_runs_the_same_pinned_v1_and_current_engine_guards_as_main(self):
        import yaml
        root = Path(__file__).resolve().parents[2]
        workflow = yaml.safe_load((root / '.github/workflows/fast-development.yml').read_text())
        step = next(s for s in workflow['jobs']['home2-integration']['steps'] if s.get('name') == 'Verify browser engine preservation and isolated APK')
        guard = step['run'].split('mkdir -p app/build/home2-evidence',1)[0]
        with tempfile.TemporaryDirectory() as directory:
            temporary=Path(directory);bins=temporary/'bin';bins.mkdir();trace=temporary/'trace'
            for command in ('git','python3'):
                executable=bins/command
                executable.write_text('#!/bin/bash\nprintf "%s %s\\n" "'+command+'" "$*" >> "$NOVA_GUARD_TRACE"\n')
                executable.chmod(0o755)
            for branch in ('main','feature/novaos-build87-brain-status','feature/novaos-code83-cloud-teacher','feature/novaos-guardian-retention89'):
                trace.write_text('')
                subprocess.run(['/bin/bash','-ec',guard],cwd=root,check=True,
                    env=dict(os.environ,GITHUB_REF='refs/heads/'+branch,RUNNER_TEMP=directory,NOVA_GUARD_TRACE=str(trace),PATH=str(bins)+':'+os.environ['PATH']))
                commands=trace.read_text().splitlines()
                checks=[s for s in commands if s.startswith('python3 ')]
                self.assertEqual(2,len(checks),(branch,commands))
                self.assertTrue(checks[0].endswith('/scripts/verify_v1_preservation1711.py'))
                self.assertEqual('python3 scripts/verify_sanctuary_browser_scope.py',checks[1])
                self.assertTrue(any('worktree add --detach' in s and '3f2bac952d68f54566251126329b643da498ca44' in s for s in commands))

    def fixture(self, directory):
        values = historical.Upgrade1711Controls().gate_fixture(directory)
        marker = values[0]
        marker.update(baseline_code=88, candidate_code=89)
        values[1] += (
            b"Authorization: authenticated owner push\n"
            b"Release scope: public stable after disposable upgrade verification\n"
            b"Physical Fold acceptance: pending\n"
            b"Runtime workflow run: 123\n"
        )
        marker["review_sha256"] = gate.sha(values[1])
        values[4]["digest"] = "sha256:" + marker["runtime_archive_sha256"]
        return values

    def test_main_runs_the_same_modern_shell_checks_as_the_feature_branch(self):
        import yaml
        root = Path(__file__).resolve().parents[2]
        workflow = yaml.safe_load((root / ".github/workflows/fast-development.yml").read_text())
        job = workflow["jobs"]["home2-integration"]
        for step in job["steps"]:
            script = step.get("run", step.get("with", {}).get("script", ""))
            for line in script.splitlines():
                if "GITHUB_REF" in line and "feature/novaos-code81-core-apps" in line:
                    self.assertIn("refs/heads/main", line)
        self.assertIn("refs/heads/main", next(step for step in job["steps"] if step.get("name") == "Focused Android 17 integration and privacy tests")["env"]["NOVA_VISUAL_FULL"])

    def test_main_source_passes_provenance_before_separate_runtime_report_validation(self):
        with tempfile.TemporaryDirectory() as directory:
            values = self.fixture(directory)
            self.assertEqual("main", values[2]["head_branch"])
            with patch.object(gate, "runtime_cases", return_value=1) as reports:
                proof = gate.validate(*values)
            reports.assert_called_once()
            self.assertEqual("READY_FOR_GATED_SIGNER", proof["decision"])

    def test_other_branch_cannot_supply_production_runtime_evidence(self):
        with tempfile.TemporaryDirectory() as directory:
            original = self.fixture(directory)
            for branch in ("feature/novaos-code81-core-apps", "release/unreviewed"):
                values = copy.deepcopy(original)
                values[2]["head_branch"] = branch
                with self.subTest(branch=branch), patch.object(gate, "runtime_cases") as reports:
                    with self.assertRaisesRegex(ValueError, "runtime source/branch/event"):
                        gate.validate(*values)
                    reports.assert_not_called()

    def test_changed_source_or_artifact_invalidates_provenance(self):
        with tempfile.TemporaryDirectory() as directory:
            original = self.fixture(directory)
            for index, field, value in ((2, "head_sha", "2" * 40), (4, "digest", "sha256:" + "0" * 64)):
                values = copy.deepcopy(original)
                values[index][field] = value
                with self.subTest(field=field), patch.object(gate, "runtime_cases") as reports:
                    with self.assertRaises(ValueError):
                        gate.validate(*values)
                    reports.assert_not_called()

    def test_provenance_alone_cannot_replace_required_original_reports(self):
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaisesRegex(ValueError, "Missing or duplicate focused cases"):
                gate.validate(*self.fixture(directory))


if __name__ == "__main__":
    unittest.main()
