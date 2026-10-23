#!/usr/bin/env python3
"""Host checks for release-branch isolation and fresh UI evidence."""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]

class ReleaseConfigurationTest(unittest.TestCase):
    def config(self, ref):
        return subprocess.run(['bash', '-c', 'source scripts/release_configuration.sh; printf "%s %s %s %s" "$release_version" "$release_code" "$baseline_code" "$baseline_sha"'], cwd=ROOT, env={**os.environ, 'GITHUB_REF': ref}, capture_output=True, text=True)

    def test_new_branch_requires_code33_to_34(self):
        result = self.config('refs/heads/release/1.7.1-professional-ui')
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(result.stdout, '1.7.1 34 33 18789f0f595abdfcd54e931671a82c23289af1ea')

    def test_old_branch_keeps_code32_to_33(self):
        result = self.config('refs/heads/release/1.7-encrypted-camera')
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(result.stdout, '1.7 33 32 7765c79874556e8093612b64eeb529e095c9c287')

    def test_other_refs_fail_closed(self):
        for ref in ['refs/heads/main', 'refs/pull/12/merge', '', 'refs/heads/release/1.7.1-professional-ui-extra']:
            with self.subTest(ref=ref):
                self.assertNotEqual(self.config(ref).returncode, 0)

class FreshEvidenceTest(unittest.TestCase):
    def verify(self, xml):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory)
            (path / 'TEST-synthetic.xml').write_text(xml)
            return subprocess.run(['python3', str(ROOT / 'scripts/verify_professional_ui_results.py'), str(path), 'example.ProfessionalUiTest'], capture_output=True, text=True)

    def test_passing_required_class(self):
        self.assertEqual(self.verify('<testsuite><testcase classname="example.ProfessionalUiTest" name="visible"/></testsuite>').returncode, 0)

    def test_failed_or_skipped_tests_fail_closed(self):
        for outcome in ['failure', 'error', 'skipped']:
            with self.subTest(outcome=outcome):
                self.assertNotEqual(self.verify(f'<testsuite><testcase classname="example.ProfessionalUiTest" name="visible"><{outcome}/></testcase></testsuite>').returncode, 0)

    def test_existing_unrelated_assumption_skip_is_reported(self):
        result = self.verify('<testsuite><testcase classname="example.ProfessionalUiTest" name="visible"/><testcase classname="example.OptionalCapabilityTest" name="capability"><skipped/></testcase></testsuite>')
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn('example.OptionalCapabilityTest#capability', result.stdout)

    def test_missing_required_class_fails_closed(self):
        self.assertNotEqual(self.verify('<testsuite><testcase classname="example.OtherTest" name="visible"/></testsuite>').returncode, 0)

if __name__ == '__main__':
    unittest.main()
