package uk.co.traynor.privategallery.launcher

import android.app.Activity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import uk.co.traynor.privategallery.core.guardian.GuardianInboxSection
import uk.co.traynor.privategallery.core.home2.NovaCoreApp
import uk.co.traynor.privategallery.intelligence.IntelligenceDestination
import uk.co.traynor.privategallery.intelligence.intelligencePrivateAllowed

internal data class LauncherIntelligenceActions(
    val openPrivate: () -> Unit,
    val openDestination: (IntelligenceDestination) -> Boolean,
    val openEvidence: (String) -> Boolean,
    val openApp: (String) -> Boolean = { false },
)

internal fun LauncherLayout.intelligenceNominations(): Set<String> =
    (pages.asSequence().flatten() + dock.asSequence()).flatMap { item ->
        when (item) {
            is LauncherItem.App -> sequenceOf(item.component)
            is LauncherItem.Folder -> item.apps.asSequence()
            else -> emptySequence()
        }
    }.toSet()

internal val IntelligenceDestination.coreApp: NovaCoreApp?
    get() = when (this) {
        IntelligenceDestination.NOTES -> NovaCoreApp.NOTES
        IntelligenceDestination.TASKS -> NovaCoreApp.TASKS
        IntelligenceDestination.CALENDAR -> NovaCoreApp.CALENDAR
        else -> null
    }

internal fun Activity.openIntelligenceGuardian(destination: IntelligenceDestination): Boolean {
    if (!intelligencePrivateAllowed(this) || !intelligenceResumed()) return false
    val section = when (destination) {
        IntelligenceDestination.GUARDIAN -> GuardianInboxSection.PENDING
        IntelligenceDestination.HELD -> GuardianInboxSection.COOLING
        else -> return false
    }
    return runCatching { GuardianInboxActivity.open(this, section) }.isSuccess
}

internal fun Activity.openIntelligenceEvidence(id: String): Boolean {
    if (!intelligencePrivateAllowed(this) || !intelligenceResumed() || !id.matches(Regex("[a-fA-F0-9]{64}"))) return false
    return runCatching { GuardianInboxActivity.openEvidenceSource(this, id.lowercase()) }.isSuccess
}

internal fun Activity.intelligenceResumed() =
    (this as? LifecycleOwner)?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) == true
