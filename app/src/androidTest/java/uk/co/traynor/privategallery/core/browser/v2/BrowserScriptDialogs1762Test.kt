package uk.co.traynor.privategallery.core.browser.v2

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class BrowserScriptDialogs1762Test {
 @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
 private lateinit var session:BrowserV2Session
 private var network=true
 @After fun close(){if(::session.isInitialized)compose.runOnIdle {session.destroyAll()}}
 private fun fixture(test:(String)->Unit)=compose.runOnIdle {
  session=BrowserV2Session(compose.activity,BrowserVpnGate {network},NoopBrowserV2Listener)
  session.activeWebView()
  test(session.tabs.activeTab.id)
 }
 @Test fun backgroundAndLateReplyCannotApproveOldWebsiteDialog()=fixture {id->
  val answers=mutableListOf<String?>()
  session.onScriptDialog(id,"https://example.invalid/secret?q=private","question","",BrowserScriptDialog.Kind.CONFIRM){answers+=it}
  val request=checkNotNull(session.scriptDialog)
  assertEquals("example.invalid",request.origin)
  session.pauseForBackground()
  assertEquals(listOf<String?>(null),answers);assertNull(session.scriptDialog)
  session.resumeForeground();session.resolveScriptDialog(request,"yes")
  assertEquals(1,answers.size)
 }
 @Test fun networkRevocationAndInactiveTabRequestsFailClosed()=fixture {id->
  val answers=mutableListOf<String?>()
  session.onScriptDialog("missing","https://example.invalid","question","",BrowserScriptDialog.Kind.CONFIRM){answers+=it}
  assertEquals(listOf<String?>(null),answers)
  session.onScriptDialog(id,"https://example.invalid","question","",BrowserScriptDialog.Kind.PROMPT){answers+=it}
  network=false;session.enforceNetworkPolicy()
  assertEquals(listOf<String?>(null,null),answers);assertNull(session.scriptDialog)
 }
 @Test fun explicitReplyIsOnceOnlyAndCloseCancels()=fixture {id->
  val answers=mutableListOf<String?>()
  session.onScriptDialog(id,"https://example.invalid","question","",BrowserScriptDialog.Kind.PROMPT){answers+=it}
  val request=checkNotNull(session.scriptDialog)
  session.resolveScriptDialog(request,"answer");session.resolveScriptDialog(request,"late")
  assertEquals(listOf("answer"),answers)
  session.onScriptDialog(id,"https://example.invalid","question","",BrowserScriptDialog.Kind.CONFIRM){answers+=it}
  session.close(id)
  assertEquals(listOf("answer",null),answers);assertNull(session.scriptDialog)
 }
}
