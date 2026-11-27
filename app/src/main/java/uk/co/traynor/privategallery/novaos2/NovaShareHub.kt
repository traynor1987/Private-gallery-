package uk.co.traynor.privategallery.novaos2

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uk.co.traynor.privategallery.core.home2.SpaceContext
import uk.co.traynor.privategallery.home2.CoreWorkspaceHeader
import uk.co.traynor.privategallery.home2.NovaNote
import uk.co.traynor.privategallery.home2.NovaNotesLibrary
import uk.co.traynor.privategallery.home2.NovaTask
import uk.co.traynor.privategallery.home2.NovaTaskLibrary
import uk.co.traynor.privategallery.intelligence.intelligencePrivateAllowed

internal const val NOVA_SHARE_TEXT_LIMIT = 8_000
internal const val NOVA_SHARE_TASK_LIMIT = 4_096
private const val NOVA_SHARE_TITLE_LIMIT = 200

internal fun parseNovaSharedText(value: CharSequence?): String? {
    if (value == null || value.length !in 1..NOVA_SHARE_TEXT_LIMIT) return null
    val text = value.toString().replace("\r\n", "\n").replace('\r', '\n')
    return text.takeIf { it.isNotBlank() && it.none { char -> char.isISOControl() && char !in "\n\t" } }
}

internal fun novaShareTitle(text: String): String = text.lineSequence().map(String::trim)
    .firstOrNull(String::isNotBlank)?.let { first ->
        first.take(NOVA_SHARE_TITLE_LIMIT).trimEnd().let { if (it.lastOrNull()?.isHighSurrogate() == true) it.dropLast(1) else it }
    }.orEmpty()

/** Android text shares only stage a bounded review in memory. Main owns authentication. */
class NovaShareTargetActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = if (savedInstanceState == null && intelligencePrivateAllowed(this)) {
            runCatching { acceptedNovaShare(intent) }.getOrNull()
        } else null
        setIntent(Intent())
        if (text == null) { finish(); return }
        AlertDialog.Builder(this)
            .setTitle("Open Nova Share Hub?")
            .setMessage("Unlock Primary Space to review this text and choose where to save or share it.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Continue") { _, _ ->
                if (intelligencePrivateAllowed(this)) {
                    openNovaPrimaryPresentation(this) { space, done, canUse -> NovaShareHub(space, text, done, canUse) }
                }
            }
            .setOnDismissListener { finish() }
            .show()
    }
}

internal fun acceptedNovaShare(intent: Intent): String? {
    if (intent.action != Intent.ACTION_SEND || intent.type != "text/plain" || intent.data != null ||
        intent.selector != null || intent.hasExtra(Intent.EXTRA_STREAM)) return null
    intent.clipData?.let { clips ->
        if (clips.itemCount != 1) return null
        val clip = clips.getItemAt(0)
        if (clip.uri != null || clip.intent != null) return null
    }
    return parseNovaSharedText(intent.getCharSequenceExtra(Intent.EXTRA_TEXT))
}

private data class NovaExport(val uri: Uri, val text: String)

@Composable
internal fun NovaShareHub(space: SpaceContext, inbound: String?, onBack: () -> Unit, canUse: () -> Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val resumed = lifecycle.isAtLeast(Lifecycle.State.RESUMED)
    var text by remember { mutableStateOf(inbound.orEmpty()) }
    var title by remember { mutableStateOf(novaShareTitle(text)) }
    var status by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }
    var outgoing by remember { mutableStateOf<String?>(null) }
    var export by remember { mutableStateOf<NovaExport?>(null) }
    val saveFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        val queued = outgoing
        outgoing = null
        if (uri?.scheme == "content" && queued != null) export = NovaExport(uri, queued)
        else working = false
    }

    fun authorized() = resumed && space.spaceId == "primary" && space.current() && canUse()

    LaunchedEffect(resumed, export) {
        val current = export ?: return@LaunchedEffect
        if (!resumed) return@LaunchedEffect
        val written = if (!authorized()) false else withContext(Dispatchers.IO) {
            runCatching {
                val payload = current.text.toByteArray(Charsets.UTF_8)
                try {
                    check(space.current() && canUse())
                    val stream = checkNotNull(context.contentResolver.openOutputStream(current.uri, "w"))
                    stream.use {
                        check(space.current() && canUse())
                        it.write(payload)
                    }
                } finally { payload.fill(0) }
            }.isSuccess
        }
        export = null
        working = false
        status = if (written) "Text saved in the selected document." else "Text write could not be completed."
    }

    fun save(toNotes: Boolean) {
        if (!authorized() || working) return
        val body = parseNovaSharedText(text) ?: return
        val name = title.trim().ifBlank { novaShareTitle(body) }
        if (name.isBlank() || (!toNotes && body.length > NOVA_SHARE_TASK_LIMIT)) return
        working = true
        status = null
        scope.launch {
            try {
                val saved = withContext(Dispatchers.IO) {
                    runCatching { saveNovaSharedRecord(space, name, body, toNotes, canUse) }.getOrDefault(false)
                }
                if (!authorized()) return@launch
                status = when {
                    !saved -> "The destination could not be saved. Retry when it is available."
                    toNotes -> "Saved to Nova Notes."
                    else -> "Added to Nova Tasks."
                }
                if (saved) { text = ""; title = "" }
            } finally { working = false }
        }
    }

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState())
            .padding(16.dp).testTag("nova-share-review"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CoreWorkspaceHeader("Nova Share Hub", true, onBack)
        if (!authorized()) {
            Text("Share review is paused while Primary Space is unavailable.")
        } else {
            Text("Review the text and choose where to send it.", style = MaterialTheme.typography.bodyMedium)
            val selectable = parseNovaSharedText(text)
            OutlinedTextField(title, { value -> title = value.filterNot(Char::isISOControl).take(NOVA_SHARE_TITLE_LIMIT) }, enabled = !working,
                label = { Text("Title") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(text, { if (it.length <= NOVA_SHARE_TEXT_LIMIT) text = it }, enabled = !working,
                label = { Text("Shared text") }, modifier = Modifier.fillMaxWidth(), minLines = 4, maxLines = 10)
            Button(onClick = { save(true) }, enabled = selectable != null && !working,
                modifier = Modifier.fillMaxWidth()) { Text("Save to Nova Notes") }
            OutlinedButton(onClick = { save(false) }, enabled = selectable != null && !working &&
                text.length <= NOVA_SHARE_TASK_LIMIT, modifier = Modifier.fillMaxWidth()) { Text("Add to Nova Tasks") }
            if (text.length > NOVA_SHARE_TASK_LIMIT) Text("Tasks accept up to 4,096 characters. Notes and Files can keep this longer text.")
            OutlinedButton(onClick = {
                val body = selectable ?: return@OutlinedButton
                if (!authorized() || working) return@OutlinedButton
                outgoing = body
                working = true
                status = null
                if (runCatching { saveFile.launch("nova-share.txt") }.isFailure) {
                    outgoing = null
                    working = false
                    status = "Android Files is unavailable."
                }
            }, enabled = selectable != null && !working, modifier = Modifier.fillMaxWidth()) { Text("Save as a text file") }
            OutlinedButton(onClick = {
                val body = selectable ?: return@OutlinedButton
                if (!authorized() || working) return@OutlinedButton
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, body)
                val chooser = Intent.createChooser(send, "Share this text").putExtra(
                    Intent.EXTRA_EXCLUDE_COMPONENTS, arrayOf(android.content.ComponentName(context, NovaShareTargetActivity::class.java)))
                if (runCatching { context.startActivity(chooser) }.isFailure) status = "Android Share is unavailable."
            }, enabled = selectable != null && !working, modifier = Modifier.fillMaxWidth()) { Text("Choose an app to share with") }
            status?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

internal fun saveNovaSharedRecord(
    space: SpaceContext, title: String, body: String, toNotes: Boolean, canUse: () -> Boolean,
): Boolean {
    if (space.spaceId != "primary" || !space.current() || !canUse()) return false
    require(title.isNotBlank() && title.length <= NOVA_SHARE_TITLE_LIMIT && title.none(Char::isISOControl) &&
        parseNovaSharedText(body) == body)
    require(toNotes || body.length <= NOVA_SHARE_TASK_LIMIT)
    val id = UUID.randomUUID().toString()
    return if (toNotes) {
        val notes = NovaNotesLibrary(space)
        val before = notes.read()
        canUse() && notes.save(before, before.notes + NovaNote(id, title, body)) != null
    } else {
        val tasks = NovaTaskLibrary(space)
        val before = tasks.read()
        canUse() && tasks.save(before, before.tasks + NovaTask(id, title, body)) != null
    }
}
