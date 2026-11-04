package uk.co.traynor.novaos.preview

import android.graphics.Bitmap
import android.os.SystemClock
import android.webkit.WebView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class PreviewAcceptanceTest {
 @get:Rule val rule=createAndroidComposeRule<MainActivity>()
 @Before fun cleanPreview(){rule.runOnUiThread{
  val d=rule.activity.previewData;d.change("primary"){Profile()};d.change("hidden"){Profile(wallpaper=Wallpaper(builtin="Midnight"))};if(d.state.current!="primary")d.switchProfile();d.setTheme("dark")
 };rule.waitForIdle()}
 private fun data()=rule.activity.previewData
 private fun entry()=data().catalogue.first{it.id=="photopea"}
 @Test fun packageAndCatalogueArePreviewOnly(){
  assertEquals("uk.co.traynor.novaos.preview",rule.activity.packageName)
  assertEquals(50,data().catalogue.size)
  assertEquals(50,data().catalogue.count{it.classification=="Website Shortcut"})
  assertTrue(data().catalogue.all{it.url.startsWith("https://")&&it.source.isNotBlank()&&it.date=="2026-11-04"})
  val pkg=rule.activity.packageManager.getPackageInfo(rule.activity.packageName,android.content.pm.PackageManager.GET_PERMISSIONS)
  assertEquals(listOf("android.permission.INTERNET"),pkg.requestedPermissions?.filterNot{it.contains("DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")}?:emptyList<String>())
 }
 @Test fun installIsIdempotentProfileLocalAndPersistent(){
  rule.runOnUiThread{val d=data();d.install(entry());d.install(entry());d.addHome(d.entryKey(entry()),1);d.pin(d.entryKey(entry()))}
  assertEquals(1,data().profile.installed.size)
  val restored=PreviewData(rule.activity);assertEquals(1,restored.profile.installed.size);assertTrue(restored.profile.dock.contains(restored.entryKey(entry())))
  rule.runOnUiThread{data().switchProfile()};assertTrue(data().profile.installed.isEmpty());assertFalse(data().profile.dock.contains(data().entryKey(entry())))
 }
 @Test fun uninstallCleansHomeDockFolderAndDrawer(){
  rule.runOnUiThread{val d=data();d.install(entry());val id=d.entryKey(entry());d.addHome(id);d.pin(id);d.createFolder("Tools",listOf(id,"store"),0);d.uninstall(id)}
  val id=data().entryKey(entry());assertFalse(data().profile.installed.any{it.id==id});assertFalse(data().profile.pages.flatten().contains(id));assertFalse(data().profile.dock.contains(id));assertFalse(data().profile.folders.flatMap{it.apps}.contains(id));assertFalse(data().drawerIds().contains(id))
 }
 @Test fun wallpaperAndLayoutProfilesRoundTrip(){
  rule.runOnUiThread{data().change{it.copy(wallpaper=Wallpaper("Dusk",zoom=1.7f,x=.5f,y=-.4f,blur=8f,dim=.3f),columns=3,iconSize=70)};data().addPage()}
  val restored=PreviewData(rule.activity);assertEquals("Dusk",restored.profile.wallpaper.builtin);assertEquals(1.7f,restored.profile.wallpaper.zoom);assertEquals(3,restored.profile.columns);assertEquals(3,restored.profile.pages.size);assertEquals("Midnight",restored.profile("hidden").wallpaper.builtin)
 }
 @Test fun storeSearchAndConfirmedInstallWork(){
  rule.onAllNodesWithTag("app-store")[0].performClick()
  rule.onNodeWithTag("store-search").performTextInput("Photopea")
  rule.onNodeWithText("Photopea",useUnmergedTree=false).performClick()
  rule.onNodeWithTag("install-app").performClick();rule.onNodeWithTag("confirm-install").performClick()
  rule.onNodeWithTag("open-installed").assertIsDisplayed();assertEquals(1,data().profile.installed.size)
 }
 private fun js(view:WebView,script:String):String {
  val latch=CountDownLatch(1);var result=""
  rule.runOnUiThread{view.evaluateJavascript(script){result=it;latch.countDown()}}
  assertTrue("Javascript callback missing",latch.await(10,TimeUnit.SECONDS));return result
 }
 @Test fun primaryV1SettingsAndHomeContinuityUseSameWebView(){
  rule.runOnUiThread{data().install(entry())}
  rule.onNodeWithTag("open-apps").performClick();rule.onNodeWithTag("drawer-search").performTextInput("Photopea");rule.onNodeWithTag("app-"+data().entryKey(entry())).performClick()
  rule.onNodeWithTag("preview-web-app").assertIsDisplayed()
  lateinit var view:WebView
  rule.runOnUiThread{
   view=rule.activity.browserPool.active!!.view
   assertTrue(view.settings.javaScriptEnabled);assertTrue(view.settings.domStorageEnabled);assertFalse(view.settings.allowFileAccess);assertFalse(view.settings.allowContentAccess);assertFalse(view.settings.javaScriptCanOpenWindowsAutomatically);assertEquals(100,view.settings.textZoom)
   view.loadDataWithBaseURL("https://example.com/","<html><meta name='viewport' content='width=device-width'><input id='memo' value='initial'><script>window.previewReady=true;</script></html>","text/html","UTF-8",null)
  }
  var ready=false;val deadline=SystemClock.uptimeMillis()+10000
  while(!ready&&SystemClock.uptimeMillis()<deadline){ready=js(view,"window.previewReady===true")=="true";if(!ready)SystemClock.sleep(100)}
  assertTrue(ready);assertEquals("\"kept\"",js(view,"document.getElementById('memo').value='kept'"))
  rule.onNodeWithTag("web-return-home").performClick();rule.onNodeWithTag("preview-home").assertIsDisplayed()
  rule.onNodeWithTag("open-apps").performClick();rule.onNodeWithTag("drawer-search").performTextInput("Photopea");rule.onNodeWithTag("app-"+data().entryKey(entry())).performClick()
  assertSame(view,rule.activity.browserPool.active!!.view);assertEquals("\"kept\"",js(view,"document.getElementById('memo').value"))
 }
 @Test fun previewTabsKeepAppOwner(){
  rule.runOnUiThread{
   val pool=rule.activity.browserPool;val app=entry();val first=pool.open(app,"primary");val second=pool.create(app,"primary",false);pool.select(second)
   assertEquals(app.id,pool.active!!.app.id);assertEquals(2,pool.tabs.count{it.profile=="primary"&&it.app.id==app.id});pool.select(first);assertSame(first.view,pool.open(app,"primary").view)
   assertNotSame(first.view,pool.open(app,"hidden").view);pool.closeApp("hidden",app.id);assertTrue(pool.tabs.any{it.id==first.id})
  }
 }
 private fun shell(command:String){val fd=InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command);FileInputStream(fd.fileDescriptor).use{it.readBytes()};fd.close()}
 private fun screenshot(name:String){
  rule.waitForIdle();SystemClock.sleep(900)
  val image=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot();val dir=File(rule.activity.filesDir,"screenshots");dir.mkdirs();File(dir,"$name.png").outputStream().use{assertTrue(image.compress(Bitmap.CompressFormat.PNG,100,it))};image.recycle()
 }
 @Test fun foldViewportLayoutsAndLargeTextRender(){
  try {
   shell("wm density 420")
   for((name,size) in listOf("cover" to "904x2316","inner" to "1840x2208","landscape" to "2316x904")){
    shell("wm size $size");SystemClock.sleep(1200);rule.waitForIdle();rule.onNodeWithTag("preview-home").assertExists();rule.onNodeWithTag("open-apps").assertIsDisplayed();screenshot("home-$name")
   }
   shell("wm size 904x2316");shell("settings put system font_scale 1.3");SystemClock.sleep(1200);rule.waitForIdle();rule.onNodeWithTag("open-apps").performClick();rule.onNodeWithTag("drawer-search").assertIsDisplayed();screenshot("drawer-large-text")
  }finally{shell("settings put system font_scale 1.0");shell("wm size reset");shell("wm density reset")}
 }
}
