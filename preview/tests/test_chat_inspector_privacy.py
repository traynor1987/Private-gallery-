import pathlib,unittest
ROOT=pathlib.Path(__file__).resolve().parents[1]
class InspectorPrivacyTests(unittest.TestCase):
 def test_script_has_no_private_content_or_account_network_storage_interfaces(self):
  script=(ROOT/'app/src/main/assets/chat_inspector_v01.js').read_text()
  for forbidden in ['innerText','textContent','innerHTML','outerHTML','getAttribute','cookie','localStorage','sessionStorage','indexedDB','fetch(','XMLHttpRequest','WebSocket','dispatchEvent','.click(','.src','setAttribute','MutationObserver','setInterval']:
   self.assertNotIn(forbidden,script)
  self.assertIn("location.origin!=='https://web.whatsapp.com'",script)
  self.assertIn('.slice(0,64)',script);self.assertIn('n<256',script)
 def test_native_inspector_has_no_unrestricted_bridge_upload_logging_or_screenshots(self):
  source=(ROOT/'app/src/main/java/uk/co/traynor/novaos/preview/ChatInspector.kt').read_text()
  for forbidden in ['addJavascriptInterface','loadUrl(','takeScreenshot','Bitmap','Log.','HttpClient','URLConnection','ACTION_SEND']:
   self.assertNotIn(forbidden,source)
  self.assertIn('if(!explicitAction)return',source);self.assertIn('repeat(3)',source)
  self.assertIn('ChatInspectorRules.validateReport(raw)',source)
  self.assertIn('inspector.report(profile)',source)
