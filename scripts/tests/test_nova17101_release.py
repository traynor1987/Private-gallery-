import tempfile,unittest,zipfile,json,sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from verify_nova17101_runtime import verify,MATRIX
class NovaRuntimeGateTest(unittest.TestCase):
 def archive(self,omit=None,skip=None):
  folder=tempfile.TemporaryDirectory();self.addCleanup(folder.cleanup);path=Path(folder.name)/'runtime.zip'
  cases=[]
  for cls,names in json.loads(MATRIX.read_text()).items():
   for name in names:
    if (cls,name)==omit:continue
    cases.append(f'<testcase classname="{cls}" name="{name}">'+('<skipped/>' if (cls,name)==skip else '')+'</testcase>')
  with zipfile.ZipFile(path,'w') as z:z.writestr('outputs/androidTest-results/TEST.xml','<testsuite tests="%d" failures="0" errors="0">'%len(cases)+''.join(cases)+'</testsuite>')
  return path
 def test_all_required_cases_pass(self):self.assertGreater(verify(self.archive()),54)
 def test_omitted_new_navigation_case_denies_signer(self):
  case=('uk.co.traynor.privategallery.NovaNavigation17101Test','authenticatedExistingInstallStartsAtNovaHomeAndAppRootReturnsHome')
  with self.assertRaises(ValueError):verify(self.archive(omit=case))
 def test_skipped_visual_case_denies_signer(self):
  case=('uk.co.traynor.privategallery.NovaHomePresentation17101Test','coverInnerLandscapeSplitAndLargeTextRemainUsableInBothThemes')
  with self.assertRaises(ValueError):verify(self.archive(skip=case))
