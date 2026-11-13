package uk.co.traynor.privategallery.ui

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uk.co.traynor.privategallery.core.evidence.EvidenceCatalog
import uk.co.traynor.privategallery.core.evidence.EvidenceController
import uk.co.traynor.privategallery.core.guardian.GuardianCaptureStore
import uk.co.traynor.privategallery.core.guardian.PersonalImpact
import uk.co.traynor.privategallery.launcher.PersonalImpactDialog

@Composable
internal fun EvidencePersonalImpact(controller: EvidenceController, record: EvidenceCatalog.Evidence) {
    val access = controller.access
    if (access.owner != EvidenceCatalog.Owner.PRIMARY || !controller.current() ||
        (!record.mime().startsWith("image/") && record.ocr().isEmpty())) return

    val scope = rememberCoroutineScope()
    var open by remember(record.id()) { mutableStateOf(false) }
    var busy by remember(record.id()) { mutableStateOf(false) }
    var existing by remember(record.id()) { mutableStateOf<PersonalImpact?>(null) }
    var status by remember(record.id()) { mutableStateOf<String?>(null) }

    fun <T> perform(operation: (GuardianCaptureStore) -> T, publish: (T) -> Unit) {
        if (busy || !controller.current()) return
        busy = true
        status = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { controller.withEvidenceImpact(record.id()) { store, current ->
                    check(current.mime().startsWith("image/") || current.ocr().isNotEmpty())
                    operation(store)
                } }
            }
            access.publishIfCurrent {
                busy = false
                result.onSuccess(publish).onFailure { status = "Feedback could not be updated." }
            }
        }
    }

    TextButton(enabled = !busy, onClick = {
        perform({ it.evidenceImpactFeedback(record.id()) }) { (settings, feedback) ->
            existing = feedback
            if (settings.collect || feedback != null) open = true
            else status = "Enable feedback collection in Guardian’s Nova Brain settings."
        }
    }) { Text("Add or edit personal impact feedback") }
    status?.let { Text(it) }
    if (open) PersonalImpactDialog(existing, null, busy, onSave = { feedback ->
        perform({ it.saveEvidenceImpact(record.id(), feedback) }) { saved ->
            if (saved) open = false else status = "Feedback collection is off."
        }
    }, onDelete = {
        perform({ it.deleteEvidenceImpact(setOf(record.id())) }) { open = false }
    }, onClose = { open = false }, evidenceOnly = true)
}
