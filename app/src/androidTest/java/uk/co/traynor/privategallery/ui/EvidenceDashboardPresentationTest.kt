package uk.co.traynor.privategallery.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import uk.co.traynor.privategallery.core.ui.AppTheme

/** Real adaptive gestures on the presentation component; source authority lives in controller tests. */
class EvidenceDashboardPresentationTest {
 private val compose=createAndroidComposeRule<ComponentActivity>()
 private val viewport=BrowserViewportRule(fontScale=2f){name->if(name.contains("landscape"))840 to 392 else 320 to 840}
 @get:Rule val rules:TestRule=RuleChain.outerRule(viewport).around(compose)
 private fun show(busy:Boolean=false,action:(String)->Unit={}) {
  compose.setContent {
   var query by remember{mutableStateOf("")}
   PrivateGalleryTheme(AppTheme.LIGHT) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
     EvidenceDashboardOverview(12,3,busy,query,{query=it},
      {action("import")},{action("existing")},{action("library")},{action("timeline")},{action("ai")},
      {action("incident")},{action("archive")},{action("refresh")})
    }
   }
  }
 }
 @Test fun coverLargeTextImportHasAccessibleTargetAndOnlyInvokesImport() {
  val actions=mutableListOf<String>();show{actions+=it}
  compose.onNodeWithText("Import Evidence").performScrollTo().assertIsDisplayed().assertHeightIsAtLeast(48.dp).performClick()
  assertEquals(listOf("import"),actions)
 }
 @Test fun landscapeLargeTextSearchAndShortcutsRemainReachable() {
  val actions=mutableListOf<String>();show{actions+=it}
  compose.onNodeWithTag("evidence-dashboard-search").performScrollTo().performTextInput("receipt")
  compose.onNodeWithTag("evidence-dashboard-search").assertTextContains("receipt")
  compose.onNodeWithText("Ask Nova AI").performScrollTo().performClick()
  assertEquals(listOf("ai"),actions)
  compose.onNodeWithText("View timeline").performScrollTo().performClick()
  assertEquals(listOf("ai","timeline"),actions)
 }
 @Test fun busyImportCannotStartAnotherWorkflow() {
  show(busy=true)
  compose.onNodeWithText("Import Evidence").performScrollTo().assertIsNotEnabled()
  compose.onNodeWithText("More actions").performScrollTo().performClick()
  compose.onNodeWithText("Add existing evidence").performScrollTo().assertIsNotEnabled()
 }
}
