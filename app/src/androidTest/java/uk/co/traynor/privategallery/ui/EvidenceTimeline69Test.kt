package uk.co.traynor.privategallery.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import uk.co.traynor.privategallery.core.backup.BackupFixture62
import uk.co.traynor.privategallery.core.evidence.EvidenceController
import uk.co.traynor.privategallery.core.ui.AppTheme
import uk.co.traynor.privategallery.core.vault.ImportResult
import uk.co.traynor.privategallery.core.vault.VaultImportSource

class EvidenceTimeline69Test {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun standaloneScreenshotWithoutMetadataOrOcrAppearsAndOpensOriginal() {
        val directory = BackupFixture62.temporary("timeline-69")
        val bitmap = Bitmap.createBitmap(80, 120, Bitmap.Config.ARGB_8888)
        val bytes = try {
            ByteArrayOutputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
                output.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
        try {
            BackupFixture62(directory).use { fixture ->
                val original = fixture.primary.importVerified(
                    VaultImportSource("Unassigned screenshot.png", "image/png", { bytes.inputStream() }),
                ) as ImportResult.Imported
                val controller = compose.runOnIdle { EvidenceController(fixture.context, fixture.access) }
                try {
                    compose.setContent {
                        CompositionLocalProvider(LocalContext provides fixture.context) {
                            PrivateGalleryTheme(AppTheme.LIGHT) { EvidenceLocker(controller, {}, {}, {}, {}, {}) }
                        }
                    }
                    compose.waitUntil(15_000) { controller.state.value.catalog != null && !controller.state.value.busy }
                    compose.runOnIdle { controller.register(original.item.id, extract = false) }
                    compose.waitUntil(15_000) {
                        val state = controller.state.value
                        !state.busy && state.catalog?.evidence()?.size == 1
                    }
                    val catalog = fixture.access.store.read()
                    assertTrue(catalog.incidents().isEmpty())
                    assertTrue(catalog.evidence().single().ocr().isEmpty())
                    compose.onNodeWithText("Timeline").performClick()
                    compose.evidenceNode(hasText("Capture date unknown")).assertIsDisplayed()
                    compose.evidenceNode(hasText("Correct capture date")).performClick()
                    compose.onNodeWithText("Choose date and time").performClick()
                    compose.onNodeWithText("Next").performClick()
                    compose.onNodeWithTag("media-hour").performTextReplacement("22")
                    compose.onNodeWithTag("media-minute").performTextReplacement("18")
                    compose.onNodeWithText("Save date").performClick()
                    compose.onNodeWithText("Save").performClick()
                    compose.waitUntil(15_000) { controller.state.value.captureTimes[catalog.evidence().single().id()] != null }
                    assertTrue(fixture.access.store.read().evidence().single().metadata().captureAt() == 0L)
                    compose.evidenceNode(hasText("Owner confirmed", substring = true)).assertIsDisplayed()
                    compose.evidenceNode(hasText("Open screenshot")).performClick()
                    compose.evidenceNode(hasText("Original screenshot")).performClick()
                    compose.waitUntil(15_000) {
                        compose.onAllNodesWithContentDescription("Original evidence content").fetchSemanticsNodes().isNotEmpty()
                    }
                    compose.evidenceNode(hasContentDescription("Original evidence content")).assertIsDisplayed()
                } finally {
                    compose.runOnIdle { controller.close() }
                }
            }
        } finally {
            bytes.fill(0)
            directory.deleteRecursively()
        }
    }
}
