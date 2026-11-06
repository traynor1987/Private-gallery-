package uk.co.traynor.privategallery.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.lifecycle.Lifecycle
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import uk.co.traynor.privategallery.core.privateapps.PrivateAppCatalog
import java.util.UUID

class NovaNavigation59Test {
 @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
 private fun fixture()=PrivateAppCatalog.App(UUID.randomUUID().toString(),"Navigation fixture","https://example.test/","https://example.test/","standalone","",false,true,false)
 @Test fun commonRowHasThreeRealActionsAndAppOverflowIsFourth() {
  var recents=0;var homes=0;var backs=0;var overflow=0
  compose.setContent {PrivateGalleryTheme {NovaNavigationRow({recents++},{homes++},{backs++},onOverflow={overflow++})}}
  val buttons=listOf("nova-recents","nova-home","nova-back","web-app-menu")
  val bounds=buttons.map {compose.onNodeWithTag(it).assertIsDisplayed().fetchSemanticsNode().boundsInRoot}
  assertTrue(bounds.zipWithNext().all {(left,right)->left.right<=right.left})
  buttons.forEach {compose.onNodeWithTag(it).performClick()}
  compose.runOnIdle {assertEquals(listOf(1,1,1,1),listOf(recents,homes,backs,overflow))}
 }
 @Test fun nativeRowDoesNotExposeAppOverflow() {
  compose.setContent {PrivateGalleryTheme {NovaNavigationRow({},{},{})}}
  compose.onNodeWithTag("web-app-menu").assertDoesNotExist()
  compose.onNodeWithTag("nova-recents").assertIsDisplayed()
 }
 @Test fun menuBackDismissesBeforePageAndDisabledRowsNeverInvoke() {
  val app=fixture();var pageBacks=0;var forwards=0;var refreshes=0
  compose.setContent {PrivateGalleryTheme {Column(Modifier.fillMaxSize()) {
   Box(Modifier.weight(1f).fillMaxWidth().testTag("retained-page"))
   PrivateAppControls(app,"https://example.test/","Primary Space",false,false,false,false,
    {pageBacks++},{forwards++},{refreshes++},{},{},{},{},immersive=true)
  }}}
  compose.onNodeWithTag("web-app-menu").performClick()
  compose.onNodeWithTag("private-app-menu-sheet").assertIsDisplayed()
  listOf("Back","Forward","Refresh","Open in Browser","Screenshot to Vault").forEach {
   compose.onNodeWithText(it).performScrollTo().assertIsNotEnabled().performClick()
  }
  compose.runOnIdle {assertEquals(0,pageBacks+forwards+refreshes);compose.activity.onBackPressedDispatcher.onBackPressed()}
  compose.onNodeWithTag("private-app-menu-sheet").assertDoesNotExist()
  compose.runOnIdle {assertEquals(0,pageBacks)}
 }
 @Test fun capturedSheetCallbackCannotReviveAfterBackgroundAndAnotherOpen() {
  val app=fixture();var homes=0
  compose.setContent {PrivateGalleryTheme {PrivateAppControls(app,"https://example.test/","Primary Space",true,true,true,false,
   {},{},{},{},{},{},{},onExitGallery={homes++},immersive=true)}}
  compose.onNodeWithTag("web-app-menu").performClick()
  val oldHome=checkNotNull(compose.onNodeWithText("Nova Home").performScrollTo().fetchSemanticsNode().config[SemanticsActions.OnClick].action)
  compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
  compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
  compose.onNodeWithTag("private-app-menu-sheet").assertDoesNotExist()
  compose.onNodeWithTag("web-app-menu").performClick()
  compose.runOnIdle {oldHome();assertEquals(0,homes)}
  compose.onNodeWithTag("private-app-menu-sheet").assertIsDisplayed()
  compose.onNodeWithText("Nova Home").performScrollTo().performClick()
  compose.runOnIdle {assertEquals(1,homes)}
 }
 @Test fun replacingAppWithoutReplacingHostRetiresOldMenuAndBindsNewLifecycle() {
  val appA=fixture();val appB=fixture()
  var selected by mutableStateOf(appA)
  val dispatched=mutableListOf<String>()
  compose.setContent {PrivateGalleryTheme {
   // Recompose one host in place, as a pending saved app changes before tab binding.
   val rendered=selected
   Column(Modifier.fillMaxSize()) {
    Box(Modifier.weight(1f).fillMaxWidth().testTag("same-tab-host"))
    PrivateAppControls(rendered,rendered.startUrl(),"Primary Space",true,true,true,false,
     {},{},{},{},{},{},{},onExitGallery={dispatched+=rendered.id()},immersive=true,
     current={selected.id()==rendered.id()})
   }
  }}
  fun captureHome()=checkNotNull(compose.onNodeWithText("Nova Home").performScrollTo().fetchSemanticsNode().config[SemanticsActions.OnClick].action)
  compose.onNodeWithTag("web-app-menu").performClick()
  val oldA=captureHome()
  compose.runOnIdle {selected=appB}
  compose.onNodeWithTag("same-tab-host").assertIsDisplayed()
  compose.onNodeWithTag("private-app-menu-sheet").assertDoesNotExist()
  compose.runOnIdle {oldA();assertTrue("Retired app A must not dispatch through app B's current authority",dispatched.isEmpty())}
  compose.onNodeWithTag("web-app-menu").performClick()
  val oldB=captureHome()
  compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
  compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
  compose.onNodeWithTag("private-app-menu-sheet").assertDoesNotExist()
  compose.onNodeWithTag("web-app-menu").performClick()
  compose.runOnIdle {oldA();oldB();assertTrue("Neither retired sheet may revive after reopening B",dispatched.isEmpty())}
  compose.onNodeWithTag("private-app-menu-sheet").assertIsDisplayed()
  compose.onNodeWithText("Nova Home").performScrollTo().performClick()
  compose.runOnIdle {assertEquals(listOf(appB.id()),dispatched)}
 }
 @Test fun capturedMenuActionRechecksAvailabilityBeforeDispatch() {
  val app=fixture();var enabled by mutableStateOf(true);var refreshes=0
  compose.setContent {PrivateGalleryTheme {PrivateAppControls(app,app.startUrl(),"Primary Space",enabled,true,true,false,
   {},{},{refreshes++},{},{},{},{},immersive=true)}}
  compose.onNodeWithTag("web-app-menu").performClick()
  val refresh=checkNotNull(compose.onNodeWithText("Refresh").performScrollTo().fetchSemanticsNode().config[SemanticsActions.OnClick].action)
  compose.runOnIdle {enabled=false}
  compose.onNodeWithText("Refresh").performScrollTo().assertIsNotEnabled()
  compose.runOnIdle {refresh();assertEquals("A callback captured while available must respect the current disabled state",0,refreshes)}
  compose.onNodeWithTag("private-app-menu-sheet").assertIsDisplayed()
 }
 @Test fun sheetAdaptsToLargeTextAndRevokedAuthorityCannotAct() {
  val app=fixture();var live by mutableStateOf(true);var homes=0
  compose.setContent {val density=LocalDensity.current
   CompositionLocalProvider(LocalDensity provides Density(density.density,1.6f)) {PrivateGalleryTheme {
    Box(Modifier.fillMaxSize(),contentAlignment=Alignment.BottomCenter) {
     Box(Modifier.width(360.dp)) {PrivateAppControls(app,"https://example.test/","Primary Space",true,true,true,false,
      {},{},{},{},{},{},{},onExitGallery={homes++},immersive=true,current={live})}
    }
   }}
  }
  compose.onNodeWithTag("web-app-menu").performClick()
  compose.onNodeWithText("Nova Home").performScrollTo().assertIsDisplayed()
  compose.runOnIdle {live=false}
  compose.waitUntil(2000) {compose.onAllNodesWithTag("private-app-menu-sheet").fetchSemanticsNodes().isEmpty()}
  compose.runOnIdle {assertEquals(0,homes)}
 }
}
