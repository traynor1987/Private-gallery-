package uk.co.traynor.privategallery.ui

import android.graphics.Bitmap
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import uk.co.traynor.privategallery.core.editor.*
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/** Synthetic pixels only. Exercises selection, real rendering, consent and provider/save boundaries. */
class AiEditorProfessionalTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun image(colour: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(64, 40, Bitmap.Config.ARGB_8888).apply { eraseColor(colour) }
        return try { PhotoRenderer.encode(bitmap) } finally { bitmap.recycle() }
    }

    private fun waitFor(tag: String) {
        compose.waitUntil(10000) { compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun assertThumbnail(tag: String, red: Float, green: Float, blue: Float) {
        val pixels = compose.onNodeWithTag(tag, useUnmergedTree = true).captureToImage().toPixelMap()
        val pixel = pixels[pixels.width / 2, pixels.height / 2]
        assertEquals(red, pixel.red, .04f)
        assertEquals(green, pixel.green, .04f)
        assertEquals(blue, pixel.blue, .04f)
    }

    @Test fun bothSourcesCanBeReplacedWithoutChangingExactPromptAndResultCanBeDiscarded() {
        AiConsentStore(compose.activity).clear()
        val fixture = mapOf("original" to image(Color.BLUE), "green" to image(Color.GREEN), "red" to image(Color.RED))
        val exactPrompt = "  Put Photo 1 beside Photo 2.\nKeep both colours!  "
        val requests = AtomicInteger()
        val saves = AtomicInteger()
        val provider = object : AiImageEditProvider {
            override val id = "professional-combine-fixture"
            override val displayName = "Synthetic provider"
            override val capabilities = setOf(AiCapability.COMBINE_PHOTOS)
            override suspend fun edit(request: AiEditRequest): ByteArray {
                assertEquals(exactPrompt, request.parameters.prompt)
                assertFalse(request.parameters.enhancePrompt)
                val first = PhotoRenderer.render(request.image, PhotoEdit(), false)
                val second = PhotoRenderer.render(requireNotNull(request.referenceImage), PhotoEdit(), false)
                try { assertEquals(Color.RED, first.getPixel(0, 0)); assertEquals(Color.GREEN, second.getPixel(0, 0)) }
                finally { first.recycle(); second.recycle() }
                requests.incrementAndGet()
                return image(Color.YELLOW)
            }
        }
        compose.setContent { PhotoEditor("original", null, onCancel = {}, onSave = { _, _, _ -> fail("AI result used ordinary save") },
            loadForEditing = { id, cancelled, done -> assertFalse(cancelled()); done(Result.success(fixture.getValue(id).copyOf())) },
            onSaveAi = { bytes, provenance, cancelled, done ->
                assertFalse(cancelled()); assertEquals(provider.id, provenance.providerId)
                val result = PhotoRenderer.render(bytes, PhotoEdit(), false)
                try { assertEquals(Color.YELLOW, result.getPixel(0, 0)) } finally { result.recycle() }
                saves.incrementAndGet(); done(Result.success(Unit))
            }, provider = provider, combineCandidates = listOf("green", "red")) }
        compose.onNodeWithText("AI Edit").performClick()
        compose.onNodeWithText("Describe your change").performScrollTo().performTextInput(exactPrompt)
        compose.onNodeWithTag("ai-source-2").performScrollTo().performClick()
        waitFor("ai-candidate-thumbnail-green")
        assertThumbnail("ai-candidate-thumbnail-green", 0f, 1f, 0f)
        compose.onNodeWithTag("ai-candidate-green").performClick()
        waitFor("ai-source-thumbnail-2")
        compose.onNodeWithTag("ai-source-1").performScrollTo().performClick()
        waitFor("ai-candidate-thumbnail-red")
        compose.onNodeWithTag("ai-candidate-red").performClick()
        waitFor("ai-source-thumbnail-1")
        compose.onNodeWithTag("ai-source-1").performScrollTo()
        assertThumbnail("ai-source-thumbnail-1", 1f, 0f, 0f)
        assertThumbnail("ai-source-thumbnail-2", 0f, 1f, 0f)
        compose.onNodeWithContentDescription("Preview Photo 1").performClick()
        compose.onNodeWithTag("ai-large-preview").assertIsDisplayed()
        compose.onNodeWithText("Close preview").performClick()
        compose.onNodeWithText("Describe your change").performScrollTo().assertTextContains(exactPrompt)
        compose.onNodeWithText("Generate").performScrollTo().performClick()
        compose.onNodeWithText("Remote AI processing").assertIsDisplayed()
        assertEquals(0, requests.get())
        compose.onNodeWithText("Continue").performClick()
        waitFor("ai-result-preview")
        assertEquals(0, saves.get())
        compose.onNodeWithText("Discard").performScrollTo().performClick()
        compose.onNodeWithTag("ai-result-preview").assertDoesNotExist()
        compose.onNodeWithText("Describe your change").performScrollTo().assertTextContains(exactPrompt)
        compose.onNodeWithText("Generate").performScrollTo().performClick()
        waitFor("ai-result-preview")
        compose.onNodeWithText("Regenerate").performScrollTo().performClick()
        compose.waitUntil(10000) { requests.get() == 3 }
        waitFor("ai-result-preview")
        compose.onNodeWithText("Save copy").performClick()
        compose.waitUntil(10000) { saves.get() == 1 }
        fixture.values.forEach { it.fill(0) }; AiConsentStore(compose.activity).clear()
    }

    @Test fun lateAuthenticatedPickerLoadsAreWipedAfterBackgroundAndCannotRestorePreviews() {
        val source = image(Color.BLUE)
        val pending = CopyOnWriteArrayList<Pair<() -> Boolean, (Result<ByteArray>) -> Unit>>()
        val provider = object : AiImageEditProvider {
            override val id = "professional-lock-fixture"
            override val displayName = "Synthetic provider"
            override val capabilities = setOf(AiCapability.COMBINE_PHOTOS)
            override suspend fun edit(request: AiEditRequest): ByteArray = error("Must not generate")
        }
        compose.setContent { PhotoEditor("original", null, onCancel = {}, onSave = { _, _, _ -> fail("Must not save") },
            loadForEditing = { id, cancelled, done ->
                if (id == "original") done(Result.success(source.copyOf())) else pending.add(cancelled to done)
            }, provider = provider, combineCandidates = listOf("late")) }
        compose.onNodeWithText("AI Edit").performClick()
        compose.onNodeWithTag("ai-source-2").performScrollTo().performClick()
        compose.waitUntil(10000) { pending.isNotEmpty() }
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        pending.forEach { (cancelled, done) ->
            assertTrue(cancelled())
            val lateBytes = image(Color.RED)
            compose.runOnUiThread { done(Result.success(lateBytes)) }
            assertTrue(lateBytes.all { it == 0.toByte() })
        }
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.onNodeWithTag("ai-source-thumbnail-1", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("ai-candidate-thumbnail-late", useUnmergedTree = true).assertDoesNotExist()
        source.fill(0)
    }
    @Test fun sourceCardsAndActionsRemainReachableAcrossFoldViewports() {
        val original = image(Color.BLUE)
        val reference = image(Color.GREEN)
        var dimensions by mutableStateOf(360 to 760)
        var fontScale by mutableFloatStateOf(1f)
        val provider = object : AiImageEditProvider {
            override val id = "professional-viewport-fixture"
            override val displayName = "Synthetic provider"
            override val capabilities = setOf(AiCapability.COMBINE_PHOTOS)
            override suspend fun edit(request: AiEditRequest): ByteArray = error("Must not generate")
        }
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale)) {
                Box(Modifier.requiredSize(dimensions.first.dp, dimensions.second.dp)) {
                    PhotoEditor("original", null, onCancel = {}, onSave = { _, _, _ -> }, provider = provider,
                        loadForEditing = { id, _, done -> done(Result.success((if (id == "original") original else reference).copyOf())) },
                        combineCandidates = listOf("reference"))
                }
            }
        }
        compose.onNodeWithText("AI Edit").performClick()
        compose.onNodeWithTag("ai-source-2").performScrollTo().performClick()
        waitFor("ai-candidate-thumbnail-reference")
        compose.onNodeWithTag("ai-candidate-reference").performClick()
        waitFor("ai-source-thumbnail-2")
        for ((name, size) in listOf("outer-portrait" to (360 to 760), "inner-portrait" to (720 to 760),
            "outer-landscape" to (760 to 360), "inner-landscape" to (760 to 720))) {
            compose.runOnIdle { dimensions = size; fontScale = 1.3f }
            compose.onNodeWithText("Save copy").assertIsDisplayed()
            compose.onNodeWithTag("ai-source-1").performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("ai-source-2").assertIsDisplayed()
            val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
            val output = androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
                ?: java.io.File(instrumentation.targetContext.externalMediaDirs.first(), "additional_test_output").absolutePath
            val folder = java.io.File(output, "ai-editor-171").apply { mkdirs() }
            androidx.test.core.app.takeScreenshot().recycle()
            compose.runOnIdle { compose.activity.window.decorView.invalidate() }
            compose.mainClock.advanceTimeBy(64)
            compose.waitForIdle()
            androidx.test.core.app.takeScreenshot().let { bitmap ->
                java.io.File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
            compose.onNodeWithText("Describe your change").performScrollTo().assertIsDisplayed()
        }
        original.fill(0); reference.fill(0)
    }

}
