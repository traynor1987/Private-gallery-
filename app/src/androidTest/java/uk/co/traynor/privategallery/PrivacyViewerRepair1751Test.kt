package uk.co.traynor.privategallery

import androidx.activity.compose.setContent
import android.graphics.Bitmap
import android.view.WindowManager
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import java.io.ByteArrayOutputStream
import uk.co.traynor.privategallery.ui.*
import uk.co.traynor.privategallery.core.ui.MediaViewerSource

/** Disposable UI fixtures only; no owner credentials or encrypted originals are modified. */
class PrivacyViewerRepair1751Test {
 private val compose=createAndroidComposeRule<MainActivity>()
 private val settings=object:ExternalResource() {
  private var previous:String?=null
  private var hiddenPrevious:Boolean=false
  override fun before() {
   val p=InstrumentationRegistry.getInstrumentation().targetContext.getSharedPreferences("private-gallery-settings",0)
   val hidden=HiddenPreferences(InstrumentationRegistry.getInstrumentation().targetContext)
   hiddenPrevious=hidden.allowScreenshots;hidden.allowScreenshots=false
   previous=p.getString("allow-screenshots",null);p.edit().putString("allow-screenshots","true").commit()
  }
  override fun after() {
   HiddenPreferences(InstrumentationRegistry.getInstrumentation().targetContext).allowScreenshots=hiddenPrevious
   InstrumentationRegistry.getInstrumentation().targetContext.getSharedPreferences("private-gallery-settings",0).edit().apply {if(previous==null)remove("allow-screenshots") else putString("allow-screenshots",previous)}.commit()
  }
 }
 @get:Rule val rules:RuleChain=RuleChain.outerRule(settings).around(compose)
 private fun content(block:@Composable ()->Unit) { compose.runOnIdle {compose.activity.setContent(content=block)} }
 private fun jpeg():ByteArray {
  val image=Bitmap.createBitmap(80,80,Bitmap.Config.ARGB_8888)
  return try {ByteArrayOutputStream().use {image.compress(Bitmap.CompressFormat.JPEG,90,it);it.toByteArray()}} finally {image.recycle()}
 }
 private fun assertNormalWindow() {
  compose.waitUntil(3000){!compose.activity.privacyCoverVisible() && compose.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE==0}
  compose.runOnIdle {
  assertFalse("Owned UI must not activate the native privacy curtain",compose.activity.privacyCoverVisible())
  assertEquals("Screenshots explicitly enabled must remain enabled",0,compose.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE)
 }
 }
 @Test fun sortPopupDoesNotBlackGalleryOrDisableScreenshots() {
  content {PrivateGalleryTheme {
   var menu by remember {mutableStateOf(false)}
   Column {Text("Gallery fixture");Button(onClick={menu=true}){Text("Sort fixture")}
    DropdownMenu(menu,{menu=false}){DropdownMenuItem(text={Text("Date added")},onClick={menu=false})}}
  }}
  assertNormalWindow()
  val before=compose.activity.previewConcealmentEpoch
  compose.onNodeWithText("Sort fixture").performClick();compose.onNodeWithText("Date added").assertIsDisplayed()
  assertNormalWindow();compose.runOnIdle {assertEquals("Owned popup must never conceal a preview",before,compose.activity.previewConcealmentEpoch)}
  compose.onNodeWithText("Date added").performClick();assertNormalWindow()
 }
 @Test fun ownedDialogDoesNotCoverOrReleaseViewerImage() {
  var loads=0
  content {PrivateGalleryTheme {
   var dialog by remember {mutableStateOf(false)}
   Box {FullscreenMediaViewer(listOf(ViewerMediaEntry("photo","image/jpeg")),MediaViewerSource.VAULT,0,{},onLoadProtectedBytes={_,done->loads++;done(Result.success(jpeg()))})
    Button(onClick={dialog=true}){Text("Open dialog fixture")}}
   if(dialog)AlertDialog(onDismissRequest={dialog=false},title={Text("Details fixture")},confirmButton={TextButton(onClick={dialog=false}){Text("Done fixture")}})
  }}
  compose.waitUntil(10000){compose.onAllNodesWithText("Loading media…").fetchSemanticsNodes().isEmpty()}
  assertNormalWindow()
  val before=compose.activity.previewConcealmentEpoch
  compose.onNodeWithText("Open dialog fixture").performClick();compose.onNodeWithText("Details fixture").assertIsDisplayed()
  assertNormalWindow();compose.runOnIdle {assertEquals(1,loads);assertEquals("Owned dialog must never conceal a preview",before,compose.activity.previewConcealmentEpoch)}
  compose.onNodeWithText("Done fixture").performClick();compose.onNodeWithTag("viewer-image").assertExists();compose.runOnIdle {assertEquals(1,loads)}
 }
 @androidx.test.filters.SdkSuppress(minSdkVersion=29)
 @Test fun ownedDialogFocusLossSecuresEveryWindowBeforeNextFrame() {
  content {PrivateGalleryTheme {Text("Owned-window snapshot fixture")}}
  assertNormalWindow()
  var dialog:android.app.Dialog?=null
  var next:android.app.Dialog?=null
  val observed=java.util.concurrent.atomic.AtomicBoolean(false)
  val secured=java.util.concurrent.atomic.AtomicBoolean(false)
  try {
   compose.runOnIdle {
    dialog=android.app.Dialog(compose.activity)
    dialog!!.setContentView(android.widget.TextView(compose.activity).apply {text="Owned dialog"})
    dialog!!.show()
   }
   // Do not open the next window until the first window has actually received and settled focus.
   compose.waitUntil(5000){dialog?.window?.decorView?.hasWindowFocus()==true &&
    !compose.activity.window.decorView.hasWindowFocus() &&
    compose.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE==0}
   assertNormalWindow()
   compose.runOnIdle {
    val decor=checkNotNull(dialog!!.window).decorView
    assertEquals(0,(decor.layoutParams as WindowManager.LayoutParams).flags and WindowManager.LayoutParams.FLAG_SECURE)
    // Registered after the production observer. Inspect submitted flags in the same real WM callback.
    decor.viewTreeObserver.addOnWindowFocusChangeListener {focused->if(!focused) {
     secured.set((decor.layoutParams as WindowManager.LayoutParams).flags and WindowManager.LayoutParams.FLAG_SECURE!=0 &&
       compose.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE!=0)
     observed.set(true)
    }}
    next=android.app.Dialog(compose.activity)
    next!!.setContentView(android.widget.TextView(compose.activity).apply {text="Next owned dialog"})
    next!!.show()
   }
   compose.waitUntil(5000){observed.get()}
   assertTrue("Every window must be secure in the same native focus-loss callback",secured.get())
  } finally {compose.runOnIdle {next?.dismiss();dialog?.dismiss()}}
 }
 @androidx.test.filters.SdkSuppress(minSdkVersion=29)
 @Test fun temporaryProtectionNeverClearsOriginallySecureDialog() {
  content {PrivateGalleryTheme {Text("Credential window fixture")}}
  assertNormalWindow()
  var dialog:android.app.Dialog?=null
  try {
   compose.runOnIdle {
    dialog=android.app.Dialog(compose.activity)
    checkNotNull(dialog!!.window).addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    dialog!!.setContentView(android.widget.TextView(compose.activity).apply {text="Credential fixture"})
    dialog!!.show()
   }
   assertNormalWindow()
   compose.runOnIdle {
    assertTrue((checkNotNull(dialog!!.window).decorView.layoutParams as WindowManager.LayoutParams).flags and WindowManager.LayoutParams.FLAG_SECURE!=0)
    assertFalse(compose.activity.privacyCoverVisible())
   }
  } finally {compose.runOnIdle {dialog?.dismiss()}}
 }
 @androidx.test.filters.SdkSuppress(minSdkVersion=29)
 @Test fun realConcealmentCoversSecondaryWindowPixelsSynchronously() {
  content {PrivateGalleryTheme {Text("Secondary curtain fixture")}}
  assertNormalWindow()
  var dialog:android.app.Dialog?=null
  try {
   compose.runOnIdle {
    dialog=android.app.Dialog(compose.activity)
    dialog!!.setContentView(android.widget.TextView(compose.activity).apply {
     text="Sensitive fixture";setBackgroundColor(android.graphics.Color.MAGENTA)
     minWidth=240;minHeight=240
    })
    dialog!!.show()
   }
   assertNormalWindow()
   compose.runOnIdle {
    val decor=checkNotNull(dialog!!.window).decorView
    val bitmap=Bitmap.createBitmap(decor.width,decor.height,Bitmap.Config.ARGB_8888)
    try {
     decor.draw(android.graphics.Canvas(bitmap))
     assertEquals("Rendered fixture must be present before concealment",android.graphics.Color.MAGENTA,bitmap.getPixel(bitmap.width/2,bitmap.height/2))
     MainActivity::class.java.getDeclaredMethod("coverSensitiveContent").apply {isAccessible=true}.invoke(compose.activity)
     bitmap.eraseColor(android.graphics.Color.TRANSPARENT)
     decor.draw(android.graphics.Canvas(bitmap))
     assertEquals("Dialog must be visually covered in the same callback",android.graphics.Color.BLACK,bitmap.getPixel(bitmap.width/2,bitmap.height/2))
     assertEquals(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS,decor.importantForAccessibility)
     assertTrue(compose.activity.privacyCoverVisible())
    } finally {bitmap.recycle()}
   }
  } finally {compose.runOnIdle {dialog?.dismiss()}}
 }
 @Test fun backgroundRequiresExplicitReopenWithoutAutomaticDecode() {
  var loads=0
  content {PrivateGalleryTheme {FullscreenMediaViewer(listOf(ViewerMediaEntry("photo","image/jpeg")),MediaViewerSource.VAULT,0,{},onLoadProtectedBytes={_,done->loads++;done(Result.success(jpeg()))})}}
  compose.waitUntil(10000){loads==1 && compose.onAllNodesWithText("Loading media…").fetchSemanticsNodes().isEmpty()}
  compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
  compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
  compose.onNodeWithText("Preview hidden").assertIsDisplayed()
  compose.onNodeWithText("For your privacy, this image was hidden after switching apps.").assertExists()
  compose.runOnIdle {assertEquals("Background return must not decrypt again automatically",1,loads)}
  compose.onNodeWithText("Reopen to view").performClick()
  compose.waitUntil(10000){loads==2 && compose.onAllNodesWithText("Loading media…").fetchSemanticsNodes().isEmpty()}
  compose.onNodeWithTag("viewer-image").assertExists()
 }
 private fun nativeThreeFingerSequence(repeatThird:Boolean=false) {
  val instrumentation=InstrumentationRegistry.getInstrumentation()
  var w=0f;var h=0f
  compose.runOnIdle {w=compose.activity.window.decorView.width.toFloat();h=compose.activity.window.decorView.height.toFloat()}
  val down=android.os.SystemClock.uptimeMillis()
  fun send(mask:Int,count:Int,index:Int=0) {
   val props=Array(count){i->android.view.MotionEvent.PointerProperties().apply {id=i;toolType=android.view.MotionEvent.TOOL_TYPE_FINGER}}
   val coords=Array(count){i->android.view.MotionEvent.PointerCoords().apply {x=w*(.4f+i*.1f);y=h*.5f;pressure=1f;size=1f}}
   val event=android.view.MotionEvent.obtain(down,android.os.SystemClock.uptimeMillis(),mask or (index shl android.view.MotionEvent.ACTION_POINTER_INDEX_SHIFT),count,props,coords,0,0,1f,1f,0,0,android.view.InputDevice.SOURCE_TOUCHSCREEN,0)
   try {instrumentation.sendPointerSync(event)} finally {event.recycle()}
  }
  send(0,1);send(5,2,1);send(5,3,2)
  if(repeatThird){send(6,3,2);send(5,3,2)}
  send(6,3,2);send(6,2,1);send(1,1)
  instrumentation.waitForIdleSync()
 }
 @Test fun nativeThreeFingerPanicConsumesRemainingStreamAndRevokesBothVaults() {
  var stops=0
  val prefs=PrivacyShieldPreferences(compose.activity)
  val oldEnabled=prefs.enabled;val oldShortcut=prefs.shortcut
  try {
   compose.runOnIdle {
    prefs.enabled=true;prefs.shortcut=true
    val owner=androidx.lifecycle.ViewModelProvider(compose.activity)[ProtectedSessionState::class.java]
    owner.key=ByteArray(32){7};owner.session.unlock()
    assertTrue(owner.hidden.session.complete(owner.hidden.session.beginAuthentication(),ByteArray(32){9}));owner.hidden.select()
    compose.activity.sensitivePlaybackStops.add {stops++}
   }
   nativeThreeFingerSequence(true)
   compose.runOnIdle {
    val owner=androidx.lifecycle.ViewModelProvider(compose.activity)[ProtectedSessionState::class.java]
    assertEquals("One Panic per complete pointer stream",1,stops)
    assertNull(owner.key);assertFalse(owner.session.isUnlocked);assertFalse(owner.hidden.session.isUnlocked);assertFalse(owner.hidden.selected)
    assertTrue(owner.automaticBiometricPromptAttempted)
   }
  } finally {prefs.enabled=oldEnabled;prefs.shortcut=oldShortcut}
 }
 @Test fun disabledNativeThreeFingerCannotPanic() {
  val prefs=PrivacyShieldPreferences(compose.activity);val old=prefs.enabled
  try {
   compose.runOnIdle {prefs.enabled=false;val owner=androidx.lifecycle.ViewModelProvider(compose.activity)[ProtectedSessionState::class.java];owner.key=ByteArray(32){7};owner.session.unlock()}
   nativeThreeFingerSequence()
   compose.runOnIdle {assertTrue(androidx.lifecycle.ViewModelProvider(compose.activity)[ProtectedSessionState::class.java].session.isUnlocked)}
  } finally {prefs.enabled=old}
 }
 @Test fun genuineCorruptedMediaIsNotPrivacyCovered() {
  content {PrivateGalleryTheme {FullscreenMediaViewer(listOf(ViewerMediaEntry("bad","image/jpeg")),MediaViewerSource.VAULT,0,{},onLoadProtectedBytes={_,done->done(Result.success(byteArrayOf(1,2,3)))})}}
  compose.waitUntil(10000){compose.onAllNodesWithText("This image could not be displayed safely.").fetchSemanticsNodes().isNotEmpty()}
  compose.onNodeWithText("This image could not be displayed safely.").assertIsDisplayed()
  compose.onNodeWithText("Preview hidden").assertDoesNotExist()
  compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED);compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
  compose.onNodeWithText("This image could not be displayed safely.").assertExists()
  compose.onNodeWithText("Preview hidden").assertDoesNotExist()
 }
 @Test fun screenshotPreferenceSurvivesRecreationAndBackgroundReturn() {
  compose.waitUntil(10000){!compose.activity.privacyCoverVisible() && compose.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE==0}
  compose.activityRule.scenario.recreate()
  compose.waitUntil(10000){!compose.activity.privacyCoverVisible() && compose.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE==0}
  compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
  compose.activityRule.scenario.onActivity {activity->assertTrue(activity.privacyCoverVisible());assertTrue(activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE!=0)}
  compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
  compose.waitUntil(10000){!compose.activity.privacyCoverVisible() && compose.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE==0}
 }
 @Test fun screenshotDisabledStaysSecureWithoutOwnedDialogBlackout() {
  compose.runOnIdle {MainActivity::class.java.getDeclaredMethod("applyAllowScreenshots",Boolean::class.javaPrimitiveType).apply {isAccessible=true}.invoke(compose.activity,false)}
  content {PrivateGalleryTheme {AlertDialog(onDismissRequest={},title={Text("Secure dialog fixture")},confirmButton={})}}
  compose.onNodeWithText("Secure dialog fixture").assertExists()
  compose.runOnIdle {assertFalse(compose.activity.privacyCoverVisible());assertTrue(compose.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE!=0)}
 }
 @Test fun expiredReopenAuthorityCannotDecodeAgain() {
  var loads=0;var authority=true
  content {PrivateGalleryTheme {FullscreenMediaViewer(listOf(ViewerMediaEntry("photo","image/jpeg")),MediaViewerSource.VAULT,0,{},
   onReopen={authority},onLoadProtectedBytes={_,done->loads++;done(Result.success(jpeg()))})}}
  compose.waitUntil(10000){loads==1 && compose.onAllNodesWithText("Loading media…").fetchSemanticsNodes().isEmpty()}
  compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED);authority=false
  compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
  compose.onNodeWithText("Reopen to view").performClick()
  compose.onNodeWithText("Unlock the vault to view this media.").assertExists()
  compose.runOnIdle {assertEquals(1,loads)}
 }
 @Test fun quickLockActionRevokesAuthenticatedSession() {
  val prefs=PrivacyShieldPreferences(compose.activity);val old=prefs.enabled
  try {
   compose.runOnIdle {prefs.enabled=true;val owner=androidx.lifecycle.ViewModelProvider(compose.activity)[ProtectedSessionState::class.java];owner.key=ByteArray(32){7};owner.session.unlock()}
   content {PrivateGalleryTheme {PrivacyShieldSettings {}}}
   compose.onNodeWithText("Quick Lock now").performClick()
   compose.runOnIdle {val owner=androidx.lifecycle.ViewModelProvider(compose.activity)[ProtectedSessionState::class.java];assertNull(owner.key);assertFalse(owner.session.isUnlocked);assertTrue(owner.automaticBiometricPromptAttempted)}
  } finally {prefs.enabled=old}
 }
 @Test fun permissionStylePauseKeepsDecodedImageButSecuresPausedWindow() {
  var loads=0
  content {PrivateGalleryTheme {FullscreenMediaViewer(listOf(ViewerMediaEntry("photo","image/jpeg")),MediaViewerSource.VAULT,0,{},onLoadProtectedBytes={_,done->loads++;done(Result.success(jpeg()))})}}
  compose.waitUntil(10000){loads==1 && compose.onAllNodesWithText("Loading media…").fetchSemanticsNodes().isEmpty()}
  val instrumentation=InstrumentationRegistry.getInstrumentation()
  val monitor=instrumentation.addMonitor(PrivacyPauseFixtureActivity::class.java.name,null,false)
  var helper:android.app.Activity?=null
  try {
   compose.activity.startActivity(android.content.Intent(compose.activity,PrivacyPauseFixtureActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NO_USER_ACTION))
   helper=monitor.waitForActivityWithTimeout(10000)
   assertNotNull("Actual same-process floating window must launch",helper)
   compose.waitUntil(10000){compose.activity.lifecycle.currentState==Lifecycle.State.STARTED && helper!!.hasWindowFocus()}
   compose.activityRule.scenario.onActivity {activity->assertFalse("Owned pause-only overlay must not conceal",activity.privacyCoverVisible());assertTrue(activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE!=0)}
  } finally {
   instrumentation.runOnMainSync {helper?.finish()}
   instrumentation.removeMonitor(monitor)
   compose.waitUntil(10000){compose.activity.lifecycle.currentState==Lifecycle.State.RESUMED}
  }
  compose.onNodeWithTag("viewer-image").assertExists();compose.onNodeWithText("Preview hidden").assertDoesNotExist()
  assertNormalWindow();compose.runOnIdle {assertEquals(1,loads)}
 }
 @Test fun alreadyGrantedPermissionNeedsNoPrivacyExemption() {
  content {Text("Synchronous permission fixture")}
  assertNormalWindow()
  compose.runOnIdle {
   val contract=PrivacyPermissionContract(compose.activity)
   val result=contract.getSynchronousResult(compose.activity,arrayOf(android.Manifest.permission.INTERNET))
   assertNotNull(result)
   assertEquals(true,result!!.value[android.Manifest.permission.INTERNET])
   assertEquals(0,compose.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE)
   contract.close()
  }
 }
 @Test fun permissionTokensCannotClearAnotherLaunchAndCancellationRestoresPolicy() {
  content {Text("Permission token fixture")}
  assertNormalWindow()
  compose.runOnIdle {
   val first=PrivacyPermissionContract(compose.activity)
   val second=PrivacyPermissionContract(compose.activity)
   first.createIntent(compose.activity,arrayOf(android.Manifest.permission.CAMERA))
   second.createIntent(compose.activity,arrayOf(android.Manifest.permission.RECORD_AUDIO))
   first.parseResult(android.app.Activity.RESULT_CANCELED,null)
   MainActivity::class.java.getDeclaredMethod("applyScreenPrivacy",Boolean::class.javaPrimitiveType).apply {isAccessible=true}.invoke(compose.activity,true)
   assertTrue("Outstanding independent request must remain protected",compose.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE!=0)
   second.parseResult(android.app.Activity.RESULT_CANCELED,null)
   first.close();second.close()
  }
  assertNormalWindow()
 }
 @Test fun failedPermissionLaunchCannotLeavePrivacyExemption() {
  content {Text("Failed permission fixture")}
  assertNormalWindow()
  compose.runOnIdle {
   val contract=PrivacyPermissionContract(compose.activity)
   val launcher=object:androidx.activity.result.ActivityResultLauncher<Array<String>>() {
    override fun launch(input:Array<String>,options:androidx.core.app.ActivityOptionsCompat?) {
     contract.createIntent(compose.activity,input)
     throw IllegalStateException("Synthetic launch failure")
    }
    override fun unregister() {}
    override fun getContract():androidx.activity.result.contract.ActivityResultContract<Array<String>,*> =contract
   }
   assertTrue(runCatching {contract.launch(launcher,arrayOf(android.Manifest.permission.CAMERA))}.isFailure)
   MainActivity::class.java.getDeclaredMethod("applyScreenPrivacy",Boolean::class.javaPrimitiveType).apply {isAccessible=true}.invoke(compose.activity,true)
   assertEquals("Failed request must release its own token",0,compose.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE)
   contract.createIntent(compose.activity,arrayOf(android.Manifest.permission.RECORD_AUDIO))
   assertTrue(runCatching {contract.launch(launcher,arrayOf(android.Manifest.permission.CAMERA))}.isFailure)
   MainActivity::class.java.getDeclaredMethod("applyScreenPrivacy",Boolean::class.javaPrimitiveType).apply {isAccessible=true}.invoke(compose.activity,true)
   assertTrue("Failed newer request must preserve earlier request",compose.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE!=0)
   contract.parseResult(android.app.Activity.RESULT_CANCELED,null)
  }
  assertNormalWindow()
 }
 @Test fun actualAndroidPermissionDialogKeepsPhotoAndCorrectForegroundPolicy() {
  var loads=0
  content {PrivateGalleryTheme {FullscreenMediaViewer(listOf(ViewerMediaEntry("photo","image/jpeg")),MediaViewerSource.VAULT,0,{},onLoadProtectedBytes={_,done->loads++;done(Result.success(jpeg()))})}}
  compose.waitUntil(10000){loads==1 && compose.onAllNodesWithText("Loading media…").fetchSemanticsNodes().isEmpty()}
  val instrumentation=InstrumentationRegistry.getInstrumentation()
  val ui=instrumentation.uiAutomation
  val permission=android.Manifest.permission.ACCESS_FINE_LOCATION
  val permissionContract=PrivacyPermissionContract(compose.activity)
  lateinit var permissionLauncher:androidx.activity.result.ActivityResultLauncher<Array<String>>
  compose.runOnIdle {
   permissionLauncher=compose.activity.activityResultRegistry.register("real-permission-1751",permissionContract) {}
   assertEquals("Fixture must request a real ungranted permission",android.content.pm.PackageManager.PERMISSION_DENIED,compose.activity.checkSelfPermission(permission))
   permissionContract.launch(permissionLauncher,arrayOf(permission,android.Manifest.permission.ACCESS_COARSE_LOCATION))
  }
  val permissionPackages=setOf("com.android.permissioncontroller","com.google.android.permissioncontroller").filterTo(mutableSetOf()) {name->
   runCatching {compose.activity.packageManager.getApplicationInfo(name,0).flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM!=0}.getOrDefault(false)
  }
  fun denyButton():android.view.accessibility.AccessibilityNodeInfo? {
   fun find(node:android.view.accessibility.AccessibilityNodeInfo):android.view.accessibility.AccessibilityNodeInfo? {
    val id=node.viewIdResourceName ?: ""
    val text=node.text?.toString()
    if(node.packageName?.toString() in permissionPackages && node.isVisibleToUser &&
      (id.endsWith(":id/permission_deny_button") || id.endsWith(":id/permission_deny_and_dont_ask_again_button") ||
       id.endsWith(":id/permission_deny_dont_ask_again_button") || text=="Don't allow" || text=="Don’t allow" || text=="Deny"))return node
    for(i in 0 until node.childCount)node.getChild(i)?.let {find(it)?.let {return it}}
    return null
   }
   ui.rootInActiveWindow?.let {find(it)?.let {return it}}
   ui.windows.forEach {window->window.root?.let {find(it)?.let {return it}}}
   return null
  }
  var deny:android.view.accessibility.AccessibilityNodeInfo?=null
  val previousFlags=ui.serviceInfo.flags
  try {
   ui.serviceInfo=ui.serviceInfo.apply {flags=flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or android.accessibilityservice.AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS}
   val deadline=android.os.SystemClock.uptimeMillis()+10000
   while(deny==null && android.os.SystemClock.uptimeMillis()<deadline){deny=denyButton();if(deny==null)Thread.sleep(50)}
   assertNotNull("Real Android permission UI must be displayed",deny)
   instrumentation.runOnMainSync {
    assertFalse("Permission dialog must not activate the black curtain",compose.activity.privacyCoverVisible())
    assertEquals(1,loads)
    if(compose.activity.lifecycle.currentState==Lifecycle.State.STARTED)assertTrue(compose.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE!=0)
   }
  } finally {
   if(deny?.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)!=true) {
    android.os.ParcelFileDescriptor.AutoCloseInputStream(ui.executeShellCommand("input keyevent KEYCODE_BACK")).use {it.readBytes()}
   }
   val cleanupDeadline=android.os.SystemClock.uptimeMillis()+5000
   var resumed=false
   while(!resumed && android.os.SystemClock.uptimeMillis()<cleanupDeadline) {
    instrumentation.runOnMainSync {resumed=compose.activity.lifecycle.currentState==Lifecycle.State.RESUMED}
    if(!resumed)Thread.sleep(25)
   }
   ui.serviceInfo=ui.serviceInfo.apply {flags=previousFlags}
   instrumentation.runOnMainSync {permissionLauncher.unregister();permissionContract.close()}
  }
  compose.waitUntil(10000){compose.activity.lifecycle.currentState==Lifecycle.State.RESUMED}
  compose.onNodeWithTag("viewer-image").assertExists();compose.onNodeWithText("Preview hidden").assertDoesNotExist()
  assertNormalWindow();compose.runOnIdle {assertEquals(1,loads)}
 }
 @Test fun hiddenVisibleOverridesUnderlyingPrimaryGalleryScreenshotPreference() {
  content {Text("Policy fixture")}
  compose.runOnIdle {
   val activity=compose.activity
   val owner=androidx.lifecycle.ViewModelProvider(activity)[ProtectedSessionState::class.java]
   owner.key=ByteArray(32){7};owner.session.unlock();owner.primaryGallery=true
   assertTrue(owner.hidden.session.complete(owner.hidden.session.beginAuthentication(),ByteArray(32){9}))
   owner.hidden.select();owner.hidden.outside()
   @Suppress("UNCHECKED_CAST")
   val route=MainActivity::class.java.getDeclaredField("route\$delegate").apply {isAccessible=true}.get(activity) as MutableState<Route>
   route.value=Route.GALLERY
   activity.onWindowFocusChanged(true)
   assertEquals(0,activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE)
   // Same transition used by openScopedVault/Favourite: underlying Gallery route remains.
   owner.hidden.select(Route.VAULT)
   activity.onWindowFocusChanged(true)
   assertTrue("Hidden must never inherit permissive Primary policy",activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE!=0)
   assertEquals("true",activity.getSharedPreferences("private-gallery-settings",0).getString("allow-screenshots",null))
  }
 }
 @Test fun floatingQuickLockWorksWithGestureDisabledAndReturnsNeutral() {
  val prefs=PrivacyShieldPreferences(compose.activity)
  val oldEnabled=prefs.enabled;val oldQuick=prefs.quickLock;val oldShortcut=prefs.shortcut
  try {
   compose.runOnIdle {
    prefs.enabled=true;prefs.quickLock=true;prefs.shortcut=false
    val owner=androidx.lifecycle.ViewModelProvider(compose.activity)[ProtectedSessionState::class.java]
    owner.key=ByteArray(32){7};owner.session.unlock()
    compose.activity.privacySettingsChanged()
   }
   compose.onNodeWithContentDescription("Quick Lock").performClick()
   compose.waitUntil(10000){!compose.activity.privacyCoverVisible()}
   compose.runOnIdle {
    val owner=androidx.lifecycle.ViewModelProvider(compose.activity)[ProtectedSessionState::class.java]
    assertNull(owner.key);assertFalse(owner.session.isUnlocked);assertFalse(owner.hidden.selected);assertTrue(owner.automaticBiometricPromptAttempted)
   }
  } finally {prefs.enabled=oldEnabled;prefs.quickLock=oldQuick;prefs.shortcut=oldShortcut}
 }
 @Test fun panicSettingsProvideDiscoverableSafeTest() {
  content {PrivateGalleryTheme {PrivacyShieldSettings {}}}
  compose.onNodeWithText("Panic Exit",substring=false).assertExists()
  compose.onNodeWithText("Enable Panic Exit").assertExists()
  compose.onNodeWithText("Test Panic Exit safely").assertExists()
 }
}
