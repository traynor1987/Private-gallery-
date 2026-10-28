package uk.co.traynor.privategallery.ui

import uk.co.traynor.privategallery.ui.GalleryAlertDialog as AlertDialog
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

/** Only mounted while the originating vault is independently authenticated. */
@Composable
internal fun ExternalEditingDialog(
 label:String,prepared:Boolean,result:Uri?,saved:Boolean,status:String,exportChosen:Boolean,
 prepare:()->Unit,launch:()->Unit,export:()->Unit,writeExport:()->Unit,pick:()->Unit,
 preview:((Result<Pair<ByteArray,Bitmap>>)->Unit)->Unit,
 save:(ByteArray,Boolean,(Result<Unit>)->Unit)->Unit,cleanupPublic:()->Unit,close:()->Unit,
 exportGallery:(()->Unit)?=null,fallback:Boolean=false,working:Boolean=false,
 editors:List<uk.co.traynor.privategallery.core.media.ExternalEditorIntents.EditorTarget> = emptyList(),
 chooseEditor:(android.content.ComponentName)->Unit = {},dismissEditors:()->Unit = {},
) {
 val alive=remember {AtomicBoolean(true)}
 val resultAlive=remember(result) {AtomicBoolean(true)}
 var bytes by remember(result) {mutableStateOf<ByteArray?>(null)}
 var bitmap by remember(result) {mutableStateOf<Bitmap?>(null)}
 var busy by remember {mutableStateOf(false)}
 LaunchedEffect(result){busy=false}
 var replace by remember {mutableStateOf(false)}
 var message by remember {mutableStateOf("")}
 var consent by remember {mutableStateOf(false)}
 var exportConsent by remember {mutableStateOf(false)}
 var help by remember {mutableStateOf(false)}
 val blocked=busy||working
 DisposableEffect(result){onDispose {resultAlive.set(false);bytes?.fill(0);bitmap?.recycle()}}
 DisposableEffect(Unit){onDispose {alive.set(false)}}
 fun loadPreview(){busy=true;preview {r ->if(!alive.get()||!resultAlive.get()){r.getOrNull()?.let {it.first.fill(0);it.second.recycle()}}else {busy=false;r.onSuccess {bytes?.fill(0);bitmap?.recycle();bytes=it.first;bitmap=it.second}.onFailure {message="Unlock this vault and select the edited photo again."}}}}
 fun commit(replacing:Boolean){if(!resultAlive.get())return;val data=bytes?.copyOf() ?: return;busy=true;save(data,replacing){r->if(alive.get()){busy=false;message=if(r.isSuccess)"Encrypted photo verified. Original ${if(replacing)"moved to Recently Deleted" else "retained"}." else "Save interrupted. Inspect this vault’s photos and Recently Deleted before retrying."}}}
 AlertDialog(scrollBody = false, onDismissRequest={if(!busy)close()},title={Text("Edit in another app")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
  Text("Choose a compatible photo editor, then bring your photo back into this vault.")
  Text("Return to $label",style=MaterialTheme.typography.labelMedium)
  if(working)LinearProgressIndicator(Modifier.fillMaxWidth())
  if(!saved) {
   TextButton(enabled=!blocked,onClick={consent=true}){Text("Open photo editor")}
   TextButton(enabled=!blocked,onClick=pick){Text("Import edited photo")}
   if(result!=null)TextButton(enabled=!blocked,onClick=::loadPreview){Text("Preview selected result")}
   bitmap?.let {Image(it.asImageBitmap(),"Edited result preview",Modifier.fillMaxWidth().heightIn(max=180.dp))}
   if(bytes!=null) {
    TextButton(enabled=!blocked,onClick={commit(false)}){Text("Save as New")}
    TextButton(enabled=!blocked,onClick={replace=true}){Text("Replace Original…")}
   }
   TextButton(enabled=!blocked,onClick={help=!help}){Text("Samsung Gallery cannot open this photo?")}
   if(help||fallback) {
    Text("Export a copy to phone Gallery, open it and tap Edit. Save your changes, return here, then import the edited photo.")
    if(exportGallery!=null)TextButton(enabled=!blocked,onClick={exportConsent=true}){Text(if(exportChosen)"Open exported photo" else "Export a copy to Gallery")}
    if(prepared && exportGallery==null)TextButton(enabled=!blocked,onClick=export){Text("Save a copy to Files…")}
   }
   // Explicit SAF fallback remains supported; writing is never implied by selecting a location.
   if(exportChosen && exportGallery==null)TextButton(enabled=!blocked,onClick=writeExport){Text("Write confirmed export")}
  } else {
   TextButton(enabled=!blocked,onClick=cleanupPublic){Text("Clean up public copies…")}
   Text("Your edited photo is encrypted. Public copies need separate confirmation; editor caches and cloud copies cannot be erased here.")
  }
  if(status.isNotBlank())Text(status)
  if(message.isNotBlank())Text(message)
 }},confirmButton={TextButton(enabled=!busy,onClick=close){Text(if(saved)"Done" else "Cancel")}})
 if(editors.isNotEmpty())AlertDialog(onDismissRequest=dismissEditors,title={Text("Choose photo editor")},
  text={Column {editors.forEach {editor ->
   TextButton(enabled=!blocked,onClick={chooseEditor(editor.component)}) {Text(editor.label)}
  }}},confirmButton={TextButton(onClick=dismissEditors){Text("Cancel")}})
 if(consent)AlertDialog(onDismissRequest={consent=false},title={Text("Allow external editing?")},
  text={Text("A temporary photo copy will leave this vault's encryption. Your original stays encrypted. The chosen editor may keep copies or use cloud processing. Access to app-owned temporary copies expires after 15 minutes; file cleanup may happen later.")},
  confirmButton={TextButton(onClick={consent=false;if(prepared)launch()else prepare()}){Text("Continue to editor")}},
  dismissButton={TextButton(onClick={consent=false}){Text("Keep photo private")}})
 if(exportConsent)AlertDialog(onDismissRequest={exportConsent=false},title={Text(if(exportChosen)"Open exported photo?" else "Export a public copy?")},
  text={Text("The copy will be visible in phone Gallery. Edit and save it there, then return and import the edited photo. Public copies are removed only with your confirmation after encrypted import. Editor caches or cloud copies are not covered.")},
  confirmButton={TextButton(onClick={exportConsent=false;exportGallery?.invoke()}){Text(if(exportChosen)"Open Gallery" else "Export copy")}},
  dismissButton={TextButton(onClick={exportConsent=false}){Text("Cancel")}})
 if(replace)AlertDialog(onDismissRequest={replace=false},title={Text("Replace encrypted original?")},text={Text("The edited result will be imported and verified first. Only then is the original moved to Recently Deleted in this same vault.")},confirmButton={TextButton(onClick={replace=false;commit(true)}){Text("Verify and replace")}},dismissButton={TextButton(onClick={replace=false}){Text("Cancel")}})
}
