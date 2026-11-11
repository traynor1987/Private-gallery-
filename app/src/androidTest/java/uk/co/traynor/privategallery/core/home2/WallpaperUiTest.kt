package uk.co.traynor.privategallery.core.home2

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.view.WindowManager
import android.view.inspector.WindowInspector
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.Lifecycle
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import uk.co.traynor.privategallery.core.evidence.*
import uk.co.traynor.privategallery.core.hidden.*
import uk.co.traynor.privategallery.core.vault.*
import uk.co.traynor.privategallery.home2.*
import uk.co.traynor.privategallery.ui.FullscreenMediaViewer
import uk.co.traynor.privategallery.ui.ViewerMediaEntry
import uk.co.traynor.privategallery.ui.modalCaptureState1762
import uk.co.traynor.privategallery.ui.playerActivity
import uk.co.traynor.privategallery.core.ui.MediaViewerSource

class WallpaperUiTest {
 @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
 private var fixture:Fixture?=null
 private var attached by mutableStateOf(true)
 private class Fixture(base:Context,val hidden:Boolean):AutoCloseable {
  var resolver:android.content.ContentResolver?=null
  val root=File(base.cacheDir,"wallpaper-ui-${UUID.randomUUID()}")
  val context=object:ContextWrapper(base) {
   override fun getFilesDir()=File(root,"files").apply{mkdirs()}
   override fun getNoBackupFilesDir()=File(root,"no-backup").apply{mkdirs()}
   override fun getApplicationContext():Context=base.applicationContext
   override fun getContentResolver()=resolver?:base.contentResolver
  }
  val key=ByteArray(32){31};val identity=Any();val gate=Any()
  val session=HiddenSession{android.os.SystemClock.elapsedRealtime()}.also{check(it.complete(it.beginAuthentication(),ByteArray(32){73}))}
  var selected=true
  val source:VaultItem
  val payload:File
  val ciphertext:ByteArray
  init {
   val bitmap=Bitmap.createBitmap(64,48,Bitmap.Config.ARGB_8888).apply{eraseColor(android.graphics.Color.BLUE)}
   val bytes=ByteArrayOutputStream().use{out->bitmap.compress(Bitmap.CompressFormat.PNG,100,out);out.toByteArray()};bitmap.recycle()
   source=if(hidden)HiddenRepository(context,session).importMedia("secret.png","image/png",ByteArrayInputStream(bytes))else
    (AndroidVaultRepository(context,key).importVerified(VaultImportSource("primary.png","image/png",{ByteArrayInputStream(bytes)}))as ImportResult.Imported).item
   payload=if(hidden)File(context.noBackupFilesDir,"hidden-media-v1/media-${source.id}.enc")else File(context.filesDir,"vault/payloads/${source.id}.vault")
   ciphertext=payload.readBytes();bytes.fill(0)
  }
  fun access():EvidenceAccess=if(hidden)EvidenceAccess.hidden(context,session){selected}else EvidenceAccess.primary(context,identity,{identity},{selected},{key.copyOf()},gate)
  fun owner():WallpaperOwner=WallpaperOwner(if(hidden)EvidenceCatalog.Owner.HIDDEN else EvidenceCatalog.Owner.PRIMARY,{selected&&if(hidden)session.isUnlocked else true},{access()})
  fun wallpaper():Wallpaper=access().use{a->SpaceContext(context,a).use{WallpaperStore(it).read()}}
  fun assertOriginal(){assertArrayEquals(ciphertext,payload.readBytes());val current=if(hidden)HiddenRepository(context,session).items().single()else AndroidVaultRepository(context,key).items().single();assertArrayEquals(source.plaintextSha256,current.plaintextSha256)}
  override fun close(){selected=false;session.lock();key.fill(0);ciphertext.fill(0);root.deleteRecursively()}
 }
 @After fun finish(){if(fixture!=null){compose.runOnIdle{attached=false};compose.waitForIdle();fixture?.close();fixture=null}}
 private fun selectVaultWallpaper(f:Fixture) {
  val activity=compose.activity
  val tile=hasTestTag("wallpaper-vault-item-${f.source.id}") and hasClickAction() and isEnabled() and
   hasAnyAncestor(hasTestTag("wallpaper-vault-grid"))
  try {
   // A composed tile can be disabled during loading, and dialog layout can precede Android window focus.
   compose.waitUntil(10_000) {
    compose.runOnUiThread {
     WindowInspector.getGlobalWindowViews().any { view ->
      view!==activity.window.decorView && view.context.playerActivity()===activity &&
       view.isAttachedToWindow && view.hasWindowFocus() &&
       (view.layoutParams as? WindowManager.LayoutParams)?.type==WindowManager.LayoutParams.TYPE_APPLICATION
     }
    } && compose.onAllNodes(tile).fetchSemanticsNodes().size==1
   }
   compose.onNode(tile).assertIsDisplayed().performTouchInput{click()}
   compose.waitUntil(10_000){compose.onAllNodesWithTag("wallpaper-zoom").fetchSemanticsNodes().isNotEmpty()}
   compose.onNodeWithTag("wallpaper-zoom").performScrollTo().assertIsDisplayed()
  }catch(timeout:ComposeTimeoutException) {
   val grid=compose.onAllNodesWithTag("wallpaper-vault-grid").fetchSemanticsNodes().isNotEmpty()
   val readyTiles=compose.onAllNodes(tile).fetchSemanticsNodes().size
   val loadError=compose.onAllNodesWithText("Image unavailable. Choose another image.").fetchSemanticsNodes().isNotEmpty()
   throw AssertionError("Vault selection did not reach the zoom editor. grid=$grid readyTiles=$readyTiles loadError=$loadError\n${modalCaptureState1762(activity).description}",timeout)
  }
 }
 private fun home(hidden:Boolean) {
  val f=Fixture(compose.activity,hidden).also{fixture=it}
  val access=f.access()
  val ownerRequests=java.util.concurrent.atomic.AtomicInteger()
  var connected:PreviewData?=null
  compose.setContent{if(attached)CompositionLocalProvider(LocalContext provides f.context){IntegratedHome(access,true,{}, {}, {},null,null,onThemeConnection={connected=it},wallpaperOwnerFactory={ownerRequests.incrementAndGet();f.owner()})}}
  compose.onNodeWithTag("app-wallpaper").performClick()
  compose.onNodeWithTag("wallpaper-editor").assertIsDisplayed()
  compose.onNodeWithText("Original Nova wallpapers").performScrollTo().assertIsDisplayed()
  compose.onNodeWithTag("choose-system-wallpaper").performScrollTo().assertIsDisplayed()
  try {
   compose.onNodeWithTag("choose-vault-wallpaper").performScrollTo().performClick()
   compose.onNodeWithTag("wallpaper-vault-grid").assertIsDisplayed()
   compose.waitUntil(10_000){compose.onAllNodesWithTag("wallpaper-vault-item-${f.source.id}").fetchSemanticsNodes().isNotEmpty()}
  }catch(failure:Throwable) {
   val roots=compose.onAllNodes(isRoot(),useUnmergedTree=true)
   val hierarchy=(0 until roots.fetchSemanticsNodes().size).joinToString("\n"){roots[it].printToString()}
   val sources=runCatching{f.access().use{sourceAccess->WallpaperCoordinator(f.context,sourceAccess).use{source->source.images().map{it.id}}}}
   throw AssertionError("Authenticated Home wallpaper grid did not publish its existing source. hidden=$hidden ownerRequests=${ownerRequests.get()} sourceId=${f.source.id} freshSources=$sources\n$hierarchy",failure)
  }
  selectVaultWallpaper(f)
  compose.onNodeWithTag("cancel-wallpaper").performScrollTo().performClick()
  val homeWindow=compose.activity.window.decorView
  compose.waitUntil(10_000){compose.runOnUiThread{homeWindow.hasWindowFocus()}}
  compose.onNodeWithTag("wallpaper-editor").assertDoesNotExist()
  compose.runOnIdle{assertNull(f.wallpaper().image);f.assertOriginal()}
  compose.onNodeWithTag("app-wallpaper").performClick()
  compose.onNodeWithTag("choose-vault-wallpaper").performScrollTo().performClick()
  selectVaultWallpaper(f)
  compose.onNodeWithTag("apply-wallpaper").performScrollTo().performClick()
  compose.waitUntil(10_000){compose.onAllNodesWithTag("wallpaper-editor").fetchSemanticsNodes().isEmpty()}
  compose.runOnIdle{assertNotNull(f.wallpaper().image);assertEquals(f.wallpaper(),connected?.profile?.wallpaper);f.assertOriginal()}
  compose.onNodeWithTag("app-wallpaper").performClick()
  compose.onNodeWithTag("wallpaper-zoom").performScrollTo().assertIsDisplayed()
  compose.onNodeWithTag("cancel-wallpaper").performScrollTo().performClick()
  compose.runOnIdle{access.close()}
 }
 @Test fun primary_home_vault_picker_cancel_apply_and_reopen()=home(false)
 @Test fun hidden_home_vault_picker_cancel_apply_and_reopen()=home(true)
 private fun gallery(hidden:Boolean) {
  val f=Fixture(compose.activity,hidden).also{fixture=it}
  var owner by mutableStateOf<WallpaperOwner?>(null)
  compose.setContent{if(attached)CompositionLocalProvider(LocalContext provides f.context){MaterialTheme{
   FullscreenMediaViewer(listOf(ViewerMediaEntry(f.source.id,f.source.mimeType)),MediaViewerSource.VAULT,0,onClose={},
    onLoadProtectedBytes={_,done->done(runCatching{if(hidden)HiddenRepository(f.context,f.session).readForDisplay(f.source.id)else AndroidVaultRepository(f.context,f.key).readForViewing(f.source)})},
    onSetNovaWallpaper={owner=f.owner()})
   owner?.let{WallpaperEditorHost(it,true,f.source.id,onClose={owner=null})}
  }}}
  compose.onNodeWithContentDescription("Viewer menu").performClick()
  compose.onNode(hasText("Set as NovaOS Wallpaper") and hasAnyAncestor(hasTestTag("set-nova-wallpaper"))).performClick()
  compose.waitUntil(10_000){compose.onAllNodesWithTag("wallpaper-zoom").fetchSemanticsNodes().isNotEmpty()}
  compose.onNodeWithTag("apply-wallpaper").performScrollTo().performClick()
  compose.waitUntil(10_000){compose.onAllNodesWithTag("wallpaper-editor").fetchSemanticsNodes().isEmpty()}
  compose.onNodeWithTag("viewer-image").assertIsDisplayed()
  compose.runOnIdle{assertNotNull(f.wallpaper().image);f.assertOriginal()}
  compose.onNodeWithContentDescription("Viewer menu").performClick()
  compose.onNode(hasText("Set as NovaOS Wallpaper") and hasAnyAncestor(hasTestTag("set-nova-wallpaper"))).performClick()
  compose.waitUntil(10_000){compose.onAllNodesWithTag("wallpaper-zoom").fetchSemanticsNodes().isNotEmpty()}
  val applied=f.wallpaper()
  compose.onNodeWithTag("cancel-wallpaper").performScrollTo().performClick()
  compose.runOnIdle{assertEquals(applied,f.wallpaper());f.assertOriginal()}
 }
 @Test fun primary_preselected_gallery_preview_apply_and_cancel_preserve_original()=gallery(false)
 @Test fun hidden_preselected_gallery_preview_apply_and_cancel_preserve_original()=gallery(true)
 @Test fun gallery_apply_refreshes_retained_home_on_return_and_stale_save_preserves_it() {
  val f=Fixture(compose.activity,false).also{fixture=it};val access=f.access()
  var connected:PreviewData?=null
  var homeVisible by mutableStateOf(false)
  var owner by mutableStateOf<WallpaperOwner?>(f.owner())
  compose.setContent{if(attached)CompositionLocalProvider(LocalContext provides f.context){MaterialTheme{
   IntegratedHome(access,homeVisible,{}, {}, {},null,null,onThemeConnection={connected=it},wallpaperOwnerFactory=f::owner)
   owner?.let{WallpaperEditorHost(it,true,f.source.id,onClose={owner=null;homeVisible=true})}
  }}}
  compose.waitUntil(10_000){compose.onAllNodesWithTag("wallpaper-zoom").fetchSemanticsNodes().isNotEmpty()}
  compose.onNodeWithTag("apply-wallpaper").performScrollTo().performClick()
  // Apply persists on Dispatchers.IO: Compose idleness does not await that work.
  // Observe the real completion callback and dialog removal before checking Home.
  compose.waitUntil(10_000){owner==null&&homeVisible&&compose.onAllNodesWithTag("wallpaper-editor").fetchSemanticsNodes().isEmpty()}
  compose.onNodeWithTag("preview-home").assertIsDisplayed()
  compose.runOnIdle{val applied=f.wallpaper();assertNotNull(applied.image);assertEquals(applied,connected?.profile?.wallpaper);connected!!.change{it.copy(columns=5)};assertEquals(applied,f.wallpaper())}
  compose.runOnIdle{access.close()}
 }
 @Test fun leaving_hidden_presentation_closes_picker_with_independent_authentication_retained() {
  val f=Fixture(compose.activity,true).also{fixture=it};val owner=f.owner()
  var closed=false
  compose.setContent{if(attached)CompositionLocalProvider(LocalContext provides f.context){MaterialTheme{WallpaperEditorHost(owner,true,onClose={closed=true})}}}
  compose.onNodeWithTag("choose-vault-wallpaper").performScrollTo().performClick()
  compose.runOnIdle{f.selected=false;f.session.revokeOperations()}
  compose.waitUntil(5_000){closed}
  compose.onNodeWithTag("wallpaper-editor").assertDoesNotExist()
  compose.runOnIdle{assertTrue(f.session.isUnlocked);f.selected=true;assertNull(f.wallpaper().image);f.assertOriginal()}
 }
 @Test fun unreadable_encrypted_preferences_show_safe_error_without_apply_or_overwrite() {
  val f=Fixture(compose.activity,false).also{fixture=it}
  val preferenceFile=f.access().use{access->SpaceContext(f.context,access).use{space->WallpaperStore(space).apply(Wallpaper(builtin="Dusk"),null)};File(access.root,"home2").listFiles()!!.single{it.name.startsWith("prefs-")}}
  preferenceFile.writeBytes(preferenceFile.readBytes().also{it[it.lastIndex]=(it.last().toInt() xor 1).toByte()})
  val corrupted=preferenceFile.readBytes();val owner=f.owner()
  var closed=false
  compose.setContent{if(attached)CompositionLocalProvider(LocalContext provides f.context){MaterialTheme{WallpaperEditorHost(owner,true,f.source.id,onClose={closed=true})}}}
  compose.onNodeWithTag("wallpaper-open-error").assertIsDisplayed()
  compose.onNodeWithTag("apply-wallpaper").assertDoesNotExist()
  compose.onNodeWithText("Close").performClick()
  compose.runOnIdle{assertTrue(closed);assertArrayEquals(corrupted,preferenceFile.readBytes());f.assertOriginal()}
  corrupted.fill(0)
 }
 @Test fun supplied_owner_factory_denial_never_falls_back_to_home_access() {
  val f=Fixture(compose.activity,false).also{fixture=it};val access=f.access()
  var requests=0
  compose.setContent{if(attached)CompositionLocalProvider(LocalContext provides f.context){IntegratedHome(access,true,{}, {}, {},null,null,wallpaperOwnerFactory={requests++;null})}}
  compose.onNodeWithTag("app-wallpaper").performClick()
  compose.waitForIdle()
  compose.onNodeWithTag("wallpaper-editor").assertDoesNotExist()
  compose.onNodeWithTag("preview-home").assertIsDisplayed()
  compose.runOnIdle{assertEquals(1,requests);assertNull(f.wallpaper().image);access.close()}
 }
 private class Picker:ActivityResultRegistry() {
  var request:Int?=null;var launches=0
  override fun <I,O> onLaunch(requestCode:Int,contract:ActivityResultContract<I,O>,input:I,options:ActivityOptionsCompat?){request=requestCode;launches++}
  fun result(uri:android.net.Uri?){assertTrue(dispatchResult(checkNotNull(request),uri))}
 }
 private fun systemPicker(hidden:Boolean) {
  val f=Fixture(compose.activity,hidden).also{fixture=it};val picker=Picker()
  val accesses=mutableListOf<EvidenceAccess>()
  val authentication=if(hidden)f.session.authentication()else null
  val owner=WallpaperOwner(if(hidden)EvidenceCatalog.Owner.HIDDEN else EvidenceCatalog.Owner.PRIMARY,
   {f.selected&&(!hidden||f.session.isAuthenticationCurrent(authentication))},{f.access().also{accesses.add(it)}})
  val provider=object:android.content.ContentProvider() {
   override fun onCreate()=true
   override fun getType(uri:android.net.Uri)="image/png"
   override fun query(uri:android.net.Uri,projection:Array<out String>?,selection:String?,selectionArgs:Array<out String>?,sortOrder:String?):android.database.Cursor?=null
   override fun insert(uri:android.net.Uri,values:android.content.ContentValues?):android.net.Uri?=error("read only")
   override fun delete(uri:android.net.Uri,selection:String?,selectionArgs:Array<out String>?):Int=error("read only")
   override fun update(uri:android.net.Uri,values:android.content.ContentValues?,selection:String?,selectionArgs:Array<out String>?):Int=error("read only")
   override fun openAssetFile(uri:android.net.Uri,mode:String):android.content.res.AssetFileDescriptor {
    val pipe=android.os.ParcelFileDescriptor.createPipe()
    Thread{
     val bitmap=Bitmap.createBitmap(40,60,Bitmap.Config.ARGB_8888)
     try{android.os.ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]).use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}}finally{bitmap.recycle()}
    }.apply{isDaemon=true;start()}
    return android.content.res.AssetFileDescriptor(pipe[0],0,android.content.res.AssetFileDescriptor.UNKNOWN_LENGTH)
   }
  }
  provider.attachInfo(f.context,android.content.pm.ProviderInfo().apply{authority="wallpaper.ui.fixture";exported=false})
  f.resolver=android.content.ContentResolver.wrap(provider)
  val registryOwner=object:ActivityResultRegistryOwner{override val activityResultRegistry=picker}
  var closed=false
  compose.setContent{if(attached)CompositionLocalProvider(LocalContext provides f.context,LocalActivityResultRegistryOwner provides registryOwner){MaterialTheme{WallpaperEditorHost(owner,true,onClose={closed=true})}}}
  compose.onNodeWithTag("choose-system-wallpaper").performScrollTo().performClick()
  compose.runOnIdle{assertEquals(1,picker.launches);assertEquals(1,accesses.size);assertFalse(accesses.single().current())}
  compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
  androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync{if(hidden)f.session.revokeOperations();picker.result(android.net.Uri.parse("content://wallpaper.ui.fixture/image"))}
  compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
  compose.waitUntil(10_000){compose.onAllNodesWithTag("wallpaper-zoom").fetchSemanticsNodes().isNotEmpty()}
  compose.runOnIdle{assertEquals(2,accesses.size);assertFalse(accesses[0].current());assertTrue(accesses[1].current());assertFalse(closed)}
  compose.onNodeWithTag("apply-wallpaper").performScrollTo().performClick()
  compose.waitUntil(10_000){closed}
  compose.runOnIdle{assertNotNull(f.wallpaper().image);f.assertOriginal()}
 }
 @Test fun primary_system_picker_handoff_uses_fresh_access_and_explicit_apply()=systemPicker(false)
 @Test fun hidden_system_picker_handoff_uses_fresh_authority_with_same_authentication()=systemPicker(true)
 @Test fun system_picker_result_after_hidden_lock_and_reunlock_is_rejected() {
  val f=Fixture(compose.activity,true).also{fixture=it};val picker=Picker();val authentication=f.session.authentication()
  var opens=0;var closed=false
  val owner=WallpaperOwner(EvidenceCatalog.Owner.HIDDEN,{f.selected&&f.session.isAuthenticationCurrent(authentication)},{opens++;f.access()})
  val registryOwner=object:ActivityResultRegistryOwner{override val activityResultRegistry=picker}
  compose.setContent{if(attached)CompositionLocalProvider(LocalContext provides f.context,LocalActivityResultRegistryOwner provides registryOwner){MaterialTheme{WallpaperEditorHost(owner,true,onClose={closed=true})}}}
  compose.onNodeWithTag("choose-system-wallpaper").performScrollTo().performClick()
  compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
  androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync{f.session.lock();assertTrue(f.session.complete(f.session.beginAuthentication(),ByteArray(32){73}));picker.result(android.net.Uri.parse("content://wallpaper.ui.fixture/obsolete"))}
  compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
  compose.waitUntil(5_000){closed}
  compose.runOnIdle{assertEquals(1,opens);assertNull(f.wallpaper().image);f.assertOriginal()}
 }
 @Test fun fold_aspect_ratio_reflows_preview_without_changing_normalized_crop_or_effects() {
  val f=Fixture(compose.activity,false).also{fixture=it}
  val bitmap=Bitmap.createBitmap(64,48,Bitmap.Config.ARGB_8888)
  val original=Wallpaper(zoom=1.8f,x=.3f,y=-.4f,blur=8f,dim=.35f)
  var draft by mutableStateOf(original);var unfolded by mutableStateOf(false)
  compose.setContent{if(attached)CompositionLocalProvider(LocalContext provides f.context,
   androidx.compose.ui.platform.LocalConfiguration provides android.content.res.Configuration().apply{screenWidthDp=if(unfolded)760 else 380;screenHeightDp=840}) {
   DisposableEffect(Unit){onDispose{bitmap.recycle()}}
   MaterialTheme{WallpaperScreen(draft,bitmap,"Primary Space",f.context.filesDir,false,null,{draft=it},{}, {}, {}, {}, {},false)}
  }}
  val folded=compose.onNodeWithTag("wallpaper-preview").getUnclippedBoundsInRoot()
  assertEquals(380f/840,(folded.right-folded.left).value/(folded.bottom-folded.top).value,.03f)
  compose.runOnIdle{unfolded=true}
  val expanded=compose.onNodeWithTag("wallpaper-preview").getUnclippedBoundsInRoot()
  assertEquals(760f/840,(expanded.right-expanded.left).value/(expanded.bottom-expanded.top).value,.03f)
  compose.runOnIdle{assertEquals(original,draft)}
 }
}
