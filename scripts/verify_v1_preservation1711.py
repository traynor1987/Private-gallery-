"""Pin V1 configuration/storage/default tab behavior and characterize app identity fixes."""
from pathlib import Path
import subprocess,re,hashlib,json
root=Path(__file__).resolve().parents[1]
base='9efd19540a1d75efa1b30f4e23c9cbb096f93aff'
session='app/src/main/java/uk/co/traynor/privategallery/core/browser/v2/BrowserV2Session.kt'
factory='app/src/main/java/uk/co/traynor/privategallery/core/browser/v2/SecureWebViewFactory.kt'
main='app/src/main/java/uk/co/traynor/privategallery/MainActivity.kt'
vault='app/src/main/java/uk/co/traynor/privategallery/core/vault'
broker='app/src/main/java/uk/co/traynor/privategallery/core/browser/hidden/HiddenVpnBrowserBroker.kt'
# Independently reviewed A/C changes only. The baseline stays pinned to original V1.
VAULT={
 'AndroidVaultRepository.kt':('M','9d53858b7e3d950d7075f439ff5214b0d4e4cbd6cea11087cba35f28e419f66b'),
 'ChunkedVaultVideoStore.kt':('M','7f6aa651ae4cb1e75fef5e4a48b8e7c3c1aeab5c03bedc882998655069f301d5'),
 'EncryptedIndexStore.kt':('M','577b58a5886b79957cef1c799dbfece8bea9d8febc0d081f31ebdb162fabc152'),
 'EncryptedPayloadStore.kt':('M','abf4c91c1675d95c6258e04d9fc1bd64b1bfd0b583c609e86cfd7d6b69b370f0'),
 'VaultPresentationMetadata.kt':('A','60d02f692b7f54b0c38aed286b932859026c15126a1c78c770e6c61734836a8e'),
 'VaultPresentationStore.kt':('M','5104ccbbf78308454d5f05cf8836d2daf26808d49c92276f287b6e8fd6f69f6c')}
def exact_delta(repo,baseline,scope,allowed):
 def git(*args):return subprocess.check_output(['git',*args],cwd=repo)
 expected={(status,path) for path,(status,digest) in allowed.items()}
 for revision in (['HEAD'],[]):
  rows=git('diff','--name-status','--no-renames',baseline,*revision,'--',scope).decode().splitlines()
  actual={tuple(row.split('\t')) for row in rows}
  assert actual==expected,('Unreviewed changed path/status',actual^expected)
 assert not git('ls-files','--others','--',scope).strip(),'Untracked protected source'
 for path,(status,digest) in allowed.items():
  assert hashlib.sha256(git('show','HEAD:'+path)).hexdigest()==digest,('Unreviewed committed blob',path)
  assert hashlib.sha256((repo/path).read_bytes()).hexdigest()==digest,('Unreviewed working blob',path)
exact_delta(root,base,vault,{vault+'/'+name:value for name,value in VAULT.items()})
# Broker comparison now propagates cooldown/unavailable errors; no networking change is allowed.
exact_delta(root,base,broker,{broker:('M','38cf1b02530da82dc1aeb54ea562316d8d4108542eaa0eddc983819cbee13ead')})
for scope in ('core/browser','core/crypto'):
 assert not subprocess.check_output(['git','ls-files','--others','--','app/src/main/java/uk/co/traynor/privategallery/'+scope],cwd=root).strip(),'Untracked protected source'
subprocess.run(['git','diff','--exit-code',base,'--',
 'app/src/main/java/uk/co/traynor/privategallery/core/browser',
 'app/src/main/java/uk/co/traynor/privategallery/core/crypto',
 ':(exclude)'+session,':(exclude)'+factory,':(exclude)'+broker],cwd=root,check=True)
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
for name in ('select','pauseOutgoingTab'):
 assert body(old,name)==body(new,name),f'Legacy V1 {name} changed'
# Saved-app launch must classify the new tab before any callback/snapshot, with identical pause behavior.
old_app_tail='val tab = newTab(app.startUrl())\n        privateAppTabs[tab.id] = app\n        changed()'
new_app_tail='pauseOutgoingTab()\n        exitFullscreen()\n        createTab(app.startUrl(), app)'
assert body(new,'openPrivateApp')==body(old,'openPrivateApp').replace(old_app_tail,new_app_tail),'Unreviewed saved-app launch change'
# Ordinary newTab uses app=null; removing only the guarded app-classification addition gives V1 body.
creation=body(new,'createTab')
creation=creation.replace('// Classify before any renderer callback or metadata publication can escape this method.\n        if(app!=null)privateAppTabs[tab.id]=app\n        ','')
expanded=body(new,'newTab').replace('return createTab(url)',creation)
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
print(json.dumps({'result':'PASS','baseline':base,'sameV1Constructor':True,'samePoliciesAndStores':True,'factoryConfigurationPreserved':True,'factoryCallbackGuardsTightened':True,'sameOrdinaryTabOperations':True,'savedAppIdentityBeforeCallbacks':True,'additivePrivateAppLifecycleFile':session,'additiveFileSha256':hashlib.sha256((root/session).read_bytes()).hexdigest()},indent=2))
