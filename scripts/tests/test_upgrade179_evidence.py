"""Synthetic protocol receipts exercise fail-closed upgrade evidence accounting."""
import importlib.util
from pathlib import Path
import tempfile
import unittest

ROOT=Path(__file__).resolve().parents[2]
class UpgradeEvidence179(unittest.TestCase):
    def setUp(self):
        spec=importlib.util.spec_from_file_location('upgrade179',ROOT/'scripts/validate_upgrade179.py')
        self.gate=importlib.util.module_from_spec(spec);spec.loader.exec_module(self.gate)
        self.tmp=tempfile.TemporaryDirectory();self.addCleanup(self.tmp.cleanup);self.root=Path(self.tmp.name)
        self.source='1'*40;self.apk='2'*64;self.fixture=b'synthetic fixture uk.co.traynor.privategallery.hiddenprototype'
        signature='Verified using v2 scheme (APK Signature Scheme v2): true\nNumber of signers: 1\nSigner #1 certificate SHA-256 digest: '+self.gate.CERTIFICATE+'\n'
        for phase,code,version in [('baseline',50,'1.7.8'),('candidate',51,'1.7.9')]:
            (self.root/f'upgrade-{phase}-package.txt').write_text(f"package: name='uk.co.traynor.privategallery' versionCode='{code}' versionName='{version}'\nsdkVersion:'37'\ntargetSdkVersion:'37'\n")
            (self.root/f'upgrade-{phase}-signature.txt').write_text(signature)
            (self.root/f'upgrade-{phase}-sha256.txt').write_text((self.gate.BASELINE_APK if phase=='baseline' else self.apk)+'  fixture.apk\n')
            (self.root/f'upgrade-{phase}-source-sha.txt').write_text(self.gate.BASELINE_SOURCE if phase=='baseline' else self.source)
        (self.root/'upgrade-instrumentation-signature.txt').write_text(signature)
        (self.root/'upgrade-instrumentation-sha256.txt').write_text('3'*64+'  instrumentation.apk\n')
        (self.root/'upgrade-fixture-sha256.txt').write_text(self.gate.digest(self.fixture)+'  fixture.kt\n')
        changed=self.fixture.replace(b'uk.co.traynor.privategallery.hiddenprototype',b'uk.co.traynor.privategallery')
        (self.root/'upgrade-compiled-fixture-sha256.txt').write_text(self.gate.digest(changed)+'  compiled.kt\n')
        for phase in ['seed','verify','after-restart']:(self.root/f'upgrade-{phase}.txt').write_text('UpgradeFixtureTest:.\nTime: 1.0\nOK (1 test)\n')
        (self.root/'upgrade-result.txt').write_text('PASS: original signed code50 -> code51 in-place upgrade/restart; preserved\n')
    def check(self):return self.gate.validate(self.root,self.source,self.apk,self.fixture)
    def test_complete_synthetic_receipts(self):self.assertEqual(self.check()['decision'],'PASS')
    def test_wrong_source_hash_identity_certificate_and_failed_or_duplicate_phase_refused(self):
        variants={'upgrade-baseline-source-sha.txt':'0'*40,'upgrade-candidate-sha256.txt':'4'*64,'upgrade-candidate-package.txt':"package: name='uk.co.traynor.privategallery' versionCode='51' versionName='1.7.9'\nsdkVersion:'37'\ntargetSdkVersion:'37'\napplication-debuggable\n",'upgrade-baseline-signature.txt':'untrusted','upgrade-instrumentation-signature.txt':'untrusted','upgrade-verify.txt':'FAILURES!!!\nOK (1 test)\n','upgrade-after-restart.txt':'OK (1 test)\nOK (1 test)\n','upgrade-fixture-sha256.txt':'0'*64,'upgrade-compiled-fixture-sha256.txt':'0'*64}
        for name,bad in variants.items():
            with self.subTest(name=name):
                path=self.root/name;prior=path.read_text();path.write_text(bad)
                try:
                    with self.assertRaises(ValueError):self.check()
                finally:path.write_text(prior)
    def test_missing_restart_and_zero_unfrozen_source_refused(self):
        (self.root/'upgrade-after-restart.txt').unlink()
        with self.assertRaises((ValueError,OSError)):self.check()
        with self.assertRaises(ValueError):self.gate.validate(self.root,'0'*40,self.apk,self.fixture)
if __name__=='__main__':unittest.main()
