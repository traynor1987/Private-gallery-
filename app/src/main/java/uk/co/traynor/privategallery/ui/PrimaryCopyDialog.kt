package uk.co.traynor.privategallery.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uk.co.traynor.privategallery.core.hidden.PrimaryCopyAccess
import uk.co.traynor.privategallery.core.vault.VaultItem
import java.util.concurrent.atomic.AtomicBoolean

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun PrimaryCopyDialog(access: PrimaryCopyAccess, onClose: () -> Unit, onComplete: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val alive = remember { AtomicBoolean(true) }
    var pin by remember { mutableStateOf("") }
    var entries by remember { mutableStateOf<List<VaultItem>?>(null) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf(0 to 0) }
    var showHistory by remember {mutableStateOf(false)}
    val cancelled=remember {AtomicBoolean(false)}
    val close = {
        access.close(); pin = ""; selected = emptySet(); entries = null
        if (alive.compareAndSet(true, false)) onClose()
    }
    DisposableEffect(access) { onDispose { alive.set(false); access.close() } }
    LaunchedEffect(access) {
        while (access.isActive()) delay(100)
        pin = ""; selected = emptySet(); entries = null
        if (alive.compareAndSet(true, false)) onClose()
    }
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false, securePolicy = SecureFlagPolicy.SecureOn)) {
        Surface(Modifier.fillMaxSize()) {
            if (entries == null) {
                PasscodeKeypad(
                    pin = pin, onPinChange = { if (!busy) pin = it.take(12) },
                    appTitle = "Copy from Primary Vault", action = if (busy) "Verifying…" else "Continue",
                    message = error, enabled = !busy,
                    secondary = { TextButton(onClick = close) { Text("Cancel") } },
                    onSubmit = {
                    if (busy || pin.length !in 6..12) return@PasscodeKeypad
                    val input = pin.toCharArray(); pin = ""; busy = true; error = null
                    scope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                        val result = try { withContext(Dispatchers.IO) { runCatching { access.authenticate(input); access.items() } } }
                        finally { input.fill('\u0000') }
                        if (alive.get() && access.isActive()) {
                            busy = false
                            result.onSuccess { entries = it }.onFailure { error = "Primary authentication failed. Check your PIN and try again." }
                        }
                    }
                })
            } else {
                Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(16.dp)) {
                    Text("Primary Vault", style = MaterialTheme.typography.headlineSmall)
                    Text("Copy to Hidden · originals stay in Primary · up to 2 GiB each")
                    FlowRow {
                        TextButton(enabled=!busy,onClick={selected=entries.orEmpty().filter {it.plaintextSize<=uk.co.traynor.privategallery.core.hidden.HiddenChunkStore.MAX_BYTES}.mapTo(linkedSetOf()){it.id}}) {Text("Select all")}
                        TextButton(enabled=!busy,onClick={selected=emptySet()}) {Text("Deselect all")}
                        TextButton(enabled=!busy,onClick={showHistory=true}) {Text("Transfer history")}
                    }
                    if(showHistory) TransferHistoryDialog(access,onDismiss={showHistory=false})
                    if (busy) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text("Copying ${progress.first} of ${progress.second}")
                    }
                    LazyVerticalGrid(columns = GridCells.Adaptive(100.dp), modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(entries.orEmpty(), key = { it.id }) { item ->
                            val enabled = !busy && item.plaintextSize <= uk.co.traynor.privategallery.core.hidden.HiddenChunkStore.MAX_BYTES
                            Column(Modifier.clickable(enabled = enabled) {
                                selected = if (item.id in selected) selected - item.id else selected + item.id
                            }) {
                                PrimaryCopyPreview(access, item, !busy)
                                Row {
                                    Checkbox(checked = item.id in selected, onCheckedChange = null, enabled = enabled)
                                    Text(item.displayName, maxLines = 2, modifier = Modifier.weight(1f))
                                }
                                if (item.plaintextSize > uk.co.traynor.privategallery.core.hidden.HiddenChunkStore.MAX_BYTES) Text("Over 2 GiB")
                            }
                        }
                    }
                    if (entries.orEmpty().isEmpty()) Text("No Primary media available.")
                    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = {if(busy)cancelled.set(true) else close()}) { Text(if(busy)"Stop after this item" else "Cancel") }
                        Button(enabled = !busy && selected.isNotEmpty(), onClick = {
                            val ids = selected.toList(); busy = true; cancelled.set(false); progress = 0 to ids.size
                            scope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    runCatching { access.transfer(ids,false,{cancelled.get()}) { done, total ->
                                        scope.launch { if (alive.get()) progress = done to total }
                                    } }
                                }
                                if (alive.get() && access.isActive()) {
                                    val message = result.fold(
                                        { "Verified ${it.verified} of ${it.total} in Hidden; ${it.failed} failed. Primary originals unchanged." },
                                        { "Copy interrupted. Verified items remain in Hidden; Primary originals unchanged." },
                                    )
                                    // Claim terminal delivery before revocation wakes the session watcher.
                                    if (alive.compareAndSet(true, false)) { access.close(); onComplete(message) }
                                }
                            }
                        }) { Text("Copy to Hidden (${selected.size})") }
                    }
                }
            }
        }
    }
}

@Composable
private fun PrimaryCopyPreview(access: PrimaryCopyAccess, item: VaultItem, load: Boolean) {
    var bitmap by remember(item.id) { mutableStateOf<Bitmap?>(null) }
    DisposableEffect(item.id) { onDispose { bitmap?.recycle(); bitmap = null } }
    LaunchedEffect(item.id, load) {
        if (load && bitmap == null && item.plaintextSize <= 16L * 1024 * 1024) {
            var pending: Bitmap? = null
            try {
                withContext(Dispatchers.IO) { pending = runCatching { access.preview(item.id) }.getOrNull() }
                if (access.isActive()) { bitmap = pending; pending = null }
            } finally { pending?.recycle() }
        }
    }
    Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
        bitmap?.let { Image(it.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
    }
}
