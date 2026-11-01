package uk.co.traynor.privategallery.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeTestRule

/** Materialize lazy rows before querying them; static dialog buttons have no scroll ancestor. */
internal fun ComposeTestRule.evidenceNode(matcher:SemanticsMatcher,last:Boolean=false):SemanticsNodeInteraction {
 waitForIdle()
 val nodes=onAllNodes(matcher).fetchSemanticsNodes()
 val lazyAncestor=onAllNodes(matcher and hasAnyAncestor(hasScrollToNodeAction())).fetchSemanticsNodes().isNotEmpty()
 if(nodes.isEmpty()||lazyAncestor) {
  // Resolve by matcher inside the current lazy tree. A text edit can replace lazy rows;
  // retaining a node across the resulting scroll/IME layout keeps an obsolete node ID.
  onAllNodes(hasScrollToNodeAction()).onLast().performScrollToNode(matcher)
  waitForIdle()
 }
 // Lazy item materialization does not guarantee a deeply nested action is in the viewport.
 // Resolve the child again before scrolling its actual bounds into view.
 if(onAllNodes(matcher and hasAnyAncestor(hasScrollAction())).fetchSemanticsNodes().isNotEmpty()) {
  val current=if(last)onAllNodes(matcher).onLast()else onNode(matcher)
  current.performScrollTo();waitForIdle()
 }
 val node=if(last)onAllNodes(matcher).onLast()else onNode(matcher)
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
