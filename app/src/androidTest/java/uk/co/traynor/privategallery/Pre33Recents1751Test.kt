package uk.co.traynor.privategallery

import android.graphics.Bitmap
import android.graphics.Color
import android.os.ParcelFileDescriptor
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.runtime.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import uk.co.traynor.privategallery.ui.*
import uk.co.traynor.privategallery.core.ui.MediaViewerSource

/** Requires the real Launcher/Recents provided by the API32 Google APIs image (ATD lacks it). */
@SdkSuppress(maxSdkVersion=32)
class Pre33Recents1751Test {
 private val compose=createAndroidComposeRule<MainActivity>()
 private val automation=object:org.junit.rules.ExternalResource() {
  override fun before() {InstrumentationRegistry.getInstrumentation().uiAutomation.waitForIdle(500,10000)}
 }
 @get:Rule val rules:org.junit.rules.RuleChain=org.junit.rules.RuleChain.outerRule(automation).around(compose)
 private fun pinkJpeg():ByteArray {
  val bitmap=Bitmap.createBitmap(120,120,Bitmap.Config.ARGB_8888).apply {eraseColor(Color.rgb(249,5,137))}
  return try {ByteArrayOutputStream().use {bitmap.compress(Bitmap.CompressFormat.JPEG,95,it);it.toByteArray()}} finally {bitmap.recycle()}
 }
 private fun pinkSamples(bitmap:Bitmap):Int {
  var count=0
  for(y in 0 until bitmap.height step 4)for(x in 0 until bitmap.width step 4) {
   val pixel=bitmap.getPixel(x,y)
   val r=Color.red(pixel);val g=Color.green(pixel);val b=Color.blue(pixel)
   // Detect both the bright fixture and a dimmed/white-blended task thumbnail by hue.
   if(r>20 && b>8 && r>g*2 && b>g*1.5 && r>b*1.25 && b>r*.35)count++
  }
  return count
 }
 @Test fun screenshotsAllowedForegroundButActualRecentsCannotExposeRenderedPhoto()=verifyRecents(false)
 @Test fun actualRecentsCannotExposeOwnedDialogImageWhenScreenshotsAllowed()=verifyRecents(true)
 private fun verifyRecents(withDialog:Boolean) {
  val activity=compose.activity
  val prefs=activity.getSharedPreferences("private-gallery-settings",0)
  val previous=prefs.getString("allow-screenshots",null)
  val instrumentation=InstrumentationRegistry.getInstrumentation()
  val ui=instrumentation.uiAutomation
  val previousServiceFlags=ui.serviceInfo.flags
  val output=File(InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
   ?: File(instrumentation.targetContext.externalMediaDirs.first(),"additional_test_output").absolutePath,"release1751-recents/${if(withDialog) "owned-dialog" else "photo"}").apply {mkdirs()}
  val appLabel=activity.applicationInfo.loadLabel(activity.packageManager).toString()
  fun trace(message:String) {File(output,"trace.txt").appendText("time=${android.os.SystemClock.uptimeMillis()} $message\n")}
  fun capture(name:String):Int {
   trace("$name: before ordinary screencap")
   val png=ParcelFileDescriptor.AutoCloseInputStream(ui.executeShellCommand("screencap -p")).use {it.readBytes()}
   check(png.size>=8 && png.take(8).map {it.toInt() and 255}==listOf(137,80,78,71,13,10,26,10)) {"Capture must be a valid PNG, never a fabricated black fallback"}
   val image=checkNotNull(android.graphics.BitmapFactory.decodeByteArray(png,0,png.size)) {"Screenshot PNG must decode"}
   return try {
    check(image.width>=200 && image.height>=400) {"Whole display screenshot dimensions required"}
    val count=pinkSamples(image)
    File(output,"$name.png").writeBytes(png)
    trace("$name width=${image.width} height=${image.height} pinkSamples=$count")
    count
   } finally {image.recycle()}
  }
  fun launcherOwnsForeground():Boolean=ui.rootInActiveWindow?.packageName?.toString()?.contains("launcher",ignoreCase=true)==true
  fun overviewReady():Boolean {
   val root=ui.rootInActiveWindow ?: return false
   if(!root.packageName.toString().contains("launcher",ignoreCase=true))return false
   var overview=false;var snapshot=false;var label=false
   fun visit(node:android.view.accessibility.AccessibilityNodeInfo) {
    if(node.isVisibleToUser) {
     val id=node.viewIdResourceName ?: ""
     if(id.endsWith(":id/overview_panel"))overview=true
     if(id.endsWith(":id/snapshot"))snapshot=true
     if(node.text?.contains(appLabel)==true || node.contentDescription?.contains(appLabel)==true)label=true
    }
    for(i in 0 until node.childCount)node.getChild(i)?.let {visit(it)}
   }
   visit(root)
   return overview && snapshot && label
  }
  fun key(code:Int) {ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("input keyevent $code")).use {it.readBytes()}}
  var primaryFailure:Throwable?=null
  try {
   ui.serviceInfo=ui.serviceInfo.apply {flags=flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or android.accessibilityservice.AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS}
   compose.runOnIdle {
    MainActivity::class.java.getDeclaredMethod("applyAllowScreenshots",Boolean::class.javaPrimitiveType).apply {isAccessible=true}.invoke(activity,true)
    activity.setContent {PrivateGalleryTheme {
     FullscreenMediaViewer(listOf(ViewerMediaEntry("recents-synthetic","image/jpeg")),MediaViewerSource.VAULT,0,{},onLoadProtectedBytes={_,done->done(Result.success(pinkJpeg()))})
     if(withDialog) {
      val thumbnail=remember {Bitmap.createBitmap(180,180,Bitmap.Config.ARGB_8888).apply {eraseColor(Color.rgb(249,5,137))}}
      DisposableEffect(thumbnail){onDispose {thumbnail.recycle()}}
      AlertDialog(onDismissRequest={},title={Text("Synthetic details")},text={Image(thumbnail.asImageBitmap(),"Synthetic protected thumbnail",Modifier.size(180.dp))},confirmButton={})
     }
    }}
   }
   compose.waitUntil(10000){compose.onAllNodesWithText("Loading media…").fetchSemanticsNodes().isEmpty()}
   compose.onNodeWithTag("viewer-image").assertExists()
   compose.runOnIdle {assertEquals(0,activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE)}
   val renderDeadline=android.os.SystemClock.uptimeMillis()+10000
   var foreground=capture("foreground");var renderFrame=0
   while(foreground<=1000 && android.os.SystemClock.uptimeMillis()<renderDeadline) {
    Thread.sleep(50)
    foreground=capture("foreground-settle-${renderFrame++}")
   }
   assertTrue("Rendered fixture must be visible and screenshot-enabled before switching",foreground>1000)
   key(android.view.KeyEvent.KEYCODE_APP_SWITCH)
   // Quickstep can keep the old Activity RESUMED; Launcher readiness, not onPause,
   // establishes the actual Recents boundary. Every owned window must already be secure there.
   val overviewDeadline=android.os.SystemClock.uptimeMillis()+15000
   var ready=false;var frame=0;var launcherSeen=false
   while(!ready && android.os.SystemClock.uptimeMillis()<overviewDeadline) {
    // Input command completion can precede the actual foreground-window switch.
    // Keep those full-app frames as evidence; security assertions start at Launcher ownership.
    trace("transition $frame: before foreground-root query")
    if(launcherOwnsForeground())launcherSeen=true
    val pink=capture(if(launcherSeen) "transition-${frame++}" else "before-background-${frame++}")
    if(launcherSeen)assertTrue("Launcher transition must not contain the rendered fixture",pink<64)
    ready=launcherSeen && overviewReady()
    if(!ready)Thread.sleep(50)
   }
   assertTrue("Launcher overview, visible task snapshot and this app label must be rendered",ready)
   instrumentation.runOnMainSync {
    assertTrue("Pre-33 snapshot protection must cover resumed Recents",activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE!=0)
    assertTrue("External focus must hold the native privacy curtain",activity.privacyCoverVisible())
   }
   ui.waitForIdle(500,5000)
   repeat(5) {index ->
    assertTrue("Overview must remain rendered",overviewReady())
    assertTrue("Settled task card must not reveal the rendered fixture",capture("settled-$index")<64)
    Thread.sleep(100)
   }
   File(output,"trace.txt").appendText("PASS: real Launcher overview/task card confirmed; transition and settled frames concealed\n")
  } catch(failure:Throwable) {
   primaryFailure=failure
   runCatching {trace("PRIMARY FAILURE: ${failure.stackTraceToString()}")}.onFailure {failure.addSuppressed(it)}
   throw failure
  } finally {
   try {
    ui.serviceInfo=ui.serviceInfo.apply {flags=previousServiceFlags}
   } catch(cleanup:Throwable) {
    primaryFailure?.addSuppressed(cleanup) ?: throw cleanup
   } finally {
    try {
     instrumentation.runOnMainSync {activity.finish()}
     val finishDeadline=android.os.SystemClock.uptimeMillis()+10000
     while(!activity.isDestroyed && android.os.SystemClock.uptimeMillis()<finishDeadline) {
      instrumentation.waitForIdleSync()
      Thread.sleep(25)
     }
     check(activity.isDestroyed) {"Fixture Activity must finish before rule cleanup"}
    }
    catch(cleanup:Throwable) {primaryFailure?.addSuppressed(cleanup) ?: throw cleanup}
    finally {prefs.edit().apply {if(previous==null)remove("allow-screenshots") else putString("allow-screenshots",previous)}.commit()}
   }
  }
 }
}
