package uk.co.traynor.privategallery.ui

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*
import uk.co.traynor.privategallery.core.ui.AppTheme

class BrowserBetaNoticeTest {
 @get:Rule val compose=createComposeRule()
 private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
 private val preferences get()=context.getSharedPreferences("nova-browser-beta",Context.MODE_PRIVATE)
 private var oldRevision:Int?=null
 @Before fun reset(){oldRevision=if(preferences.contains("notice-revision"))preferences.getInt("notice-revision",0)else null;check(preferences.edit().remove("notice-revision").commit())}
 @After fun restore(){val edit=preferences.edit();oldRevision?.let{edit.putInt("notice-revision",it)}?:edit.remove("notice-revision");check(edit.commit())}
 @Test fun contentStartsOnlyAfterAcknowledgementAndNewCompositionRemembersIt() {
  var generation by mutableIntStateOf(0)
  var starts=0
  compose.setContent {PrivateGalleryTheme {key(generation){
   if(!BrowserBetaNotice({})) {SideEffect {starts++};Box(Modifier.testTag("browser-body"))}
  }}}
  compose.onNodeWithTag("browser-body").assertDoesNotExist()
  compose.runOnIdle{assertEquals(0,starts);assertFalse(BrowserBetaAcknowledgement(context).accepted())}
  compose.onNodeWithTag("browser-beta-continue").performScrollTo().performClick()
  compose.onNodeWithTag("browser-body").assertExists()
  compose.runOnIdle{assertTrue(BrowserBetaAcknowledgement(context).accepted());generation++}
  compose.onNodeWithText("Beta browser privacy").assertDoesNotExist()
  compose.onNodeWithTag("browser-body").assertExists()
  compose.runOnIdle{assertEquals(setOf("notice-revision"),preferences.all.keys)}
 }
 @Test fun savedAppIdentityNeverSkipsTheSamePrivacyAcknowledgement() {
  var starts=0
  compose.setContent {PrivateGalleryTheme {
   if(!BrowserBetaNotice({},appName="Music fixture")){SideEffect {starts++};Box(Modifier.testTag("app-body"))}
  }}
  compose.onNodeWithText("Music fixture").assertIsDisplayed()
  compose.onNodeWithText("Browser & Tor — Beta").assertDoesNotExist()
  compose.onNodeWithText("WebView may write cookies",substring=true).performScrollTo().assertIsDisplayed()
  compose.onNodeWithTag("app-body").assertDoesNotExist()
  compose.runOnIdle {assertEquals(0,starts);assertFalse(BrowserBetaAcknowledgement(context).accepted())}
  compose.onNodeWithText("Continue to app").performScrollTo().performClick()
  compose.onNodeWithTag("app-body").assertExists()
  compose.runOnIdle {assertTrue(BrowserBetaAcknowledgement(context).accepted())}
 }
 @Test fun homeLockAndPanicDoNotAcknowledgeOrStartContent() {
  var home=0;var lock=0;var panic=0;var starts=0
  compose.setContent{CompositionLocalProvider(LocalNovaSecurityActions provides NovaSecurityActions({home++},{lock++},{panic++},null)){
   PrivateGalleryTheme{if(!BrowserBetaNotice({error("Must use owner Home")})){SideEffect{starts++}}}
  }}
  compose.onNodeWithContentDescription("Lock Space").performClick()
  compose.onNodeWithContentDescription("Panic Exit").performClick()
  compose.onNodeWithContentDescription("Nova Home").performClick()
  compose.runOnIdle{assertEquals(1,home);assertEquals(1,lock);assertEquals(1,panic);assertEquals(0,starts);assertFalse(BrowserBetaAcknowledgement(context).accepted())}
 }
 @Test fun privacyDisclosureAndActionsFitCoverInnerThemesAndLargeText() {
  var dark by mutableStateOf(false);var width by mutableStateOf(328.dp);var height by mutableStateOf(600.dp)
  compose.setContent{CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density,1.5f)){
   PrivateGalleryTheme(if(dark)AppTheme.DARK else AppTheme.LIGHT){Box(Modifier.requiredSize(width,height)){BrowserBetaNotice({})}}
  }}
  for(inner in listOf(false,true)) {
   compose.runOnIdle{dark=inner;width=if(inner)840.dp else 328.dp;height=if(inner)500.dp else 600.dp}
   compose.onNodeWithText("WebView may write cookies",substring=true).performScrollTo().assertIsDisplayed()
   compose.onNodeWithTag("browser-beta-continue").performScrollTo().assertIsDisplayed()
   compose.onNodeWithText("Return Home").performScrollTo().assertIsDisplayed()
  }
 }
}
