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
  compose.runOnUiThread {
   session=BrowserV2Session(compose.activity,BrowserVpnGate{false},NoopBrowserV2Listener,rendererAllowed={false})
   session.openPrivateApp(base)
  }
  try {
   compose.setContent {PrivateGalleryTheme {if(mounted)BrowserV2Home(session,BrowserSearchEngine.GOOGLE,{_,_->},{_,_->},false,{},emptyList(),{_,_,_->},{},{it(emptyList())},{it()},{},staticContentHost=true)}}
   compose.onNodeWithText("Standalone fixture").assertIsDisplayed()
   compose.onNodeWithTag("browser-v2-address").assertDoesNotExist()
   compose.onNodeWithTag("browser-v2-toolbar").assertDoesNotExist()
   compose.onNodeWithContentDescription("App home").assertDoesNotExist()
   compose.onNodeWithContentDescription("Expand app controls").performClick()
   compose.onNodeWithTag("browser-v2-address").assertIsDisplayed()
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
   compose.onNodeWithContentDescription("Collapse app controls").performClick()
   compose.onNodeWithTag("browser-v2-address").assertDoesNotExist()
   compose.onNodeWithTag("browser-v2-toolbar").assertDoesNotExist()
  } finally {
   compose.runOnIdle{mounted=false};compose.waitForIdle();compose.runOnIdle{session.destroyAll()}
  }
 }
}
