package uk.co.traynor.privategallery.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Activity-owned pages inherit the vault screenshot policy; credential pages remain protected. */
@Composable internal fun EvidencePage(onBack:()->Unit,sensitive:Boolean=false,content:@Composable ()->Unit) {
 val activity=androidx.activity.compose.LocalActivity.current
 val novaActivity=activity as? uk.co.traynor.privategallery.MainActivity
 DisposableEffect(activity,sensitive){
  val token=if(sensitive)novaActivity?.beginProtectedPage()else null
  val genericProtection=if(sensitive&&novaActivity==null)activity?.window?.let{EvidenceSensitiveWindowProtection.acquire(it)}else null
  onDispose{token?.let{novaActivity?.endProtectedPage(it)};genericProtection?.close()}
 }
 BackHandler(onBack=onBack)
 Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background){
  Column(Modifier.fillMaxSize().navigationBarsPadding().imePadding()) {
   Box(Modifier.weight(1f).fillMaxWidth()){content()}
   NovaNativeNavigation(onBack=onBack)
  }
 }
}

@Composable internal fun EvidenceWorkflowPage(onDismissRequest:()->Unit,title:@Composable ()->Unit,
 text:@Composable ()->Unit,confirmButton:@Composable ()->Unit,dismissButton:@Composable ()->Unit,
 sensitive:Boolean=false) {
 EvidencePage(onDismissRequest,sensitive=sensitive) {
  Column(
   Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(horizontal = 16.dp, vertical = 8.dp),
   verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
   // Measure the action row before the heading and form when the keyboard reduces the viewport.
   Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    title()
    Box(Modifier.weight(1f).fillMaxWidth()) { text() }
   }
   Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
    dismissButton()
    confirmButton()
   }
  }
 }
}
