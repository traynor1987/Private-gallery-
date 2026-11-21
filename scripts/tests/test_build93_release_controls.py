"""Build96 identity and authentic Build95 upgrade contract; no signing or release claims."""
from pathlib import Path
import subprocess
import unittest
from unittest.mock import patch

import fetch_code81_upgrade_baseline as baseline
from scripts.tests import test_code81_main_release_controls as main_controls

ROOT = Path(__file__).resolve().parents[2]


class Build95ReleaseControls(unittest.TestCase):
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
        self.assertRegex(build, r'versionCode\s*=\s*96\b')
        self.assertIn('versionName = "1.7.50"', build)
        configuration = (ROOT / 'scripts/release_configuration1711.sh').read_text()
        for line in ('release_version=1.7.50', 'release_code=96',
                     'baseline_version=1.7.49', 'baseline_code=95',
                     'baseline_sha=b003dafbad69a46d7f83896738030200a0dd0df4'):
            self.assertIn(line, configuration.splitlines())
        self.assertEqual(152057539, baseline.BASELINE_SIZE)
        self.assertEqual('27547f5630d5102bfd115db5fa96d598fb8d71ec4a3fc5425ce046eb1319087c', baseline.BASELINE_SHA256)
        self.assertEqual('https://github.com/traynor1987/Private-gallery-/releases/download/v1.7.49/private-gallery-release.apk', baseline.BASELINE_URL)
        fixture = (ROOT / 'docs/release1711/UpgradeFixtureTest.kt').read_text()
        self.assertIn('baselineCode == 95 && candidateCode == 96', fixture)
        self.assertIn('assertEquals(96L,available.release?.versionCode)', fixture)
        self.assertIn('original=95 candidate=96', fixture)
        live = (ROOT / 'docs/build85/verify_live_build84.py').read_text()
        for text in ("installed() == 95", "installed() != 96", "1.7.50 · Build 96 available", "1.7.50 · Up to date", 'baseline_code=95, installed_code=96'):
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
