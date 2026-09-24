package uk.co.traynor.privategallery.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
internal fun ViewerTopBar(counter: String, onBack: () -> Unit, onMore: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxWidth().testTag("viewer-top-bar"), color = Color.Black.copy(alpha=.78f), contentColor = Color.White) {
        Row(Modifier.windowInsetsPadding(WindowInsets.statusBars).fillMaxWidth().padding(horizontal=8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text(counter, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            IconButton(onMore) { Icon(Icons.Default.MoreVert, "Viewer menu") }
        }
    }
}
@Composable
internal fun ViewerBottomBar(onEdit: (() -> Unit)?, onRestore: (() -> Unit)?, onCollection: (() -> Unit)?, onCopy: (() -> Unit)?, onMove: (() -> Unit)?, onMore: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxWidth().testTag("viewer-bottom-bar"), color = Color.Black.copy(alpha=.82f), contentColor = Color.White) {
        Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal=8.dp, vertical=4.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            onCollection?.let { ViewerAction("Collection", Icons.Default.CreateNewFolder, it) }
            onEdit?.let { ViewerAction("Edit", Icons.Default.Edit, it) }
            onRestore?.let { ViewerAction("Restore", Icons.Default.FileDownload, it) }
            onCopy?.let { ViewerAction("Copy to Vault", Icons.Default.ContentCopy, it) }
            onMove?.let { ViewerAction("Move to Vault", Icons.Default.Lock, it) }
            ViewerAction("More", Icons.Default.MoreHoriz, onMore)
        }
    }
}
@Composable
private fun ViewerAction(label: String, icon: ImageVector, onClick: () -> Unit) {
    TextButton(onClick, modifier = Modifier.defaultMinSize(minWidth=48.dp, minHeight=56.dp), colors = ButtonDefaults.textButtonColors(contentColor=Color.White), contentPadding = PaddingValues(6.dp)) {
        Column(horizontalAlignment=Alignment.CenterHorizontally, verticalArrangement=Arrangement.spacedBy(4.dp)) { Icon(icon, null, Modifier.size(22.dp)); Text(label, style=MaterialTheme.typography.labelSmall, maxLines=1) }
    }
}
