package uk.co.traynor.privategallery.ui

import uk.co.traynor.privategallery.ui.GalleryAlertDialog as AlertDialog
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uk.co.traynor.privategallery.core.editor.RetiredModelStorage
import java.util.Locale

@Composable
internal fun RetiredModelCleanup() {
    val context = LocalContext.current
    val storage = remember(context) { RetiredModelStorage(context.noBackupFilesDir) }
    var used by remember { mutableLongStateOf(storage.reclaimableBytes()) }
    var confirm by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    if (used > 0) {
        TextButton(onClick = { confirm = true }) {
            Text("Remove downloaded AI models · Free ${String.format(Locale.UK, "%.2f", used / 1_000_000_000.0)} GB")
        }
        if (error) Text("Could not remove all model files. Retry after checking free storage.")
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false },
        title = { Text("Remove downloaded AI models?") },
        text = { Text("Removes old local AI weights and partial downloads from Private Gallery storage. Vault media and cloud provider settings stay intact.") },
        confirmButton = { TextButton(modifier = Modifier.heightIn(min = 48.dp), onClick = {
            confirm = false
            scope.launch {
                error = runCatching { withContext(Dispatchers.IO) { storage.remove() } }.isFailure
                used = withContext(Dispatchers.IO) { storage.reclaimableBytes() }
            }
        }) { Text("Remove models") } },
        dismissButton = { TextButton(modifier = Modifier.heightIn(min = 48.dp), onClick = { confirm = false }) { Text("Cancel") } })
}
