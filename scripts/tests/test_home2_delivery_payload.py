"""Execute the actual signing workflow's staging body against harmless synthetic inputs."""
import hashlib
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile

import yaml

REPO = Path(__file__).resolve().parents[2]
REPORTS = ('compilation.json', 'baseline-build.gradle.kts', 'instrumentation-package.txt',
           'instrumentation-manifest.txt', 'compile.log')


class Home2DeliveryPayloadTest(unittest.TestCase):
    def stage(self, unexpected=None):
        workflow = yaml.safe_load((REPO / '.github/workflows/acceptance1762.yml').read_text())
        step = next(s for s in workflow['jobs']['home2-production']['steps']
                    if s.get('name') == 'Stage the one verified production APK and complete upgrade evidence')
        code = step['run'].split("python3 - <<'PY'\n", 1)[1].split('\nPY\n', 1)[0]
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            build = root / 'app/build'
            evidence = build / 'production-evidence'
            upgrade = evidence / 'from63'
            upgrade.mkdir(parents=True)
            source = '2' * 40
            candidate = build / 'outputs/apk/release/app-release.apk'
            candidate.parent.mkdir(parents=True)
            candidate.write_bytes(b'synthetic authorized application')
            digest = hashlib.sha256(candidate.read_bytes()).hexdigest()
            (upgrade / 'upgrade-result.txt').write_text('PASS: synthetic upgrade')
            (upgrade / 'upgrade-candidate-source-sha.txt').write_text(source)
            (upgrade / 'upgrade-candidate-sha256.txt').write_text(digest + ' application.apk')
            for phase in ('seed', 'verify', 'after-restart'):
                (upgrade / f'upgrade-{phase}.txt').write_text('OK (1 test)')
            runner = root / 'runner'
            inputs = runner / 'release1711-inputs'
            inputs.mkdir(parents=True)
            for name in ('github-run.json', 'github-jobs.json', 'github-artifact.json',
                         'baseline-github-run.json', 'baseline-github-job.json', 'baseline-github-artifact.json'):
                (inputs / name).write_text('{}')
            controls = runner / 'release1711-controls'
            controls.mkdir()
            (controls / 'review.txt').write_text('Synthetic reviewed inputs')
            compiled = runner / 'compiled'
            compiled.mkdir()
            for name in REPORTS:
                (compiled / name).write_text('receipt:' + name)
            (compiled / 'instrumentation.apk').write_bytes(b'synthetic compiled test harness')
            signed = runner / 'signed-instrumentation.apk'
            signed.write_bytes(b'synthetic production-target test harness')
            if unexpected:
                (evidence / unexpected).write_bytes(b'must never be published')
            env = dict(RUNNER_TEMP=str(runner), FROZEN_SOURCE=source,
                       PRIVATE_GALLERY_1711_COMPILED_DIR=str(compiled),
                       PRIVATE_GALLERY_1711_SIGNED_INSTRUMENTATION_APK=str(signed))
            prior = Path.cwd()
            try:
                os.chdir(root)
                with patch.dict(os.environ, env):
                    if unexpected:
                        with self.assertRaisesRegex(AssertionError, 'Evidence contains'):
                            exec(compile(code, '<actual workflow staging>', 'exec'), {})
                        self.assertFalse((build / 'NovaOS-1.7.30-visual-foundation-code64-evidence.zip').exists())
                        return
                    exec(compile(code, '<actual workflow staging>', 'exec'), {})
                self.assertEqual(candidate.read_bytes(), (build / 'NovaOS-1.7.30-visual-foundation-code64-owner-review.apk').read_bytes())
                with zipfile.ZipFile(build / 'NovaOS-1.7.30-visual-foundation-code64-evidence.zip') as zipped:
                    names = zipped.namelist()
                    self.assertFalse(any(Path(n).suffix.lower() in {'.apk', '.apks', '.aab', '.jks', '.keystore', '.zip'} for n in names))
                    for name in REPORTS:
                        self.assertEqual(('receipt:' + name).encode(), zipped.read('compiled-upgrade/' + name))
                    self.assertIn(b'synthetic DEMO data', zipped.read('REVIEW-NOTES.md'))
                    summary = json.loads(zipped.read('release-summary.json'))
                    self.assertEqual(digest, summary['apk_sha256'])
                    self.assertEqual(('1.7.30-visual-foundation', 64, 'PASS'), (summary['version'], summary['version_code'], summary['original_key_code63_upgrade']))
            finally:
                os.chdir(prior)

    def test_baseline_provenance_and_compilation_precede_credentials_for_existing_job(self):
        workflow=yaml.safe_load((REPO/'.github/workflows/acceptance1762.yml').read_text())
        self.assertEqual(8,len(workflow['jobs']))
        steps=workflow['jobs']['home2-production']['steps']
        fetch=next(s['with']['script'] for s in steps if s.get('name')=='Fetch original exact-source runtime API evidence and pinned signed baseline')
        for pin in ('run_id:42386518404','job_id:128444994749','artifact_id:13580371185',
                    "baselineJob.run_id!==42386518404", "baselineJob.name!=='home2-production'",
                    "baselineJob.conclusion!=='success'", "baselineJob.head_sha!=='278bcc5b1ba7f75eac73f2a171711a7255c8dae9'",
                    '60999212','ac871706d60359a2f08f12806bf146829b1d9e848d172d7e3edeb608507e8b6a'):
            self.assertIn(pin,fetch)
        compile_index=next(i for i,s in enumerate(steps) if s.get('name')=='Compile reviewed baseline-compatible fixture with no signing environment')
        gate_index=next(i for i,s in enumerate(steps) if s.get('name')=='Validate original runtime reports and independent production approval before signer access')
        self.assertLess(gate_index,compile_index)
        tools=next(s['run'] for s in steps if s.get('name')=='Install Android 17 tools and verify the original code63 APK')
        self.assertIn('cmdline-tools;22.0',tools)
        self.assertIn('platforms;android-37.1',tools)
        upgrade_index=next(i for i,s in enumerate(steps) if s.get('name')=='Original-key code63 to code64 upgrade and restart without uninstall or clear')
        upgrade=steps[upgrade_index]['with']
        self.assertEqual(('37.0','google_apis','x86_64','novaos1711-original-key-from63'),
                         (upgrade['api-level'],upgrade['target'],upgrade['arch'],upgrade['avd-name']))
        self.assertIs(True,upgrade['force-avd-creation'])
        self.assertIs(False,upgrade['disable-animations'])
        self.assertIn('-no-snapshot',upgrade['emulator-options'])
        stage_index=next(i for i,s in enumerate(steps) if s.get('name')=='Stage the one verified production APK and complete upgrade evidence')
        delivery_index=next(i for i,s in enumerate(steps) if s.get('name')=='Preserve verified delivery and evidence')
        self.assertLess(upgrade_index,stage_index)
        self.assertLess(stage_index,delivery_index)
        self.assertEqual('novaos-1.7.30-visual-foundation-code64-owner-review',steps[delivery_index]['with']['name'])
        for i,step in enumerate(steps):
            if 'secrets.PRIVATE_GALLERY_SIGNING_' in str(step): self.assertLess(compile_index,i)
        job=workflow['jobs']['home2-production']
        self.assertEqual('read',job['permissions']['contents'])
        self.assertIn("github.ref == 'refs/heads/feature/novaos-visual-foundation'",job['if'])
        self.assertIn("run.head_branch!=='feature/novaos-visual-foundation'",fetch)
        self.assertIn("baselineRun.head_branch!=='integration/novaos-1.7-home2'",fetch)
        self.assertNotIn('gh release',str(steps))
        notes=steps[stage_index]['run']
        self.assertIn('synthetic DEMO data',notes)
        self.assertIn('do not activate protection',notes)
        self.assertIn('Galaxy Z Fold acceptance remains pending',notes)

    def test_only_application_is_delivered_and_all_upgrade_receipts_remain(self):
        self.stage()

    def test_unexpected_installable_or_signing_payload_is_rejected_before_archiving(self):
        for name in ('unexpected.apk', 'unexpected.APK', 'split.apks', 'bundle.aab', 'signer.jks', 'signer.keystore', 'nested.zip'):
            with self.subTest(name=name):
                self.stage(name)


if __name__ == '__main__':
    unittest.main()
