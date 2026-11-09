package uk.co.traynor.privategallery.launcher

import android.content.pm.ApplicationInfo
import java.text.Collator

internal enum class LauncherCategory(val label: String) {
    SUGGESTIONS("Suggestions"),
    RECENTLY_USED("Recently Used"),
    RECENTLY_ADDED("Recently Added"),
    NOVA_OS("NovaOS Apps"),
    SOCIAL("Social"),
    PRODUCTIVITY("Productivity"),
    ENTERTAINMENT("Entertainment"),
    GAMES("Games"),
    UTILITIES("Utilities"),
    CREATIVITY("Creativity"),
    FINANCE("Finance"),
    SHOPPING("Shopping"),
    HEALTH_FITNESS("Health & Fitness"),
    OTHER("Other"),
}

internal data class LauncherLibraryGroup(val category: LauncherCategory, val apps: List<LauncherApp>)

private const val RECENT_INSTALL_AGE = 14 * 86_400_000L

/** Receives the same visible catalogue as Drawer. History sources can only reorder eligible entries. */
internal fun launcherLibraryGroups(
    apps: List<LauncherApp>,
    suggestedApps: List<LauncherApp> = emptyList(),
    recentlyUsed: List<LauncherApp> = emptyList(),
    nowMillis: Long = System.currentTimeMillis(),
): List<LauncherLibraryGroup> {
    val byComponent = apps.associateBy(LauncherApp::component)
    val byName = byComponent.values.sortedWith(launcherLibraryAppOrder())
    val byCategory = byName.groupBy(::launcherPrimaryCategory)
    fun eligible(history: List<LauncherApp>) =
        history.distinctBy(LauncherApp::component).mapNotNull { byComponent[it.component] }

    return LauncherCategory.entries.map { category ->
        val members = when (category) {
            LauncherCategory.SUGGESTIONS -> eligible(suggestedApps)
            LauncherCategory.RECENTLY_USED -> eligible(recentlyUsed)
            LauncherCategory.RECENTLY_ADDED -> byName.filter { app ->
                val installed = app.firstInstallTime
                installed != null && installed in 1..nowMillis && nowMillis - installed <= RECENT_INSTALL_AGE
            }.sortedByDescending(LauncherApp::firstInstallTime)
            else -> byCategory[category].orEmpty()
        }
        LauncherLibraryGroup(category, members)
    }
}

internal fun launcherLibraryMatches(apps: List<LauncherApp>, query: String): List<LauncherApp> {
    val term = query.trim()
    return apps.distinctBy(LauncherApp::component)
        .filter { it.label.contains(term, ignoreCase = true) || it.profileLabel?.contains(term, ignoreCase = true) == true }
        .sortedWith(launcherLibraryAppOrder())
}

private fun launcherLibraryAppOrder(): Comparator<LauncherApp> {
    val collator = Collator.getInstance()
    return Comparator { a, b ->
        collator.compare(a.label, b.label).takeIf { it != 0 } ?: a.component.compareTo(b.component)
    }
}

private fun launcherPrimaryCategory(app: LauncherApp): LauncherCategory {
    if (app.packageName.startsWith("nova:")) return LauncherCategory.NOVA_OS
    // Android does not define categories for these common app types.
    knownPackageCategories[app.packageName]?.let { return it }
    return when (app.category) {
        ApplicationInfo.CATEGORY_GAME -> LauncherCategory.GAMES
        ApplicationInfo.CATEGORY_AUDIO, ApplicationInfo.CATEGORY_VIDEO, ApplicationInfo.CATEGORY_NEWS ->
            LauncherCategory.ENTERTAINMENT
        ApplicationInfo.CATEGORY_IMAGE -> LauncherCategory.CREATIVITY
        ApplicationInfo.CATEGORY_SOCIAL -> LauncherCategory.SOCIAL
        ApplicationInfo.CATEGORY_PRODUCTIVITY -> LauncherCategory.PRODUCTIVITY
        ApplicationInfo.CATEGORY_MAPS, ApplicationInfo.CATEGORY_ACCESSIBILITY -> LauncherCategory.UTILITIES
        else -> LauncherCategory.OTHER
    }
}

private val knownPackageCategories = mapOf(
    "com.paypal.android.p2pmobile" to LauncherCategory.FINANCE,
    "com.google.android.apps.walletnfcrel" to LauncherCategory.FINANCE,
    "com.samsung.android.spay" to LauncherCategory.FINANCE,
    "com.revolut.revolut" to LauncherCategory.FINANCE,
    "com.amazon.mShop.android.shopping" to LauncherCategory.SHOPPING,
    "com.ebay.mobile" to LauncherCategory.SHOPPING,
    "com.etsy.android" to LauncherCategory.SHOPPING,
    "com.vinted" to LauncherCategory.SHOPPING,
    "com.sec.android.app.shealth" to LauncherCategory.HEALTH_FITNESS,
    "com.google.android.apps.fitness" to LauncherCategory.HEALTH_FITNESS,
    "com.fitbit.FitbitMobile" to LauncherCategory.HEALTH_FITNESS,
    "com.strava" to LauncherCategory.HEALTH_FITNESS,
    "com.android.settings" to LauncherCategory.UTILITIES,
    "com.android.dialer" to LauncherCategory.UTILITIES,
    "com.google.android.dialer" to LauncherCategory.UTILITIES,
    "com.sec.android.dialer" to LauncherCategory.UTILITIES,
    "com.sec.android.app.myfiles" to LauncherCategory.UTILITIES,
    "com.google.android.apps.nbu.files" to LauncherCategory.UTILITIES,
    "com.google.android.calculator" to LauncherCategory.UTILITIES,
    "com.sec.android.app.popupcalculator" to LauncherCategory.UTILITIES,
    "com.google.android.deskclock" to LauncherCategory.UTILITIES,
    "com.sec.android.app.clockpackage" to LauncherCategory.UTILITIES,
    "com.google.android.GoogleCamera" to LauncherCategory.CREATIVITY,
    "com.sec.android.app.camera" to LauncherCategory.CREATIVITY,
    "com.google.android.apps.photos" to LauncherCategory.CREATIVITY,
    "com.sec.android.gallery3d" to LauncherCategory.CREATIVITY,
    "com.whatsapp" to LauncherCategory.SOCIAL,
    "com.google.android.apps.messaging" to LauncherCategory.SOCIAL,
    "com.samsung.android.messaging" to LauncherCategory.SOCIAL,
    "com.google.android.gm" to LauncherCategory.PRODUCTIVITY,
    "com.google.android.calendar" to LauncherCategory.PRODUCTIVITY,
    "com.samsung.android.calendar" to LauncherCategory.PRODUCTIVITY,
)
