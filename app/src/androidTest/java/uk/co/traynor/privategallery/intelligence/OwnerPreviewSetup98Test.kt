package uk.co.traynor.privategallery.intelligence

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.os.PersistableBundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import uk.co.traynor.privategallery.launcher.GuardianTheme
import uk.co.traynor.privategallery.ui.BrowserViewportRule

/** All clipboard values, checks and images use synthetic fixtures in the debug package. */
class OwnerPreviewSetup98Test {
    private val compose = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rule = RuleChain.outerRule(BrowserViewportRule {
        if (it.startsWith("inner")) 820 to 920 else 375 to 850
    }).around(compose)

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context get() = instrumentation.targetContext
    private val token = "0123abcdeffedcba".repeat(4)
    private val syntheticTime = Instant.parse("2026-11-25T20:30:00Z")

    @Test fun coverPasteSetupChecksAndRestoresEncryptedConfiguration() = withCleanPreview { store ->
        val attempts = AtomicInteger()
        val check: (OwnerPreviewConnection, () -> Boolean) -> OwnerPreviewDiagnostics = { connection, current ->
            assertTrue(current())
            assertTrue(connection.token == token)
            assertEquals(OWNER_PREVIEW_ORIGIN, connection.configuration().origin)
            attempts.incrementAndGet()
            disarmed()
        }
        show(check)
        compose.onNodeWithText("Set up Owner Preview").performClick()
        val save = compose.onNodeWithText("Save and enable")
        save.assertIsNotEnabled()
        paste("not a preview token")
        save.assertIsNotEnabled()
        compose.onNodeWithText(OwnerPreviewTokenValidation.INVALID_CHARACTERS.message).assertIsDisplayed()
        compose.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        save.assertIsNotEnabled()
        compose.onNodeWithText(OwnerPreviewTokenValidation.MISSING.message).assertIsDisplayed()
        paste(" ".repeat(20) + "Preview token for the app only: ${token.uppercase()}\r\n")
        save.assertIsEnabled().assertIsDisplayed()
        compose.onNodeWithText(OwnerPreviewTokenValidation.VALID.message).assertIsDisplayed()

        val field = compose.onNodeWithTag("owner-preview-token").fetchSemanticsNode().config
        val displayed = field[SemanticsProperties.EditableText].text
        assertTrue(field.contains(SemanticsProperties.Password))
        assertTrue(displayed.isNotBlank() && displayed.all { it == '•' })
        capture("owner-preview-cover-setup")

        save.performClick()
        awaitDisarmed()
        assertEquals(1, attempts.get())
        compose.onNodeWithText("Test Guardian second opinion").assertIsNotEnabled()
        assertTrue(store.read()?.token == token)
        val encrypted = File(context.noBackupFilesDir, "ai-provider-nova-brain-owner-preview.enc")
        assertTrue(encrypted.isFile)
        assertFalse(encrypted.readBytes().decodeToString().contains(token))

        compose.activityRule.scenario.recreate()
        show(check)
        compose.waitUntil(5_000) { compose.onNodeWithText("Check preview connection").isDisplayed() }
        compose.onNodeWithText("Check preview connection").performClick()
        awaitDisarmed()
        assertEquals(2, attempts.get())
    }

    @Test fun innerSetupShowsSafeDiagnostics() = withCleanPreview {
        show { _, current -> assertTrue(current()); disarmed() }
        compose.onNodeWithText("Set up Owner Preview").performClick()
        paste(token)
        compose.onNodeWithText("Save and enable").assertIsDisplayed().performClick()
        awaitDisarmed()
        compose.onNodeWithText("Connection diagnostics").performScrollTo().performClick()
        val report = compose.onNodeWithTag("nova-owner-preview-diagnostics")
        report.performScrollTo().assertIsDisplayed()
        val text = report.fetchSemanticsNode().config[SemanticsProperties.Text].single().text
        assertFalse(text.contains(token))
        for (entry in listOf(OWNER_PREVIEW_ORIGIN, "HTTP: 200", "TLS: Verified", "Authenticated: Yes",
            "Preview state: Disarmed", "Paid analysis: Disabled", "Expiration at last check: Active")) {
            assertTrue("Missing diagnostics field: $entry", text.contains(entry))
        }
        capture("owner-preview-inner-diagnostics")
        compose.onNodeWithText("Copy diagnostics").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(text, clipboard().primaryClip?.getItemAt(0)?.text?.toString())
        }
    }

    @Test fun coverCheckExplainsTransportFailuresAndKeepsPaidControlsDisabled() = withCleanPreview { store ->
        store.enable(token)
        val failures = listOf(OwnerPreviewFailure.SERVER_UNAVAILABLE, OwnerPreviewFailure.OFFLINE,
            OwnerPreviewFailure.TLS, OwnerPreviewFailure.AUTHENTICATION)
        val attempts = AtomicInteger()
        show { _, current ->
            assertTrue(current())
            OwnerPreviewDiagnostics(syntheticTime, failure = failures[attempts.getAndIncrement()])
        }
        val check = compose.onNodeWithText("Check preview connection")
        compose.waitUntil(5_000) { check.isDisplayed() }
        for (failure in failures) {
            check.performClick()
            compose.waitUntil(5_000) { compose.onNodeWithText(failure.message).isDisplayed() }
            compose.onNodeWithText("Test Ask Nova").assertIsNotEnabled()
            compose.onNodeWithText("Test Guardian second opinion").assertIsNotEnabled()
        }
    }

    private fun show(check: (OwnerPreviewConnection, () -> Boolean) -> OwnerPreviewDiagnostics) {
        compose.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                GuardianTheme {
                    Surface(Modifier.fillMaxSize()) {
                        Box(Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(12.dp)) {
                            OwnerPreviewSettings(canUsePreview = { true }, checkPreview = check)
                        }
                    }
                }
            }
        }
    }

    private fun paste(text: String) {
        val clip = ClipData.newPlainText("Synthetic fixture", text)
        clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
        compose.runOnIdle { clipboard().setPrimaryClip(clip) }
        compose.onNodeWithText("Paste token").performClick()
    }

    private fun clipboard() = context.getSystemService(ClipboardManager::class.java)

    private fun awaitDisarmed() = compose.waitUntil(5_000) {
        compose.onNodeWithText("Authenticated · read-only preview, paid analysis off").isDisplayed()
    }

    private fun disarmed() = OwnerPreviewDiagnostics(
        syntheticTime, OwnerPreviewStatus(false, 2, false, 250_000, 250_000), 200, OwnerPreviewTls.VERIFIED,
        expiration = OwnerPreviewExpiration.ACTIVE, expiresAt = syntheticTime.plusSeconds(1800),
    )

    private fun capture(name: String) {
        compose.waitForIdle()
        val directory = checkNotNull(context.getExternalFilesDir("intelligence"))
        val image = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        try {
            File(directory, "$name.png").outputStream().use {
                check(image.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        } finally {
            image.recycle()
        }
    }

    private fun withCleanPreview(block: (OwnerPreviewStore) -> Unit) {
        check(context.packageName.endsWith(".hiddenprototype"))
        val store = OwnerPreviewStore(context)
        check(!store.isEnabled())
        try {
            block(store)
        } finally {
            store.disable()
            compose.runOnIdle { clipboard().clearPrimaryClip() }
        }
    }
}
