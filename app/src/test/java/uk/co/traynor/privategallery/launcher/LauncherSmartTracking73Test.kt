package uk.co.traynor.privategallery.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherSmartTracking73Test {
    private val mail = LauncherApp("mail/mail.Home", "mail", "Mail")
    private val maps = LauncherApp("maps/maps.Home", "maps", "Maps")
    private val privateApp = LauncherApp("private/private.Home", "private", "Private")

    @Test fun automaticOrdersSuccessfulLaunchesAndOffCollectsNothing() {
        val automatic = LauncherSmartState().record(mail.component, 30)
            .record(maps.component, 40).record(mail.component, 20)
        assertEquals(LauncherTrackingMode.AUTOMATIC, automatic.mode)
        assertEquals(listOf(maps, mail), automatic.recentApps(listOf(mail, maps)))

        val off = automatic.copy(mode = LauncherTrackingMode.OFF)
        assertEquals(off, off.record(privateApp.component, 50))
        assertTrue(off.recentApps(listOf(mail, maps, privateApp)).isEmpty())
        assertTrue(off.suggestedApps(listOf(mail, maps, privateApp)).isEmpty())
    }

    @Test fun suggestionsFilterCurrentEligibilityAndExclusionsBeforeLimiting() {
        val state = LauncherSmartState(
            pinned = setOf(mail.component, privateApp.component),
            excluded = setOf(maps.component),
        ).record(maps.component, 20).record(privateApp.component, 40)
        assertEquals(listOf(mail), state.suggestedApps(listOf(mail, maps)))
        assertTrue(state.recentApps(listOf(mail, maps)).isEmpty())
        assertTrue(state.copy(showSuggestions = false).suggestedApps(listOf(mail, maps)).isEmpty())
    }

    @Test fun exclusionRemovesExistingHistoryAndPreventsFutureRecording() {
        val state = LauncherSmartState(pinned = setOf(mail.component)).record(mail.component, 10)
            .withExclusion(mail.component)
        assertFalse(mail.component in state.pinned)
        assertTrue(state.withExclusion(mail.component).recentApps(listOf(mail)).isEmpty())
        assertEquals(state, state.record(mail.component, 20))
    }

    @Test fun resetSuggestionsKeepsRecentsAndPinsWhileRemovingOlderInferredSuggestions() {
        val state = LauncherSmartState(pinned = setOf(mail.component))
            .record(maps.component, 20).copy(suggestionsResetAt = 30)
        assertEquals(listOf(maps), state.recentApps(listOf(mail, maps)))
        assertEquals(listOf(mail), state.suggestedApps(listOf(mail, maps)))
        assertEquals(listOf(mail, maps), state.record(maps.component, 40).suggestedApps(listOf(mail, maps)))
        assertEquals(listOf(mail), state.copy(mode = LauncherTrackingMode.OFF).pinnedApps(listOf(mail, maps)))
    }

    @Test fun historyStaysBoundedAndDisplayUsesTheSelectedLimit() {
        val apps = (1..100).map { LauncherApp("app$it/app$it.Home", "app$it", "App $it") }
        val state = apps.foldIndexed(LauncherSmartState(recentLimit = 4)) { index, current, app ->
            current.record(app.component, index + 1L)
        }
        assertEquals(64, state.history.size)
        assertEquals(listOf("app100", "app99", "app98", "app97"), state.recentApps(apps).map { it.packageName })
        assertEquals(16, state.copy(recentLimit = 16).recentApps(apps).size)
    }

    @Test fun extendedAcceptsOnlyExactEligibleLauncherComponents() {
        val eligible = setOf(mail.component, maps.component)
        assertEquals(mail.component, matchLauncherUsageEvent("mail", "mail.Home", eligible))
        assertEquals(null, matchLauncherUsageEvent("mail", "mail.InternalActivity", eligible))
        assertEquals(null, matchLauncherUsageEvent("private", "private.Home", eligible))
        assertEquals(null, matchLauncherUsageEvent("mail", "maps.Home", eligible))
    }
}
