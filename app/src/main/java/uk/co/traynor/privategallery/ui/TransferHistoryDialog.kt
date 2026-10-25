package uk.co.traynor.privategallery.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicBoolean
import uk.co.traynor.privategallery.core.hidden.PrimaryCopyAccess
import uk.co.traynor.privategallery.core.hidden.TransferJournal

/** Only composed inside independently authenticated Hidden + fresh Primary copy scope. */
@Composable
internal fun TransferHistoryDialog(access:PrimaryCopyAccess,onDismiss:()->Unit) {
 val scope=rememberCoroutineScope();val stopped=remember {AtomicBoolean(false)}
 var operations by remember {mutableStateOf<List<TransferJournal.Operation>>(emptyList())}
 var message by remember {mutableStateOf("Loading encrypted history…")}
 var busy by remember {mutableStateOf(false)}
 var revision by remember {mutableIntStateOf(0)}
 DisposableEffect(access) {onDispose {stopped.set(true)}}
 LaunchedEffect(access,revision) {val result=withContext(Dispatchers.IO){runCatching {access.history()}};if(access.isActive())result.onSuccess {operations=it;message=if(it.isEmpty())"No recorded transfers." else "History is encrypted and visible only after Hidden authentication."}.onFailure {message="History could not be authenticated. Records retained."}}
 val time=remember {DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm").withZone(ZoneId.systemDefault())}
 AlertDialog(onDismissRequest={if(!busy)onDismiss()},title={Text("Transfer history")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
  Text(message)
  LazyColumn(Modifier.heightIn(max=420.dp)) {items(operations,key={it.id}) {op ->
   Column(Modifier.fillMaxWidth().padding(vertical=8.dp)) {
    Text("${if(op.move)"Move" else "Copy"} · ${if(op.direction=="PRIMARY_TO_HIDDEN")"Primary → Hidden" else "Hidden → Primary"}")
    Text("${time.format(Instant.ofEpochMilli(op.started))} · ${op.items.size} items")
    Text("${op.status} · ${op.verified()} verified · ${op.failed()} failed")
    if(op.ended>0)Text("Ended ${time.format(Instant.ofEpochMilli(op.ended))}")
    if(op.status!in setOf("COMPLETE","CANCELLED")) Row {
     TextButton(enabled=!busy,onClick={busy=true;stopped.set(false);scope.launch {
      val result=withContext(Dispatchers.IO){runCatching {access.resume(op.id,{stopped.get()}) {done,total->scope.launch {message="$done / $total processed"}}}}
      if(access.isActive()){busy=false;message=result.fold({"${it.verified} verified; ${it.failed} failed; originals retained where incomplete."},{"Interrupted. Authenticate again before resuming."});revision++}
     }}) {Text(if(op.failed()>0)"Retry / resume" else "Resume")}
     TextButton(enabled=!busy,onClick={busy=true;scope.launch {withContext(Dispatchers.IO){runCatching {access.cancelRecorded(op.id)}};busy=false;revision++}}) {Text("Cancel transfer")}
    }
   }
  }}
  if(busy)TextButton(onClick={stopped.set(true)}) {Text("Stop after current item")}
 }},confirmButton={TextButton(enabled=!busy,onClick=onDismiss){Text("Close")}},dismissButton={TextButton(enabled=!busy,onClick={busy=true;scope.launch {val result=withContext(Dispatchers.IO){runCatching {access.clearHistory()}};busy=false;if(result.isFailure)message="History cleanup failed; media unchanged.";revision++}}){Text("Clear finished history")}})
}
