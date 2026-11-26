package uk.co.traynor.privategallery.novaos2

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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

    @Test fun coverArrangementIsEncryptedAndInnerStaysIndependent() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".hiddenprototype"))
        val store = NovaSystemConfig(context, "control-centre")
        val saved = store.read()
        val wide = mutableStateOf(false)
        try {
            store.clear()
            compose.setContent { NovaControlCentre(host(wide.value)) }
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
            ControlNoticeCards(IntelligenceGuardian(8, 4, 2, 1, 7, 8, 3, true))
        }
        compose.onNodeWithText("Held · 2").performClick()
        compose.onNodeWithText("Guardian keeps held notifications in protected review.").assertIsDisplayed()
        compose.onNodeWithText("Low concern · 4").performClick()
        compose.onNodeWithText("Guardian keeps held notifications in protected review.").assertDoesNotExist()
        compose.onNodeWithText("Guardian assessed these alerts as low concern.").assertIsDisplayed()
    }

    @Test fun innerDeviceAndNotificationControlsAreReachableInDarkTheme() {
        compose.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) { NovaControlCentre(host(true)) }
        }
        compose.onNodeWithTag("control-centre-inner").assertIsDisplayed()
        compose.waitUntil(10_000) { compose.onAllNodes(
            androidx.compose.ui.test.hasTestTag("control-tile-internet")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Notifications").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Media").performScrollTo().assertIsDisplayed()
        captureNovaSystem("control-inner")
    }

    private fun host(wide: Boolean) = NovaSystemHost(
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
            foregroundAllowed = { true }, launch = { false }, openCoreApp = { false },
            openSystemSettings = { false }, openUpdates = { false },
        ),
    )
}
