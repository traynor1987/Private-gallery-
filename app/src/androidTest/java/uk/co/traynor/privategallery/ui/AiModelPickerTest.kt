package uk.co.traynor.privategallery.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import uk.co.traynor.privategallery.core.editor.ReplicateEditModel

class AiModelPickerTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun compactSelectionOpensSheetAndUpdatesWithoutHorizontalClipping() {
        var width by mutableIntStateOf(320)
        var selected by mutableStateOf(ReplicateEditModel.SEEDREAM_5_PRO)
        var quality by mutableStateOf("1K")
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) { PrivateGalleryTheme {
                Box(Modifier.requiredSize(width.dp, 720.dp)) {
                    AiSinglePicker("Model", selected.pickerItem(false, quality), ReplicateEditModel.entries.map { it.pickerItem(false, quality) },
                        onSelect = { selected = ReplicateEditModel.valueOf(it) })
                }
            } }
        }
        compose.onNodeWithTag("model-selector").assertIsDisplayed()
        compose.onNodeWithText("1K · ≈$0.045 / image").assertIsDisplayed()
        compose.onNodeWithTag("model-selector").performClick()
        compose.onNodeWithText("Choose model").assertIsDisplayed()
        compose.onNodeWithTag("picker-FILL").performScrollTo().performClick()
        compose.onNodeWithText("Replace or remove a selected area").assertIsDisplayed()
        compose.runOnIdle { width = 720; selected = ReplicateEditModel.SEEDREAM_5_PRO; quality = "2K" }
        compose.onNodeWithText("2K · ≈$0.09 / image").assertIsDisplayed()
        val bounds = compose.onNodeWithTag("model-selector").fetchSemanticsNode().boundsInRoot
        assertTrue(bounds.right <= 720f)
    }

    @Test fun adultIsSecondaryBadgeOnlyForSupportedModel() {
        compose.setContent { PrivateGalleryTheme {
            AiSinglePicker("Model", ReplicateEditModel.SEEDREAM.pickerItem(true, "1K"),
                ReplicateEditModel.entries.map { it.pickerItem(true, "1K") }, onSelect = {})
        } }
        compose.onNodeWithTag("model-selector").performClick()
        compose.onNodeWithText("Adult").assertIsDisplayed()
        assertTrue(ReplicateEditModel.SEEDREAM_5_PRO.pickerItem(true, "1K").badge == null)
    }
    @Test fun providerUsesCompactSelectionAndBottomSheet() {
        var provider by mutableStateOf("REPLICATE")
        val choices = listOf(AiPickerItem("REPLICATE", "Replicate", "Connected", ""),
            AiPickerItem("OPENAI", "OpenAI", "Connected", ""))
        compose.setContent { PrivateGalleryTheme {
            AiSinglePicker("Provider", choices.first { it.key == provider }, choices, { provider = it })
        } }
        compose.onNodeWithTag("provider-selector").assertIsDisplayed().performClick()
        compose.onNodeWithText("Choose provider").assertIsDisplayed()
        compose.onNodeWithTag("picker-OPENAI").performClick()
        compose.onNodeWithTag("provider-selector").assertContentDescriptionContains("OpenAI")
    }
}
