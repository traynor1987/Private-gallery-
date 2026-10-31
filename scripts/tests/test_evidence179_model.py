"""Host behavior tests for the pinned build-only OCR model preparer."""
import hashlib
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / 'scripts/prepare_evidence179_model.sh'
PUBLIC_MODEL = Path(os.environ.get('EVIDENCE179_MODEL_CACHE', '/workspace/scratch/4181ccd09b2e/evidence179-research/eng.traineddata'))
SHA = '7d4322bd2a7749724879683fc3912cb542f19906c83bcc1a52132556427170b2'


class ModelPreparation(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.dir = Path(self.tmp.name)
        self.output = self.dir / 'assets/tessdata/eng.traineddata'
        self.bin = self.dir / 'bin'
        self.bin.mkdir()

    def run_prepare(self, cache=None, curl_body='exit 99\n'):
        curl = self.bin / 'curl'
        curl.write_text('#!/bin/sh\n' + curl_body)
        curl.chmod(0o755)
        env = dict(os.environ, PATH=str(self.bin) + ':' + os.environ['PATH'])
        env.pop('EVIDENCE179_MODEL_CACHE', None)
        if cache:
            env['EVIDENCE179_MODEL_CACHE'] = str(cache)
        return subprocess.run(['bash', str(SCRIPT), str(self.output)], env=env, capture_output=True, text=True)

    @unittest.skipUnless(PUBLIC_MODEL.exists(), 'public staged model unavailable')
    def test_verified_public_cache_prepares_exact_asset_without_network(self):
        result = self.run_prepare(PUBLIC_MODEL)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(hashlib.sha256(self.output.read_bytes()).hexdigest(), SHA)
        self.assertEqual(self.output.stat().st_size, 4113088)
        self.assertEqual(self.run_prepare().returncode, 0, 'valid existing output must not fetch')

    @unittest.skipUnless(PUBLIC_MODEL.exists(), 'public staged model unavailable')
    def test_download_accepts_only_fixed_official_https_model(self):
        import shlex
        call = self.dir / 'curl-arguments'
        result = self.run_prepare(curl_body='printf "%s\\n" "$@" > ' + shlex.quote(str(call)) + '\nwhile [ "$1" != "--output" ]; do shift; done\nshift\ncp ' + shlex.quote(str(PUBLIC_MODEL)) + ' "$1"\n')
        self.assertEqual(result.returncode, 0, result.stderr)
        args = call.read_text().splitlines()
        self.assertIn('https://raw.githubusercontent.com/tesseract-ocr/tessdata_fast/4.1.0/eng.traineddata', args)
        self.assertIn('--fail', args)
        self.assertIn('--proto-redir', args)
        self.assertIn('=https', args)
        self.assertEqual(hashlib.sha256(self.output.read_bytes()).hexdigest(), SHA)

    def test_bad_explicit_cache_fails_closed(self):
        bad = self.dir / 'bad'
        bad.write_bytes(bytes(4113088))
        self.assertNotEqual(self.run_prepare(bad).returncode, 0)
        self.assertFalse(self.output.exists())
        self.assertEqual(list(self.output.parent.glob('*.partial.*')), [])

    def test_partial_download_failure_leaves_no_asset(self):
        result = self.run_prepare(curl_body='while [ "$1" != "--output" ]; do shift; done\nshift\nprintf partial > "$1"\nexit 22\n')
        self.assertNotEqual(result.returncode, 0)
        self.assertFalse(self.output.exists())
        self.assertEqual(list(self.output.parent.glob('*.partial.*')), [])

    def test_successful_download_with_wrong_hash_is_rejected(self):
        result = self.run_prepare(curl_body='while [ "$1" != "--output" ]; do shift; done\nshift\nhead -c 4113088 /dev/zero > "$1"\n')
        self.assertNotEqual(result.returncode, 0)
        self.assertFalse(self.output.exists())
        self.assertEqual(list(self.output.parent.glob('*.partial.*')), [])

    @unittest.skipUnless(PUBLIC_MODEL.exists(), 'public staged model unavailable')
    def test_corrupted_output_cannot_be_accepted_as_cache(self):
        self.output.parent.mkdir(parents=True)
        self.output.write_bytes(b'corrupt')
        result = self.run_prepare(PUBLIC_MODEL)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(hashlib.sha256(self.output.read_bytes()).hexdigest(), SHA)

    def test_packaging_pins_model_and_runtime_adapter_has_no_fetch(self):
        import json
        manifest = json.loads((ROOT / 'app/src/main/assets/evidence179/model-manifest.json').read_text())
        self.assertEqual(manifest['eng.traineddata']['sha256'], SHA)
        self.assertEqual(manifest['eng.traineddata']['bytes'], 4113088)
        runtime = (ROOT / 'app/src/main/java/uk/co/traynor/privategallery/core/evidence/EvidenceOcr.kt').read_text()
        for forbidden in ['https://', 'http://', 'HttpURLConnection', 'OkHttpClient', 'URL(', 'openConnection(', 'contentResolver']:
            self.assertNotIn(forbidden, runtime)
        self.assertNotIn('.traineddata', [p.suffix for p in (ROOT / 'app/src/main/assets').rglob('*')])
        build = (ROOT / 'app/build.gradle.kts').read_text()
        self.assertIn('cz.adaptech.tesseract4android:tesseract4android:4.9.0', build)
        self.assertIn('addGeneratedSourceDirectory(', build)
        self.assertIn('PrepareEvidence179ModelTask::assetsDirectory', build)
        self.assertNotIn('assets.srcDir(evidence179Assets)', build)
        self.assertIn('dependsOn(prepareEvidence179Model)', build)

    def test_native_results_cannot_trigger_a_second_recognition(self):
        runtime = (ROOT / 'app/src/main/java/uk/co/traynor/privategallery/core/evidence/EvidenceOcr.kt').read_text()
        self.assertFalse('api.getUTF8Text(' in runtime, 'unsafe TessBaseAPI text getter remains')
        self.assertFalse('api.meanConfidence(' in runtime, 'unsafe TessBaseAPI confidence getter remains')
        self.assertIn('check(!hocr.isNullOrBlank())', runtime)


if __name__ == '__main__':
    unittest.main()
