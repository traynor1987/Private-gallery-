package uk.co.traynor.privategallery.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import uk.co.traynor.privategallery.core.privateapps.*
import uk.co.traynor.privategallery.core.ui.AppTheme

class PrivateAppsUi178Test {
 @get:Rule val compose=createComposeRule()
 @Test fun libraryCrudLaunchAndRevocation() {
  val context=ApplicationProvider.getApplicationContext<android.content.Context>()
  val root=File(context.cacheDir,"privateapps-ui-test").apply{deleteRecursively();mkdirs()}
  val key=ByteArray(32){3};var live=true;var closed=0;var launched=""
  val store=PrimaryPrivateAppStore(root,key,{live},{check(live);it()})
  val controller=PrivateAppsController(store.owner(),{live},{action,bytes,id->PrivateAppCommands.execute(store,action,bytes,id)},{launched=it})
  compose.setContent {PrivateGalleryTheme(AppTheme.DARK){PrivateAppsLibrary(controller,{closed++})}}
  compose.waitUntil(5000){compose.onAllNodesWithText("No private apps yet").fetchSemanticsNodes().isNotEmpty()}
  compose.onNodeWithText("Add Private App").performClick()
  compose.onNodeWithTag("private-app-name").performTextInput("Fixture")
  compose.onNodeWithTag("private-app-url").performTextInput("https://example.test/")
  compose.onNodeWithText("Save").performClick()
  compose.waitUntil(5000){compose.onAllNodesWithText("Fixture").fetchSemanticsNodes().isNotEmpty()}
  compose.onNodeWithContentDescription("Edit Fixture").performClick()
  compose.onNodeWithTag("private-app-name").performTextReplacement("Renamed")
  compose.onNodeWithText("Save").performClick()
  compose.waitUntil(5000){compose.onAllNodesWithText("Renamed").fetchSemanticsNodes().isNotEmpty()}
  compose.onNodeWithText("Open").performClick()
  compose.runOnIdle{assertEquals("https://example.test/",launched);assertEquals(1,closed)}
  compose.onNodeWithContentDescription("Remove Renamed").performClick()
  compose.onNodeWithText("Remove app").performClick()
  compose.waitUntil(5000){compose.onAllNodesWithText("No private apps yet").fetchSemanticsNodes().isNotEmpty()}
  compose.runOnIdle{live=false}
  compose.waitUntil(5000){closed==2}
  compose.onNodeWithText("No private apps yet").assertDoesNotExist()
  key.fill(0);root.deleteRecursively()
 }
}
