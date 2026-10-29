package uk.co.traynor.privategallery.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import uk.co.traynor.privategallery.VaultMediaTile
import uk.co.traynor.privategallery.core.vault.*
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.atomic.AtomicBoolean

/** Callbacks belong to the originating authenticated vault; this panel never acquires keys. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable internal fun RecentlyDeletedPanel(
    items:List<VaultItem>, revision:Int,
    preview:(VaultItem,(Result<Bitmap>)->Unit)->Unit,
    loadRetention:((Int)->Unit)->Unit,
    saveRetention:(Int,(String)->Unit)->Unit,
    change:(List<VaultItem>,Boolean,(String)->Unit)->Unit,
    refresh:()->Unit,
    modifier:Modifier=Modifier,
) {
    var selected by remember {mutableStateOf<Set<String>>(emptySet())}
    var days by remember {mutableIntStateOf(30)}
    var busy by remember {mutableStateOf(false)}
    var status by remember {mutableStateOf("")}
    var confirm by remember {mutableStateOf<List<VaultItem>?>(null)}
    var retentionOpen by remember {mutableStateOf(false)}
    var retentionCandidate by remember {mutableStateOf<Int?>(null)}
    val alive=remember {AtomicBoolean(true)}
    DisposableEffect(Unit){onDispose{alive.set(false)}}
    LaunchedEffect(Unit){loadRetention {if(alive.get())days=it}}
    LaunchedEffect(Unit){while(true){kotlinx.coroutines.delay(60_000);if(!busy)refresh()}}
    LaunchedEffect(items){selected=selected.intersect(items.mapTo(hashSetOf()){it.id})}
    fun update(targets:List<VaultItem>,delete:Boolean) {
        if(busy || targets.isEmpty())return
        busy=true
        change(targets,delete){message ->if(alive.get()){status=message;busy=false;selected=emptySet();refresh()}}
    }
    Column(modifier,verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("Recently Deleted",style=MaterialTheme.typography.titleLarge)
        Text(if(days==0)"Encrypted · kept until you delete permanently" else "Encrypted · retained for $days days",style=MaterialTheme.typography.bodyMedium)
        Text("Expiry runs when this vault is authenticated. Locked vaults are checked on unlock.",style=MaterialTheme.typography.bodySmall)
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            TextButton(enabled=!busy,onClick={retentionOpen=true}){Text("Retention")}
            TextButton(enabled=!busy && items.isNotEmpty(),onClick={selected=if(selected.size==items.size)emptySet() else items.mapTo(hashSetOf()){it.id}}){Text(if(selected.size==items.size && items.isNotEmpty())"Deselect all" else "Select all")}
            TextButton(enabled=!busy && items.isNotEmpty(),onClick={confirm=items.toList()}){Text("Empty Recently Deleted")}
        }
        if(selected.isNotEmpty())FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Button(enabled=!busy,onClick={update(items.filter {it.id in selected},false)}){Text("Restore (${selected.size})")}
            OutlinedButton(enabled=!busy,onClick={confirm=items.filter {it.id in selected}}){Text("Delete permanently")}
        }
        if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        if(status.isNotEmpty())Text(status,style=MaterialTheme.typography.bodySmall)
        if(items.isEmpty())Text("Recently Deleted is empty. Newly deleted media stays encrypted in this vault until restored or permanently deleted.")
        else LazyVerticalGrid(GridCells.Adaptive(140.dp),Modifier.weight(1f),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            items(items,key={it.id}){item ->Column {
                VaultMediaTile(item,preview,revision,selected=item.id in selected,onClick={if(!busy)selected=if(item.id in selected)selected-item.id else selected+item.id})
                val deleted=item.deletedAtEpochMillis
                Text(if(deleted==null)"Deletion date unavailable" else "Deleted ${DateFormat.getDateInstance().format(Date(deleted))}",style=MaterialTheme.typography.bodySmall)
                val remaining=deleted?.let {RecentlyDeletedPolicy.remainingDays(it,System.currentTimeMillis(),days)}
                Text(if(days==0)"No automatic expiry" else if(remaining==0L)"Expiry due" else "${remaining ?: days.toLong()} days remaining",style=MaterialTheme.typography.bodySmall)
            }}
        }
    }
    confirm?.let {targets ->GalleryAlertDialog(onDismissRequest={confirm=null},title={Text("Delete permanently?")},
        text={Text("Permanently remove ${targets.size} encrypted items from this vault? This cannot be undone. This removes app-managed files; physical erasure from flash storage or external backups cannot be guaranteed.")},
        confirmButton={TextButton(onClick={confirm=null;update(targets,true)}){Text("Delete permanently")}},
        dismissButton={TextButton(onClick={confirm=null}){Text("Cancel")}})}
    if(retentionOpen)GalleryAlertDialog(onDismissRequest={retentionOpen=false},title={Text("Retention for this vault")},
        text={Column{RecentlyDeletedPolicy.supportedDays.forEach {value ->
            TextButton(onClick={retentionOpen=false;retentionCandidate=value}){Text((if(value==days)"✓ " else "")+if(value==0)"Never automatically delete" else "$value days")}
        }}},confirmButton={TextButton(onClick={retentionOpen=false}){Text("Close")}})
    retentionCandidate?.let {value ->GalleryAlertDialog(onDismissRequest={retentionCandidate=null},title={Text("Change retention?")},
        text={Text("This applies to items already in Recently Deleted. Items older than the new period will expire during the next authenticated check.")},
        confirmButton={TextButton(onClick={retentionCandidate=null;busy=true;saveRetention(value){message ->if(alive.get()){status=message;busy=false;loadRetention {if(alive.get())days=it};refresh()}}}){Text("Apply")}},
        dismissButton={TextButton(onClick={retentionCandidate=null}){Text("Cancel")}})}
}
