package uk.co.traynor.privategallery.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Presentation only: direction-specific credentials and operation authority stay with owners. */
@Composable internal fun NovaTransferSummary(source:String,destination:String,count:Int,move:Boolean) {
 Card(Modifier.fillMaxWidth()) {
  Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
   Text(if(move)"Move $count selected items"else"Copy $count selected items",style=MaterialTheme.typography.titleMedium)
   Text("$source → $destination",style=MaterialTheme.typography.bodyLarge)
   Text(if(move)"Sources are removed only after verified transfer. Unfinished items retain their originals."else"Originals remain unchanged.",style=MaterialTheme.typography.bodySmall)
  }
 }
}
@Composable internal fun NovaTransferProgress(completed:Int,total:Int,stopping:Boolean=false,onCancel:(()->Unit)?=null) {
 Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(12.dp)) {
  Text("$completed / $total processed")
  LinearProgressIndicator(progress={if(total<=0)0f else (completed.toFloat()/total).coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth())
  Text(if(stopping)"Cancellation requested. Completed verified copies remain."else"Keep the app open. Cancellation retains originals for unfinished transfers.",style=MaterialTheme.typography.bodySmall)
  if(onCancel!=null)OutlinedButton(onClick=onCancel,enabled=!stopping,modifier=Modifier.fillMaxWidth()){Text("Cancel transfer")}
 }
}
