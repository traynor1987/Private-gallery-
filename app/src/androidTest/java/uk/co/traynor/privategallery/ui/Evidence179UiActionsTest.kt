package uk.co.traynor.privategallery.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import uk.co.traynor.privategallery.core.ui.AppTheme

class Evidence179UiActionsTest {
 private val compose=createAndroidComposeRule<ComponentActivity>()
 private val viewport=BrowserViewportRule(fontScale=2f){320 to 840}
 @get:Rule val rules:TestRule=RuleChain.outerRule(viewport).around(compose)
 @Test fun helperScrollsToActionDeepInsideOversizedLazyCardBeforeGesture() {
  var accepted=0
  compose.setContent {
   PrivateGalleryTheme(AppTheme.LIGHT) {
    LazyColumn(Modifier.fillMaxSize()) {
     item {Spacer(Modifier.height(900.dp))}
     item {
      Card(Modifier.fillMaxWidth().testTag("oversized-suggestion")) {
       Column {
        Text("Suggested grouping · unverified")
        Spacer(Modifier.height(900.dp))
        TextButton(onClick={accepted++}){Text("Review / accept")}
       }
      }
     }
    }
   }
  }
  compose.evidenceNode(hasText("Review / accept") and hasAnyAncestor(hasTestTag("oversized-suggestion")))
   .assertIsDisplayed().performClick()
  compose.runOnIdle{assertEquals(1,accepted)}
 }
}
