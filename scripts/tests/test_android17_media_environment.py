import sys,unittest,tempfile,json
from pathlib import Path
from unittest.mock import patch
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
import verify_android17_media_environment as V
class MediaEnvironmentTest(unittest.TestCase):
 def capture(self,**changes):
  values={'sdk':'37','qemu':'1','selinux':'Enforcing','avcdec':'','source_sha':'a'*40};values.update(changes)
  with tempfile.TemporaryDirectory() as f:
   out=Path(f)/'receipt.json'
   with patch.object(V,'observe',return_value=values):V.capture(out)
   return json.loads(out.read_text())
 def test_software_enforcing_passes(self):self.assertEqual('Enforcing',self.capture()['selinux'])
 def test_no_hardware_path_accepted(self):
  with self.assertRaises(ValueError):self.capture(avcdec='2')
 def test_bad_software_property_rejected(self):
  with self.assertRaises(ValueError):self.capture(avcdec='garbage')
 def test_permissive_rejected(self):
  with self.assertRaises(ValueError):self.capture(selinux='Permissive')
 def test_wrong_sdk_rejected(self):
  with self.assertRaises(ValueError):self.capture(sdk='36')
 def test_physical_device_rejected(self):
  with self.assertRaises(ValueError):self.capture(qemu='0')
if __name__=='__main__':unittest.main()
