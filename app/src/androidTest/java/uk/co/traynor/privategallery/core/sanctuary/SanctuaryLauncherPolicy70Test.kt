package uk.co.traynor.privategallery.core.sanctuary

import android.app.KeyguardManager
import android.app.UiAutomation
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.traynor.privategallery.BuildConfig
import uk.co.traynor.privategallery.core.sanctuary.SanctuaryLauncherPolicy.Decision
import uk.co.traynor.privategallery.home2.SanctuarySettings

@RunWith(AndroidJUnit4::class)
class SanctuaryLauncherPolicy70Test {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val automation get() = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
    private val store get() = SanctuaryStore.get(context)

    @Test fun approvalAndRevocationChangeLauncherAndGuardianTogether() {
        disposableEmulator()
        val external = nonessentialApps().first()
        withSession {
            assertEquals(Decision.UNAPPROVED, SanctuaryLauncherPolicy.decision(context, external))
            ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
                scenario.onActivity { assertFalse(SanctuaryGuardian.launch(it, external)) }
                val approved = setOf(external)
                assertTrue(store.saveAndroidApprovals(checkNotNull(store.current.value).id, SanctuaryMode.WORK, approved))
                assertEquals(approved, store.preset(SanctuaryMode.WORK).androidApps)
                assertEquals(Decision.APPROVED, SanctuaryLauncherPolicy.decision(context, external))
                scenario.onActivity { assertTrue(SanctuaryGuardian.launch(it, external)) }
                await("approved app reached Android foreground") { topIs(external) }
            }
            assertTrue(store.saveAndroidApprovals(checkNotNull(store.current.value).id, SanctuaryMode.WORK, emptySet()))
            assertEquals(Decision.UNAPPROVED, SanctuaryLauncherPolicy.decision(context, external))
            assertEquals(listOf(false, true), store.approvalHistory().filter { it.packageName == external }.take(2).map { it.approved })
        }
    }

    @Test fun androidSettingsAndPhoneStayExemptDuringProtection() {
        disposableEmulator()
        withSession {
            val settings = requireNotNull(context.packageManager.resolveActivity(
                Intent(Settings.ACTION_SETTINGS),
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()),
            )?.activityInfo?.packageName)
            val phone = requireNotNull(context.getSystemService(android.telecom.TelecomManager::class.java)?.defaultDialerPackage)
            assertEquals(Decision.ESSENTIAL, SanctuaryLauncherPolicy.decision(context, context.packageName))
            assertEquals(Decision.ESSENTIAL, SanctuaryLauncherPolicy.decision(context, settings))
            assertEquals(Decision.ESSENTIAL, SanctuaryLauncherPolicy.decision(context, phone))
            val permissionControllers = setOf("com.android.permissioncontroller", "com.google.android.permissioncontroller")
            ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
                scenario.onActivity { assertTrue(SanctuaryLauncherPolicy.openHomeSettings(it)) }
                await("Android Home selection settings reached foreground") {
                    val resumed = resumedActivities()
                    resumed.any { it.startsWith("$settings/") } ||
                        resumed.any { component ->
                            val controller = component.substringBefore('/')
                            controller in permissionControllers && component.endsWith(".DefaultAppActivity") &&
                                SanctuaryLauncherPolicy.decision(context, controller) == Decision.ESSENTIAL
                        }
                }
            }
            ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
                scenario.onActivity { assertTrue(SanctuaryLauncherPolicy.openEmergencyPhone(it)) }
                await("Android Phone reached foreground") { topIs(phone) }
            }
        }
    }

    @Test fun deviceCredentialAuthorisesOnlyTheWorkspacePause() {
        disposableEmulator()
        val (first, second) = nonessentialApps().take(2)
        val keyguard = requireNotNull(context.getSystemService(KeyguardManager::class.java))
        check(!keyguard.isDeviceSecure) { "A disposable emulator without an existing screen lock is required" }
        val pin = "436890"
        try {
            val configured = shell("locksettings set-pin $pin")
            assertTrue(configured, configured.contains("Pin set", ignoreCase = true))
            await("screen lock configured") { keyguard.isDeviceSecure }
            withSession {
                ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
                    scenario.onActivity { host -> host.setContent { SanctuarySettings(store, {}, {}) } }
                    compose.onNodeWithTag("sanctuary-pause-android-protection").performScrollTo().performClick()
                    // API 37 hides the secure PIN editor from accessibility. Wait for the
                    // real system prompt before sending the disposable emulator's PIN.
                    var focusedSince: Long? = null
                    await("Android BiometricPrompt owns stable window focus") {
                        val focused = shell("dumpsys window").lineSequence().any {
                            "mCurrentFocus=" in it && it.trimEnd().endsWith(" BiometricPrompt}")
                        }
                        val now = SystemClock.elapsedRealtime()
                        focusedSince = if (focused) focusedSince ?: now else null
                        focusedSince?.let { now - it >= 1_000L } == true
                    }
                    assertEquals(Decision.UNAPPROVED, SanctuaryLauncherPolicy.decision(context, first))
                    assertEquals(Decision.UNAPPROVED, SanctuaryLauncherPolicy.decision(context, second))
                    shell("input keyevent ${pin.map { "KEYCODE_$it" }.joinToString(" ")} KEYCODE_ENTER")
                    await("device credential paused Android app protection") {
                        !SanctuaryLauncherPolicy.launcherProtectionActive(context)
                    }
                    assertTrue(SanctuaryLauncherPolicy.decision(context, first).canLaunch)
                    assertTrue(SanctuaryLauncherPolicy.decision(context, second).canLaunch)
                    assertTrue(store.refresh()?.androidApps.orEmpty().isEmpty())
                }
            }
            withSession {
                assertEquals(Decision.UNAPPROVED, SanctuaryLauncherPolicy.decision(context, first))
                assertTrue(SanctuaryLauncherPolicy.launcherProtectionActive(context))
            }
        } finally {
            val cleared = shell("locksettings clear --old $pin")
            if (keyguard.isDeviceSecure) assertTrue(cleared, cleared.contains("Lock credential cleared", ignoreCase = true))
            shell("wm dismiss-keyguard")
        }
    }

    @Test fun persistentShieldBlocksExternalAppAndClearsOnNovaLauncherHome() {
        disposableEmulator()
        val targetPackage = context.packageName
        val blocked = nonessentialApps().first()
        val launcher = ComponentName(targetPackage, "uk.co.traynor.privategallery.launcher.NovaLauncherActivity")
        val service = ComponentName(context, SanctuaryAccessibilityService::class.java).flattenToString()
        val savedServices = shell("settings get secure enabled_accessibility_services").trim()
        val savedEnabled = shell("settings get secure accessibility_enabled").trim()
        val previousShield = SanctuaryLauncherPolicy.outsideProtectionEnabled(context)
        try {
            shell("pm grant $targetPackage android.permission.POST_NOTIFICATIONS")
            shell("appops set $targetPackage SYSTEM_ALERT_WINDOW allow")
            shell("appops set $targetPackage GET_USAGE_STATS allow")
            shell("settings put secure enabled_accessibility_services $service")
            shell("settings put secure accessibility_enabled 1")
            await("real Accessibility service connected") { SanctuaryGuardian.isAccessibilityConnected() }
            assertTrue(SanctuaryLauncherPolicy.setOutsideProtectionEnabled(context, true))

            withSession {
                ActivityScenario.launch(ComponentActivity::class.java).use { host ->
                    host.onActivity { assertTrue(SanctuaryGuardian.start(it)) }
                    await("Guardian is ready") { SanctuaryGuardian.monitorState == SanctuaryGuardian.MonitorState.MONITORING }
                    fun launchBlocked() {
                        val launch = shell("am start -W -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -p $blocked")
                        assertFalse(launch, "Error" in launch)
                        await("real overlay covers the external app without redirecting it") {
                            topIs(blocked) && overlayPresent() &&
                                SanctuaryGuardian.monitorState == SanctuaryGuardian.MonitorState.SHIELDING
                        }
                    }
                    launchBlocked()
                    val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setComponent(launcher)
                    ActivityScenario.launch<android.app.Activity>(home).use {
                        await("own Home removes the external overlay") {
                            SanctuaryLauncherPolicy.launcherVisible() && !overlayPresent()
                        }
                        launchBlocked()
                        assertTrue(store.saveAndroidApprovals(checkNotNull(store.current.value).id, SanctuaryMode.WORK, setOf(blocked)))
                        await("shared approval removes the overlay without another launch") {
                            topIs(blocked) && !overlayPresent()
                        }
                        assertTrue(store.saveAndroidApprovals(checkNotNull(store.current.value).id, SanctuaryMode.WORK, emptySet()))
                        await("shared revocation restores the overlay without another launch") {
                            topIs(blocked) && overlayPresent()
                        }
                    }
                }
            }
        } finally {
            SanctuaryLauncherPolicy.setOutsideProtectionEnabled(context, previousShield)
            restoreSecureSetting("enabled_accessibility_services", savedServices)
            restoreSecureSetting("accessibility_enabled", savedEnabled)
            shell("appops set $targetPackage SYSTEM_ALERT_WINDOW default")
            shell("appops set $targetPackage GET_USAGE_STATS default")
        }
    }

    private fun overlayPresent(): Boolean =
        shell("dumpsys window windows").lineSequence().any { "Window{" in it && "Nova Sanctuary}" in it }

    private fun restoreSecureSetting(key: String, value: String) {
        if (value == "null" || value.isEmpty()) shell("settings delete secure $key")
        else shell("settings put secure $key '$value'")
    }

    private fun withSession(test: () -> Unit) {
        assertNull("Isolated Sanctuary session required", store.refresh())
        val preset = store.preset(SanctuaryMode.WORK)
        try {
            assertTrue(store.savePreset(SanctuaryMode.WORK, preset.copy(androidApps = emptySet(), enableGuardian = true, allowExternalApps = true)))
            assertTrue(store.start(SanctuaryMode.WORK))
            test()
        } finally {
            store.end(SanctuaryStore.END_PHRASE)
            SanctuaryGuardian.stop(context)
            store.savePreset(SanctuaryMode.WORK, preset)
        }
    }

    private fun nonessentialApps(): List<String> {
        val installed = SanctuaryGuardian.installedApps(context)
        val essential = SanctuaryGuardian.essentialPackages(context)
        val preferred = listOf("com.google.android.calculator", "com.android.calculator2", "com.google.android.deskclock",
            "com.android.deskclock", "com.android.chrome", "com.android.camera2")
        return (preferred + installed.keys).distinct().filter { it in installed && it !in essential }
    }

    private fun disposableEmulator() {
        check(Build.VERSION.SDK_INT == 37 && BuildConfig.DEBUG && context.packageName.endsWith(".hiddenprototype") &&
            shell("getprop ro.kernel.qemu").trim() == "1")
    }

    private fun resumedActivities(): List<String> {
        val activity = Regex("(?:topResumedActivity|mResumedActivity)[=:].*?\\bu[0-9]+ ([^ }]+/[^ }]+)")
        return shell("dumpsys activity activities").lineSequence()
            .mapNotNull { activity.find(it)?.groupValues?.get(1) }.toList()
    }

    private fun topIs(packageName: String): Boolean = resumedActivities().any { it.startsWith("$packageName/") }

    private fun shell(command: String): String =
        ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).use {
            it.readBytes().decodeToString()
        }

    private fun await(label: String, condition: () -> Boolean) {
        val until = SystemClock.elapsedRealtime() + 15_000L
        while (SystemClock.elapsedRealtime() < until) {
            if (condition()) return
            SystemClock.sleep(150)
        }
        error(label)
    }
}
