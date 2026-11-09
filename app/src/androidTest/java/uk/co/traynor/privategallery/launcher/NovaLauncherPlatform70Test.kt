package uk.co.traynor.privategallery.launcher

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.KeyEvent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import uk.co.traynor.privategallery.LauncherActivity
import uk.co.traynor.privategallery.MainActivity
import uk.co.traynor.privategallery.core.sanctuary.SanctuaryLauncherPolicy
import uk.co.traynor.privategallery.core.sanctuary.SanctuaryMode
import uk.co.traynor.privategallery.core.sanctuary.SanctuaryStore

class NovaLauncherPlatform70Test {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val home = ComponentName(context, NovaLauncherActivity::class.java)

    @Test fun homeRegistrationAndExistingWorkspaceEntryAreSeparate() {
        val manager = context.packageManager
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(context.packageName)
        val resolvedHome = manager.queryIntentActivities(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
            .map { ComponentName(it.activityInfo.packageName, it.activityInfo.name) }
        assertEquals(listOf(home), resolvedHome)
        val appIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(context.packageName)
        val appEntries = manager.queryIntentActivities(appIntent, 0).map { it.activityInfo.name }
        assertTrue(LauncherActivity::class.java.name in appEntries)
        assertFalse(NovaLauncherActivity::class.java.name in appEntries)
        val homeInfo = manager.getActivityInfo(home, 0)
        val workspace = manager.getActivityInfo(ComponentName(context, MainActivity::class.java), 0)
        assertTrue(homeInfo.exported)
        assertFalse(workspace.exported)
        assertNotEquals(workspace.taskAffinity, homeInfo.taskAffinity)
    }

    @Test fun defaultHomeCanBeSelectedAndPreviousLauncherRestored() {
        val resolveCommand = "cmd package resolve-activity --brief --user current -a android.intent.action.MAIN -c android.intent.category.HOME"
        val original = shell(resolveCommand).lineSequence().last().trim()
        assertNotNull("Isolated Android 17 runner needs its existing Home app", ComponentName.unflattenFromString(original))
        assertNotEquals(home, ComponentName.unflattenFromString(original))
        try {
            shell("cmd package set-home-activity --user current ${home.flattenToString()}")
            await { ComponentName.unflattenFromString(shell(resolveCommand).lineSequence().last().trim()) == home }
            shell("input keyevent ${KeyEvent.KEYCODE_HOME}")
            await { shell("dumpsys activity activities").lineSequence().any {
                "topResumedActivity" in it && home.className in it
            } }
        } finally {
            shell("cmd package set-home-activity --user current $original")
            await { shell(resolveCommand).lineSequence().last().trim() == original }
            shell("input keyevent ${KeyEvent.KEYCODE_HOME}")
            finishLaunchers()
        }
    }

    @Test fun launcherIgnoresForeignIntentDataAndRecreatesInItsOwnTask() {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setComponent(home)
            .setData(android.net.Uri.parse("content://foreign.invalid/evidence/forged"))
            .putExtra("workspace", "hidden").putExtra("unlock", true)
        withLauncher(intent) { activity ->
            instrumentation.runOnMainSync {
                assertTrue(activity.isTaskRoot)
                assertEquals(home, activity.componentName)
                assertTrue(activity.intent.extras == null || activity.intent.extras!!.isEmpty)
                assertEquals(null, activity.intent.data)
                activity.recreate()
            }
            val recreated = resumedLauncher(excluding = activity)
            instrumentation.runOnMainSync {
                assertTrue(recreated.isTaskRoot)
                assertEquals(home, recreated.componentName)
            }
        }
    }

    @Test fun appDiscoveryAndLaunchUseAnInstalledPersonalProfileEntry() {
        val directory = LauncherAppDirectory(context)
        val apps = directory.apps()
        assertFalse(apps.any { it.packageName == context.packageName })
        val settings = apps.first { it.packageName == "com.android.settings" }
        withLauncher {
            instrumentation.runOnMainSync { assertTrue(directory.start(settings)) }
            await { instrumentation.uiAutomation.rootInActiveWindow?.packageName == settings.packageName }
            shell("input keyevent ${KeyEvent.KEYCODE_BACK}")
        }
    }

    @Test fun activeSanctuaryBlocksLauncherTouchUntilTheSharedApprovalChanges() {
        val sanctuary = SanctuaryStore.get(context)
        val mode = SanctuaryMode.LEARNING
        assertEquals(null, sanctuary.refresh())
        val preset = sanctuary.preset(mode)
        val launchStore = NovaLauncherStore(context)
        val original = launchStore.read()
        val entries = LauncherAppDirectory(context).apps()
        val decisions = SanctuaryLauncherPolicy.decisions(context, entries.map(LauncherApp::packageName))
        val ordinary = entries.filter { decisions[it.packageName] == SanctuaryLauncherPolicy.Decision.UNRESTRICTED }
        val target = ordinary.firstOrNull { it.packageName == "com.android.chrome" } ?: ordinary.first()
        val layout = LauncherLayout(pages = listOf(listOf(LauncherItem.App(target.component))))
        assertTrue(launchStore.save(NovaLauncherState(configured = true, cover = layout, inner = layout)))
        try {
            assertTrue(sanctuary.savePreset(mode, preset.copy(androidApps = emptySet(), allowExternalApps = true)))
            assertTrue(sanctuary.startFoldTest(mode))
            val session = checkNotNull(sanctuary.refresh())
            withLauncher {
                val appTag = "launcher-app-${target.component}"
                compose.waitUntil(15_000) { compose.onAllNodesWithTag(appTag).fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithTag(appTag).performTouchInput { click() }
                compose.onNodeWithText("Sanctuary protection").assertIsDisplayed()
                compose.onNodeWithText("${target.label} is blocked by the active Sanctuary rules.").assertIsDisplayed()
                compose.onAllNodes(hasClickAction() and hasAnyAncestor(isDialog())).assertCountEquals(1)
                assertEquals(context.packageName, instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString())
                compose.onNodeWithText("Back to Nova Launcher").performClick()
                assertTrue(sanctuary.saveAndroidApprovals(session.id, mode, setOf(target.packageName)))
                compose.onNodeWithTag(appTag).performTouchInput { click() }
                await { instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString() == target.packageName }
                shell("input keyevent ${KeyEvent.KEYCODE_BACK}")
            }
        } finally {
            if (sanctuary.refresh() != null) assertTrue(sanctuary.end(SanctuaryStore.END_PHRASE))
            assertTrue(sanctuary.savePreset(mode, preset))
            assertTrue(launchStore.save(original))
        }
    }

    @Test fun deniedNativeWidgetBindingRemovesUnownedHostId() {
        val manager = context.getSystemService(AppWidgetManager::class.java)
        val provider = manager.getInstalledProvidersForProfile(android.os.Process.myUserHandle())
            .single { it.provider == ComponentName(instrumentation.context.packageName, LauncherWidgetFixture70::class.java.name) }
        val host = AppWidgetHost(context, NovaLauncherActivity.WIDGET_HOST_ID)
        val before = host.appWidgetIds.toSet()
        withLauncher { activity ->
            instrumentation.runOnMainSync { activity.bindWidget(provider, wide = false) }
            await { host.appWidgetIds.toSet().minus(before).size == 1 }
            val allocated = host.appWidgetIds.toSet().minus(before).single()
            try {
                await { instrumentation.uiAutomation.rootInActiveWindow?.let { it.packageName != context.packageName } == true }
                assertEquals(null, manager.getAppWidgetInfo(allocated))
                shell("input keyevent ${KeyEvent.KEYCODE_BACK}")
                await { allocated !in host.appWidgetIds.toSet() }
                assertFalse(NovaLauncherStore(context).read().widgets.any { it.id == allocated })
            } finally {
                if (allocated in host.appWidgetIds) host.deleteAppWidgetId(allocated)
            }
        }
    }

    private fun withLauncher(
        intent: Intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setComponent(home),
        block: (NovaLauncherActivity) -> Unit,
    ) {
        // ActivityScenario matches the original Intent; this exported Activity replaces it with
        // a neutral HOME Intent. Observe actual instances and stages instead of matching Intent data.
        finishLaunchers()
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            block(resumedLauncher())
        } finally {
            finishLaunchers()
        }
    }

    private fun resumedLauncher(excluding: NovaLauncherActivity? = null): NovaLauncherActivity {
        var result: NovaLauncherActivity? = null
        await {
            instrumentation.runOnMainSync {
                result = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                    .filterIsInstance<NovaLauncherActivity>().singleOrNull { it !== excluding }
            }
            result != null
        }
        return requireNotNull(result)
    }

    private fun finishLaunchers() {
        var activities = emptyList<NovaLauncherActivity>()
        instrumentation.runOnMainSync {
            val monitor = ActivityLifecycleMonitorRegistry.getInstance()
            activities = Stage.values().filter { it != Stage.DESTROYED }.flatMap(monitor::getActivitiesInStage)
                .filterIsInstance<NovaLauncherActivity>().distinct()
            activities.forEach { it.finish() }
        }
        if (activities.isNotEmpty()) await {
            var destroyed = false
            instrumentation.runOnMainSync { destroyed = activities.all { it.isDestroyed } }
            destroyed
        }
    }

    private fun shell(command: String): String =
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command))
            .bufferedReader().use { it.readText().trim() }

    private fun await(predicate: () -> Boolean) {
        val expires = SystemClock.elapsedRealtime() + 15_000
        do {
            if (predicate()) return
            SystemClock.sleep(75)
        } while (SystemClock.elapsedRealtime() < expires)
        assertTrue("Expected Android state was not reached", predicate())
    }
}
