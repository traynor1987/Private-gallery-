package uk.co.traynor.privategallery.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID
import uk.co.traynor.privategallery.core.browser.BrowserSearchEngine
import uk.co.traynor.privategallery.core.browser.v2.*
import uk.co.traynor.privategallery.core.privateapps.PrivateAppCatalog

/** Actual production chrome composition; static page host grants no network/renderer authority. */
class PrivateAppPresentation48Test {
 @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
 @Test fun presentation_and_address_preferences_are_independent_without_permanent_app_toolbars() {
  lateinit var session:BrowserV2Session
  val base=PrivateAppCatalog.App(UUID.randomUUID().toString(),"Standalone fixture","https://example.test/app/","https://example.test/app/","standalone","",false,true,false)
  var mounted by mutableStateOf(true)
  var connectionBlocked by mutableStateOf(false)
  compose.runOnUiThread {
   session=BrowserV2Session(compose.activity,BrowserVpnGate{false},NoopBrowserV2Listener,rendererAllowed={false})
   session.openPrivateApp(base)
  }
  try {
   compose.setContent {PrivateGalleryTheme {if(mounted)BrowserV2Home(session,BrowserSearchEngine.GOOGLE,{_,_->},{_,_->},false,{},emptyList(),{_,_,_->},{},{it(emptyList())},{it()},{},staticContentHost=true,connectionPresentation=BrowserConnectionPresentation(connectionBlocked))}}
   compose.onNodeWithText("Standalone fixture").assertDoesNotExist()
   compose.onNodeWithContentDescription("App menu").assertIsDisplayed()
   compose.onNodeWithTag("browser-v2-chrome").assertDoesNotExist()
   compose.onNodeWithTag("browser-v2-address").assertDoesNotExist()
   compose.onNodeWithTag("browser-v2-toolbar").assertDoesNotExist()
   compose.runOnIdle {connectionBlocked=true}
   compose.onNodeWithTag("browser-v2-chrome").assertDoesNotExist()
   compose.onNodeWithTag("browser-vpn-state").assertIsDisplayed()
   compose.onNodeWithTag("browser-v2-address").assertDoesNotExist()
   compose.runOnIdle {connectionBlocked=false}
   compose.onNodeWithTag("browser-v2-chrome").assertDoesNotExist()
   compose.onNodeWithContentDescription("App home").assertDoesNotExist()
   val root=compose.onNodeWithTag("browser-v2-root").fetchSemanticsNode().boundsInRoot
   val page=compose.onNodeWithTag("browser-v2-page-region").fetchSemanticsNode().boundsInRoot
   val nav=compose.onNodeWithTag("nova-navigation").fetchSemanticsNode().boundsInRoot
   assertTrue(page.top>=root.top);assertEquals(page.bottom,nav.top,1f)
   assertTrue(nav.bottom<=root.bottom)
   compose.onNodeWithContentDescription("App menu").performClick()
   compose.onNodeWithText("App Settings").performScrollTo().assertIsDisplayed().performClick()
   compose.onNodeWithTag("browser-v2-address").assertDoesNotExist()
   val tab=session.tabs.activeTab.id
   fun set(style:Boolean,address:Boolean) {
    compose.runOnIdle {
     session.updatePrivateApp(PrivateAppCatalog.App(base.id(),base.name(),base.startUrl(),base.scope(),base.display(),base.icon(),base.pinned(),address,base.requireAuthentication(),style,""))
    }
    compose.waitForIdle()
    compose.runOnIdle {assertEquals(tab,session.tabs.activeTab.id);assertNull(session.activeWebViewOrNull())}
   }
   set(true,false)
   compose.onNodeWithTag("browser-v2-address").assertDoesNotExist()
   compose.onNodeWithTag("browser-v2-toolbar").assertDoesNotExist()
   set(false,false)
   compose.onNodeWithTag("browser-v2-address").assertDoesNotExist()
   compose.onNodeWithTag("browser-v2-toolbar").assertIsDisplayed()
   set(false,true)
   compose.onNodeWithTag("browser-v2-address").assertIsDisplayed()
   compose.onNodeWithTag("browser-v2-toolbar").assertIsDisplayed()
   set(true,true)
   compose.onNodeWithContentDescription("App menu").assertIsDisplayed()
   compose.onNodeWithTag("browser-v2-chrome").assertDoesNotExist()
   compose.onNodeWithTag("browser-v2-address").assertDoesNotExist()
   compose.onNodeWithTag("browser-v2-toolbar").assertDoesNotExist()
  } finally {
   compose.runOnIdle{mounted=false};compose.waitForIdle();compose.runOnIdle{session.destroyAll()}
  }
 }
}
