package uk.co.traynor.privategallery

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import uk.co.traynor.privategallery.core.home.*
import uk.co.traynor.privategallery.ui.*

class NovaNavigation17101Test {
 @get:Rule val compose=createAndroidComposeRule<MainActivity>()
 @Test fun authenticatedExistingInstallStartsAtNovaHomeAndAppRootReturnsHome() {
  val activity=compose.activity
  val state=ViewModelProvider(activity)[ProtectedSessionState::class.java]
  @Suppress("UNCHECKED_CAST") val route=MainActivity::class.java.getDeclaredField("route\$delegate").apply{isAccessible=true}.get(activity) as MutableState<Route>
  val key=ByteArray(32){71}
  try {
   compose.runOnIdle {state.key=key;state.session.unlock();state.homePrimaryAuthentication=null;state.homeVisible=false;route.value=Route.VAULT}
   compose.waitUntil(15000){compose.onAllNodesWithTag("private-home1710").fetchSemanticsNodes().isNotEmpty()}
   compose.onNodeWithText("Primary Space").assertExists()
   for(app in listOf(HomeLayout.Shortcut.GALLERY,HomeLayout.Shortcut.VAULT,HomeLayout.Shortcut.FAVOURITES,
       HomeLayout.Shortcut.COLLECTIONS,HomeLayout.Shortcut.RECENTLY_DELETED,HomeLayout.Shortcut.SETTINGS,HomeLayout.Shortcut.EVIDENCE_LOCKER)) {
    compose.runOnIdle {
     MainActivity::class.java.getDeclaredMethod("launchHomeShortcut",HomeLayout.Shortcut::class.java).apply{isAccessible=true}.invoke(activity,app)
     assertEquals("Reuse existing authorised feature route",NovaNavigation.route(app),route.value.name)
    }
    compose.waitUntil(15000){compose.onAllNodesWithContentDescription("Nova Home").fetchSemanticsNodes().isNotEmpty()}
    compose.onNodeWithTag("private-home1710").assertDoesNotExist()
    compose.onNodeWithContentDescription("Nova Home").performClick()
    compose.onNodeWithTag("private-home1710").assertExists()
   }
   compose.runOnIdle{activity.rememberNovaCollectionPosition("test-position");assertEquals("test-position",activity.novaCollectionPosition());activity.lockNovaSpace();activity.returnPrivateHome();assertNull(activity.novaCollectionPosition())}
   compose.onNodeWithTag("private-home1710").assertDoesNotExist()
  } finally {compose.runOnIdle{state.hidden.leave();state.key=null;state.session.lock();route.value=Route.LOCK};key.fill(0)}
 }
}
