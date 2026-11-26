package uk.co.traynor.privategallery.novaos2

import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import uk.co.traynor.privategallery.launcher.LauncherUiActions
import uk.co.traynor.privategallery.launcher.NovaLauncherState

class NovaSystemsFoundationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun menuOpensOnlyRegisteredSystemAndReturnsToMenu() {
        val feature = NovaSystemFeature(NovaSystemId.THEMES, "Appearance studio", "Display preferences") {
            Text("Studio content")
        }
        val actions = LauncherUiActions(save = { true }, launchApp = {}, launchShortcut = {}, openWorkspace = {},
            selectHome = {}, continueWithCurrentHome = {}, emergencyPhone = {}, reviewSanctuary = {},
            setOutsideProtection = {}, chooseWallpaper = {}, addWidget = {}, removeWidget = {},
            exportLayout = {}, importLayout = {}, renderWidget = { _, _ -> })
        compose.setContent {
            var selected by remember { mutableStateOf<NovaSystemId?>(null) }
            val available = setOf(feature.id)
            val platform = LauncherNovaSystemsActions({ true }, { false }, { false }, { false }, { false })
            MaterialTheme {
                NovaSystemsPanel(listOf(feature), selected, NovaSystemHost(
                    NovaLauncherState(), emptyList(), emptyMap(), actions, false, available,
                    { selected = null }, { id ->
                        (id in available).also { if (it) selected = id }
                    }, platform,
                ))
            }
        }
        compose.onNodeWithText("NovaOS systems").assertIsDisplayed()
        compose.onNodeWithText("Appearance studio").performClick()
        compose.onNodeWithText("Studio content").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("NovaOS systems").assertIsDisplayed()
    }

    @Test fun configIsEncryptedAndPrimaryTicketsAreSingleUse() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val marker = "synthetic-novaos2-fixture"
        val store = NovaSystemConfig(context, "foundation-test")
        try {
            store.write(marker)
            assertEquals(marker, NovaSystemConfig(context, "foundation-test").read())
            val ciphertext = File(context.noBackupFilesDir, "ai-provider-novaos2-foundation-test.enc")
            assertFalse(ciphertext.readBytes().decodeToString().contains(marker))
        } finally { store.clear() }
        assertNull(store.read())
        val ticket = NovaPrimaryPresentation.offer { _, _, _ -> }
        assertNull(NovaPrimaryPresentation.take(ticket + "x"))
        assertNotNull(NovaPrimaryPresentation.take(ticket))
        assertNull(NovaPrimaryPresentation.take(ticket))
    }

    @Test fun settingsActionsAreAllowlistedAndAppScoped() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val details = novaSystemSettingsIntent(context, Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        assertEquals("package:${context.packageName}", details?.dataString)
        assertEquals(context.packageName, novaSystemSettingsIntent(context, Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            ?.getStringExtra(Settings.EXTRA_APP_PACKAGE))
        assertNull(novaSystemSettingsIntent(context, "android.intent.action.VIEW"))
    }
}
