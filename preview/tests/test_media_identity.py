"""Execute the actual bounded public metadata read against synthetic MediaSession data."""
import pathlib,re,json,subprocess,unittest
class MediaIdentityTest(unittest.TestCase):
 def test_exact_title_is_preserved_or_rejected_never_truncated_into_another_identity(self):
  source=(pathlib.Path(__file__).parents[1]/'app/src/main/java/uk/co/traynor/novaos/preview/PreviewBrowser.kt').read_text()
  script=re.search(r'view.evaluateJavascript\("""(.*?)"""',source,re.S).group(1)
  for length in [130,513]:
   title='A'*length
   sandbox=json.dumps(dict(navigator=dict(mediaSession=dict(metadata=dict(title=title),playbackState='playing'))))
   code='const vm=require("node:vm");console.log(JSON.parse(vm.runInNewContext('+json.dumps(script)+','+sandbox+')).title)'
   actual=subprocess.check_output(['node','-e',code],text=True).strip()
   self.assertEqual(title if length<=512 else '',actual)
