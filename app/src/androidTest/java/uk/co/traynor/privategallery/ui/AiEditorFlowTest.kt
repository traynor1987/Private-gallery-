package uk.co.traynor.privategallery.ui

import android.graphics.Bitmap
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import uk.co.traynor.privategallery.core.editor.*

class AiEditorFlowTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun consentPrecedesRemoteEditAndResultWaitsForSaveCopy() {
        val consent = AiConsentStore(compose.activity)
        consent.clear()
        fun image(colour: Int): ByteArray {
            val bitmap = Bitmap.createBitmap(64, 40, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(colour)
            return try { PhotoRenderer.encode(bitmap) } finally { bitmap.recycle() }
        }
        val source = image(Color.BLUE)
        var requests = 0
        var copies = 0
        val provider = object : AiImageEditProvider {
            override val id = "test-only"
            override val displayName = "Test provider"
            override val capabilities = setOf(AiCapability.GENERATIVE_EDIT)
            override suspend fun edit(request: AiEditRequest): ByteArray {
                assertEquals("Make it red", request.parameters.prompt)
                val sent = PhotoRenderer.render(request.image, PhotoEdit(), false)
                assertEquals(64, sent.width); assertEquals(Color.BLUE, sent.getPixel(0,0)); sent.recycle()
                requests++
                return image(Color.RED)
            }
        }
        compose.setContent { PrivateGalleryTheme { PhotoEditor("selected", { _, done -> done(Result.success(source.copyOf())) }, onCancel = {}, onSave = { _, _, _ -> fail("Remote result entered local save route") }, onSaveRemote = { output, cancelled, done ->
            assertFalse(cancelled()); val bitmap = PhotoRenderer.render(output, PhotoEdit(), false)
            assertEquals(Color.RED, bitmap.getPixel(0,0)); bitmap.recycle(); copies++; done(Result.success(Unit))
        }, provider = provider) } }
        compose.onNodeWithText("AI Edit").performClick()
        compose.onNodeWithText("Describe your change").performScrollTo().performTextInput("Make it red")
        compose.onNodeWithText("Generate").performScrollTo().performClick()
        compose.onNodeWithText("Remote AI processing").assertIsDisplayed()
        assertEquals(0, requests)
        compose.onNodeWithText("Continue").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("Preview your AI edit before saving.").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(1, requests); assertEquals(0, copies)
        compose.onNodeWithText("Save copy").performClick()
        compose.waitUntil(10000) { copies == 1 }
        source.fill(0); consent.clear()
    }
}
