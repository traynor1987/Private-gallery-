package uk.co.traynor.privategallery.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONObject
import uk.co.traynor.privategallery.core.privateapps.PrivateAppIcons
import java.util.concurrent.atomic.AtomicBoolean

/** Uses only the current Hidden broker's scoped list and preview; never creates upload grants. */
@Composable
internal fun HiddenPrivateAppIconPicker(
 generation:Long,isCurrent:()->Boolean,loadPage:(String,Int)->String,loadPreview:(String,String)->ByteArray,onResult:(ByteArray?)->Unit,
) {
 val alive=remember{AtomicBoolean(true)}
 val scope=rememberCoroutineScope()
 val slots=remember{Semaphore(3)}
 var items by remember{mutableStateOf<List<HiddenNativeUploadItem>>(emptyList())}
 var query by remember{mutableStateOf("")}
 var loading by remember{mutableStateOf(true)}
 var choosing by remember{mutableStateOf(false)}
 var error by remember{mutableStateOf("")}
 DisposableEffect(Unit){onDispose{alive.set(false)}}
 LaunchedEffect(generation) {
  try {
   val loaded=mutableListOf<HiddenNativeUploadItem>();var offset=0;var id=""
   do {
    val page=withContext(Dispatchers.IO){check(isCurrent());JSONObject(loadPage(id,offset))}
    check(alive.get() && isCurrent());require(page.getLong("generation")==generation)
    val returned=page.getString("query");require(returned.length<=64 && (id.isEmpty() || returned==id));id=returned
    val batch=page.getJSONArray("items");require(batch.length()<=120)
    repeat(batch.length()){index ->val item=batch.getJSONObject(index)
     val key=item.getString("id");val name=item.getString("name");val mime=item.getString("mime")
     require(key.length<=64 && name.length<=200 && mime.startsWith("image/"));loaded.add(HiddenNativeUploadItem(key,name,mime))
    }
    val next=page.getInt("next");val total=page.getInt("total")
    require(next==offset+batch.length() && next<=total && (next==total || next>offset));offset=next
   }while(offset<page.getInt("total"))
   query=id;items=loaded;loading=false
  }catch(_:Exception){if(alive.get()){loading=false;error="Images could not be loaded. Cancel and try again."}}
 }
 GalleryAlertDialog(scrollBody=false,properties=DialogProperties(securePolicy=SecureFlagPolicy.SecureOn),onDismissRequest={onResult(null)},title={Text("Choose icon from Hidden Vault")},text={
  Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
   Text("Select an image for this app's icon. Upload-restricted images are excluded; nothing is sent to the website.",style=MaterialTheme.typography.bodySmall)
   if(loading || choosing)LinearProgressIndicator(Modifier.fillMaxWidth())
   if(error.isNotEmpty())Text(error,color=MaterialTheme.colorScheme.error)
   if(!loading && items.isEmpty() && error.isEmpty())Text("No eligible images in Hidden Vault.")
   LazyVerticalGrid(columns=GridCells.Adaptive(88.dp),modifier=Modifier.heightIn(max=360.dp),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
    items(items,key={it.id}) {item ->
     var icon by remember(item.id){mutableStateOf("")}
     LaunchedEffect(item.id,query) {
      val normalized=withContext(Dispatchers.IO){runCatching{slots.withPermit {
       check(alive.get() && isCurrent());val bytes=loadPreview(query,item.id)
       try{require(bytes.size<=300_000);PrivateAppIcons.normalizeSelected(bytes)}finally{bytes.fill(0)}
      }}.getOrDefault("")}
      if(alive.get() && isCurrent())icon=normalized
     }
     Column(Modifier.fillMaxWidth().semantics{contentDescription="Use ${item.name} as icon"}.clickable(enabled=!choosing){
      choosing=true;error=""
      scope.launch {
       var owned:ByteArray?=null
       try {
        withContext(Dispatchers.IO){check(alive.get() && isCurrent());owned=loadPreview(query,item.id);require(checkNotNull(owned).size<=300_000)}
        check(alive.get() && isCurrent());val selected=checkNotNull(owned);owned=null;onResult(selected)
       }catch(_:Exception){if(alive.get()){choosing=false;error="Image unavailable. Choose another image."}}
       finally{owned?.fill(0)}
      }
     }.padding(8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
      PrivateAppIcon(icon,item.name);Text(item.name,maxLines=2,style=MaterialTheme.typography.labelSmall)
     }
    }
   }
  }
 },confirmButton={},dismissButton={TextButton(onClick={onResult(null)}){Text("Cancel")}})
}
