package uk.co.traynor.privategallery.launcher

import uk.co.traynor.privategallery.core.sanctuary.SanctuaryLauncherPolicy.Decision
import uk.co.traynor.privategallery.core.sanctuary.SanctuarySession

/** Fixed launcher IDs are layout references. They are never resolved into Android intents. */
internal enum class LauncherBuiltIn(
    val route: String,
    val label: String,
    val utility: LauncherUtility? = null,
) {
    SANCTUARY("sanctuary", "Nova Sanctuary"),
    CALCULATOR(LauncherUtility.CALCULATOR.route, LauncherUtility.CALCULATOR.label, LauncherUtility.CALCULATOR),
    ARCADE(LauncherUtility.ARCADE.route, LauncherUtility.ARCADE.label, LauncherUtility.ARCADE),
    STORE(LauncherUtility.STORE.route, LauncherUtility.STORE.label, LauncherUtility.STORE),
    SAFE_NOTIFICATIONS("safe-notifications", "Safe Notifications"),
    PRIMARY_GALLERY("vault", "Primary Gallery");

    private val policyKey: String get() = "nova:$route"

    fun app(applicationId: String): LauncherApp =
        LauncherApp(component(applicationId), policyKey, label)

    fun decision(session: SanctuarySession?): Decision = when {
        this == SANCTUARY -> Decision.ESSENTIAL
        session == null -> Decision.UNRESTRICTED
        session.allowsNovaApp(route) -> Decision.APPROVED
        else -> Decision.UNAPPROVED
    }

    private fun component(applicationId: String) = "$applicationId/.launcher.builtin.$name"

    companion object {
        fun find(applicationId: String, component: String): LauncherBuiltIn? =
            entries.firstOrNull { it.component(applicationId) == component }
    }
}
