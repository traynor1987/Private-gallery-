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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
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
 chooseEditor:(android.content.ComponentName)->Unit = {},dismissEditors:()->Unit = {},acknowledgeReceipt:(()->Unit)?=null,
 launchSamsung:(()->Unit)?=null,compatibleEditorAvailable:Boolean=true,
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
 var samsungConsent by remember {mutableStateOf(false)}
 var exportConsent by remember {mutableStateOf(false)}
 var acknowledge by remember {mutableStateOf(false)}
 val bodyHeight=(LocalConfiguration.current.screenHeightDp.dp * 0.58f).coerceAtMost(520.dp)
 val blocked=busy||working
 DisposableEffect(result){onDispose {resultAlive.set(false);bytes?.fill(0);bitmap?.recycle()}}
 DisposableEffect(Unit){onDispose {alive.set(false)}}
 fun loadPreview(){busy=true;preview {r ->if(!alive.get()||!resultAlive.get()){r.getOrNull()?.let {it.first.fill(0);it.second.recycle()}}else {busy=false;r.onSuccess {bytes?.fill(0);bitmap?.recycle();bytes=it.first;bitmap=it.second}.onFailure {message="Unlock this vault and select the edited photo again."}}}}
 fun commit(replacing:Boolean){if(!resultAlive.get())return;val data=bytes?.copyOf() ?: return;busy=true;save(data,replacing){r->if(alive.get()){busy=false;message=if(r.isSuccess)"Encrypted photo verified. Original ${if(replacing)"moved to Recently Deleted" else "retained"}." else "Save interrupted. Inspect this vault’s photos and Recently Deleted before retrying."}}}
 AlertDialog(scrollBody = false, onDismissRequest={if(!busy)close()},title={Text("Edit in another app")},text={Column(Modifier.fillMaxWidth().heightIn(max=bodyHeight).verticalScroll(rememberScrollState()).testTag("external_editor_actions"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
  Text("Choose a compatible photo editor, then bring your photo back into this vault.")
  Text("Return to $label",style=MaterialTheme.typography.labelMedium)
  if(working)LinearProgressIndicator(Modifier.fillMaxWidth())
  if(!saved) {
   if(launchSamsung!=null)Button(modifier=Modifier.fillMaxWidth().heightIn(min=48.dp),enabled=!blocked,onClick={samsungConsent=true;consent=true}){Text("Edit with Samsung Gallery")}
   if(compatibleEditorAvailable)OutlinedButton(modifier=Modifier.fillMaxWidth().heightIn(min=48.dp),enabled=!blocked,onClick={samsungConsent=false;consent=true}){Text("Choose another compatible photo editor")}
   OutlinedButton(modifier=Modifier.fillMaxWidth().heightIn(min=48.dp),enabled=!blocked,onClick=pick){Text("Import edited result")}
   if(result!=null)OutlinedButton(modifier=Modifier.fillMaxWidth().heightIn(min=48.dp),enabled=!blocked,onClick=::loadPreview){Text("Preview selected result")}
   bitmap?.let {Image(it.asImageBitmap(),"Edited result preview",Modifier.fillMaxWidth().heightIn(max=180.dp))}
   if(bytes!=null) {
    OutlinedButton(modifier=Modifier.fillMaxWidth().heightIn(min=48.dp),enabled=!blocked,onClick={commit(false)}){Text("Save as New")}
    OutlinedButton(modifier=Modifier.fillMaxWidth().heightIn(min=48.dp),enabled=!blocked,onClick={replace=true}){Text("Replace Original…")}
   }
   if(fallback || (!compatibleEditorAvailable && launchSamsung==null)) {
    Text("Samsung Gallery needs a public copy. Export it to phone Gallery, tap Edit, then save your changes. Return here and choose Import edited result. Save as New keeps your encrypted original.")
    if(exportGallery!=null)OutlinedButton(modifier=Modifier.fillMaxWidth().heightIn(min=48.dp),enabled=!blocked,onClick={exportConsent=true}){Text(if(exportChosen)"Open exported photo" else "Export a copy to Gallery")}
    if(prepared && exportGallery==null)OutlinedButton(modifier=Modifier.fillMaxWidth().heightIn(min=48.dp),enabled=!blocked,onClick=export){Text("Save a copy to Files…")}
   }
   // Explicit SAF fallback remains supported; writing is never implied by selecting a location.
   if(exportChosen && exportGallery==null)OutlinedButton(modifier=Modifier.fillMaxWidth().heightIn(min=48.dp),enabled=!blocked,onClick=writeExport){Text("Write confirmed export")}
  } else {
   OutlinedButton(modifier=Modifier.fillMaxWidth().heightIn(min=48.dp),enabled=!blocked,onClick=cleanupPublic){Text("Clean up public copies…")}
   Text("Your edited photo is encrypted. Public copies need separate confirmation; editor caches and cloud copies cannot be erased here.")
  }
  if(!saved && exportChosen)OutlinedButton(modifier=Modifier.fillMaxWidth().heightIn(min=48.dp),enabled=!blocked,onClick=cleanupPublic){Text("Clean up public copies…")}
  if(acknowledgeReceipt!=null)OutlinedButton(modifier=Modifier.fillMaxWidth().heightIn(min=48.dp),enabled=!blocked,onClick={acknowledge=true}){Text("Forget recovery receipt…")}
  if(status.isNotBlank())Text(status)
  if(message.isNotBlank())Text(message)
 }},confirmButton={TextButton(modifier=Modifier.fillMaxWidth().heightIn(min=48.dp),enabled=!busy,onClick=close){Text(if(saved)"Done" else "Cancel")}})
 if(acknowledge)AlertDialog(onDismissRequest={acknowledge=false},title={Text("Forget editing recovery?")},text={Text("This removes only this app's recovery receipt. Public photos, Samsung caches and cloud copies may remain. Check and remove them separately.")},confirmButton={TextButton(onClick={acknowledge=false;acknowledgeReceipt?.invoke()}){Text("Acknowledge and forget")}},dismissButton={TextButton(onClick={acknowledge=false}){Text("Keep receipt")}})
 if(editors.isNotEmpty())AlertDialog(onDismissRequest=dismissEditors,title={Text("Choose photo editor")},
  text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {editors.forEach {editor ->
   OutlinedButton(modifier=Modifier.fillMaxWidth().heightIn(min=48.dp),enabled=!blocked,onClick={chooseEditor(editor.component)}) {Text(editor.label)}
  }}},confirmButton={TextButton(onClick=dismissEditors){Text("Cancel")}})
 if(consent)AlertDialog(onDismissRequest={consent=false},title={Text("Allow external editing?")},
  text={Text("A temporary photo copy will leave this vault's encryption. Your original stays encrypted. The chosen editor may keep copies or use cloud processing. Access to app-owned temporary copies expires after 15 minutes; file cleanup may happen later.")},
  confirmButton={TextButton(onClick={consent=false;if(samsungConsent)launchSamsung?.invoke()else if(prepared)launch()else prepare()}){Text("Continue to editor")}},
  dismissButton={TextButton(onClick={consent=false}){Text("Keep photo private")}})
 if(exportConsent)PublicCopyExportDialog(exportChosen,{exportConsent=false},{exportConsent=false;exportGallery?.invoke()})
 if(replace)AlertDialog(onDismissRequest={replace=false},title={Text("Replace encrypted original?")},text={Text("The edited result will be imported and verified first. Only then is the original moved to Recently Deleted in this same vault.")},confirmButton={TextButton(onClick={replace=false;commit(true)}){Text("Verify and replace")}},dismissButton={TextButton(onClick={replace=false}){Text("Cancel")}})
}
