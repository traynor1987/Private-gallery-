package uk.co.traynor.privategallery

import androidx.compose.runtime.MutableState
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Controlled test identity only; actual Main Home admission and Hidden capability refresh. */
class HomeNavigation1710Test {
 @get:Rule val compose=createAndroidComposeRule<MainActivity>()
 @Test fun homeRequiresAuthenticationAndSurvivesHiddenPresentationRevocation() {
  val activity=compose.activity
  assertTrue(BuildConfig.DEBUG)
  val state=ViewModelProvider(activity)[ProtectedSessionState::class.java]
  @Suppress("UNCHECKED_CAST") val route=MainActivity::class.java.getDeclaredField("route\$delegate").apply{isAccessible=true}.get(activity) as MutableState<Route>
  val settings=activity.getSharedPreferences("private-gallery-settings",0)
  val prior=settings.getBoolean("home-existing-install",true)
  val priorScreenshots=settings.getString("allow-screenshots",null)
  settings.edit().putBoolean("home-existing-install",false).commit()
  val key=ByteArray(32){32}
  try {
   compose.runOnIdle{route.value=Route.LOCK;state.key=null;state.session.lock();activity.returnPrivateHome()}
   compose.onNodeWithTag("private-home1710").assertDoesNotExist()
   compose.runOnIdle{state.key=key;state.session.unlock();route.value=Route.VAULT;MainActivity::class.java.getDeclaredMethod("applyAllowScreenshots",Boolean::class.javaPrimitiveType).apply{isAccessible=true}.invoke(activity,true);activity.returnPrivateHome()}
   compose.waitUntil(15000){compose.onAllNodesWithTag("private-home1710").fetchSemanticsNodes().isNotEmpty()}
   compose.onNodeWithText("Primary Vault").assertExists()
   compose.runOnIdle{
    state.hidden.select();assertTrue(state.hidden.session.complete(state.hidden.session.beginAuthentication(),ByteArray(32){82}))
    state.hidden.authenticated=true;activity.returnPrivateHome()
   }
   compose.waitUntil(15000){compose.onAllNodes(hasText("Hidden Vault") and hasAnyAncestor(hasTestTag("private-home1710"))).fetchSemanticsNodes().isNotEmpty()}
   compose.runOnIdle{state.session.lock();state.hidden.outside();state.primaryGallery=true;route.value=Route.GALLERY;activity.returnPrivateHome()
    val capture=MainActivity::class.java.getDeclaredMethod("currentScreenshotPreference").apply{isAccessible=true}.invoke(activity) as Boolean
    assertFalse("Hidden Home must not inherit underlying device Gallery screenshot allowance",capture)
   }
   compose.waitUntil(15000){compose.onAllNodesWithTag("private-home1710").fetchSemanticsNodes().isNotEmpty()}
   compose.onNode(hasText("Hidden Vault") and hasAnyAncestor(hasTestTag("private-home1710"))).assertExists()
   compose.runOnIdle{activity.panicExit()}
   compose.onNodeWithTag("private-home1710").assertDoesNotExist()
   assertFalse(state.hidden.session.isUnlocked);assertFalse(state.session.isUnlocked)
  }finally{
   compose.runOnIdle{state.hidden.leave();state.session.lock();state.key=null;route.value=Route.LOCK}
   settings.edit().putBoolean("home-existing-install",prior).apply{if(priorScreenshots==null)remove("allow-screenshots")else putString("allow-screenshots",priorScreenshots)}.commit();key.fill(0)
  }
 }
}
