"""Build88 identity and authentic Build87 upgrade contract; no signing or release claims."""
from pathlib import Path
import unittest
from unittest.mock import patch

import fetch_code81_upgrade_baseline as baseline
from scripts.tests import test_code81_main_release_controls as main_controls

ROOT = Path(__file__).resolve().parents[2]


class Build88ReleaseControls(unittest.TestCase):
    def test_current_candidate_and_baseline_are_consistent_across_existing_controls(self):
        build = (ROOT / 'app/build.gradle.kts').read_text()
        self.assertRegex(build, r'versionCode\s*=\s*88\b')
        self.assertIn('versionName = "1.7.42"', build)
        configuration = (ROOT / 'scripts/release_configuration1711.sh').read_text()
        for line in ('release_version=1.7.42', 'release_code=88',
                     'baseline_version=1.7.41', 'baseline_code=87',
                     'baseline_sha=9075b111a87e9dbcf39566b71c2763b380b58088'):
            self.assertIn(line, configuration.splitlines())
        self.assertEqual(151582387, baseline.BASELINE_SIZE)
        self.assertEqual('05b1122c80e6746e242419d08f48882d516ac6730eda4a4befa57a8e83afd0d4', baseline.BASELINE_SHA256)
        self.assertEqual('https://github.com/traynor1987/Private-gallery-/releases/download/v1.7.41/private-gallery-release.apk', baseline.BASELINE_URL)
        fixture = (ROOT / 'docs/release1711/UpgradeFixtureTest.kt').read_text()
        self.assertIn('baselineCode == 87 && candidateCode == 88', fixture)
        self.assertIn('assertEquals(88L,available.release?.versionCode)', fixture)
        self.assertIn('original=87 candidate=88', fixture)
        live = (ROOT / 'docs/build85/verify_live_build84.py').read_text()
        for text in ("installed() == 87", "installed() != 88", "1.7.42 · Build 88 available", "1.7.42 · Up to date", 'baseline_code=87, installed_code=88'):
            self.assertIn(text, live)

    def test_code83_preparation_run_remains_bound_to_exact_source_and_main_ancestry(self):
        import tempfile
        from scripts.tests.test_code81_main_release_controls import gate
        with tempfile.TemporaryDirectory() as directory:
            values = main_controls.MainReleaseControls().fixture(directory)
            branch = 'feature/novaos-code83-cloud-teacher'
            values[0]['runtime_branch'] = branch
            values[2]['head_branch'] = branch
            values[1] += ('Runtime branch: '+branch+'\n').encode()
            values[0]['review_sha256'] = gate.sha(values[1])
            with patch.object(gate, 'runtime_cases', return_value=329) as reports:
                proof = gate.validate(*values)
            reports.assert_called_once()
            self.assertEqual(329, proof['validated_case_count'])
            values[2]['head_sha'] = '2'*40
            with patch.object(gate, 'runtime_cases') as reports, self.assertRaisesRegex(ValueError, 'runtime source/branch/event'):
                gate.validate(*values)
            reports.assert_not_called()
        workflow = (ROOT / '.github/workflows/sanctuary-production.yml').read_text()
        self.assertIn("['git','merge-base','--is-ancestor',marker['source'],head]", workflow)


if __name__ == '__main__':
    unittest.main()
