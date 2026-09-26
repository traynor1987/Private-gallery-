package uk.co.traynor.privategallery.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import uk.co.traynor.privategallery.core.browser.BrowserSearchEngine
import uk.co.traynor.privategallery.core.browser.BrowserBookmark
import uk.co.traynor.privategallery.core.browser.v2.BrowserTab
import uk.co.traynor.privategallery.core.browser.v2.BrowserHistoryEntry
import java.net.URI
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** In-app surfaces; no activity, new WebView, page capture, remote favicon or URL persistence. */
@Composable
internal fun BrowserPanel(title: String, subtitle: String, onClose: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {}, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column {
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to Browser") }
                    Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                        Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
                        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    actions()
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                content()
            }
        }
    }
}

internal fun browserHost(url: String): String = runCatching { URI(url).host.orEmpty() }.getOrDefault("")

@Composable
internal fun BrowserTabsPanel(tabs: List<BrowserTab>, selectedId: String, onSelect: (String) -> Unit,
    onRemove: (String) -> Unit, onNew: () -> Unit, onClose: () -> Unit) {
    BrowserPanel("Tabs", "${tabs.size} open · up to 8 tabs", onClose, actions = {
        FilledTonalIconButton(onClick = onNew) { Icon(Icons.Default.Add, "New tab") }
    }) {
        LazyVerticalGrid(columns = GridCells.Adaptive(160.dp), contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(tabs, key = { it.id }) { tab ->
                val current = tab.id == selectedId
                Card(onClick = { onSelect(tab.id) }, shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(if (current) 2.dp else 1.dp, if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                    colors = CardDefaults.cardColors(containerColor = if (current) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer)) {
                    Row(Modifier.fillMaxWidth().padding(start = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (current) "Current" else "Tab", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                        IconButton(onClick = { onRemove(tab.id) }) { Icon(Icons.Default.Close, "Close tab") }
                    }
                    Column(Modifier.fillMaxWidth().heightIn(min = 136.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(if (tab.url.isBlank()) Icons.Default.Add else Icons.Default.Language, null,
                            modifier = Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(tab.title.ifBlank { "New tab" }, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(browserHost(tab.url).ifBlank { "Search or enter an address" }, style = MaterialTheme.typography.bodySmall,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun BrowserLibrarySearch(query: String, onQuery: (String) -> Unit, label: String) {
    OutlinedTextField(query, onQuery, modifier = Modifier.fillMaxWidth().padding(16.dp),
        singleLine = true, shape = RoundedCornerShape(24.dp), leadingIcon = { Icon(Icons.Default.Search, null) },
        placeholder = { Text(label) })
}

@Composable
private fun BrowserLibraryRow(title: String, url: String, icon: ImageVector, onOpen: () -> Unit,
    trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(start = 20.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Icon(icon, null, Modifier.padding(12.dp).size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title.ifBlank { browserHost(url) }, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(browserHost(url), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailing()
    }
}

@Composable
private fun BrowserLibraryEmpty(title: String, detail: String, icon: ImageVector) {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun BrowserBookmarksPanel(bookmarks: List<BrowserBookmark>, onOpen: (String) -> Unit,
    onRemove: (String) -> Unit, onClose: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val filtered = bookmarks.filter { query.isBlank() || it.title.contains(query, true) || browserHost(it.url).contains(query, true) }
    BrowserPanel("Bookmarks", "${bookmarks.size} saved", onClose) {
        BrowserLibrarySearch(query, { query = it }, "Search bookmarks")
        if (filtered.isEmpty()) BrowserLibraryEmpty(if (bookmarks.isEmpty()) "No bookmarks yet." else "No matching bookmarks",
            "Save a page from the Browser menu to find it here.", Icons.Default.Bookmarks)
        LazyColumn {
            items(filtered, key = { it.id }) { bookmark ->
                BrowserLibraryRow(bookmark.title, bookmark.url, Icons.Default.StarOutline, { onOpen(bookmark.url) }) {
                    IconButton(onClick = { onRemove(bookmark.id) }) { Icon(Icons.Default.DeleteOutline, "Remove bookmark") }
                }
            }
        }
    }
}

@Composable
internal fun BrowserHistoryPanel(history: List<BrowserHistoryEntry>, savingHistory: Boolean, onOpen: (String) -> Unit,
    onClear: () -> Unit, onClose: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }
    val zone = ZoneId.systemDefault()
    val groups = history.filter { query.isBlank() || it.title.contains(query, true) || browserHost(it.url).contains(query, true) }
        .sortedByDescending { it.visitedAtEpochMillis }.groupBy { Instant.ofEpochMilli(it.visitedAtEpochMillis).atZone(zone).toLocalDate() }
    BrowserPanel("History", if (savingHistory) "Saved privately on this device" else "Saving history is off", onClose, actions = {
        IconButton(onClick = { confirmClear = true }, enabled = history.isNotEmpty()) { Icon(Icons.Default.DeleteOutline, "Clear history") }
    }) {
        BrowserLibrarySearch(query, { query = it }, "Search history")
        if (groups.isEmpty()) BrowserLibraryEmpty(if (history.isEmpty()) "No saved history." else "No matching history",
            if (savingHistory) "Visited pages will appear here." else "Enable history in Browser settings to save visited pages.", Icons.Default.History)
        LazyColumn {
            groups.forEach { (date, entries) ->
                item(key = "date-$date") { Text(date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)),
                    Modifier.padding(horizontal = 20.dp, vertical = 12.dp).semantics { heading() },
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
                items(entries, key = { it.id }) { entry ->
                    BrowserLibraryRow(entry.title, entry.url, Icons.Default.History, { onOpen(entry.url) })
                }
            }
        }
    }
    if (confirmClear) AlertDialog(onDismissRequest = { confirmClear = false }, title = { Text("Clear browsing history?") },
        text = { Text("Removes saved visits from this device. Bookmarks and open tabs are kept.") },
        confirmButton = { TextButton(onClick = { confirmClear = false; onClear() }) { Text("Clear") } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } })
}

@Composable
private fun BrowserSettingToggle(title: String, detail: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(value, onChange)
    }
}

/** Shared by the Browser's own settings page and Settings → Browser, with one source of truth. */
@Composable
internal fun BrowserSettingsContent(searchEngine: BrowserSearchEngine, onSearchEngine: (BrowserSearchEngine) -> Unit,
    saveHistory: Boolean, onSaveHistory: (Boolean) -> Unit, requireVpn: Boolean, onRequireVpn: (Boolean) -> Unit,
    autoConnect: Boolean, onAutoConnect: (Boolean) -> Unit, clearOnLock: Boolean, onClearOnLock: (Boolean) -> Unit,
    onClearData: () -> Unit, contentBlocker: uk.co.traynor.privategallery.core.browser.v2.BrowserContentBlocker? = null) {
    var confirmClear by remember { mutableStateOf(false) }
    var cleared by remember { mutableStateOf(false) }
    var blockingEnabled by remember(contentBlocker) { mutableStateOf(contentBlocker?.enabled ?: true) }
    val context = androidx.compose.ui.platform.LocalContext.current
    var uploadPolicy by remember { mutableStateOf(uk.co.traynor.privategallery.core.browser.v2.BrowserUploadPreference.read(context)) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Search engine", style = MaterialTheme.typography.titleMedium)
        Text("Searches are sent only to the selected provider.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.selectableGroup()) {
            BrowserSearchEngine.entries.forEach { engine -> GalleryChoiceRow(engine.label, engine == searchEngine) { onSearchEngine(engine) } }
        }
        HorizontalDivider()
        Text("Privacy & connection", style = MaterialTheme.typography.titleMedium)
        if (contentBlocker != null) {
            BrowserSettingToggle("Block ads and automatic popups", "Blocks requests to a bundled set of ad hosts and popups without a tap. Turn off for a site from the Browser menu if it breaks. Site exceptions last until the app closes.", blockingEnabled) {
                blockingEnabled = it
                contentBlocker.enabled = it
            }
        }
        BrowserSettingToggle("Save browsing history", "Keep an encrypted record of visited pages on this device.", saveHistory, onSaveHistory)
        BrowserSettingToggle("Require VPN for browsing", "Block Browser networking until your VPN connection is confirmed.", requireVpn, onRequireVpn)
        BrowserSettingToggle("Auto-connect VPN", "Connect the selected profile when opening Browser. Manage profiles in Settings → VPN.", autoConnect, onAutoConnect)
        BrowserSettingToggle("Clear data on lock", "Clear Browser history, cache, cookies and site storage when Private Gallery locks.", clearOnLock, onClearOnLock)
        HorizontalDivider()
        Text("File uploads", style = MaterialTheme.typography.titleMedium)
        Text("Websites receive only files you explicitly select. Vault uploads require confirmation.", style = MaterialTheme.typography.bodySmall)
        Column(Modifier.selectableGroup()) {
            listOf(
                uk.co.traynor.privategallery.core.browser.v2.BrowserUploadPolicy.VAULT_ONLY to "Vault only",
                uk.co.traynor.privategallery.core.browser.v2.BrowserUploadPolicy.VAULT_AND_DEVICE to "Vault + Android picker",
                uk.co.traynor.privategallery.core.browser.v2.BrowserUploadPolicy.BLOCKED to "Block uploads",
            ).forEach { (value, label) -> GalleryChoiceRow(label, uploadPolicy == value) {
                uploadPolicy = value
                uk.co.traynor.privategallery.core.browser.v2.BrowserUploadPreference.write(context, value)
            } }
        }
        HorizontalDivider()
        Text("Browsing data", style = MaterialTheme.typography.titleMedium)
        Text("Clear history, cache, cookies and site storage. You may need to sign in to websites again. Vault media is not affected.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = { confirmClear = true }, modifier = Modifier.fillMaxWidth()) { Text("Clear browsing data") }
        if (cleared) Text("Browsing data cleared.", style = MaterialTheme.typography.bodySmall)
    }
    if (confirmClear) AlertDialog(onDismissRequest = { confirmClear = false }, title = { Text("Clear browsing data?") },
        text = { Text("Clears history, cookies, cache and site storage. Vault media and bookmarks are kept.") },
        confirmButton = { TextButton(onClick = { confirmClear = false; onClearData(); cleared = true }) { Text("Clear") } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } })
}
