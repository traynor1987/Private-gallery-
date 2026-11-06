package uk.co.traynor.privategallery.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import uk.co.traynor.privategallery.core.evidence.EvidenceAiReport

/** Render real report UI and exercise its actual source actions; no network/private data. */
class EvidenceReport60UiTest {
 @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
 private val id="00000000-0000-4000-8000-000000000001"
 private fun report()=EvidenceAiReport.Report("INSUFFICIENT_EVIDENCE","A selected passage may contain a threat","LOW",
  listOf("Missing conversational context"),listOf("Only owner-selected material"),emptyList(),
  listOf(EvidenceAiReport.Instance(listOf(EvidenceAiReport.Category.THREATS),"Potential threat","LOW","UNKNOWN",
   listOf("May quote another speaker"),listOf(EvidenceAiReport.Reference(id,"I will hurt you.","transcript",null)))),
  EvidenceAiReport.Category.entries.associateWith{if(it==EvidenceAiReport.Category.THREATS)1 else 0},0)

 @Test fun nativeReportShowsQualifiedCountsDisclaimerPassageAndSourceAction() {
  var opened by mutableStateOf("")
  compose.setContent{MaterialTheme{Column(Modifier.verticalScroll(rememberScrollState())){
   EvidenceBehaviourReportContent(report(),{true},{opened=it})
   Text("Opened source: $opened")
  }}}
  compose.onNodeWithText(EvidenceAiReport.DISCLAIMER).assertExists()
  compose.onNodeWithText("Insufficient Evidence*").assertExists()
  compose.onNodeWithText("Threats*: 1*").assertExists()
  compose.onNodeWithText("I will hurt you.",substring=true).assertExists()
  compose.onNodeWithTag("report-source-$id").performScrollTo().performClick()
  compose.onNodeWithText("Opened source: $id").assertExists()
 }

 @Test fun revokedSourceIsDisabledAndCapturedActionCannotOpenIt() {
  var allowed by mutableStateOf(true)
  var opened by mutableStateOf("")
  compose.setContent{MaterialTheme{Column(Modifier.verticalScroll(rememberScrollState())){
   EvidenceBehaviourReportContent(report(),{allowed},{opened=it})
   Text("Opened source: $opened")
  }}}
  val node=compose.onNodeWithTag("report-source-$id").performScrollTo()
  val captured=node.fetchSemanticsNode().config[SemanticsActions.OnClick].action!!
  compose.runOnIdle{allowed=false}
  node.assertIsNotEnabled()
  compose.runOnIdle{captured();assertEquals("",opened)}
 }
}
