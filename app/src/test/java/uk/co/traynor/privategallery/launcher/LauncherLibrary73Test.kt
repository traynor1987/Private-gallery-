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

    @Test fun cardGridOmitsEmptyCategoriesExceptExplicitPins() {
        val game = app("example.game", "Game", ApplicationInfo.CATEGORY_GAME)
        val groups = launcherLibraryGroups(listOf(game))

        assertEquals(
            listOf(LauncherLibraryGroup(LauncherCategory.GAMES, listOf(game)),
                LauncherLibraryGroup(LauncherCategory.FINANCE, emptyList())),
            launcherLibraryVisibleGroups(groups, setOf(LauncherCategory.FINANCE), emptyList()),
        )
    }

    @Test fun pinnedRecentAppsKeepTheCardVisibleAndDoNotDuplicateHistory() {
        val game = app("example.game", "Game", ApplicationInfo.CATEGORY_GAME)
        val groups = launcherLibraryGroups(listOf(game))
        val recents = LauncherLibraryGroup(LauncherCategory.RECENTLY_USED, listOf(game))

        assertEquals(recents, launcherLibraryVisibleGroups(groups, emptySet(), listOf(game)).first())
        assertEquals(recents, launcherLibraryVisibleGroups(
            launcherLibraryGroups(listOf(game), recentlyUsed = listOf(game)), emptySet(), listOf(game),
        ).first())
    }

    @Test fun frequentAppsLeadPrimaryCategoriesAndOverridesAreProfileSpecific() {
        val personal = app("com.whatsapp", "WhatsApp")
        val friends = personal.copy(component = "${personal.component}#42")
        val chat = app("example.chat", "Alpha Chat", ApplicationInfo.CATEGORY_SOCIAL)
        val forum = app("example.forum", "Beta Forum", ApplicationInfo.CATEGORY_SOCIAL)
        val groups = launcherLibraryGroups(
            listOf(personal, friends, chat, forum),
            recentlyUsed = listOf(forum),
            categoryOverrides = mapOf(friends.component to LauncherCategory.UTILITIES),
            launchCounts = mapOf(personal.component to 15),
        ).associate { it.category to it.apps }

        assertEquals(listOf(personal, forum, chat), groups[LauncherCategory.SOCIAL])
        assertEquals(listOf(friends), groups[LauncherCategory.UTILITIES])
        assertEquals(listOf(forum), groups[LauncherCategory.RECENTLY_USED])
        assertEquals(listOf(personal, friends), launcherLibraryMatches(groups.values.flatten(), "whatsapp"))
    }

    @Test fun suggestionsAndRecentlyAddedLeadCategoryOrder() {
        val now = 30 * 86_400_000L
        val recent = app("example.recent", "Recently Installed", install = now - 500)
        val suggestion = app("example.suggested", "Suggested")
        val groups = launcherLibraryGroups(listOf(recent, suggestion), listOf(suggestion), listOf(recent), now)

        assertEquals(
            listOf(LauncherCategory.SUGGESTIONS, LauncherCategory.RECENTLY_ADDED, LauncherCategory.RECENTLY_USED),
            launcherLibraryVisibleGroups(groups, emptySet(), emptyList()).take(3).map { it.category },
        )
    }

    @Test fun independentlyVisibleHistoryDoesNotPopulatePrimaryCategories() {
        val friends = app("com.whatsapp", "Friends WhatsApp").let { it.copy(component = "${it.component}#42") }
        val personal = app("com.whatsapp", "WhatsApp")
        val groups = launcherLibraryGroups(
            apps = listOf(friends),
            suggestedApps = listOf(personal, friends),
            recentlyUsed = listOf(personal, friends),
            suggestionCatalog = listOf(personal, friends),
            recentCatalog = listOf(friends),
        ).associate { it.category to it.apps }

        assertEquals(listOf(friends), groups[LauncherCategory.SOCIAL])
        assertEquals(listOf(personal, friends), groups[LauncherCategory.SUGGESTIONS])
        assertEquals(listOf(friends), groups[LauncherCategory.RECENTLY_USED])
    }

    private fun app(packageName: String, label: String, category: Int? = null, install: Long? = null) =
        LauncherApp("$packageName/.Main", packageName, label, category = category, firstInstallTime = install)
}
