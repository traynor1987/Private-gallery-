package uk.co.traynor.privategallery.novaos2

import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import uk.co.traynor.privategallery.ui.BrowserViewportRule

class NovaPrivacyDashboardUiTest {
    private val compose = createAndroidComposeRule<ComponentActivity>()
    @get:Rule val rule = RuleChain.outerRule(BrowserViewportRule(fontScale = 1.15f) {
        if (it.startsWith("inner")) 820 to 920 else 375 to 850
    }).around(compose)

    @Test fun coverPermissionDenialUsesExistingSystemSettings() {
        var requestedSettings: String? = null
        compose.setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize().safeDrawingPadding()) {
                    NovaPrivacyContent(fixture(), false, null, {}, { requestedSettings = it }, {})
                }
            }
        }
        compose.onNodeWithTag("nova-privacy-dashboard").assertIsDisplayed()
        compose.onNodeWithText("Approximate permission granted").assertIsDisplayed()
        captureNovaSystem("privacy-cover")
        compose.onNodeWithText("Review Guardian notification access").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS, requestedSettings) }
        compose.onNodeWithText("Hidden Vault", substring = true).assertDoesNotExist()
    }

    @Test fun innerReadOnlyStatusKeepsUnknownSignalsExplicit() {
        compose.setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize().safeDrawingPadding()) { NovaPrivacyContent(fixture(), true, null, {}, {}, {}) }
            }
        }
        compose.onNodeWithText("NovaOS app permissions").assertIsDisplayed()
        compose.onNodeWithText("Protection").assertIsDisplayed()
        compose.onNodeWithText(NovaPrivacyVpn.UNAVAILABLE.label).assertIsDisplayed()
        compose.onNodeWithText("Android provides no device-wide Tor connection status.").assertIsDisplayed()
        captureNovaSystem("privacy-inner")
    }

    @Test fun coverDiagnosticsCopiesOnlyPublicStatusAndMasksClipboardPreview() {
        val sample = fixture().copy(sanctuary = "private session detail", backupSchedule = "private archive detail")
        compose.setContent { MaterialTheme { NovaPrivacyContent(sample, false, null, {}, {}, {}) } }
        compose.onNodeWithText("Copy privacy diagnostics").performScrollTo().performClick()
        compose.onNodeWithText("Privacy status copied.").assertExists()
        compose.runOnIdle {
            val clipboard = compose.activity.getSystemService(android.content.ClipboardManager::class.java)
            val clip = checkNotNull(clipboard.primaryClip)
            val report = clip.getItemAt(0).text.toString()
            assertEquals(sample.diagnostics(), report)
            org.junit.Assert.assertFalse(report.contains("private"))
            org.junit.Assert.assertTrue(clip.description.extras?.getBoolean(android.content.ClipDescription.EXTRA_IS_SENSITIVE) == true)
        }
    }

    private fun fixture() = NovaPrivacySnapshot(NovaPrivacyAccess.OFF, NovaPrivacyAccess.OFF, NovaPrivacyAccess.OFF,
        NovaPrivacyLocation.APPROXIMATE, NovaPrivacyAccess.ALLOWED, NovaPrivacyGuardian.ACCESS_OFF,
        NovaPrivacyAccess.OFF, NovaPrivacyAccess.OFF, NovaPrivacyAccess.OFF, NovaPrivacyVpn.UNAVAILABLE,
        "No active Sanctuary session", NovaPrivacyAccess.UNAVAILABLE, "Scheduling off", null)
}
