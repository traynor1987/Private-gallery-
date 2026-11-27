package uk.co.traynor.privategallery.novaos2

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract.CommonDataKinds.Phone
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import uk.co.traynor.privategallery.home2.CoreWorkspaceHeader

internal fun normalizeNovaDialNumber(raw: String): String? {
    if (raw.length !in 1..80) return null
    val value = raw.filterNot { it.isWhitespace() || it in "().-" }
    val number = value.removePrefix("+")
    return value.takeIf { number.length in 1..24 && number.all { digit -> digit in '0'..'9' } }
}

private data class NovaPhoneEntry(val id: Long, val name: String, val number: String)
private data class NovaContactsResult(val entries: List<NovaPhoneEntry>, val truncated: Boolean)
private const val CONTACT_PAGE_SIZE = 200

private fun contactsAllowed(context: Context): Boolean =
    context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

private fun novaContactNumbers(context: Context, filter: String): NovaContactsResult? {
    if (!contactsAllowed(context)) return null
    val uri = if (filter.isEmpty()) Phone.CONTENT_URI else Uri.withAppendedPath(Phone.CONTENT_FILTER_URI, Uri.encode(filter))
    return context.contentResolver.query(uri, arrayOf(Phone._ID, Phone.DISPLAY_NAME, Phone.NUMBER), null, null,
        "${Phone.SORT_KEY_PRIMARY} ASC, ${Phone._ID} ASC")?.use { cursor ->
        val result = buildList {
            while (size < CONTACT_PAGE_SIZE && cursor.moveToNext()) {
                val number = cursor.getString(2)?.takeIf { it.isNotBlank() } ?: continue
                val name = cursor.getString(1)?.takeIf { it.isNotBlank() } ?: "Unnamed contact"
                add(NovaPhoneEntry(cursor.getLong(0), name, number))
            }
        }
        NovaContactsResult(result, cursor.moveToNext())
    }
}

@Composable
internal fun NovaContactDirectory(onBack: () -> Unit, canUse: () -> Boolean) {
    val context = LocalContext.current
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val resumed = lifecycle.isAtLeast(Lifecycle.State.RESUMED)
    var opened by remember { mutableStateOf(false) }
    var permission by remember { mutableStateOf(contactsAllowed(context)) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<NovaContactsResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permission = contactsAllowed(context)
    }
    LaunchedEffect(resumed) { if (resumed) permission = contactsAllowed(context) }
    LaunchedEffect(resumed, opened, permission, query) {
        results = null
        error = null
        if (resumed && canUse() && opened && permission) {
            if (query.isNotEmpty()) delay(180)
            val observed = withContext(Dispatchers.IO) {
                runCatching { novaContactNumbers(context, query.trim()) }.getOrNull()
            }
            if (canUse() && contactsAllowed(context)) {
                results = observed
                if (observed == null) error = "Android Contacts is unavailable."
            }
        }
    }
    LazyColumn(
        Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(horizontal = 16.dp)
            .testTag("nova-contacts"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { CoreWorkspaceHeader("Nova Contacts", true, onBack) }
        if (!opened || !permission) {
            item {
                NovaSystemSection("Phone contacts") {
                    Text("Search device contacts when you choose to open them. Names and numbers stay in Android Contacts.")
                    if (opened && !permission) Text("Contacts permission is unavailable.")
                    Button(onClick = {
                        if (!resumed || !canUse()) return@Button
                        opened = true
                        if (!contactsAllowed(context)) request.launch(Manifest.permission.READ_CONTACTS)
                        else permission = true
                    }, enabled = resumed && canUse()) { Text(if (permission) "Show contacts" else "Allow contacts access") }
                }
            }
        } else if (resumed && canUse()) {
            item {
                OutlinedTextField(query, { query = it.take(100) }, label = { Text("Search names or numbers") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            item {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text(results?.let { if (it.truncated) "Showing the first $CONTACT_PAGE_SIZE numbers" else "${it.entries.size} numbers" }
                        ?: error ?: "Reading contacts…", style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f).padding(top = 12.dp))
                    TextButton(onClick = { opened = false; query = ""; results = null }) { Text("Hide contacts") }
                }
            }
            items(results?.entries.orEmpty(), key = NovaPhoneEntry::id) { contact ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                        Text(contact.name, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleSmall)
                        Text(contact.number, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium)
                    }
                    val number = normalizeNovaDialNumber(contact.number)
                    if (number != null) TextButton(onClick = {
                        if (canUse() && contactsAllowed(context)) {
                            val dial = Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null))
                            if (runCatching { context.startActivity(dial) }.isFailure) error = "The Android dialler is unavailable."
                        }
                    }) { Text("Dial") }
                }
                HorizontalDivider()
            }
            if (error != null && results != null) item { Text(checkNotNull(error)) }
        }
    }
}
