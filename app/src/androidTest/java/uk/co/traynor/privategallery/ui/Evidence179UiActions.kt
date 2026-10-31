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
