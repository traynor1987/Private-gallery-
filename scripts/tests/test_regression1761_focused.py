"""Reject substituted, failed, skipped or unbound focused repair evidence."""
import importlib.util, tempfile, unittest, sys, hashlib
from pathlib import Path
import xml.etree.ElementTree as E
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'scripts'))
import verify_regression1761_focused as V

class FocusedRepairEvidenceTest(unittest.TestCase):
 def fixture(self,root):
  apk=root/'candidate.apk';apk.write_bytes(b'synthetic-apk')
  for label in ('default','software'):
   d=root/label;d.mkdir();(d/'source-sha.txt').write_text('a'*40);(d/'sdk.txt').write_text('37');(d/'android-release.txt').write_text('17');(d/'selinux.txt').write_text('Enforcing');(d/'selinux-after.txt').write_text('Enforcing');(d/'apk-sha256.txt').write_text(hashlib.sha256(apk.read_bytes()).hexdigest());(d/'command-exit-status.txt').write_text('1' if label=='default' else '0')
   (d/'avcdec.txt').write_text('2' if label=='default' else '')
   suite=E.Element('testsuite')
   names=[V.CONTROL] if label=='default' else V.REQUIRED
   for name in names:
    cls,method=name.split('#');c=E.SubElement(suite,'testcase',classname=cls,name=method)
    if label=='default':E.SubElement(c,'failure').text='ComposeTimeoutException: 15000 ms'
   E.ElementTree(suite).write(d/'TEST-synthetic.xml')
   (d/'logcat.txt').write_text(V.DEFAULT_MARKERS[0]+'\n'+'\n'.join(V.DEFAULT_MARKERS[1:]) if label=='default' else 'c2.android.avc.decoder\nPGVideoContinuity synthetic buffering=0')
  return apk
 def check(self,mutation=None):
  with tempfile.TemporaryDirectory() as f:
   r=Path(f);apk=self.fixture(r)
   if mutation:mutation(r,apk)
   return V.verify(r,'a'*40,apk)
 def test_exact_control_and_positive_pass(self):self.assertEqual(len(V.REQUIRED),self.check()['passing_cases'])
 def test_wrong_source_rejected(self):
  with self.assertRaises(ValueError):self.check(lambda r,a:(r/'software/source-sha.txt').write_text('b'*40))
 def test_permissive_rejected(self):
  with self.assertRaises(ValueError):self.check(lambda r,a:(r/'software/selinux.txt').write_text('Permissive'))
 def test_after_permissive_rejected(self):
  with self.assertRaises(ValueError):self.check(lambda r,a:(r/'software/selinux-after.txt').write_text('Permissive'))
 def test_unrelated_memfd_denial_rejected(self):
  with self.assertRaises(ValueError):self.check(lambda r,a:(r/'default/logcat.txt').write_text('\n'.join(V.DEFAULT_MARKERS).replace('scontext=u:r:mediacodec:s0','scontext=u:r:other:s0')))
 def test_garbage_decoder_property_rejected(self):
  with self.assertRaises(ValueError):self.check(lambda r,a:(r/'software/avcdec.txt').write_text('garbage'))
 def test_wrong_apk_rejected(self):
  with self.assertRaises(ValueError):self.check(lambda r,a:a.write_bytes(b'other'))
 def test_missing_negative_chain_rejected(self):
  with self.assertRaises(ValueError):self.check(lambda r,a:(r/'default/logcat.txt').write_text('some timeout'))
 def test_default_pass_cannot_prove_causation(self):
  with self.assertRaises(ValueError):self.check(lambda r,a:(r/'default/command-exit-status.txt').write_text('0'))
 def test_skip_or_failure_rejected(self):
  for outcome in ('skipped','failure'):
   def mutate(r,a):
    p=r/'software/TEST-synthetic.xml';t=E.parse(p);E.SubElement(next(t.getroot().iter('testcase')),outcome);t.write(p)
   with self.assertRaises(ValueError):self.check(mutate)
 def test_missing_or_duplicate_positive_case_rejected(self):
  def mutate(r,a):
   p=r/'software/TEST-synthetic.xml';t=E.parse(p);cs=list(t.getroot());cs[-1].set('name',cs[-2].get('name'));cs[-1].set('classname',cs[-2].get('classname'));t.write(p)
  with self.assertRaises(ValueError):self.check(mutate)
 def test_default_codec_in_positive_rejected(self):
  with self.assertRaises(ValueError):self.check(lambda r,a:(r/'software/logcat.txt').write_text('c2.goldfish.h264.decoder'))
if __name__=='__main__':unittest.main()
