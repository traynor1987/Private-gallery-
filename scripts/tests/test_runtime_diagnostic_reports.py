"""Exercise the actual diagnostic archive verifier with synthetic native reports."""
from pathlib import Path
import subprocess,tempfile,unittest,xml.etree.ElementTree as E
ROOT=Path(__file__).resolve().parents[2]
PAIRS=[('HiddenTorBootstrapTest','packagedTorBootstrapsInPrivateProcessAndStopsWhenReleased'),('HiddenRuntimeFailureTest','stalledConnectTimeoutDisconnectsRuntimeAndClosesSocket'),('HiddenRuntimeFailureTest','sessionRevocationDuringStalledConnectCancelsRuntimeAndClosesSocket')]
class RuntimeReportsTest(unittest.TestCase):
 def verify(self,mode):
  script=(ROOT/'scripts/run_tor1761_diagnostic.sh').read_text().split("<<'PY' || archive_status=$?\n",1)[1].split('\nPY\n',1)[0]
  with tempfile.TemporaryDirectory() as folder:
   root=Path(folder);out=root/'evidence';out.mkdir();(out/'source-sha.txt').write_text('synthetic');(out/'original-command-status.txt').write_text('0')
   reports=root/'app/build/outputs/androidTest-results/connected/debug';reports.mkdir(parents=True)
   suite=E.Element('testsuite')
   pairs=PAIRS if mode!='duplicate' else [PAIRS[0],PAIRS[1],PAIRS[1]]
   if mode=='missing':pairs=pairs[:2]
   for i,(cls,name) in enumerate(pairs):
    case=E.SubElement(suite,'testcase',classname='uk.co.traynor.privategallery.core.browser.hidden.'+cls,name=name)
    if mode=='skipped' and i==1:E.SubElement(case,'skipped')
   E.ElementTree(suite).write(reports/'native.xml')
   (reports/'logcat-synthetic.txt').write_text('System.out: Tor control observation: phase=subscribed\nSystem.out: Tor control observation: readerAlive='+('true' if mode=='reader_alive' else 'false')+'\n')
   return subprocess.run(['python3','-c',script,str(out)],cwd=root,capture_output=True,text=True)
 def test_exact_three_cases_pass(self):
  r=self.verify('valid');self.assertEqual(0,r.returncode,r.stderr)
 def test_lingering_observer_is_rejected(self):self.assertNotEqual(0,self.verify('reader_alive').returncode)
 def test_skip_is_rejected(self):self.assertNotEqual(0,self.verify('skipped').returncode)
 def test_duplicate_cannot_replace_revocation_case(self):self.assertNotEqual(0,self.verify('duplicate').returncode)
 def test_missing_case_is_rejected(self):self.assertNotEqual(0,self.verify('missing').returncode)
if __name__=='__main__':unittest.main()
