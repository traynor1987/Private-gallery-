package uk.co.traynor.privategallery.launcher

import android.app.Activity
import android.app.role.RoleManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import uk.co.traynor.privategallery.LauncherActivity
import uk.co.traynor.privategallery.MainActivity
import uk.co.traynor.privategallery.core.sanctuary.SanctuaryMode
import uk.co.traynor.privategallery.core.sanctuary.SanctuaryStore

class LauncherDefaultHomeEntry73Test {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test fun appIconOffersDefaultHomeAndHonorsTheUsersReminderChoice() {
        assertAnotherAppIsDefaultHome()
        val store = NovaLauncherStore(context)
        val original = store.askForDefaultHome()
        val entry = checkNotNull(context.packageManager.getLaunchIntentForPackage(context.packageName))
        try {
            assertTrue(store.setAskForDefaultHome(true))
            context.startActivity(entry)
            awaitReminder()
            compose.onNodeWithText("Not Now").performClick()
            awaitResumed(MainActivity::class.java)
            assertTrue(store.askForDefaultHome())
            finishAppEntry()

            context.startActivity(entry)
            awaitReminder()
            compose.onNodeWithText("Don’t Ask Again").performClick()
            awaitResumed(MainActivity::class.java)
            assertFalse(store.askForDefaultHome())
            finishAppEntry()

            context.startActivity(entry)
            awaitResumed(MainActivity::class.java)
            compose.onNodeWithText("Make NovaOS your default Home?").assertDoesNotExist()
        } finally {
            finishAppEntry()
            assertTrue(store.setAskForDefaultHome(original))
        }
    }

    @Test fun appIconSkipsTheReminderDuringSanctuaryProtection() {
        assertAnotherAppIsDefaultHome()
        val sanctuary = SanctuaryStore.get(context)
        assertEquals(null, sanctuary.refresh())
        val store = NovaLauncherStore(context)
        val original = store.askForDefaultHome()
        val entry = checkNotNull(context.packageManager.getLaunchIntentForPackage(context.packageName))
        try {
            assertTrue(store.setAskForDefaultHome(true))
            assertTrue(sanctuary.startFoldTest(SanctuaryMode.LEARNING))
            context.startActivity(entry)
            awaitResumed(MainActivity::class.java)
            compose.onNodeWithText("Make NovaOS your default Home?").assertDoesNotExist()
            assertTrue(store.askForDefaultHome())
        } finally {
            finishAppEntry()
            if (sanctuary.refresh() != null) assertTrue(sanctuary.end(SanctuaryStore.END_PHRASE))
            assertTrue(store.setAskForDefaultHome(original))
        }
    }

    private fun assertAnotherAppIsDefaultHome() {
        val roles = context.getSystemService(RoleManager::class.java)
        assertTrue(roles.isRoleAvailable(RoleManager.ROLE_HOME))
        assertFalse("Run with another Android Home app selected", roles.isRoleHeld(RoleManager.ROLE_HOME))
    }

    private fun awaitReminder() {
        awaitResumed(LauncherActivity::class.java)
        val title = "Make NovaOS your default Home?"
        compose.waitUntil(15_000) { compose.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(title).assertIsDisplayed()
    }

    private fun awaitResumed(type: Class<out Activity>) {
        compose.waitUntil(15_000) {
            var resumed = false
            instrumentation.runOnMainSync {
                resumed = ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED).any { !it.isFinishing && type.isInstance(it) }
            }
            resumed
        }
    }

    private fun finishAppEntry() {
        instrumentation.runOnMainSync {
            val monitor = ActivityLifecycleMonitorRegistry.getInstance()
            Stage.values().filter { it != Stage.DESTROYED }.flatMap(monitor::getActivitiesInStage)
                .filter { it is LauncherActivity || it is MainActivity }
                .distinct().forEach(Activity::finish)
        }
        instrumentation.waitForIdleSync()
    }
}
