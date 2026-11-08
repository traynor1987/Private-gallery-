package uk.co.traynor.privategallery.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayOutputStream
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import uk.co.traynor.privategallery.core.backup.BackupFixture62
import uk.co.traynor.privategallery.core.evidence.EvidenceController
import uk.co.traynor.privategallery.core.ui.AppTheme
import uk.co.traynor.privategallery.core.vault.ImportResult
import uk.co.traynor.privategallery.core.vault.VaultImportSource

class EvidenceLocker68UiTest {
    private val compose = createAndroidComposeRule<ComponentActivity>()
    private val viewport = BrowserViewportRule(fontScale = 1f) { name ->
        if (name.contains("expanded")) 840 to 900 else 360 to 850
    }

    @get:Rule val rules: TestRule = RuleChain.outerRule(viewport).around(compose)

    @Test fun coverConnectsCollectionRunsLocalOcrAndShowsTimeline() = exercise("folded")
    @Test fun expandedConnectsCollectionRunsLocalOcrAndShowsTimeline() = exercise("unfolded")

    private fun exercise(layout: String) {
        val directory = BackupFixture62.temporary("evidence-68")
        val screenshot = screenshot()
        try {
            BackupFixture62(directory).use { fixture ->
                val original = fixture.primary.importVerified(VaultImportSource(
                    "Synthetic WhatsApp screenshot.png", "image/png", { screenshot.inputStream() },
                )) as ImportResult.Imported
                val collection = fixture.primary.createCollection("Evidence Collection")
                fixture.primary.addItemsToCollection(collection.id, listOf(original.item.id))
                val controller = compose.runOnIdle { EvidenceController(fixture.context, fixture.access) }
                try {
                    compose.setContent {
                        CompositionLocalProvider(LocalContext provides fixture.context) {
                            PrivateGalleryTheme(AppTheme.LIGHT) { EvidenceLocker(controller, {}, {}, {}, {}, {}) }
                        }
                    }
                    compose.waitUntil(15_000) { controller.state.value.collections.any { it.id == collection.id } && !controller.state.value.busy }
                    compose.evidenceNode(hasText("Connect Vault collection")).performClick()
                    compose.evidenceNode(hasText("Connect") and hasClickAction()).performClick()
                    compose.waitUntil(60_000) {
                        val state = controller.state.value
                        !state.syncingCollections && !state.busy &&
                            state.chronology?.messages?.any { it.visibleClock.contains("21:46") } == true
                    }
                    val registered = fixture.access.store.read().evidence().single()
                    assertEquals(original.item.id, registered.sourceId())
                    assertEquals("Evidence Collection", registered.metadata().embedded()["sourceCollectionName"])
                    assertEquals(1240, registered.metadata().width())
                    assertEquals(0L, registered.metadata().captureAt())
                    assertTrue(registered.ocr().single().text().contains("21:46"))
                    compose.onNodeWithText("Done").performClick()
                    saveScreenshot("$layout-home")
                    compose.onNodeWithText("Timeline").performClick()
                    compose.evidenceNode(hasText("Visible message time 21:46", substring = true)).assertIsDisplayed()
                    saveScreenshot("$layout-timeline")
                } finally {
                    compose.runOnIdle { controller.close() }
                }
            }
        } finally {
            screenshot.fill(0)
            directory.deleteRecursively()
        }
    }

    private fun screenshot(): ByteArray {
        val bitmap = Bitmap.createBitmap(1240, 500, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = 56f
                typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            }
            canvas.drawText("31 October 2026", 80f, 115f, paint)
            canvas.drawText("Alice: Please reply soon 21:46", 80f, 235f, paint)
            canvas.drawText("Alice: Did you see this 21:47", 80f, 355f, paint)
            return ByteArrayOutputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
                output.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun saveScreenshot(name: String) {
        val arguments = InstrumentationRegistry.getArguments()
        val output = arguments.getString("additionalTestOutputDir") ?: return
        val directory = File(output, "evidence-locker-68").apply { mkdirs() }
        val screenshot = compose.onNodeWithTag("evidence-locker-68").captureToImage().asAndroidBitmap()
        File(directory, "$name.png").outputStream().use {
            check(screenshot.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }
}
