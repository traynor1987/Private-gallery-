package uk.co.traynor.privategallery.novaos2

import android.Manifest
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.lifecycle.Lifecycle
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import uk.co.traynor.privategallery.intelligence.IntelligenceGuardian
import uk.co.traynor.privategallery.launcher.LauncherUiActions
import uk.co.traynor.privategallery.launcher.NovaLauncherState
import uk.co.traynor.privategallery.ui.BrowserViewportRule

class ControlCentreAndroid17Test {
    private val compose = createAndroidComposeRule<ComponentActivity>()

    @get:Rule val rule = RuleChain.outerRule(BrowserViewportRule {
        if (it.startsWith("inner")) 820 to 920 else 375 to 850
    }).around(compose)

    private val feature = NovaSystemFeature(NovaSystemId.CONTROL, "Control Centre", "Device controls") {
        NovaControlCentre(it)
    }

    @Test fun coverArrangementIsEncryptedAndInnerStaysIndependent() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".hiddenprototype"))
        val store = NovaSystemConfig(context, "control-centre")
        val saved = store.read()
        val wide = mutableStateOf(false)
        try {
            store.clear()
            compose.setContent { NovaSystemsPanel(listOf(feature), feature.id, host(wide.value)) }
            compose.onNodeWithTag("control-centre-cover").assertIsDisplayed()
            compose.waitUntil(10_000) { compose.onAllNodes(
                androidx.compose.ui.test.hasTestTag("control-tile-internet")).fetchSemanticsNodes().isNotEmpty() }
            captureNovaSystem("control-cover")
            compose.onNodeWithText("Arrange quick actions").performScrollTo().performClick()
            compose.onNodeWithTag("control-arrange-internet").performScrollTo().performClick()
            compose.waitUntil(10_000) {
                ControlTile.INTERNET in ControlCentreLayout.decode(store.read()).cover.hidden
            }
            val encrypted = File(context.noBackupFilesDir, "ai-provider-novaos2-control-centre.enc")
            assertTrue(encrypted.isFile)
            assertFalse(encrypted.readBytes().decodeToString().contains("control-centre-v1"))
            compose.onNodeWithText("Done").performScrollTo().performClick()
            compose.onNodeWithTag("control-tile-internet").assertDoesNotExist()
            compose.runOnIdle { wide.value = true }
            compose.onNodeWithTag("control-centre-inner").assertIsDisplayed()
            compose.onNodeWithTag("control-tile-internet").performScrollTo().assertIsDisplayed()
            assertEquals(emptySet<ControlTile>(), ControlCentreLayout.decode(store.read()).inner.hidden)
        } finally {
            if (saved == null) store.clear() else store.write(saved)
        }
    }

    @Test fun innerGuardianSummaryOnlyExpandsCategoryExplanations() {
        compose.setContent {
            NovaSystemSection("Notification categories") {
                ControlNoticeCards(IntelligenceGuardian(8, 4, 2, 1, 7, 8, 3, true))
            }
        }
        compose.onNodeWithText("Held · 2").performClick()
        compose.onNodeWithText("Guardian keeps held notifications in protected review.").assertIsDisplayed()
        compose.onNodeWithText("Low concern · 4").performClick()
        compose.onNodeWithText("Guardian keeps held notifications in protected review.").assertDoesNotExist()
        compose.onNodeWithText("Guardian assessed these alerts as low concern.").assertIsDisplayed()
    }

    @Test fun innerDeviceAndNotificationControlsAreReachableInDarkTheme() {
        compose.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                NovaSystemsPanel(listOf(feature), feature.id, host(true))
            }
        }
        compose.onNodeWithTag("control-centre-inner").assertIsDisplayed()
        compose.waitUntil(10_000) { compose.onAllNodes(
            androidx.compose.ui.test.hasTestTag("control-tile-internet")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Notifications").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Media").performScrollTo().assertIsDisplayed()
        captureNovaSystem("control-inner")
    }

    @Test fun innerOptedInMediaPausesWithoutLeavingResumedState() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".hiddenprototype"))
        val store = NovaSystemConfig(context, "control-centre")
        val previous = store.read()
        val topResumed = mutableStateOf(true)
        val lifecycle = compose.activity.lifecycle
        val paused = compose.onNodeWithText("Media controls pause when NovaOS is out of focus.")
        try {
            store.write(ControlCentreLayout(mediaEnabled = true).encode())
            compose.setContent {
                NovaSystemsPanel(listOf(feature), feature.id, host(true) { topResumed.value })
            }
            compose.waitUntil(10_000) { compose.onAllNodes(
                androidx.compose.ui.test.hasTestTag("control-tile-internet")).fetchSemanticsNodes().isNotEmpty() }
            paused.assertDoesNotExist()
            compose.runOnIdle {
                assertEquals(Lifecycle.State.RESUMED, lifecycle.currentState)
                topResumed.value = false
            }
            paused.performScrollTo().assertIsDisplayed()
            compose.runOnIdle {
                assertEquals(Lifecycle.State.RESUMED, lifecycle.currentState)
                topResumed.value = true
            }
            paused.assertDoesNotExist()
        } finally {
            if (previous == null) store.clear() else store.write(previous)
        }
    }

    @Test fun coverCollapsePreservesEssentialActionsAndSurvivesInnerTransition() {
        val wide = mutableStateOf(false)
        compose.setContent { NovaSystemsPanel(listOf(feature), feature.id, host(wide.value)) }
        val toggle = compose.onNodeWithTag("control-panel-expansion")
        val internet = compose.onNodeWithTag("control-tile-internet")
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasTestTag("control-tile-internet")).fetchSemanticsNodes().isNotEmpty()
        }

        toggle.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Expanded")).performClick()
        val collapsed = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Collapsed")
        toggle.assert(collapsed)
        internet.performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Media").assertDoesNotExist()
        compose.onNodeWithText("Notifications").assertDoesNotExist()
        compose.onNodeWithTag("control-window-brightness").assertDoesNotExist()
        captureNovaSystem("control-cover-collapsed")

        compose.runOnIdle { wide.value = true }
        compose.onNodeWithTag("control-centre-inner").assertIsDisplayed()
        toggle.assert(collapsed).performClick()
        compose.onNodeWithText("Notifications").performScrollTo().assertIsDisplayed()
    }

    @Test fun coverPrivacyReportsNovaOsGrantsAndUsesNativeFallback() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val actions = mutableListOf<String>()
        compose.setContent {
            NovaSystemsPanel(listOf(feature), feature.id, host(false, openSettings = { action ->
                actions += action
                action == Settings.ACTION_APPLICATION_DETAILS_SETTINGS
            }))
        }
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasTestTag("control-tile-privacy")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("control-tile-privacy").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(listOf(Settings.ACTION_PRIVACY_SETTINGS, Settings.ACTION_APPLICATION_DETAILS_SETTINGS), actions)
        }
        compose.onNodeWithTag("control-tile-working").assertDoesNotExist()
        compose.onNodeWithText("NovaOS permission grants").performScrollTo().assertIsDisplayed()
        val camera = if (context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            "Allowed"
        } else {
            "Denied"
        }
        compose.onNodeWithText("Camera · $camera").performScrollTo().assertIsDisplayed()
    }

    @Test fun innerBrightnessRestoresWhenTopResumeIsLost() {
        val activity = compose.activity
        val window = activity.window
        val topResumed = mutableStateOf(true)
        val original = window.attributes.screenBrightness
        val previous = .25f
        val requested = .65f
        try {
            compose.runOnIdle { window.attributes = window.attributes.apply { screenBrightness = previous } }
            compose.setContent { NovaSystemsPanel(listOf(feature), feature.id, host(true) { topResumed.value }) }
            compose.waitUntil(10_000) {
                compose.onAllNodes(hasTestTag("control-window-brightness")).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("control-window-brightness").performScrollTo()
                .performSemanticsAction(SemanticsActions.SetProgress) { it(requested) }
            compose.runOnIdle {
                assertEquals(requested, window.attributes.screenBrightness, .01f)
                assertEquals(Lifecycle.State.RESUMED, activity.lifecycle.currentState)
                topResumed.value = false
            }
            compose.runOnIdle {
                assertEquals(Lifecycle.State.RESUMED, activity.lifecycle.currentState)
                assertEquals(previous, window.attributes.screenBrightness, .01f)
            }
        } finally {
            compose.runOnIdle { window.attributes = window.attributes.apply { screenBrightness = original } }
        }
    }

    private fun host(
        wide: Boolean,
        openSettings: (String) -> Boolean = { false },
        foregroundAllowed: () -> Boolean = { true },
    ) = NovaSystemHost(
        state = NovaLauncherState(),
        apps = emptyList(),
        decisions = emptyMap(),
        actions = LauncherUiActions(
            save = { true }, launchApp = {}, launchShortcut = {}, openWorkspace = {}, selectHome = {},
            continueWithCurrentHome = {}, emergencyPhone = {}, reviewSanctuary = {},
            setOutsideProtection = {}, chooseWallpaper = {}, addWidget = {}, removeWidget = {},
            exportLayout = {}, importLayout = {}, renderWidget = { _, _ -> },
        ),
        wide = wide,
        available = setOf(NovaSystemId.CONTROL),
        onBack = {},
        open = { false },
        platform = LauncherNovaSystemsActions(
            foregroundAllowed = foregroundAllowed, launch = { false }, openCoreApp = { false },
            openSystemSettings = openSettings, openUpdates = { false },
        ),
    )
}
