package uk.co.traynor.privategallery.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.ZoneId
import uk.co.traynor.privategallery.core.ui.MediaSelection
import uk.co.traynor.privategallery.core.vault.VaultItem

/** No media payload is opened by this dialog. Scope is supplied by the authenticated view. */
@Composable
internal fun MediaSelectionDialog(items:List<VaultItem>,scopeLabel:String,onDismiss:()->Unit,onSelect:(Set<String>)->Unit) {
 var dateMode by remember {mutableStateOf(true)}
 var taken by remember {mutableStateOf(false)}
 var start by remember {mutableStateOf(LocalDate.now().toString())}
 var end by remember {mutableStateOf(LocalDate.now().toString())}
 val zone=remember {ZoneId.systemDefault()}
 var sizeMode by remember {mutableIntStateOf(0)}
 var first by remember {mutableStateOf("100")}
 var second by remember {mutableStateOf("500")}
 val match=remember(items,dateMode,taken,start,end,sizeMode,first,second) {runCatching {
  if(dateMode) {
   val from=LocalDate.parse(start.trim());val to=LocalDate.parse(end.trim())
   require(!to.isBefore(from)) {"End date must follow start date"}
   items.filter {item -> MediaSelection.date(if(taken)item.adjustedTakenAtEpochMillis ?: item.capturedAtEpochMillis ?: 0L else item.addedAtEpochMillis ?: item.importedAtEpochMillis,from,to,zone)}
  } else {
   val low=Math.multiplyExact(first.trim().toLong(),1_000_000L)
   val high=if(sizeMode==2)Math.multiplyExact(second.trim().toLong(),1_000_000L) else 0L
   require(low>=0 && (sizeMode!=2||high>=low)) {"Enter valid non-negative size bounds"}
   items.filter {MediaSelection.size(it.plaintextSize,sizeMode,low,high)}
  }
 }}
 AlertDialog(onDismissRequest=onDismiss,title={Text("Select by date or size")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
  Text("Scope: $scopeLabel (${items.size} items)")
  Row {FilterChip(selected=dateMode,onClick={dateMode=true},label={Text("Date range")});Spacer(Modifier.width(8.dp));FilterChip(selected=!dateMode,onClick={dateMode=false},label={Text("File size")})}
  if(dateMode) {
   Row {FilterChip(selected=!taken,onClick={taken=false},label={Text("Date Added")});Spacer(Modifier.width(8.dp));FilterChip(selected=taken,onClick={taken=true},label={Text("Date Taken")})}
   OutlinedTextField(start,{start=it},label={Text("Start date · YYYY-MM-DD")},singleLine=true,modifier=Modifier.fillMaxWidth())
   OutlinedTextField(end,{end=it},label={Text("End date · YYYY-MM-DD")},singleLine=true,modifier=Modifier.fillMaxWidth())
   Text("Both dates included · ${zone.id}. Missing dates excluded. Date Taken uses your edited display date where set.")
  } else {
   Row {TextButton(onClick={sizeMode=(sizeMode+1)%3}) {Text(listOf("Larger than","Smaller than","Between (inclusive)")[sizeMode])}}
   OutlinedTextField(first,{first=it},label={Text("Size in MB")},singleLine=true,modifier=Modifier.fillMaxWidth())
   if(sizeMode==2)OutlinedTextField(second,{second=it},label={Text("Maximum MB")},singleLine=true,modifier=Modifier.fillMaxWidth())
   Row {listOf(100L,500L,1000L).forEach {preset->TextButton(onClick={first=preset.toString();sizeMode=0}) {Text(if(preset==1000L)"Over 1 GB" else "$preset MB+")}}}
   Text("1 MB = 1,000,000 bytes. Unknown sizes excluded.")
  }
  Text(match.fold({"${it.size} matching items"},{"Check dates (YYYY-MM-DD) and size bounds."}),color=if(match.isSuccess)MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error)
 }},confirmButton={TextButton(enabled=match.isSuccess,onClick={onSelect(match.getOrThrow().mapTo(linkedSetOf()){it.id})}) {Text("Select matches")}},dismissButton={TextButton(onClick=onDismiss) {Text("Cancel")}})
}
