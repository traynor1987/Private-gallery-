package uk.co.traynor.privategallery.ui

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID
import java.util.function.UnaryOperator
import uk.co.traynor.privategallery.core.privateapps.*
import uk.co.traynor.privategallery.core.ui.AppTheme

class PrivateAppSessionSettings178Test {
 @get:Rule val compose=createComposeRule()
 @Test fun fresh_pin_dialog_rejects_wrong_pin_accepts_correct_once_and_discards_revoked_request() {
  var live=true;var accepted=0;var rejected=0;var lastInput:CharArray?=null
  var request by mutableStateOf<PrivateAppAuthenticationRequest?>(null)
  fun makeRequest(hidden:Boolean)=PrivateAppAuthenticationRequest(hidden,{
   assertSame("Presentation authority must only inspect native UI on Main",android.os.Looper.getMainLooper(),android.os.Looper.myLooper());live
  },{input ->
   assertNotSame("Credential derivation must not block Main",android.os.Looper.getMainLooper(),android.os.Looper.myLooper())
   lastInput=input;input.concatToString()=="654321"
  },{ok ->if(ok)accepted++ else rejected++;request=null})
  compose.setContent {PrivateGalleryTheme(AppTheme.LIGHT){request?.let {PrivateAppAuthentication(it)}}}
  compose.runOnIdle {request=makeRequest(true)}
  compose.onNodeWithText("Enter your separate Hidden PIN.").assertIsDisplayed()
  compose.onNodeWithText("PIN").performTextInput("123456")
  compose.onNodeWithText("Open app").performClick()
  compose.waitUntil(5000){compose.onAllNodesWithText("Authentication failed or is temporarily unavailable.").fetchSemanticsNodes().isNotEmpty()}
  compose.runOnIdle {assertEquals(0,accepted);assertTrue(checkNotNull(lastInput).all{it=='\u0000'})}
  compose.onNodeWithText("PIN").performTextInput("654321")
  compose.onNodeWithText("Open app").performClick()
  compose.waitUntil(5000){accepted==1}
  compose.runOnIdle {request=makeRequest(false)}
  compose.onNodeWithText("Cancel").performClick();compose.runOnIdle {assertEquals(1,rejected);request=makeRequest(false);live=false}
  compose.waitUntil(5000){rejected==2}
  compose.runOnIdle {assertEquals(1,accepted)}
 }
 @Test fun shared_profile_effects_require_explicit_confirmation_and_hidden_has_no_retention_toggle() {
  val entry=PrivateAppCatalog.App(UUID.randomUUID().toString(),"Shared App","https://example.test/","","browser","",false,true,false)
  var value=PrivateAppCatalog(false,listOf(entry));var keep=false;var clears=0;var show by mutableStateOf(true)
  val store=object:PrivateAppStore {
   override fun owner()=PrivateAppStore.Owner.PRIMARY
   override fun read()=value
   override fun update(change:UnaryOperator<PrivateAppCatalog>):PrivateAppCatalog {value=change.apply(value);return value}
  }
  val primary=PrivateAppsController(store.owner(),{true},{a,b,id->PrivateAppCommands.execute(store,a,b,id)},{},keepSignedIn={keep},changeKeepSignedIn={keep=it},clearProfile={clears++})
  val hidden=PrivateAppsController(PrivateAppStore.Owner.HIDDEN_VPN,{true},{_,_,_->value},{},clearProfile={clears++})
  var controller by mutableStateOf(primary)
  compose.setContent {PrivateGalleryTheme(AppTheme.LIGHT){if(show)key(controller){
   val displayed=controller
   PrivateAppSettings(displayed,entry.id(),{}, {},{if(controller===displayed)show=false})
  }}}
  compose.waitUntil(5000){compose.onAllNodesWithTag("private-app-retention-toggle").fetchSemanticsNodes().isNotEmpty()}
  compose.onNodeWithTag("private-app-retention-toggle").performScrollTo().performClick()
  compose.runOnIdle {assertFalse(keep)}
  compose.onNodeWithText("Cancel").performClick();compose.runOnIdle {assertFalse(keep)}
  compose.onNodeWithTag("private-app-retention-toggle").performScrollTo().performClick()
  compose.onNodeWithText("Confirm").performClick();compose.runOnIdle {assertTrue(keep)}
  compose.onNodeWithText("Clear shared browser session and data").performScrollTo().performClick()
  compose.runOnIdle {assertEquals(0,clears)}
  compose.onNodeWithText("Cancel").performClick();compose.runOnIdle {assertEquals(0,clears)}
  compose.onNodeWithText("Clear shared browser session and data").performScrollTo().performClick()
  compose.onNodeWithText("Confirm").performClick()
  // Clearing closes the original dialog. Observe that publication before opening a
  // different owner's dialog; the callback side effect alone is not window disposal.
  compose.waitUntil(5000){clears==1 && compose.onAllNodesWithText("Private app settings").fetchSemanticsNodes().isEmpty()}
  compose.runOnIdle {assertEquals(1,clears);controller=hidden;show=true}
  compose.waitUntil(5000){compose.onAllNodesWithTag("private-app-auth-toggle").fetchSemanticsNodes().isNotEmpty()}
  compose.onNodeWithTag("private-app-retention-toggle").assertDoesNotExist()
 }
}
