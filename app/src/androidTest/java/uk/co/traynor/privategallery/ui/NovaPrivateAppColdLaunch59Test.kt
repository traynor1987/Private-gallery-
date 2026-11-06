package uk.co.traynor.privategallery.ui

import androidx.compose.runtime.MutableState
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import uk.co.traynor.privategallery.*
import uk.co.traynor.privategallery.core.browser.v2.BrowserV2Session
import uk.co.traynor.privategallery.core.evidence.EvidenceAccess
import uk.co.traynor.privategallery.core.home.HomeLayout
import uk.co.traynor.privategallery.core.home2.NativeAppsBridge
import uk.co.traynor.privategallery.core.privateapps.PrivateAppCatalog
import java.io.File
import java.util.UUID

/** Actual Home bridge, production controller/session and existing fail-closed policy. No VPN
 * credentials, transport implementation, renderer gate or WebView settings are replaced. */
class NovaPrivateAppColdLaunch59Test {
 @get:Rule val compose=createAndroidComposeRule<MainActivity>()
 private fun call(name:String,vararg args:Any?):Any?=MainActivity::class.java.declaredMethods.single{it.name==name}.apply{isAccessible=true}.invoke(compose.activity,*args)
 @Suppress("UNCHECKED_CAST") private fun route()=MainActivity::class.java.getDeclaredField("route\$delegate").apply{isAccessible=true}.get(compose.activity) as MutableState<Route>
 private fun fixture(body:(PrivateAppCatalog.App,BrowserV2Session,()->Unit)->Unit) {
  val a=compose.activity;val state=ViewModelProvider(a)[ProtectedSessionState::class.java]
  val settings=a.getSharedPreferences("private-gallery-settings",0);val before=settings.all.toMap()
  val beta=a.getSharedPreferences("nova-browser-beta",0);val betaBefore=beta.all.toMap()
  val roots=listOf("private-apps","browser-session","evidence-state/home2","evidence-state/home1710").map{File(a.filesDir,it)}
  val saved=roots.associateWith{r->if(r.exists())r.walkTopDown().filter{it.isFile}.associate{it.relativeTo(r).path to it.readBytes()}else emptyMap()}
  roots.forEach{it.deleteRecursively()}
  val key=ByteArray(32){79};val lifetime=a.lifecycleScope.coroutineContext[Job]
  val app=PrivateAppCatalog.App(UUID.randomUUID().toString(),"Cold launch fixture","https://cold-launch.invalid/app/","https://cold-launch.invalid/app/","standalone","",false,false,false)
  try {
   compose.runOnIdle {
    beta.edit().putInt("notice-revision",1).commit()
    call("applyBrowserRequireVpn",false);call("applyBrowserAutoConnectVpn",false);call("applyHideContent",false)
    call("applyAutoLockTimeout",uk.co.traynor.privategallery.core.security.AutoLockTimeout.MINUTES_5)
    state.hidden.leave();state.key=key;state.session.unlock();state.homePrimaryAuthentication=state.session.captureAuthenticationEpoch();state.homeVisible=false
    route().value=Route.VAULT;call("launchHomeShortcut",HomeLayout.Shortcut.BROWSER)
   }
   compose.waitUntil(15_000){compose.onAllNodesWithTag("browser-v2-root").fetchSemanticsNodes().isNotEmpty()}
   val owner=compose.runOnIdle{(call("getBrowserV2Session") as BrowserV2Session).also{checkNotNull(a.privateAppsController()).save(app)}}
   val launch:()->Unit={
    compose.runOnIdle {
     a.returnPrivateHome()
     val access=checkNotNull(call("homeAccess") as? EvidenceAccess)
     try {checkNotNull(call("homeNativeApps",access) as? NativeAppsBridge).launch(app.id())}finally{access.close()}
    }
   }
   body(app,owner,launch)
  } finally {
   InstrumentationRegistry.getInstrumentation().runOnMainSync{a.lockNovaSpace();state.hidden.leave();state.key=null;state.session.lock();route().value=Route.LOCK}
   compose.activityRule.scenario.close();runBlocking{lifetime?.children?.toList()?.joinAll()}
   roots.forEach{r->r.deleteRecursively();saved.getValue(r).forEach{(n,b)->File(r,n).apply{parentFile?.mkdirs();writeBytes(b)};b.fill(0)}}
   fun restore(p:android.content.SharedPreferences,old:Map<String,*>) {p.edit().clear().apply{old.forEach{(k,v)->when(v){is Boolean->putBoolean(k,v);is String->putString(k,v);is Int->putInt(k,v);is Long->putLong(k,v);is Float->putFloat(k,v);is Set<*>->@Suppress("UNCHECKED_CAST") putStringSet(k,v as Set<String>)}}}.commit()}
   restore(settings,before);restore(beta,betaBefore);key.fill(0)
  }
 }
 @Test fun actualNativeRootsAndStandaloneBrowserShareOneRowAndRecentsWithoutAppOverflow()=fixture{_,_,_->
  listOf(HomeLayout.Shortcut.GALLERY,HomeLayout.Shortcut.VAULT,HomeLayout.Shortcut.SETTINGS,HomeLayout.Shortcut.BROWSER).forEach {destination->
   compose.runOnIdle{call("launchHomeShortcut",destination)}
   compose.onAllNodesWithTag("nova-navigation").assertCountEquals(1)
   listOf("nova-recents","nova-home","nova-back").forEach{compose.onNodeWithTag(it).assertIsDisplayed()}
   compose.onNodeWithTag("web-app-menu").assertDoesNotExist()
   compose.onNodeWithTag("nova-recents").performClick()
   compose.onNodeWithTag("nova-recents-screen").assertIsDisplayed()
   compose.onNodeWithTag("nova-home").performClick()
   compose.onNodeWithTag("preview-home").assertIsDisplayed()
  }
 }
 @Test fun firstColdLaunchStaysInIntendedAppWhileRequiredVpnUnavailableAndRetryKeepsUuid()=fixture{app,owner,launch->
  compose.runOnIdle{call("applyBrowserRequireVpn",true)}
  launch()
  compose.onNodeWithTag("private-app-pending-launch").assertIsDisplayed()
  compose.onNodeWithTag("browser-v2-chrome").assertDoesNotExist()
  compose.onNodeWithTag("browser-v2-address").assertDoesNotExist()
  compose.onNodeWithTag("browser-v2-toolbar").assertDoesNotExist()
  compose.onNodeWithTag("browser-vpn-state").assertIsDisplayed()
  val ordinary=compose.runOnIdle{assertFalse(owner.mediaNetworkingAllowed());assertFalse(owner.hasPrivateAppTabs());owner.tabs.activeTab.id}
  compose.onNodeWithTag("private-app-launch-retry").performClick()
  compose.runOnIdle{assertEquals(app.id(),compose.activity.pendingNovaPrivateApp()?.id());assertEquals(ordinary,owner.tabs.activeTab.id);assertFalse(owner.hasPrivateAppTabs())}
  // Explicit user policy change models VPN-not-required; never substitute a permissive gate.
  compose.runOnIdle{call("applyBrowserRequireVpn",false);compose.activity.retryPendingNovaPrivateApp()}
  compose.waitUntil(15_000){compose.runOnIdle{owner.privateApp(owner.tabs.activeTab.id)?.id()==app.id()}}
  compose.onNodeWithTag("private-app-pending-launch").assertDoesNotExist()
  compose.onNodeWithTag("browser-v2-chrome").assertDoesNotExist()
  val id=compose.runOnIdle{owner.tabs.activeTab.id};val view=compose.runOnIdle{owner.activeWebViewOrNull()}
  assertNotNull(view)
  launch()
  compose.runOnIdle{assertEquals(id,owner.tabs.activeTab.id);assertSame(view,owner.activeWebViewOrNull());assertEquals(app.id(),owner.privateApp(id)?.id())}
  compose.onNodeWithTag("browser-v2-chrome").assertDoesNotExist()
 }
 @Test fun explicitOsVpnConsentHandoffRetainsOnlyAdmissionMetadataWhileOldControllerRetires()=fixture{app,owner,launch->
  compose.runOnIdle{call("applyBrowserRequireVpn",true)};launch()
  compose.onNodeWithTag("private-app-pending-launch").assertIsDisplayed()
  val activity=compose.activity
  val consent=MainActivity::class.java.getDeclaredField("primaryAppVpnConsentOwner").apply{isAccessible=true}
  @Suppress("UNCHECKED_CAST")
  val pendingState=MainActivity::class.java.getDeclaredField("novaPrivateAppLaunch\$delegate").apply{isAccessible=true}.get(activity) as MutableState<Any?>
  val oldController=compose.runOnIdle{checkNotNull(activity.privateAppsController())}
  val ticket=compose.runOnIdle{checkNotNull(pendingState.value).also{consent.set(activity,it)}}
  // Simulated OS result boundary; execute the REAL host Stop/cleanup/Resume, not a fake gate.
  compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
  InstrumentationRegistry.getInstrumentation().runOnMainSync{activity.retryPendingNovaPrivateApp();assertSame(ticket,pendingState.value)}
  compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
  compose.onNodeWithTag("private-app-pending-launch").assertIsDisplayed()
  compose.runOnIdle{
   assertFalse("The old transfer/controller authority must remain retired",oldController.isCurrent())
   assertEquals(app.id(),activity.pendingNovaPrivateApp()?.id());assertFalse(owner.mediaNetworkingAllowed());assertFalse(owner.hasPrivateAppTabs())
   consent.set(activity,null) // Android consent denial clears only this handoff bookmark.
   activity.retryPendingNovaPrivateApp();assertEquals(app.id(),activity.pendingNovaPrivateApp()?.id());assertFalse(owner.hasPrivateAppTabs())
   consent.set(activity,ticket);activity.lockNovaSpace()
   assertNull(activity.pendingNovaPrivateApp());assertNull(consent.get(activity))
   val state=ViewModelProvider(activity)[ProtectedSessionState::class.java]
   state.key=ByteArray(32){81};state.session.unlock();route().value=Route.BROWSER
   @Suppress("UNCHECKED_CAST") val original=ticket.javaClass.getDeclaredField("current").apply{isAccessible=true}.get(ticket) as ()->Boolean
   assertFalse("Another unlock cannot revive original admission",original())
   activity.retryPendingNovaPrivateApp();assertNull(activity.pendingNovaPrivateApp());assertFalse(owner.hasPrivateAppTabs())
  }
 }
 @Test fun changedSavedDefinitionRejectsBlockedLaunchToHomeInsteadOfOrdinaryBrowser()=fixture{app,owner,launch->
  compose.runOnIdle{call("applyBrowserRequireVpn",true)};launch()
  compose.onNodeWithTag("private-app-pending-launch").assertIsDisplayed()
  compose.runOnIdle {
   checkNotNull(compose.activity.privateAppsController()).remove(app.id())
   call("applyBrowserRequireVpn",false);compose.activity.retryPendingNovaPrivateApp()
   assertNull(compose.activity.pendingNovaPrivateApp());assertFalse(owner.hasPrivateAppTabs())
  }
  compose.onNodeWithTag("preview-home").assertIsDisplayed()
  compose.onNodeWithTag("browser-v2-chrome").assertDoesNotExist()
 }
 @Test fun pendingColdLaunchIsCancelledByHomeBackgroundAndActualLock()=fixture{app,owner,launch->
  compose.runOnIdle{call("applyBrowserRequireVpn",true)};launch()
  compose.onNodeWithTag("private-app-pending-launch").assertIsDisplayed()
  compose.onNodeWithTag("nova-home").performClick()
  compose.onNodeWithTag("preview-home").assertIsDisplayed()
  compose.runOnIdle{assertNull(compose.activity.pendingNovaPrivateApp());assertFalse(owner.hasPrivateAppTabs())}
  launch();compose.onNodeWithTag("private-app-pending-launch").assertIsDisplayed()
  compose.runOnIdle{call("applyHideContent",true);assertNull(compose.activity.pendingNovaPrivateApp());call("applyHideContent",false);assertNull(compose.activity.pendingNovaPrivateApp())}
  launch();compose.onNodeWithTag("private-app-pending-launch").assertIsDisplayed()
  compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
  compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
  compose.runOnIdle{assertNull(compose.activity.pendingNovaPrivateApp());assertFalse(owner.hasPrivateAppTabs())}
  compose.onNodeWithTag("private-app-pending-launch").assertDoesNotExist()
  launch();compose.onNodeWithTag("private-app-pending-launch").assertIsDisplayed()
  compose.runOnIdle{compose.activity.lockNovaSpace();assertNull(compose.activity.pendingNovaPrivateApp());compose.activity.retryPendingNovaPrivateApp();assertNull(compose.activity.pendingNovaPrivateApp());assertFalse(owner.hasPrivateAppTabs())}
  compose.onNodeWithContentDescription("App menu").assertDoesNotExist()
 }
}
