package uk.co.traynor.privategallery

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
  override fun before() {
   val p=InstrumentationRegistry.getInstrumentation().targetContext.getSharedPreferences("private-gallery-settings",0)
   previous=p.getString("allow-screenshots",null);p.edit().putString("allow-screenshots","true").commit()
  }
  override fun after() {
   InstrumentationRegistry.getInstrumentation().targetContext.getSharedPreferences("private-gallery-settings",0).edit().apply {if(previous==null)remove("allow-screenshots") else putString("allow-screenshots",previous)}.commit()
  }
 }
 @get:Rule val rules:RuleChain=RuleChain.outerRule(settings).around(compose)
 private fun jpeg():ByteArray {
  val image=Bitmap.createBitmap(80,80,Bitmap.Config.ARGB_8888)
  return try {ByteArrayOutputStream().use {image.compress(Bitmap.CompressFormat.JPEG,90,it);it.toByteArray()}} finally {image.recycle()}
 }
 private fun assertNormalWindow()=compose.runOnIdle {
  assertFalse("Owned UI must not activate the native privacy curtain",compose.activity.privacyCoverVisible())
  assertEquals("Screenshots explicitly enabled must remain enabled",0,compose.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE)
 }
 @Test fun sortPopupDoesNotBlackGalleryOrDisableScreenshots() {
  compose.setContent {PrivateGalleryTheme {
   var menu by remember {mutableStateOf(false)}
   Column {Text("Gallery fixture");Button(onClick={menu=true}){Text("Sort fixture")}
    DropdownMenu(menu,{menu=false}){DropdownMenuItem(text={Text("Date added")},onClick={menu=false})}}
  }}
  compose.onNodeWithText("Sort fixture").performClick();compose.onNodeWithText("Date added").assertIsDisplayed()
  assertNormalWindow()
  compose.onNodeWithText("Date added").performClick();assertNormalWindow()
 }
 @Test fun ownedDialogDoesNotCoverOrReleaseViewerImage() {
  var loads=0
  compose.setContent {PrivateGalleryTheme {
   var dialog by remember {mutableStateOf(false)}
   Box {FullscreenMediaViewer(listOf(ViewerMediaEntry("photo","image/jpeg")),MediaViewerSource.VAULT,0,{},onLoadProtectedBytes={_,done->loads++;done(Result.success(jpeg()))})
    Button(onClick={dialog=true}){Text("Open dialog fixture")}}
   if(dialog)AlertDialog(onDismissRequest={dialog=false},title={Text("Details fixture")},confirmButton={TextButton(onClick={dialog=false}){Text("Done fixture")}})
  }}
  compose.waitUntil(10000){compose.onAllNodesWithText("Loading media…").fetchSemanticsNodes().isEmpty()}
  compose.onNodeWithText("Open dialog fixture").performClick();compose.onNodeWithText("Details fixture").assertIsDisplayed()
  assertNormalWindow();compose.runOnIdle {assertEquals(1,loads)}
  compose.onNodeWithText("Done fixture").performClick();compose.onNodeWithTag("viewer-image").assertExists();compose.runOnIdle {assertEquals(1,loads)}
 }
 @Test fun backgroundRequiresExplicitReopenWithoutAutomaticDecode() {
  var loads=0
  compose.setContent {PrivateGalleryTheme {FullscreenMediaViewer(listOf(ViewerMediaEntry("photo","image/jpeg")),MediaViewerSource.VAULT,0,{},onLoadProtectedBytes={_,done->loads++;done(Result.success(jpeg()))})}}
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
 @Test fun genuineCorruptedMediaIsNotPrivacyCovered() {
  compose.setContent {PrivateGalleryTheme {FullscreenMediaViewer(listOf(ViewerMediaEntry("bad","image/jpeg")),MediaViewerSource.VAULT,0,{},onLoadProtectedBytes={_,done->done(Result.success(byteArrayOf(1,2,3)))})}}
  compose.onNodeWithText("This image could not be displayed safely.").assertIsDisplayed()
  compose.onNodeWithText("Preview hidden").assertDoesNotExist()
 }
 @Test fun panicSettingsProvideDiscoverableSafeTest() {
  compose.setContent {PrivateGalleryTheme {PrivacyShieldSettings {}}}
  compose.onNodeWithText("Panic Exit",substring=false).assertExists()
  compose.onNodeWithText("Enable Panic Exit").assertExists()
  compose.onNodeWithText("Test Panic Exit safely").assertExists()
 }
}
