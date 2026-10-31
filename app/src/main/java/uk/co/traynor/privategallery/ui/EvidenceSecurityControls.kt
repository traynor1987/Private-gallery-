package uk.co.traynor.privategallery.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*

internal class EvidenceSecurityActions(val lock:()->Unit,val panic:()->Unit,val hide:()->Unit)
internal val LocalEvidenceSecurityActions=staticCompositionLocalOf<EvidenceSecurityActions?>{null}
/** Close returns to the vault; these explicit actions retain their distinct security meaning. */
@Composable internal fun EvidenceSecurityControls(){
 val actions=LocalEvidenceSecurityActions.current?:return
 var open by remember{mutableStateOf(false)}
 Box {
  IconButton(onClick={open=true}){Icon(Icons.Default.MoreVert,"Evidence security controls")}
  DropdownMenu(expanded=open,onDismissRequest={open=false}) {
   DropdownMenuItem(text={Text("Lock Vault")},onClick={open=false;actions.lock()})
   DropdownMenuItem(text={Text("Hide Content")},onClick={open=false;actions.hide()})
   DropdownMenuItem(text={Text("Panic Exit")},onClick={open=false;actions.panic()})
  }
 }
}

@Composable internal fun EvidenceDialogTitle(text:String){
 androidx.compose.foundation.layout.Row(androidx.compose.ui.Modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){
  Text(text,androidx.compose.ui.Modifier.weight(1f),style=MaterialTheme.typography.titleLarge)
  EvidenceSecurityControls()
 }
}
