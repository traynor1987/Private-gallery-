package uk.co.traynor.privategallery.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import uk.co.traynor.privategallery.core.editor.PromptEnhancementStore
import uk.co.traynor.privategallery.core.editor.PromptKind
import uk.co.traynor.privategallery.core.ui.AppTheme

/** Actual editor chrome must wrap while its form remains scrollable on a narrow window. */
class PromptTemplateLayout1762Test {
    private val compose = createAndroidComposeRule<ComponentActivity>()
    private val viewport = BrowserViewportRule { 320 to 640 }
    @get:Rule val rules: TestRule = RuleChain.outerRule(viewport).around(compose)

    @Test fun narrowLargeTextKeepsActionsReachable() {
        var dismissed = false
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                PrivateGalleryTheme(AppTheme.DARK) {
                    PromptTemplateEditor(PromptKind.CREATE, PromptEnhancementStore(compose.activity)) { dismissed = true }
                }
            }
        }
        compose.onNodeWithText("Reset").assertIsDisplayed()
        compose.onNodeWithText("Cancel").assertIsDisplayed()
        compose.onNodeWithText("Save").assertIsDisplayed()
        compose.onNodeWithText("Preview with a sample prompt").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Cancel").assertIsDisplayed().performClick()
        compose.runOnIdle { assertTrue(dismissed) }
    }
}
