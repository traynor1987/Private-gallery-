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
            upgrade = evidence / 'from57'
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
                         'baseline-github-run.json', 'baseline-github-artifact.json'):
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
                        self.assertFalse((build / 'NovaOS-1.7.11.2-home2-code58-evidence.zip').exists())
                        return
                    exec(compile(code, '<actual workflow staging>', 'exec'), {})
                self.assertEqual(candidate.read_bytes(), (build / 'NovaOS-1.7.11.2-home2-code58-owner-acceptance.apk').read_bytes())
                with zipfile.ZipFile(build / 'NovaOS-1.7.11.2-home2-code58-evidence.zip') as zipped:
                    names = zipped.namelist()
                    self.assertFalse(any(Path(n).suffix.lower() in {'.apk', '.apks', '.aab', '.jks', '.keystore', '.zip'} for n in names))
                    for name in REPORTS:
                        self.assertEqual(('receipt:' + name).encode(), zipped.read('compiled-upgrade/' + name))
                    summary = json.loads(zipped.read('release-summary.json'))
                    self.assertEqual(digest, summary['apk_sha256'])
                    self.assertEqual(('1.7.11.2', 58, 'PASS'), (summary['version'], summary['version_code'], summary['original_key_code57_upgrade']))
            finally:
                os.chdir(prior)

    def test_only_application_is_delivered_and_all_upgrade_receipts_remain(self):
        self.stage()

    def test_unexpected_installable_or_signing_payload_is_rejected_before_archiving(self):
        for name in ('unexpected.apk', 'unexpected.APK', 'split.apks', 'bundle.aab', 'signer.jks', 'signer.keystore', 'nested.zip'):
            with self.subTest(name=name):
                self.stage(name)


if __name__ == '__main__':
    unittest.main()
