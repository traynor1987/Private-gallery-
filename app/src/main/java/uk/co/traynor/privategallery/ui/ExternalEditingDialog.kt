package uk.co.traynor.privategallery.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import java.util.concurrent.atomic.AtomicBoolean

/** Only mounted by MainActivity while the exact originating vault is independently unlocked. */
@Composable
internal fun ExternalEditingDialog(
 label:String,prepared:Boolean,result:Uri?,saved:Boolean,status:String,exportChosen:Boolean,
 prepare:()->Unit,launch:()->Unit,export:()->Unit,writeExport:()->Unit,pick:()->Unit,
 preview:((Result<Pair<ByteArray,Bitmap>>)->Unit)->Unit,
 save:(ByteArray,Boolean,(Result<Unit>)->Unit)->Unit,cleanupPublic:()->Unit,close:()->Unit,
) {
 val alive=remember {AtomicBoolean(true)}
 val resultAlive=remember(result) {AtomicBoolean(true)}
 var bytes by remember(result) {mutableStateOf<ByteArray?>(null)}
 var bitmap by remember(result) {mutableStateOf<Bitmap?>(null)}
 var busy by remember {mutableStateOf(false)}
 LaunchedEffect(result){busy=false}
 var replace by remember {mutableStateOf(false)}
 var message by remember {mutableStateOf("")}
 DisposableEffect(result){onDispose {resultAlive.set(false);bytes?.fill(0);bitmap?.recycle()}}
 DisposableEffect(Unit){onDispose {alive.set(false)}}
 fun loadPreview(){busy=true;preview {r ->if(!alive.get()||!resultAlive.get()){r.getOrNull()?.let {it.first.fill(0);it.second.recycle()}}else {busy=false;r.onSuccess {bytes?.fill(0);bitmap?.recycle();bytes=it.first;bitmap=it.second}.onFailure {message="Result unavailable. Unlock the originating vault and select the edited photo again."}}}}
 fun commit(replacing:Boolean){if(!resultAlive.get())return;val data=bytes?.copyOf() ?: return;busy=true;save(data,replacing){r->if(alive.get()){busy=false;message=if(r.isSuccess)"Encrypted result verified. Original ${if(replacing)"moved to Recently Deleted" else "retained"}." else "Save failed or authentication expired. Original retained."}}}
 AlertDialog(onDismissRequest={if(!busy)close()},title={Text("Edit with Samsung Gallery")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
  Text("Origin and return destination: $label")
  Text("External editing temporarily leaves this vault's encryption boundary. Samsung AI may use cloud processing. Samsung caches, backups and cloud copies cannot be erased by Private Gallery.")
  Text("Your original stays encrypted. If Samsung does not return a result, save the edit in Samsung, return and choose Select edited result. After returning, reopen the originating photo and choose Edit with Samsung Gallery again to resume. If this vault locks, authenticate first.")
  if(!saved) {
   if(!prepared)TextButton(enabled=!busy,onClick=prepare) {Text("Confirm temporary handoff")}
   if(prepared) {
    TextButton(enabled=!busy,onClick=launch){Text("Open supported Samsung editor")}
    TextButton(enabled=!busy,onClick=export){Text("Manual fallback: export a copy…")}
   }
   if(exportChosen)TextButton(enabled=!busy,onClick=writeExport){Text("Write confirmed export")}
   TextButton(enabled=!busy,onClick=pick){Text("Select edited result…")}
   if(result!=null)TextButton(enabled=!busy,onClick=::loadPreview){Text("Preview selected result")}
   bitmap?.let {Image(it.asImageBitmap(),"Edited result preview",Modifier.fillMaxWidth().heightIn(max=180.dp))}
   if(bytes!=null) {
    TextButton(enabled=!busy,onClick={commit(false)}){Text("Save as New")}
    TextButton(enabled=!busy,onClick={replace=true}){Text("Replace Original…")}
   }
  } else TextButton(enabled=!busy,onClick=cleanupPublic){Text("Request deletion of known public copies")}
  if(status.isNotBlank())Text(status)
  if(message.isNotBlank())Text(message)
  Text("App-owned temporary copies expire after 15 minutes and are removed after verified import or cancellation. Public copies need separate cleanup. After process death, reselect the original and edited result; no vault key or filename is saved for this handoff.")
 }},confirmButton={TextButton(enabled=!busy,onClick=close){Text(if(saved)"Done" else "Cancel")}})
 if(replace)AlertDialog(onDismissRequest={replace=false},title={Text("Replace encrypted original?")},text={Text("The edited result will be imported and verified first. Only then is the original moved to Recently Deleted in this same vault.")},confirmButton={TextButton(onClick={replace=false;commit(true)}){Text("Verify and replace")}},dismissButton={TextButton(onClick={replace=false}){Text("Cancel")}})
}
