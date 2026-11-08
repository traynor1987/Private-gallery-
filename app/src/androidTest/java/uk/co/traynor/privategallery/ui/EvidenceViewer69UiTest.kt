package uk.co.traynor.privategallery.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import java.io.ByteArrayOutputStream
import java.util.UUID
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import uk.co.traynor.privategallery.core.backup.BackupFixture62
import uk.co.traynor.privategallery.core.evidence.EvidenceCatalog
import uk.co.traynor.privategallery.core.evidence.EvidenceChronology68
import uk.co.traynor.privategallery.core.evidence.EvidenceController
import uk.co.traynor.privategallery.core.ui.AppTheme
import uk.co.traynor.privategallery.core.vault.ImportResult
import uk.co.traynor.privategallery.core.vault.VaultImportSource

class EvidenceViewer69UiTest {
    private val compose = createAndroidComposeRule<ComponentActivity>()
    private val viewport = BrowserViewportRule(fontScale = 1f) { method ->
        if (method.contains("expanded")) 840 to 900 else 360 to 850
    }

    @get:Rule val rules: TestRule = RuleChain.outerRule(viewport).around(compose)

    @Test fun coverShowsOriginalAndOwnerApprovalPreservesLegacyOcr() = exercise(approve = true)
    @Test fun expandedShowsConversationAndRemembersOwnerRejection() = exercise(approve = false)

    private fun exercise(approve: Boolean) {
        val directory = BackupFixture62.temporary("evidence-viewer-69")
        val png = screenshot()
        val raw = "8 November 2026\nCan  you call?Please confirm when you arrive. 21:46\nI will call you in five minutes 21:49\nMissed voice call\n21:55"
        try {
            BackupFixture62(directory).use { fixture ->
                val imported = fixture.primary.importVerified(VaultImportSource(
                    "Synthetic conversation.png", "image/png", { png.inputStream() },
                )) as ImportResult.Imported
                val controller = compose.runOnIdle { EvidenceController(fixture.context, fixture.access) }
                try {
                    compose.runOnIdle { controller.register(imported.item.id, extract = false) }
                    compose.waitUntil(20_000) {
                        val state = controller.state.value
                        !state.busy && state.catalog?.evidence()?.size == 1
                    }
                    val record = fixture.access.store.read().evidence().single()
                    val extraction = EvidenceCatalog.Ocr(UUID.randomUUID().toString(), 1L, "Synthetic legacy OCR fixture",
                        0, 1080, 1600, raw, 70f, emptyList(), emptyList(), 1, false)
                    fixture.access.store.update { it.saveOcr(record.id(), extraction, 1L) }
                    compose.runOnIdle { controller.refresh() }
                    compose.setContent {
                        CompositionLocalProvider(LocalContext provides fixture.context) {
                            PrivateGalleryTheme(AppTheme.LIGHT) { EvidenceLocker(controller, {}, {}, {}, {}, {}) }
                        }
                    }
                    compose.waitUntil(20_000) {
                        val state = controller.state.value
                        !state.busy && state.chronology?.messages?.any { it.kind == EvidenceChronology68.Kind.CALL } == true
                    }
                    compose.onNodeWithText("Evidence").performClick()
                    compose.evidenceNode(hasText("Synthetic conversation.png")).performClick()
                    compose.evidenceNode(hasText("Original screenshot") and hasClickAction()).performClick()
                    val preview = hasContentDescription("Original evidence content")
                    compose.waitUntil(15_000) { compose.onAllNodes(preview).fetchSemanticsNodes().isNotEmpty() }
                    compose.evidenceNode(preview).assertIsDisplayed()
                    compose.evidenceNode(hasText("Extracted conversation") and hasClickAction()).performClick()
                    compose.evidenceNode(hasText("Visible time · 21:46")).assertIsDisplayed()
                    compose.evidenceNode(hasText("Visible time · 21:49")).assertIsDisplayed()
                    compose.evidenceNode(hasText("Call shown in screenshot")).assertIsDisplayed()
                    compose.onNodeWithText("Synthetic legacy OCR fixture · 1 pages").assertDoesNotExist()
                    compose.evidenceNode(hasText("Compare suggested text") and hasClickAction()).performClick()
                    compose.evidenceNode(hasText("Suggested corrected text")).assertIsDisplayed()
                    compose.evidenceNode(hasText(if (approve) "Approve correction" else "Reject suggestion") and hasClickAction()).performClick()
                    compose.waitUntil(15_000) {
                        val state = controller.state.value
                        !state.busy && state.catalog?.audit()?.lastOrNull()?.action() ==
                            (if (approve) "REVISE_OCR" else "REJECT_OCR_SUGGESTION")
                    }
                    val stored = fixture.access.store.read().evidence().single()
                    assertEquals(raw, stored.ocr().single().text())
                    assertEquals(record.metadata(), stored.metadata())
                    val original = fixture.read(imported.item.id)
                    try { assertArrayEquals(png, original) } finally { original.fill(0) }
                    if (approve) {
                        assertTrue(stored.corrections().single().text().contains("Can you call? Please"))
                        assertEquals(extraction.id(), stored.corrections().single().ocrId())
                    } else {
                        assertTrue(stored.corrections().isEmpty())
                        compose.onNodeWithText("Timeline").performClick()
                        compose.onNodeWithText("Evidence").performClick()
                        compose.evidenceNode(hasText("Synthetic conversation.png")).performClick()
                        compose.evidenceNode(hasText("Advanced details") and hasClickAction()).performClick()
                        compose.evidenceNode(hasText("Synthetic legacy OCR fixture · 1 pages")).assertIsDisplayed()
                        assertEquals(1, fixture.access.store.read().rejectedSuggestions().size)
                        compose.onNodeWithText("Compare suggested text").assertDoesNotExist()
                    }
                } finally {
                    compose.runOnIdle { controller.close() }
                }
            }
        } finally {
            png.fill(0)
            directory.deleteRecursively()
        }
    }

    private fun screenshot(): ByteArray {
        val bitmap = Bitmap.createBitmap(1080, 1600, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            canvas.drawColor(Color.rgb(236, 229, 221))
            paint.color = Color.rgb(7, 94, 84)
            canvas.drawRect(0f, 0f, 1080f, 190f, paint)
            paint.color = Color.WHITE
            paint.textSize = 60f
            canvas.drawText("Alice", 84f, 126f, paint)
            fun bubble(left: Float, top: Float, right: Float, colour: Int, vararg rows: String) {
                paint.color = colour
                canvas.drawRoundRect(left, top, right, top + 120f + 63f * rows.size, 20f, 20f, paint)
                paint.color = Color.DKGRAY
                paint.textSize = 46f
                rows.forEachIndexed { index, text ->
                    canvas.drawText(text, left + 28f, top + 62f + index * 63f, paint)
                }
            }
            bubble(300f, 240f, 780f, Color.WHITE, "8 November 2026")
            bubble(40f, 500f, 910f, Color.WHITE, "Can  you call?Please confirm", "when you arrive.", "21:46")
            bubble(165f, 900f, 1030f, Color.rgb(220, 248, 198), "I will call you in five minutes", "21:49")
            bubble(40f, 1280f, 690f, Color.WHITE, "Missed voice call", "21:55")
            return ByteArrayOutputStream().use {
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
                it.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
    }
}
