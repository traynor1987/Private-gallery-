"""Pin V1 configuration/storage and characterize the sole additive app-lifecycle change."""
from pathlib import Path
import subprocess,re,hashlib,json
root=Path(__file__).resolve().parents[1]
base='9efd19540a1d75efa1b30f4e23c9cbb096f93aff'
session='app/src/main/java/uk/co/traynor/privategallery/core/browser/v2/BrowserV2Session.kt'
factory='app/src/main/java/uk/co/traynor/privategallery/core/browser/v2/SecureWebViewFactory.kt'
main='app/src/main/java/uk/co/traynor/privategallery/MainActivity.kt'
subprocess.run(['git','diff','--exit-code',base,'--',
 'app/src/main/java/uk/co/traynor/privategallery/core/browser',
 'app/src/main/java/uk/co/traynor/privategallery/core/crypto',
 'app/src/main/java/uk/co/traynor/privategallery/core/vault',':(exclude)'+session,':(exclude)'+factory],cwd=root,check=True)
def historical(path):return subprocess.check_output(['git','show',base+':'+path],cwd=root).decode()
old=historical(session);new=(root/session).read_text()
def body(text,name):
 match=re.search(r'^    (?:private )?fun '+re.escape(name)+r'\(',text,re.M);assert match,name
 start=text.index('{',match.end());depth=1;end=start+1
 while depth:
  if text[end]=='{':depth+=1
  elif text[end]=='}':depth-=1
  end+=1
 return text[start+1:end-1].strip()
# These existing V1 entry points keep their original pause/selection semantics.
for name in ('openPrivateApp','select','pauseOutgoingTab'):
 assert body(old,name)==body(new,name),f'Legacy V1 {name} changed'
expanded=body(new,'newTab').replace('return createTab(url)',body(new,'createTab'))
assert re.sub(r'\s+',' ',body(old,'newTab'))==re.sub(r'\s+',' ',expanded),'Legacy newTab creation/eviction changed'
def configuration(text):
 a=text.index('    private val browserV2Session by lazy')
 return text[a:text.index('    private var browserBookmarks',a)]
assert configuration(historical(main))==configuration((root/main).read_text()),'V1 constructor, factory or settings changed'
# Only callback liveness/identity checks may change in the existing factory.
original_factory=historical(factory)
expected_factory=original_factory.replace('if (!live) return false\n', 'if (!live || view !== this@webView) return false\n')
expected_factory=expected_factory.replace('override fun onRequestFocus(view: WebView) { event', 'override fun onRequestFocus(view: WebView) { if(!live || view !== this@webView)return; event')
expected_factory=expected_factory.replace('override fun onCloseWindow(window: WebView) { event', 'override fun onCloseWindow(window: WebView) { if(!live || window !== this@webView)return; event')
expected_factory=expected_factory.replace('override fun onPermissionRequest(request: android.webkit.PermissionRequest) {\n', 'override fun onPermissionRequest(request: android.webkit.PermissionRequest) {\n                if (!live) { request.deny(); return }\n')
expected_factory=expected_factory.replace('override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) {\n', 'override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) {\n                if (!live) { callback.invoke(origin, false, false); return }\n')
expected_factory=expected_factory.replace('callback.invoke(value, allow, retain)', 'callback.invoke(value, allow && live, retain && live)')
expected_factory=expected_factory.replace('fileChooserParams: FileChooserParams): Boolean {\n', 'fileChooserParams: FileChooserParams): Boolean {\n                if (!live || webView !== this@webView) { filePathCallback.onReceiveValue(null); return true }\n')
expected_factory=expected_factory.replace('filePathCallback.onReceiveValue(values)', 'filePathCallback.onReceiveValue(if (live) values else null)')
assert expected_factory==(root/factory).read_text(), 'Factory changed beyond reviewed callback liveness/identity guards'
print(json.dumps({'result':'PASS','baseline':base,'sameV1Constructor':True,'samePoliciesAndStores':True,'factoryConfigurationPreserved':True,'factoryCallbackGuardsTightened':True,'sameLegacyTabOperations':True,'additivePrivateAppLifecycleFile':session,'additiveFileSha256':hashlib.sha256((root/session).read_bytes()).hexdigest()},indent=2))
