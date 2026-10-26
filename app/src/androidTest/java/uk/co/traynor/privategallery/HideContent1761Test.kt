package uk.co.traynor.privategallery
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Actual Activity/persisted presentation; no real vault media or credentials are created. */
class HideContent1761Test {
 @get:Rule val compose=createAndroidComposeRule<MainActivity>()
 private fun invoke(a:MainActivity,name:String,vararg args:Any?)=MainActivity::class.java.declaredMethods.single {it.name==name}.apply {isAccessible=true}.invoke(a,*args)
 private fun gallery(a:MainActivity){invoke(a,"setRoute",Route.GALLERY)}
 @Test fun concealedTabsRestartAndPanicRetainFlagWithoutDeletingSyntheticState() {
  val ctx=InstrumentationRegistry.getInstrumentation().targetContext
  val prefs=ctx.getSharedPreferences("private-gallery-settings",0)
  val previous=prefs.getString("hide-content",null)
  val sentinel=File(ctx.filesDir,"presentation-fixture-${java.util.UUID.randomUUID()}")
  val bytes=byteArrayOf(3,9,4,7);sentinel.writeBytes(bytes)
  try {
   compose.activityRule.scenario.onActivity {a ->
    invoke(a,"applyHideContent",true)
    val state=ViewModelProvider(a)[ProtectedSessionState::class.java]
    assertTrue(state.hidden.session.complete(state.hidden.session.beginAuthentication(),ByteArray(32){9}));state.hidden.select();gallery(a)
   }
   compose.onNodeWithText("No photos").assertExists()
   for(tab in listOf("Vault","Favourite","Browser","Settings")) {
    compose.onNode(hasText(tab) and hasClickAction()).performClick()
    compose.onAllNodesWithText("Hidden",substring=true).assertCountEquals(0)
   }
   compose.onNodeWithText("Private Gallery 0.0.1 · Build 4").assertExists()
   compose.onNodeWithText("Gallery & Vault").performScrollTo().performClick()
   compose.onNodeWithText("Storage").assertDoesNotExist()
   compose.onAllNodesWithText("Hidden",substring=true).assertCountEquals(0)
   compose.onNodeWithContentDescription("Back to Settings").performClick()
   compose.onNodeWithText("Updates & About").performScrollTo().performClick()
   compose.onNodeWithText("0.0.1 · Build 4").assertExists()
   compose.onNodeWithText("Private Gallery 0.0.1").performScrollTo().assertExists()
   compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
   compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
   compose.activityRule.scenario.recreate()
   compose.activityRule.scenario.onActivity {a ->assertEquals(true,invoke(a,"getHideContent"));gallery(a)}
   compose.onNodeWithText("No photos").assertExists()
   compose.activityRule.scenario.onActivity {a ->
    a.panicExit();assertEquals(true,invoke(a,"getHideContent"))
    ViewModelProvider(a)[ProtectedSessionState::class.java].hidden.leave()
    invoke(a,"applyHideContent",false);assertEquals(false,invoke(a,"getHideContent"))
   }
   assertArrayEquals(bytes,sentinel.readBytes())
  } finally {
   compose.activityRule.scenario.onActivity {a ->ViewModelProvider(a)[ProtectedSessionState::class.java].hidden.leave();invoke(a,"applyHideContent",previous!=null&&previous!="false")}
   val e=prefs.edit();if(previous==null)e.remove("hide-content")else e.putString("hide-content",previous);check(e.commit())
   sentinel.delete();bytes.fill(0)
  }
 }
}
