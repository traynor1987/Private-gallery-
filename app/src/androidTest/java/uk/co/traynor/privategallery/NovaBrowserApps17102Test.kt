package uk.co.traynor.privategallery

import androidx.compose.runtime.MutableState
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import uk.co.traynor.privategallery.core.home.HomeLayout
import uk.co.traynor.privategallery.core.browser.hidden.HiddenConnectionPolicy

class NovaBrowserApps17102Test {
 @get:Rule val compose=createAndroidComposeRule<MainActivity>()
 @Test fun launcherKeepsTorHiddenOnlyAndIgnoresLegacyTransportPreference() {
  val activity=compose.activity
  val state=ViewModelProvider(activity)[ProtectedSessionState::class.java]
  @Suppress("UNCHECKED_CAST") val route=MainActivity::class.java.getDeclaredField("route\$delegate").apply{isAccessible=true}.get(activity) as MutableState<Route>
  val launch=MainActivity::class.java.getDeclaredMethod("launchHomeShortcut",HomeLayout.Shortcut::class.java).apply{isAccessible=true}
  val preferences=activity.getSharedPreferences("hidden-browser-v16",0)
  val old=preferences.getString("mode",null)
  val homeRoot=java.io.File(activity.filesDir,"home1710")
  val priorHome=if(homeRoot.exists())homeRoot.walkTopDown().filter{it.isFile}.associate{it.relativeTo(homeRoot).path to it.readBytes()}else emptyMap()
  homeRoot.deleteRecursively()
  val key=ByteArray(32){36}
  try {
   preferences.edit().putString("mode","TOR_ONLY").commit()
   compose.runOnIdle{state.key=key;state.session.unlock();route.value=Route.VAULT;activity.returnPrivateHome()}
   compose.waitUntil(15000){compose.onAllNodesWithTag("private-home1710").fetchSemanticsNodes().isNotEmpty()}
   compose.onNodeWithTag("nova-app-TOR_BROWSER").assertDoesNotExist()
   compose.runOnIdle{launch.invoke(activity,HomeLayout.Shortcut.TOR_BROWSER);assertEquals(Route.VAULT,route.value);assertTrue(state.homeVisible)}
   compose.runOnIdle{
    state.hidden.select();assertTrue(state.hidden.session.complete(state.hidden.session.beginAuthentication(),ByteArray(32){91}));state.hidden.authenticated=true;activity.returnPrivateHome()
   }
   compose.onNodeWithTag("nova-app-grid").performScrollToNode(hasTestTag("nova-app-TOR_BROWSER"))
   compose.onNodeWithTag("nova-app-TOR_BROWSER").assertIsDisplayed()
   compose.runOnIdle {
    val prior=state.hidden.session.authority()
    launch.invoke(activity,HomeLayout.Shortcut.BROWSER)
    assertEquals(HiddenConnectionPolicy.Mode.VPN_ONLY,activity.novaHiddenBrowserMode)
    assertFalse("Old presentation authority retires before browser launch",state.hidden.session.isAuthorityCurrent(prior))
    activity.hiddenBrowserOwner?.close();activity.returnPrivateHome()
    launch.invoke(activity,HomeLayout.Shortcut.TOR_BROWSER)
    assertEquals(HiddenConnectionPolicy.Mode.TOR_ONLY,activity.novaHiddenBrowserMode)
    activity.hiddenBrowserOwner?.close();activity.returnPrivateHome()
   }
  }finally{
   compose.runOnIdle{activity.hiddenBrowserOwner?.close();state.hidden.leave();state.key=null;state.session.lock();route.value=Route.LOCK}
   homeRoot.deleteRecursively();priorHome.forEach{(path,bytes)->java.io.File(homeRoot,path).apply{parentFile?.mkdirs();writeBytes(bytes)};bytes.fill(0)}
   preferences.edit().apply{if(old==null)remove("mode")else putString("mode",old)}.commit();key.fill(0)
  }
 }
}
