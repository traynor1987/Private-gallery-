package uk.co.traynor.privategallery.ui
import android.os.StatFs
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import uk.co.traynor.privategallery.core.media.VaultStorageSummary

@Composable
internal fun VaultStoragePanel(load:((Result<VaultStorageSummary>)->Unit)->Unit) {
 var open by remember {mutableStateOf(false)}
 var summary by remember {mutableStateOf<VaultStorageSummary?>(null)}
 var error by remember {mutableStateOf(false)}
 val context=LocalContext.current
 TextButton(onClick={open=true;error=false;summary=null;load {r->if(open)r.onSuccess {summary=it}.onFailure {error=true}}}){Text("Storage")}
 if(open)AlertDialog(onDismissRequest={open=false;summary=null},title={Text("Vault storage")},text={Column(Modifier.heightIn(max=480.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
  val v=summary
  if(v==null&&!error)CircularProgressIndicator()
  if(error)Text("Storage information unavailable. Unlock this vault and retry.")
  v?.let {
   fun size(n:Long)=android.text.format.Formatter.formatFileSize(context,n)
   Text("Total in this vault: ${size(it.totalBytes)}")
   Text("Encrypted photos: ${size(it.photoBytes)} · ${it.photoCount} photos")
   Text("Encrypted videos: ${size(it.videoBytes)} · ${it.videoCount} videos")
   Text("Encrypted thumbnails: ${size(it.thumbnailBytes)}")
   Text("Staging: ${size(it.temporaryBytes)} · retained for recovery, never disposable")
   Text("Other vault files: ${size(it.otherBytes)}")
   Text("Available device storage: ${size(StatFs(context.filesDir.absolutePath).availableBytes)}")
   Text("Largest media",style=MaterialTheme.typography.titleSmall)
   it.largest.forEach {media->Text("${media.displayName} · ${size(media.plaintextSize)}")}
   Text("Potential duplicates: ${it.duplicates.size} groups",style=MaterialTheme.typography.titleSmall)
   it.duplicates.take(10).forEach {group->Text(group.joinToString("\n"){media->media.displayName})}
   Text("Duplicate candidates use recorded size and SHA-256. Review before deleting. No files are deleted automatically. These totals cover this vault only; shared app files and interrupted camera journals are excluded.",style=MaterialTheme.typography.bodySmall)
  }
 }},confirmButton={TextButton(onClick={open=false;summary=null}){Text("Close")}})
}
