package uk.co.traynor.privategallery.home2

import android.app.AutomaticZenRule
import android.app.NotificationManager
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.service.notification.ZenPolicy
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.co.traynor.privategallery.BuildConfig
import uk.co.traynor.privategallery.core.sanctuary.SanctuaryDnd
import uk.co.traynor.privategallery.core.sanctuary.SanctuaryDndState
import uk.co.traynor.privategallery.core.sanctuary.SanctuaryMode
import uk.co.traynor.privategallery.core.sanctuary.SanctuaryStore

class SanctuaryDndUiTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test fun ownedRuleEndsAndDndAccessDoesNotControlTheSession() {
        check(Build.VERSION.SDK_INT == 37 && BuildConfig.DEBUG &&
            context.packageName.endsWith(".hiddenprototype") && shell("getprop ro.kernel.qemu").trim() == "1"
        ) { "DND access verification requires the disposable API 37 debug emulator" }

        val manager = checkNotNull(context.getSystemService(NotificationManager::class.java))
        val store = SanctuaryStore.get(context)
        val original = store.preset(SanctuaryMode.WORK)
        check(store.refresh() == null)
        val packageName = context.packageName
        val originallyGranted = manager.isNotificationPolicyAccessGranted
        try {
            shell("cmd notification disallow_dnd $packageName")
            waitFor("deny access") { !manager.isNotificationPolicyAccessGranted }
            assertTrue(store.savePreset(SanctuaryMode.WORK, original.copy(enableDnd = true)))
            assertTrue(store.start(SanctuaryMode.WORK))
            assertFalse(SanctuaryDnd.start(context))
            assertEquals(SanctuaryDndState.UNAVAILABLE, SanctuaryDnd.status(context))
            assertTrue(store.end(SanctuaryStore.END_PHRASE))

            shell("cmd notification allow_dnd $packageName")
            waitFor("grant access") { manager.isNotificationPolicyAccessGranted }
            val originalPolicy = manager.notificationPolicy
            val originalFilter = manager.currentInterruptionFilter
            assertTrue(store.start(SanctuaryMode.WORK))
            assertTrue(SanctuaryDnd.start(context))
            waitFor("activate Sanctuary's rule") { SanctuaryDnd.status(context) == SanctuaryDndState.ON }

            val ruleEntry = manager.automaticZenRules.entries.single { it.value.name == "Nova Sanctuary" }
            val policy = checkNotNull(ruleEntry.value.zenPolicy)
            assertEquals(ZenPolicy.STATE_ALLOW, policy.priorityCategoryAlarms)
            assertTrue(policy.priorityCallSenders in setOf(
                ZenPolicy.PEOPLE_TYPE_STARRED, ZenPolicy.PEOPLE_TYPE_CONTACTS, ZenPolicy.PEOPLE_TYPE_ANYONE,
            ))
            val renamed = AutomaticZenRule.Builder(ruleEntry.value).setName("My focus notifications").build()
            assertTrue(manager.updateAutomaticZenRule(ruleEntry.key, renamed))
            assertTrue(store.end(SanctuaryStore.END_PHRASE))
            SanctuaryDnd.reconcile(context)
            waitFor("restore the original filter") {
                SanctuaryDnd.status(context) == SanctuaryDndState.OFF && manager.currentInterruptionFilter == originalFilter
            }
            assertEquals("My focus notifications", manager.getAutomaticZenRule(ruleEntry.key)?.name)
            assertEquals(originalPolicy, manager.notificationPolicy)

            assertTrue(store.start(SanctuaryMode.WORK))
            assertTrue(SanctuaryDnd.start(context))
            waitFor("reactivate the existing rule") { SanctuaryDnd.status(context) == SanctuaryDndState.ON }
            shell("cmd notification disallow_dnd $packageName")
            waitFor("reflect revoked access") { SanctuaryDnd.status(context) == SanctuaryDndState.UNAVAILABLE }
            assertNotNull(store.refresh())
        } finally {
            store.end(SanctuaryStore.END_PHRASE)
            shell("cmd notification allow_dnd $packageName")
            if (manager.isNotificationPolicyAccessGranted) SanctuaryDnd.reconcile(context)
            if (!originallyGranted) shell("cmd notification disallow_dnd $packageName")
            store.savePreset(SanctuaryMode.WORK, original)
        }
    }

    private fun shell(command: String): String =
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command))
            .use { it.readBytes().decodeToString() }

    private fun waitFor(operation: String, ready: () -> Boolean) {
        val timeout = SystemClock.elapsedRealtime() + 10_000L
        while (SystemClock.elapsedRealtime() < timeout) {
            if (ready()) return
            SystemClock.sleep(150)
        }
        throw AssertionError("Could not $operation. Current DND status: ${SanctuaryDnd.status(context)}")
    }
}
