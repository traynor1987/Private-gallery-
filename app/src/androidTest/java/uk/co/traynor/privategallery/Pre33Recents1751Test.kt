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
 @get:Rule val compose=createAndroidComposeRule<MainActivity>()
 private fun pinkJpeg():ByteArray {
  val bitmap=Bitmap.createBitmap(120,120,Bitmap.Config.ARGB_8888).apply {eraseColor(Color.rgb(249,5,137))}
  return try {ByteArrayOutputStream().use {bitmap.compress(Bitmap.CompressFormat.JPEG,95,it);it.toByteArray()}} finally {bitmap.recycle()}
 }
 private fun pinkSamples(bitmap:Bitmap):Int {
  var count=0
  for(y in 0 until bitmap.height step 4)for(x in 0 until bitmap.width step 4) {
   val pixel=bitmap.getPixel(x,y)
   if(Color.red(pixel)>235 && Color.green(pixel)<25 && Color.blue(pixel) in 115..155)count++
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
  fun capture(name:String):Int {
   val image=checkNotNull(ui.takeScreenshot()) {"Recents screenshot proof unavailable"}
   return try {
    val count=pinkSamples(image)
    File(output,"$name.png").outputStream().use {image.compress(Bitmap.CompressFormat.PNG,100,it)}
    File(output,"trace.txt").appendText("$name time=${android.os.SystemClock.uptimeMillis()} pinkSamples=$count\n")
    count
   } finally {image.recycle()}
  }
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
   assertTrue("Rendered fixture must be visible and screenshot-enabled before switching",capture("foreground")>1000)
   key(android.view.KeyEvent.KEYCODE_APP_SWITCH)
   val deadline=android.os.SystemClock.uptimeMillis()+10000
   var paused=false
   while(!paused && android.os.SystemClock.uptimeMillis()<deadline) {
    instrumentation.runOnMainSync {paused=!activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)}
    if(!paused)Thread.sleep(25)
   }
   assertTrue("Actual Recents must take foreground from the app",paused)
   instrumentation.runOnMainSync {assertTrue("Pre-33 snapshot protection must already be active",activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE!=0)}
   val overviewDeadline=android.os.SystemClock.uptimeMillis()+15000
   var ready=false;var frame=0
   while(!ready && android.os.SystemClock.uptimeMillis()<overviewDeadline) {
    assertTrue("Transition capture must not contain the rendered fixture",capture("transition-${frame++}")<64)
    ready=overviewReady()
    if(!ready)Thread.sleep(50)
   }
   assertTrue("Launcher overview, visible task snapshot and this app label must be rendered",ready)
   ui.waitForIdle(500,5000)
   repeat(5) {index ->
    assertTrue("Overview must remain rendered",overviewReady())
    assertTrue("Settled task card must not reveal the rendered fixture",capture("settled-$index")<64)
    Thread.sleep(100)
   }
   File(output,"trace.txt").appendText("PASS: real Launcher overview/task card confirmed; transition and settled frames concealed\n")
  } finally {
   ui.serviceInfo=ui.serviceInfo.apply {flags=previousServiceFlags}
   key(android.view.KeyEvent.KEYCODE_BACK)
   prefs.edit().apply {if(previous==null)remove("allow-screenshots") else putString("allow-screenshots",previous)}.commit()
  }
 }
}
