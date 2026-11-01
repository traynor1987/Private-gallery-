package uk.co.traynor.privategallery.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import uk.co.traynor.privategallery.core.editor.*

class EditorWorkspacePresentationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun toolsKeepPreviewAndProviderDisclosureReachableAcrossFoldSizes() {
        val bitmap = Bitmap.createBitmap(160, 100, Bitmap.Config.ARGB_8888)
        val source = try { PhotoRenderer.encode(bitmap) } finally { bitmap.recycle() }
        var size by mutableStateOf(360 to 760)
        compose.setContent { CompositionLocalProvider(LocalDensity provides Density(1f, 1.3f)) {
            Box(Modifier.requiredSize(size.first.dp, size.second.dp)) {
                PhotoEditor("source", { _, done -> done(Result.success(source.copyOf())) },
                    onCancel = {}, onSave = { _, _, _ -> }, provider = null)
            }
        } }
        try {
            compose.waitUntil(10000) { runCatching { compose.onNodeWithText("Save copy").assertIsEnabled() }.isSuccess }
            for (dimensions in listOf(360 to 760, 720 to 760, 760 to 360, 760 to 720)) {
                compose.runOnIdle { size = dimensions }
                for (tool in listOf("Crop", "Adjust", "AI Edit")) {
                    compose.onNodeWithText(tool).performClick()
                    compose.onNodeWithText("Save copy").assertIsDisplayed()
                    if (tool == "Crop") {
                        for (preset in listOf("Free", "Original", "1:1", "4:3", "16:9")) {
                            compose.onNodeWithTag("editor-crop-$preset").performScrollTo().assertIsDisplayed().performClick().assertIsSelected()
                        }
                    }
                    val canvas = compose.onNodeWithTag("editor-canvas").fetchSemanticsNode().boundsInRoot
                    assertTrue("Preview must retain working space for $dimensions / $tool", canvas.width >= 120 && canvas.height >= 100)
                }
                compose.onNodeWithTag("editor-provider-disclosure").performScrollTo().assertIsDisplayed().performClick()
                compose.onNodeWithTag("editor-provider-settings").performScrollTo().assertIsDisplayed()
                compose.onNodeWithTag("editor-provider-disclosure").performScrollTo().performClick()
                compose.onNodeWithTag("editor-provider-settings").assertDoesNotExist()
            }
        } finally { source.fill(0) }
    }
    @Test fun configuredAiSourceCanBeReplacedWithoutSendingIt() {
        val bitmap = Bitmap.createBitmap(160, 100, Bitmap.Config.ARGB_8888)
        val source = try { PhotoRenderer.encode(bitmap) } finally { bitmap.recycle() }
        var transmissions = 0
        val provider = object : AiImageEditProvider {
            override val id = "workspace-source-test"
            override val displayName = "Test provider"
            override val capabilities = setOf(AiCapability.GENERATIVE_EDIT)
            override suspend fun edit(request: AiEditRequest): ByteArray { transmissions++; error("No transmission expected") }
        }
        compose.setContent { PrivateGalleryTheme {
            PhotoEditor("source", { _, done -> done(Result.success(source.copyOf())) },
                onCancel = {}, onSave = { _, _, _ -> }, provider = provider,
                combineCandidates = listOf("alternative"))
        } }
        try {
            compose.onNodeWithText("AI Edit").performClick()
            compose.onNodeWithText("Replace source").performScrollTo().assertIsDisplayed().performClick()
            compose.onNodeWithTag("ai-source-picker-grid").assertIsDisplayed()
            compose.runOnIdle { assertTrue(transmissions == 0) }
        } finally { source.fill(0) }
    }

    @Test fun combineSetupRetainsLargeWorkingCanvas() {
        val bitmap = Bitmap.createBitmap(160, 100, Bitmap.Config.ARGB_8888)
        val source = try { PhotoRenderer.encode(bitmap) } finally { bitmap.recycle() }
        val provider = object : AiImageEditProvider {
            override val id = "workspace-combine-test"
            override val displayName = "Test provider"
            override val capabilities = setOf(AiCapability.COMBINE_PHOTOS)
            override suspend fun edit(request: AiEditRequest): ByteArray = error("No transmission expected")
        }
        compose.setContent { PrivateGalleryTheme {
            PhotoEditor("source", { _, done -> done(Result.success(source.copyOf())) },
                onCancel = {}, onSave = { _, _, _ -> }, provider = provider,
                combineCandidates = listOf("alternative"))
        } }
        try {
            compose.onNodeWithText("AI Edit").performClick()
            compose.onNodeWithTag("editor-canvas").assertIsDisplayed()
            val canvas = compose.onNodeWithTag("editor-canvas").fetchSemanticsNode().boundsInRoot
            assertTrue(canvas.width >= 120 && canvas.height >= 100)
            compose.onNodeWithTag("ai-source-2").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Save copy").assertIsDisplayed()
        } finally { source.fill(0) }
    }

    @Test fun editorControlsInheritLightAndDarkThemeAtCompactLargeFont() {
        val bitmap = Bitmap.createBitmap(160, 100, Bitmap.Config.ARGB_8888)
        val source = try { PhotoRenderer.encode(bitmap) } finally { bitmap.recycle() }
        var theme by mutableStateOf(uk.co.traynor.privategallery.core.ui.AppTheme.LIGHT)
        var expected = Color.Unspecified
        compose.setContent { CompositionLocalProvider(LocalDensity provides Density(1f, 1.8f)) {
            PrivateGalleryTheme(theme) {
                expected = MaterialTheme.colorScheme.surfaceContainerLow
                Box(Modifier.requiredSize(328.dp, 760.dp)) {
                    PhotoEditor("source", { _, done -> done(Result.success(source.copyOf())) },
                        onCancel = {}, onSave = { _, _, _ -> })
                }
            }
        } }
        try {
            for (selected in listOf(uk.co.traynor.privategallery.core.ui.AppTheme.LIGHT, uk.co.traynor.privategallery.core.ui.AppTheme.DARK)) {
                compose.runOnIdle { theme = selected }
                compose.onNodeWithText("Save copy").assertIsDisplayed()
                compose.onNodeWithText("Cancel").assertIsDisplayed()
                compose.onNodeWithContentDescription("Back").assertIsDisplayed()
                val image = compose.onNodeWithTag("editor-context-panel").captureToImage().toPixelMap()
                val actual = image[image.width - 2, image.height - 2]
                org.junit.Assert.assertEquals(expected.toArgb(), actual.toArgb())
            }
        } finally { source.fill(0) }
    }

    @Test fun ownerActionsWipeSourceBeforeDispatchAndRespectExistingCapturePolicy() {
        var action by mutableStateOf("Nova Home")
        var buffer = byteArrayOf()
        val dispatched = mutableListOf<String>()
        fun dispatch(name: String) {
            assertTrue("Plaintext must be wiped before owner navigation", buffer.isNotEmpty() && buffer.all { it == 0.toByte() })
            dispatched += name
        }
        compose.setContent { key(action) {
            val bitmap = Bitmap.createBitmap(160, 100, Bitmap.Config.ARGB_8888)
            buffer = try { PhotoRenderer.encode(bitmap) } finally { bitmap.recycle() }
            CompositionLocalProvider(LocalNovaSecurityActions provides NovaSecurityActions(
                { dispatch("Nova Home") }, { dispatch("Lock Space") }, { dispatch("Panic Exit") }, "Hidden Space")) {
                PrivateGalleryTheme { Box(Modifier.requiredSize(328.dp, 760.dp)) {
                    PhotoEditor("source", { _, done -> done(Result.success(buffer)) },
                        onCancel = {}, onSave = { _, _, _ -> })
                } }
            }
        } }
        try {
            for ((index, name) in listOf("Nova Home", "Lock Space", "Panic Exit").withIndex()) {
                compose.runOnIdle {
                    action = name
                    if (index % 2 == 0) compose.activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
                    else compose.activity.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
                }
                compose.waitUntil(10000) { runCatching { compose.onNodeWithText("Save copy").assertIsEnabled() }.isSuccess }
                compose.runOnIdle {
                    val secure = compose.activity.window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_SECURE != 0
                    org.junit.Assert.assertEquals("Editor inherits owner capture eligibility", index % 2 == 0, secure)
                }
                compose.onNodeWithContentDescription(name).performScrollTo().assertIsDisplayed().performClick()
                compose.runOnIdle { org.junit.Assert.assertEquals(name, dispatched.last()) }
            }
        } finally {
            buffer.fill(0)
            compose.runOnIdle { compose.activity.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE) }
        }
    }

}
