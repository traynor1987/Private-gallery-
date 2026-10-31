"""Native load-segment alignment is a build gate, not source-tag evidence."""
import importlib.util
from pathlib import Path
import struct
import unittest
import tempfile
import hashlib
import zipfile

class Distribution(unittest.TestCase):
    def setUp(self):
        p=Path(__file__).resolve().parents[1]/'verify_evidence179_distribution.py'
        spec=importlib.util.spec_from_file_location('distribution179',p)
        self.module=importlib.util.module_from_spec(spec);spec.loader.exec_module(self.module)
    def elf(self, alignment=16384, offset=0, address=0):
        b=bytearray(120);b[:6]=b'\x7fELF\x02\x01'
        struct.pack_into('<Q',b,32,64);struct.pack_into('<HH',b,54,56,1)
        struct.pack_into('<IIQQQQQQ',b,64,1,5,offset,address,0,0,0,alignment)
        return bytes(b)
    def test_16k_elf_accepted(self):
        self.assertEqual(self.module.check_elf(self.elf()),1)
    def test_small_alignment_incongruence_truncated_and_wrong_elf_rejected(self):
        for value in [self.elf(4096),self.elf(offset=1),self.elf()[:80],b'not elf']:
            with self.assertRaises(ValueError): self.module.check_elf(value)

    def test_packaged_model_hash_and_requested_abi_are_mandatory(self):
        # Tiny synthetic artifact exercises actual archive/hash/native validation.
        model=b'public synthetic OCR fixture'
        self.module.MODEL_SHA=hashlib.sha256(model).hexdigest()
        self.module.MODEL_BYTES=len(model)
        with tempfile.TemporaryDirectory() as directory:
            apk=Path(directory)/'fixture.apk'
            def write(value):
                with zipfile.ZipFile(apk,'w') as archive:
                    archive.writestr('assets/tessdata/eng.traineddata',value)
                    archive.writestr('lib/x86_64/libtesseract.so',self.elf())
            write(model)
            report=self.module.verify(apk,['x86_64'])
            self.assertEqual(len(report['ocr_native']),1)
            with self.assertRaises(ValueError): self.module.verify(apk,['arm64-v8a'])
            write(b'X'+model[1:])
            with self.assertRaises(ValueError): self.module.verify(apk,['x86_64'])

if __name__=='__main__': unittest.main()
