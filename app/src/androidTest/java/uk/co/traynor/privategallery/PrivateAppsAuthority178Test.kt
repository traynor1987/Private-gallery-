package uk.co.traynor.privategallery

import androidx.compose.runtime.MutableState
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID
import uk.co.traynor.privategallery.core.privateapps.*

class PrivateAppsAuthority178Test {
 @get:Rule val compose=createAndroidComposeRule<MainActivity>()
 @Test fun actual_activity_factory_requires_primary_auth_and_rejects_old_epochs() {
  val activity=compose.activity
  assertTrue(BuildConfig.DEBUG);assertEquals("uk.co.traynor.privategallery.hiddenprototype",activity.packageName)
  val state=ViewModelProvider(activity)[ProtectedSessionState::class.java]
  val folder=File(activity.filesDir,"private-apps");assertFalse("Disposable fresh fixture only",folder.exists())
  @Suppress("UNCHECKED_CAST")
  val route=MainActivity::class.java.getDeclaredField("route\$delegate").apply{isAccessible=true}.get(activity) as MutableState<Route>
  val key=ByteArray(32){21}
  try {
   compose.runOnIdle {
    assertNull(activity.privateAppsController())
    state.key=key;state.session.unlock();route.value=Route.BROWSER
   }
   lateinit var controller:PrivateAppsController
   compose.runOnIdle {
    controller=checkNotNull(activity.privateAppsController())
    assertEquals(PrivateAppStore.Owner.PRIMARY,controller.owner)
    controller.save(PrivateAppCatalog.App(UUID.randomUUID().toString(),"Auth fixture","https://example.test/","","browser","",false,true,false))
    assertEquals(1,controller.load().apps.size)
    state.hidden.select();assertNull(activity.privateAppsController())
    assertThrows(IllegalStateException::class.java){controller.load()}
    state.hidden.leave()
    state.session.onActivityStopped(100,false,false)
    assertFalse(controller.isCurrent())
    state.session.unlock() // Same key object; the authentication epoch still changes.
    assertThrows(IllegalStateException::class.java){controller.load()}
    val fresh=checkNotNull(activity.privateAppsController());assertEquals(1,fresh.load().apps.size)
    MainActivity::class.java.getDeclaredMethod("revokePhoneTransfers").apply{isAccessible=true}.invoke(activity)
    assertThrows(IllegalStateException::class.java){fresh.load()}
   }
  }finally{compose.runOnIdle{state.session.lock();state.key=null;route.value=Route.LOCK};key.fill(0);folder.deleteRecursively()}
 }
}
