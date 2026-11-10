package uk.co.traynor.privategallery.core.sanctuary

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import uk.co.traynor.privategallery.launcher.LauncherApp
import uk.co.traynor.privategallery.launcher.LauncherAppIdentity

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
        val store = SanctuaryStore.get(context)
        val session = store.refresh()
        val settings = store.launcherSettings()
        val paused = session?.let(::isPaused) == true
        return packageNames.associateWith { name ->
            when {
                session != null && !paused && name == SANCTUARY_NATIVE_WHATSAPP -> Decision.UNAPPROVED
                name in essential -> Decision.ESSENTIAL
                session == null || paused -> Decision.UNRESTRICTED
                !session.allowExternalApps -> Decision.UNAPPROVED
                session.mode == SanctuaryMode.PANIC ->
                    if (name in panicSanctuaryPersonalApps) Decision.APPROVED else Decision.UNAPPROVED
                name in settings.explicitPersonalPackages -> {
                    val personalComponent = context.packageManager.getLaunchIntentForPackage(name)
                        ?.component?.flattenToString()
                    if (personalComponent != null && settings.allows(personalComponent, name, null, session.androidApps)) {
                        Decision.APPROVED
                    } else Decision.UNAPPROVED
                }
                name in session.androidApps -> Decision.APPROVED
                else -> Decision.UNAPPROVED
            }
        }
    }

    /** Use only with the complete discovered catalogue when enrolling the default Friends profile. */
    fun appDecisions(
        context: Context,
        apps: Collection<LauncherApp>,
        allowEnrollment: Boolean = true,
    ): Map<String, Decision> {
        val store = SanctuaryStore.get(context)
        val session = store.refresh()
        val beforeEnrolment = store.launcherSettings()
        if (allowEnrollment && beforeEnrolment.autoEnrolFriends && beforeEnrolment.friendsWhatsApp == null) {
            val friend = apps.asSequence().filter {
                it.packageName == SANCTUARY_NATIVE_WHATSAPP && it.profile?.isClone == true &&
                    validSanctuaryAppIdentity(it.component, it.packageName, it.profileSerial)
            }.map(LauncherApp::component).distinct().toList().singleOrNull()
            if (friend != null) store.enrolFriendsWhatsApp(friend)
        }
        val settings = store.launcherSettings()
        val essential = SanctuaryGuardian.essentialPackages(context)
        val paused = session?.let(::isPaused) == true
        return apps.associate { app -> app.component to appDecision(app, session, settings, essential, paused) }
    }

    /** Recheck the original profile-aware target immediately before invoking LauncherApps. */
    fun appDecision(context: Context, app: LauncherApp): Decision {
        val store = SanctuaryStore.get(context)
        val session = store.refresh()
        return appDecision(app, session, store.launcherSettings(), SanctuaryGuardian.essentialPackages(context),
            session?.let(::isPaused) == true)
    }

    private fun appDecision(
        app: LauncherApp,
        session: SanctuarySession?,
        settings: SanctuaryLauncherSettings,
        essential: Set<String>,
        paused: Boolean,
    ): Decision = when {
        !validSanctuaryAppIdentity(app.component, app.packageName, app.profileSerial) -> Decision.UNAPPROVED
        session != null && !paused && app.profileSerial == null &&
            app.packageName == SANCTUARY_NATIVE_WHATSAPP -> Decision.UNAPPROVED
        app.profileSerial == null && app.packageName in essential -> Decision.ESSENTIAL
        session == null || paused -> Decision.UNRESTRICTED
        session.allowExternalApps && settings.allows(
            app.component, app.packageName, app.profileSerial, session.androidApps, session.mode,
        ) -> Decision.APPROVED
        else -> Decision.UNAPPROVED
    }

    fun visibility(context: Context): SanctuaryAppVisibility {
        val store = SanctuaryStore.get(context)
        return store.launcherSettings().visibilityFor(store.refresh()?.mode)
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

/** Usage Access is scoped to the current Android user. Never project a clone rule into it. */
internal fun approvedAndroidApps(session: SanctuarySession, settings: SanctuaryLauncherSettings): Set<String> =
    when {
        !session.allowExternalApps -> emptySet()
        session.mode == SanctuaryMode.PANIC -> panicSanctuaryPersonalApps
        else -> ((session.androidApps - settings.explicitPersonalPackages) +
            settings.guardianApprovedPersonalPackages) - SANCTUARY_NATIVE_WHATSAPP
    }

internal const val SANCTUARY_NATIVE_WHATSAPP = "com.whatsapp"

internal val panicSanctuaryPersonalApps = setOf(
    "com.google.android.apps.youtube.music",
    "com.google.android.youtube",
    "com.openai.chatgpt",
    "com.sec.android.app.sbrowser",
)

internal enum class PanicLauncherVisibility(val label: String, val description: String) {
    APPROVED_ONLY("Approved Only", "Show only approved and essential apps during Panic Sanctuary."),
    SHOW_ALL_WITH_LOCKS("Show All With Locks", "Keep restricted apps visible with locks. They remain blocked."),
    ;

    fun appVisibility(): SanctuaryAppVisibility {
        val hideBlocked = this == APPROVED_ONLY
        return SanctuaryAppVisibility(hideBlocked, hideBlocked, hideBlocked, hideBlocked)
    }
}

internal data class SanctuaryAppVisibility(
    val hideBlockedApps: Boolean = true,
    val hideFromSearch: Boolean = true,
    val hideFromSuggestions: Boolean = true,
    val hideFromRecentlyUsed: Boolean = true,
)

internal data class SanctuaryLauncherSettings(
    val friendsWhatsApp: String? = null,
    val autoEnrolFriends: Boolean = true,
    val appRules: Map<String, Boolean> = emptyMap(),
    val visibility: SanctuaryAppVisibility = SanctuaryAppVisibility(),
    val panicVisibility: PanicLauncherVisibility = PanicLauncherVisibility.APPROVED_ONLY,
) {
    private val personalRules = appRules.entries.mapNotNull { (key, allowed) ->
        LauncherAppIdentity.parse(key)?.takeIf { it.profileSerial == null }?.let {
            it.flattenedComponent.substringBefore('/') to allowed
        }
    }.groupBy({ it.first }, { it.second })
    val explicitPersonalPackages: Set<String> = personalRules.keys
    val guardianApprovedPersonalPackages: Set<String> = personalRules.filterValues { rules -> rules.all { it } }.keys

    fun visibilityFor(mode: SanctuaryMode?): SanctuaryAppVisibility =
        if (mode == SanctuaryMode.PANIC) panicVisibility.appVisibility() else visibility

    fun allows(
        component: String,
        packageName: String,
        profileSerial: Long?,
        legacy: Set<String>,
        mode: SanctuaryMode? = null,
    ): Boolean {
        if (!validSanctuaryAppIdentity(component, packageName, profileSerial)) return false
        if (packageName == SANCTUARY_NATIVE_WHATSAPP && profileSerial == null) return false
        if (mode == SanctuaryMode.PANIC) {
            return if (packageName == SANCTUARY_NATIVE_WHATSAPP) component == friendsWhatsApp
            else profileSerial == null && packageName in panicSanctuaryPersonalApps
        }
        appRules[component]?.let { return it }
        if (packageName == SANCTUARY_NATIVE_WHATSAPP) return component == friendsWhatsApp
        return profileSerial == null && packageName in legacy
    }
}

internal fun validSanctuaryAppIdentity(component: String, packageName: String, profileSerial: Long?): Boolean =
    LauncherAppIdentity.parse(component)?.let {
        it.profileSerial == profileSerial && it.flattenedComponent.substringBefore('/') == packageName
    } == true

internal fun isSanctuaryFriendsWhatsApp(component: String): Boolean =
    LauncherAppIdentity.parse(component)?.let {
        it.profileSerial != null && it.flattenedComponent.substringBefore('/') == SANCTUARY_NATIVE_WHATSAPP
    } == true
