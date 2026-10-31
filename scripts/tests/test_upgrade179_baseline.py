"""Synthetic ZIP validation tests; real original-key upgrade is a separate mandatory gate."""
import hashlib
import importlib.util
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile

ROOT=Path(__file__).resolve().parents[2]
class Baseline179(unittest.TestCase):
    def setUp(self):
        spec=importlib.util.spec_from_file_location('baseline179',ROOT/'scripts/verify_upgrade179_baseline.py')
        self.gate=importlib.util.module_from_spec(spec);spec.loader.exec_module(self.gate)
    def test_exact_archive_source_single_apk_hash_and_size(self):
        for kind in ['valid','archive','source','apk','size','multiple','missing']:
            with self.subTest(kind=kind),tempfile.TemporaryDirectory() as directory:
                archive=Path(directory)/'synthetic.zip';apk=b'synthetic original bytes'
                with zipfile.ZipFile(archive,'w') as out:
                    out.writestr('production-evidence/source-sha.txt',self.gate.SOURCE_SHA if kind!='source' else '0'*40)
                    if kind!='missing':out.writestr('app-release.apk',apk if kind!='apk' else b'substitute')
                    if kind=='multiple':out.writestr('extra.apk',apk)
                digest=hashlib.sha256(archive.read_bytes()).hexdigest()
                with patch.object(self.gate,'ARCHIVE_SHA256',digest if kind!='archive' else '0'*64),patch.object(self.gate,'APK_SHA256',hashlib.sha256(apk).hexdigest()),patch.object(self.gate,'APK_BYTES',len(apk)+(1 if kind=='size' else 0)):
                    if kind=='valid':self.assertEqual(self.gate.validated_apk(archive),apk)
                    else:
                        with self.assertRaises(ValueError):self.gate.validated_apk(archive)
if __name__=='__main__':unittest.main()
