package uk.co.traynor.privategallery.core.sanctuary

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Android app policy is shared with Guardian. Launcher layout state never supplies approvals. */
internal object SanctuaryLauncherPolicy {
    enum class Decision {
        APPROVED, UNAPPROVED, ESSENTIAL, UNRESTRICTED;

        val canLaunch: Boolean get() = this != UNAPPROVED
    }

    private const val OUTSIDE = "protect-outside-approved"
    private val revision = MutableStateFlow(0L)
    private val updates = revision.asStateFlow()
    @Volatile private var visible = false

    fun state(): StateFlow<Long> = updates

    fun decision(context: Context, packageName: String): Decision =
        decisions(context, listOf(packageName)).getValue(packageName)

    fun decisions(context: Context, packageNames: Collection<String>): Map<String, Decision> {
        val essential = SanctuaryGuardian.essentialPackages(context)
        val session = SanctuaryStore.get(context).refresh()
        val approved = session?.let(::approvedAndroidApps).orEmpty()
        val paused = session?.let(::isPaused) == true
        return packageNames.associateWith { name ->
            when {
                name in essential -> Decision.ESSENTIAL
                name in approved -> Decision.APPROVED
                session == null || paused -> Decision.UNRESTRICTED
                else -> Decision.UNAPPROVED
            }
        }
    }

    fun launcherProtectionActive(context: Context): Boolean =
        SanctuaryStore.get(context).refresh()?.let { !isPaused(it) } == true

    fun outsideProtectionEnabled(context: Context): Boolean = preferences(context).getBoolean(OUTSIDE, false)

    /** Standard Guardian coverage remains active even when this optional shield is off. */
    fun setOutsideProtectionEnabled(context: Context, enabled: Boolean): Boolean {
        val changed = preferences(context).edit().putBoolean(OUTSIDE, enabled).commit()
        if (changed) signalPolicyChange()
        return changed
    }

    fun openHomeSettings(context: Context): Boolean = SanctuaryGuardian.openHomeSettings(context)

    fun openEmergencyPhone(context: Context): Boolean = SanctuaryGuardian.openPhone(context)

    @Suppress("UNUSED_PARAMETER")
    fun launcherResumed(context: Context) {
        visible = true
        SanctuaryAccessibility.signalPolicyChange()
    }

    fun launcherPaused() {
        visible = false
        SanctuaryAccessibility.signalPolicyChange()
    }

    internal fun launcherVisible(): Boolean = visible

    internal fun isPaused(session: SanctuarySession): Boolean = SanctuaryWorkspacePause.isPaused(session.id)

    internal fun signalPolicyChange() {
        revision.update { it + 1 }
        SanctuaryAccessibility.signalPolicyChange()
    }

    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences("sanctuary-launcher-policy", Context.MODE_PRIVATE)
}

internal fun approvedAndroidApps(session: SanctuarySession): Set<String> =
    if (session.allowExternalApps) session.androidApps else emptySet()
