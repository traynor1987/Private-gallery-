package uk.co.traynor.privategallery.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

/** Original local reading surface. No network, camera preview, public cache or saved state. */
@Composable
internal fun ReadingCameraView(
    video: Boolean, recording: Boolean, saving: Boolean, bound: Boolean,
    status: String, elapsed: Long, audio: Boolean, onCapture: () -> Unit, onExit: () -> Unit,
) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("THE EVERYDAY JOURNAL", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                IconButton(onClick = onExit) { Icon(Icons.Default.Close, "Exit Reading View Camera") }
            }
            HorizontalDivider()
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Text("LIVING · A MOMENT TO PAUSE", style = MaterialTheme.typography.labelSmall)
                Text("A quieter way to spend an afternoon", style = MaterialTheme.typography.headlineMedium, fontFamily = FontFamily.Serif)
                Text("Small routines can help us notice more of the ordinary places around us.",
                    style = MaterialTheme.typography.titleMedium, fontFamily = FontFamily.Serif)
                Text("By the Journal desk · A local reading page", style = MaterialTheme.typography.labelMedium)
                val article = "An unhurried walk offers a different view of a familiar street. There is time to notice the changing light, a garden coming into season, or the sound of leaves moving in the breeze.\n\nThe simplest plans often leave the most room for discovery. A book, a comfortable seat and a little time outside can be enough to make an ordinary afternoon feel more spacious.\n\nThere is no need to make every quiet moment productive. Sometimes the useful thing is simply to slow down and pay attention."
                Text(article, style = MaterialTheme.typography.bodyLarge, fontFamily = FontFamily.Serif,
                    modifier = if (video) Modifier else Modifier.fillMaxWidth()
                        .semantics {
                            contentDescription = "Take photo from reading area"; role = Role.Button
                            if (bound && !saving) onClick("Take photo") { onCapture(); true } else disabled()
                        }
                        .pointerInput(bound, saving) {
                            if (bound && !saving) awaitEachGesture {
                                val down = awaitFirstDown()
                                var tap = true
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (change.isConsumed || event.changes.count { it.pressed } > 1 ||
                                        (change.position - down.position).getDistance() > viewConfiguration.touchSlop) tap = false
                                    if (change.changedToUpIgnoreConsumed()) {
                                        if (tap) { change.consume(); onCapture() }
                                        break
                                    }
                                    if (!change.pressed) break
                                }
                            }
                        })

            }
            // Honest capture feedback remains visible alongside Android's privacy indicators.
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if(recording || saving || status.contains("recovery",ignoreCase=true)) Text(if (recording) "REC ${(elapsed / 60).toString().padStart(2, '0')}:${(elapsed % 60).toString().padStart(2, '0')} · Audio ${if (audio) "On" else "Off"}" else status,
                    style = MaterialTheme.typography.labelMedium)
                if (video) Button(onClick = onCapture, enabled = bound && !saving,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics {
                        contentDescription = if (recording) "Stop recording" else "Start recording"
                    }) { Text(if (saving) "Finalising…" else if (recording) "Stop recording" else "Start recording") }
            }
        }
    }
}
