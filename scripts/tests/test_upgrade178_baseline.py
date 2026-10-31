"""Archived baseline refuses substitution; unit ZIP bytes are synthetic, never evidence."""
import hashlib
import importlib.util
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch
import zipfile

ROOT=Path(__file__).resolve().parents[2]
SPEC=importlib.util.spec_from_file_location('baseline178',ROOT/'scripts/verify_upgrade178_baseline.py')
GATE=importlib.util.module_from_spec(SPEC);SPEC.loader.exec_module(GATE)

class OriginalBaseline178Test(unittest.TestCase):
    def test_archive_requires_exact_checksum_single_apk_source_and_apk_bytes(self):
        for kind in ('valid','archive','source','apk','multiple','missing'):
            with self.subTest(kind=kind),tempfile.TemporaryDirectory() as directory:
                archive=Path(directory)/'artifact.zip';apk=b'unit APK bytes'
                with zipfile.ZipFile(archive,'w') as out:
                    out.writestr('production-evidence/source-sha.txt',GATE.SOURCE_SHA if kind!='source' else '0'*40)
                    if kind!='missing':out.writestr('app-release.apk',apk if kind!='apk' else b'substituted')
                    if kind=='multiple':out.writestr('extra.apk',apk)
                digest=hashlib.sha256(archive.read_bytes()).hexdigest()
                with patch.object(GATE,'ARCHIVE_SHA256',digest if kind!='archive' else '0'*64),patch.object(GATE,'APK_SHA256',hashlib.sha256(apk).hexdigest()):
                    if kind=='valid':self.assertEqual(apk,GATE.validated_apk(archive))
                    else:
                        with self.assertRaises(ValueError):GATE.validated_apk(archive)

    def test_runner_refuses_missing_wrong_or_nonproduction_baseline_before_adb(self):
        with tempfile.TemporaryDirectory() as directory:
            apk=Path(directory)/'wrong.apk';apk.write_bytes(b'wrong baseline')
            base={**os.environ,'GITHUB_ACTIONS':'true','GITHUB_REF':'refs/heads/release/1.7.8-private-apps',
                  'PRODUCTION_UPGRADE':'1','PRIVATE_GALLERY_UPGRADE_BASELINE':'48'}
            base.pop('PRIVATE_GALLERY_178_BASELINE_APK',None)
            variants=[base,{**base,'PRIVATE_GALLERY_178_BASELINE_APK':str(apk)},
                      {**base,'PRODUCTION_UPGRADE':'0','PRIVATE_GALLERY_178_BASELINE_APK':str(apk)},
                      {**base,'PRIVATE_GALLERY_UPGRADE_BASELINE':'45','PRIVATE_GALLERY_178_BASELINE_APK':str(apk)}]
            for env in variants:
                result=subprocess.run(['bash','scripts/verify_synthetic_upgrade178.sh'],cwd=ROOT,env=env,text=True,capture_output=True)
                self.assertNotEqual(0,result.returncode)
                self.assertNotIn('adb',result.stderr)

    def test_runner_builds_only_instrumentation_and_installs_archived_baseline(self):
        text=(ROOT/'scripts/verify_synthetic_upgrade178.sh').read_text()
        self.assertIn('baseline_apk="$archived_baseline"',text)
        self.assertIn('(cd "$baseline_dir" && ./gradlew assembleDebugAndroidTest)',text)
        self.assertIn('adb install "$baseline_apk"',text)
        self.assertIn(GATE.APK_SHA256,text)
        self.assertIn('upgrade-instrumentation-signature.txt',text)
        self.assertNotIn('./gradlew "$build_task" assembleDebugAndroidTest',text)

if __name__=='__main__':unittest.main()
