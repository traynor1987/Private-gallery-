import hashlib
import importlib.util
from pathlib import Path
import sys
import tempfile
import unittest

SCRIPTS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(SCRIPTS))
SPEC = importlib.util.spec_from_file_location('platform_evidence', SCRIPTS / 'verify_release1751_platforms.py')
M = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(M)


class PlatformEvidenceTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.apk = self.root / "tested-debug.apk"
        self.apk.write_bytes(b"synthetic tested APK identity")
        digest = hashlib.sha256(self.apk.read_bytes()).hexdigest()
        for platform, sdk in [('api36', '36'), ('api37', '37')]:
            base = self.root / platform
            self.write(base / 'source-sha.txt', 'tested-source')
            self.write(base / 'sdk.txt', sdk)
            self.write(base / 'command-exit-status.txt', '0')
            self.write(base / 'build-fingerprint.txt', 'synthetic-build')
            d = base / 'diagnostics'
            self.write(d / 'apk-sha256.txt', digest + '  tested-debug.apk')
            self.write(d / 'upgrade-candidate-sha256.txt', digest + '  tested-debug.apk')
            for name in ['professional-ui/source-sha.txt', 'full-regression-source-sha.txt', 'upgrade-candidate-source-sha.txt']:
                self.write(d / name, 'tested-source')
            for name, count in [('professional-ui/results', 115), ('hidden-browser-preflight', 74)]:
                self.report(d / name, count)
            self.report(base / 'full-results', 389)
            for name in ['process-seed', 'process-verify', 'camera-process-seed', 'camera-process-verify']:
                self.write(d / 'hidden-browser-preflight' / (name + '.txt'), 'OK (1 test)')
            for name in ['seed', 'verify', 'after-restart']:
                self.write(d / ('upgrade-' + name + '.txt'), 'OK (1 test)')
            self.write(d / 'upgrade-result.txt', 'PASS: synthetic upgrade')

    def write(self, path, value):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(value)

    def report(self, path, count):
        self.write(path / 'TEST-synthetic.xml', '<testsuite tests="' + str(count) + '">' + ''.join(
            '<testcase classname="synthetic" name="case' + str(i) + '"/>' for i in range(count)) + '</testsuite>')

    def test_both_fresh_platforms_required(self):
        self.assertEqual({'api36', 'api37'}, set(M.verify(self.root, 'tested-source', self.apk)['platforms']))

    def test_wrong_source_rejected(self):
        self.write(self.root / 'api37/diagnostics/full-regression-source-sha.txt', 'older-source')
        with self.assertRaises(ValueError): M.verify(self.root, 'tested-source', self.apk)

    def test_wrong_platform_rejected(self):
        self.write(self.root / 'api37/sdk.txt', '36')
        with self.assertRaises(ValueError): M.verify(self.root, 'tested-source', self.apk)

    def test_missing_full_case_rejected(self):
        self.report(self.root / 'api37/full-results', 388)
        with self.assertRaises(ValueError): M.verify(self.root, 'tested-source', self.apk)

    def test_failure_and_skip_rejected(self):
        path = self.root / 'api36/full-results/TEST-synthetic.xml'
        original = path.read_text()
        for outcome in ['failure', 'skipped']:
            self.write(path, original.replace('name="case0"/>', 'name="case0"><' + outcome + '/></testcase>'))
            with self.assertRaises(ValueError): M.verify(self.root, 'tested-source', self.apk)

    def test_different_upgrade_apk_rejected(self):
        self.write(self.root / "api37/diagnostics/upgrade-candidate-sha256.txt", "different-hash  changed.apk")
        with self.assertRaises(ValueError): M.verify(self.root, "tested-source", self.apk)

    def test_restart_without_passing_proof_rejected(self):
        self.write(self.root / 'api37/diagnostics/upgrade-after-restart.txt', 'INSTRUMENTATION_FAILED')
        with self.assertRaises(ValueError): M.verify(self.root, 'tested-source', self.apk)


if __name__ == '__main__': unittest.main()
