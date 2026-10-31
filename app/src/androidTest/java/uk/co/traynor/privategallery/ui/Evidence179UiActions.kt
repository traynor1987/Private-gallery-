package uk.co.traynor.privategallery.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeTestRule

/** Materialize lazy rows before querying them; static dialog buttons have no scroll ancestor. */
internal fun ComposeTestRule.evidenceNode(matcher:SemanticsMatcher,last:Boolean=false):SemanticsNodeInteraction {
 if(onAllNodes(matcher).fetchSemanticsNodes().isEmpty()) {
  // The last registered scroll-to-node surface belongs to the topmost Evidence dialog.
  onAllNodes(hasScrollToNodeAction()).onLast().performScrollToNode(matcher)
 }
 val nodes=onAllNodes(matcher)
 val node=if(last)nodes.onLast()else onNode(matcher)
 if(onAllNodes(matcher and hasAnyAncestor(hasScrollAction())).fetchSemanticsNodes().isNotEmpty())node.performScrollTo()
 return node
}

/** Drive the Compose frame clock while waiting for a real IO/native gate, without releasing it. */
internal fun ComposeTestRule.awaitEvidenceGate(label:String,gate:java.util.concurrent.CountDownLatch,timeoutMillis:Long,
 failureClass:()->String?={null},diagnostics:()->String) {
 try{waitUntil(timeoutMillis){gate.count==0L||failureClass()!=null}}
 catch(failure:ComposeTimeoutException){throw AssertionError("$label did not enter: ${diagnostics()} error=${failureClass()}",failure)}
 val remaining=gate.count;val error=failureClass()
 org.junit.Assert.assertEquals(if(remaining==0L)label else "$label did not enter: ${diagnostics()} error=$error",0L,remaining)
 org.junit.Assert.assertNull(if(error==null)label else "$label failed before controlled retirement: ${diagnostics()}",error)
}
