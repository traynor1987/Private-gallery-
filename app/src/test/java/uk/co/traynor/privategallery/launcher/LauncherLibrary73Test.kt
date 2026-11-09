package uk.co.traynor.privategallery.launcher

import android.content.pm.ApplicationInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LauncherLibrary73Test {
    @Test fun categorizesUsingPackageMetadataAndKeepsUnknownAppsSearchable() {
        val novelGame = app("example.studio", "Calculator", ApplicationInfo.CATEGORY_GAME)
        val finance = app("com.paypal.android.p2pmobile", "PayPal")
        val shopping = app("com.amazon.mShop.android.shopping", "Amazon Shopping")
        val fitness = app("com.sec.android.app.shealth", "Samsung Health")
        val creativity = app("example.camera", "Studio", ApplicationInfo.CATEGORY_IMAGE)
        val nova = app("nova:calculator", "Nova Calculator")
        val unknown = app("example.ambiguous", "The Office")
        val apps = listOf(novelGame, finance, shopping, fitness, creativity, nova, unknown)
        val groups = launcherLibraryGroups(apps).associate { it.category to it.apps }

        assertEquals(listOf(novelGame), groups[LauncherCategory.GAMES])
        assertEquals(listOf(finance), groups[LauncherCategory.FINANCE])
        assertEquals(listOf(shopping), groups[LauncherCategory.SHOPPING])
        assertEquals(listOf(fitness), groups[LauncherCategory.HEALTH_FITNESS])
        assertEquals(listOf(creativity), groups[LauncherCategory.CREATIVITY])
        assertEquals(listOf(nova), groups[LauncherCategory.NOVA_OS])
        assertEquals(listOf(unknown), groups[LauncherCategory.OTHER])
        assertEquals(listOf(unknown), launcherLibraryMatches(groups.values.flatten(), " the office "))
    }

    @Test fun recentGroupsKeepEligibleIdentityAndOrderAndUseOnlyRecentInstallTimes() {
        val now = 30 * 86_400_000L
        val fresh = app("example.fresh", "Fresh", install = now - 500)
        val boundary = app("example.boundary", "Boundary", install = now - 14 * 86_400_000L)
        val older = app("example.older", "Older", install = now - 15 * 86_400_000L)
        val future = app("example.future", "Future", install = now + 100)
        val unknown = app("example.unknown", "Unknown").copy(iconRevision = now)
        val filtered = app("example.filtered", "Filtered", install = now)
        val groups = launcherLibraryGroups(
            listOf(fresh, boundary, older, future, unknown),
            suggestedApps = listOf(filtered, older.copy(label = "Stale"), older, fresh),
            recentlyUsed = listOf(filtered, unknown, older),
            nowMillis = now,
        ).associate { it.category to it.apps }

        assertEquals(listOf(older, fresh), groups[LauncherCategory.SUGGESTIONS])
        assertEquals(listOf(unknown, older), groups[LauncherCategory.RECENTLY_USED])
        assertEquals(listOf(fresh, boundary), groups[LauncherCategory.RECENTLY_ADDED])
        assertFalse(groups.values.flatten().any { it.component == filtered.component })
    }

    @Test fun searchDeduplicatesRepeatedGroupMembershipWithoutCollapsingProfiles() {
        val personal = app("example.chat", "Chat")
        val work = personal.copy(component = "${personal.component}#42")
        assertEquals(listOf(personal, work), launcherLibraryMatches(listOf(personal, personal, work), "Chat"))
    }

    private fun app(packageName: String, label: String, category: Int? = null, install: Long? = null) =
        LauncherApp("$packageName/.Main", packageName, label, category = category, firstInstallTime = install)
}
