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
)

internal val IntelligenceDestination.coreApp: NovaCoreApp?
    get() = when (this) {
        IntelligenceDestination.NOTES -> NovaCoreApp.NOTES
        IntelligenceDestination.TASKS -> NovaCoreApp.TASKS
        IntelligenceDestination.CALENDAR -> NovaCoreApp.CALENDAR
        else -> null
    }

internal fun Activity.openIntelligenceGuardian(destination: IntelligenceDestination): Boolean {
    if (!intelligencePrivateAllowed(this) || !intelligenceResumed()) return false
    when (destination) {
        IntelligenceDestination.GUARDIAN -> GuardianInboxActivity.open(this, GuardianInboxSection.PENDING)
        IntelligenceDestination.HELD -> GuardianInboxActivity.open(this, GuardianInboxSection.COOLING)
        else -> return false
    }
    return true
}

internal fun Activity.openIntelligenceEvidence(id: String): Boolean {
    if (!intelligencePrivateAllowed(this) || !intelligenceResumed() || !id.matches(Regex("[a-fA-F0-9]{64}"))) return false
    GuardianInboxActivity.openEvidenceSource(this, id.lowercase())
    return true
}

internal fun Activity.intelligenceResumed() =
    (this as? LifecycleOwner)?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) == true
