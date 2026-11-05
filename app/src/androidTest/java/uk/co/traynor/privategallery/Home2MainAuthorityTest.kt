package uk.co.traynor.privategallery

import androidx.compose.runtime.MutableState
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import org.junit.Assert.*
import org.junit.Test

/** Actual MainActivity authority transitions in the isolated test identity; no owner credentials. */
class Home2MainAuthorityTest {
 private fun state(activity:MainActivity)=ViewModelProvider(activity)[ProtectedSessionState::class.java]
 @Suppress("UNCHECKED_CAST") private fun route(activity:MainActivity) = MainActivity::class.java.getDeclaredField("route\$delegate").apply{isAccessible=true}.get(activity) as MutableState<Route>
 @Test fun retained_hidden_authority_never_blocks_an_independent_primary_credential_request(){
  ActivityScenario.launch(MainActivity::class.java).use{scenario->scenario.onActivity{activity->
   val owner=state(activity)
   try {
    owner.session.lock();owner.key=null
    assertTrue(owner.hidden.session.complete(owner.hidden.session.beginAuthentication(),ByteArray(32){77}))
    owner.hidden.selected=false;route(activity).value=Route.LOCK
    assertFalse(activity.novaCredentialsRequested)
    activity.requestNovaCredentials()
    assertTrue("Primary Enter must remain usable while an independent Hidden session is retained",activity.novaCredentialsRequested)
    assertFalse(owner.session.isUnlocked)
    assertTrue(owner.hidden.session.isUnlocked)
   }finally{activity.closeNovaCredentials();owner.hidden.leave();owner.session.lock();owner.key=null;route(activity).value=Route.LOCK}
  }}
 }
 @Test fun home_revokes_browser_interaction_synchronously_before_the_next_composition(){
  ActivityScenario.launch(MainActivity::class.java).use{scenario->scenario.onActivity{activity->
   val owner=state(activity);val key=ByteArray(32){41}
   try {
    owner.key=key;owner.session.unlock();owner.hidden.leave();route(activity).value=Route.BROWSER
    owner.homePrimaryAuthentication=owner.session.captureAuthenticationEpoch();owner.homeVisible=false
    assertTrue(activity.primarySessionRetainedForNova());assertFalse(activity.primaryBrowserCoveredByNovaHome())
    activity.returnPrivateHome()
    assertTrue("Home authority changes before Compose can recompose",activity.primaryBrowserCoveredByNovaHome())
    assertTrue(activity.primarySessionRetainedForNova())
    activity.panicExit()
    assertFalse(activity.primarySessionRetainedForNova());assertFalse(owner.session.isUnlocked);assertFalse(owner.hidden.session.isUnlocked)
   }finally{owner.hidden.leave();owner.session.lock();owner.key=null;key.fill(0);route(activity).value=Route.LOCK}
  }}
 }
}
