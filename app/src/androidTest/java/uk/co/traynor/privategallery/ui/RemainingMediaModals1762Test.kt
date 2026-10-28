package uk.co.traynor.privategallery.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewRootForTest
import androidx.test.core.app.takeScreenshot as takeRenderedScreenshot
import kotlin.math.roundToInt
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import uk.co.traynor.privategallery.core.ui.AppTheme
import uk.co.traynor.privategallery.core.ui.MediaSort
import uk.co.traynor.privategallery.core.vault.VaultCollection

/** Real production modal components. No camera/player/AI/network or owner media is initialized. */
class RemainingMediaModals1762Test {
    private val compose=createAndroidComposeRule<ComponentActivity>()
    private val viewport=BrowserViewportRule(fontScale=2f) {name ->if(name.contains("Landscape"))840 to 392 else 320 to 640}
    @get:Rule val rules:TestRule=RuleChain.outerRule(viewport).around(compose)
    private fun action(label:String) = compose.onNodeWithText(label).assertIsDisplayed().assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
    private fun scrollToEnd() {
        val scroll=compose.onNode(hasScrollAction())
        scroll.performSemanticsAction(SemanticsActions.ScrollBy) {it(0f,100_000f)}
        compose.waitForIdle()
        val range=scroll.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
        assertTrue("Long production content must actually scroll",range.maxValue()>0 && range.value()>0)
    }
    private fun capture(theme:AppTheme,case:Int) {
        val bitmap=compose.onNodeWithTag("media-operation-dialog").captureToImage().asAndroidBitmap()
        try {
            val bounds=compose.activity.windowManager.currentWindowMetrics.bounds
            assertTrue(bitmap.width in 1..bounds.width());assertTrue(bitmap.height in 1..bounds.height())
            val pixel=bitmap.getPixel(bitmap.width/2,8)
            val luminance=(android.graphics.Color.red(pixel)+android.graphics.Color.green(pixel)+android.graphics.Color.blue(pixel))/3
            if(theme==AppTheme.DARK)assertTrue("Selected dark modal surface",luminance<100) else assertTrue("Selected light modal surface",luminance>180)
            val instrumentation=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
            val output=androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
                ?: java.io.File(instrumentation.targetContext.externalMediaDirs.first(),"additional_test_output").absolutePath
            val folder=java.io.File(output,"remaining-media-modals1762").apply {mkdirs()}
            java.io.File(folder,"$theme-${bounds.width()}x${bounds.height()}-$case.png").outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
        } finally {bitmap.recycle()}
    }
    private fun menuSurface(theme:AppTheme,tag:String) {
        val viewport=compose.onNodeWithTag(tag).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val popup=compose.onNode(isPopup())
        val popupNode=popup.fetchSemanticsNode()
        val popupBounds=popupNode.boundsInRoot
        val instrumentation=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        val output=androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
            ?: java.io.File(instrumentation.targetContext.externalMediaDirs.first(),"additional_test_output").absolutePath
        val bounds=compose.activity.windowManager.currentWindowMetrics.bounds
        val folder=java.io.File(output,"remaining-media-modals1762").apply {mkdirs()}
        val diagnostic=java.io.File(folder,"$theme-${bounds.width()}x${bounds.height()}-$tag.txt")
        val state=modalCaptureState1762(compose.activity)
        diagnostic.writeText(state.description+"popup=$popupBounds viewport=$viewport\n"+popup.printToString())
        assertFalse("Synthetic menu fixture must not be captured through a secure window: ${state.description}",state.secure)
        // Compose 1.7.6's popup capture calls raw UiAutomation.takeScreenshot, unlike
        // its Dialog PixelCopy path. DeviceCapture enables headless hardware drawing,
        // redraws the real windows and restores the renderer; it does not paint pixels.
        val screen=takeRenderedScreenshot()
        val location=IntArray(2)
        instrumentation.runOnMainSync { (popupNode.root as ViewRootForTest).view.getLocationOnScreen(location) }
        val left=(location[0]+popupBounds.left).roundToInt()
        val top=(location[1]+popupBounds.top).roundToInt()
        val width=popupBounds.width.roundToInt()
        val height=popupBounds.height.roundToInt()
        val bitmap=try {
            assertTrue("Popup crop belongs to the actual display",left>=0 && top>=0 && left+width<=screen.width && top+height<=screen.height)
            android.graphics.Bitmap.createBitmap(screen,left,top,width,height)
        } finally { screen.recycle() }
        try {
            assertTrue("Production menu viewport has background space",bitmap.width>8 && bitmap.height>8)
            val sampleX=(viewport.left-popupBounds.left+4).toInt()
            val sampleY=(viewport.center.y-popupBounds.top).toInt()
            assertTrue("Viewport sample belongs to the captured popup",sampleX in 0 until bitmap.width && sampleY in 0 until bitmap.height)
            val pixel=bitmap.getPixel(sampleX,sampleY)
            java.io.File(folder,"$theme-${bounds.width()}x${bounds.height()}-$tag.png").outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
            diagnostic.appendText("\nscreenCrop=($left,$top,$width,$height) sample=($sampleX,$sampleY) ARGB=${Integer.toHexString(pixel)}\n"+modalCaptureState1762(compose.activity).description)
            val luminance=(android.graphics.Color.red(pixel)+android.graphics.Color.green(pixel)+android.graphics.Color.blue(pixel))/3
            assertTrue("Menu sample must be opaque, not an isolated transparent glyph",android.graphics.Color.alpha(pixel)>=250)
            if(theme==AppTheme.DARK)assertTrue("Selected dark menu surface: ARGB=${Integer.toHexString(pixel)}",luminance<100)
            else assertTrue("Selected light menu surface: ARGB=${Integer.toHexString(pixel)}",luminance>180)
        } finally {bitmap.recycle()}
    }
    private fun verify(theme:AppTheme) {
        var fixture by mutableIntStateOf(0)
        var verifying by mutableStateOf(false)
        var confirmations=0;var dismissals=0;var retries=0;var cancellations=0;var verifications=0
        var chosen:String?=null;var sort by mutableStateOf<MediaSort?>(null);var speed by mutableStateOf<Float?>(null)
        val long=(0..100).joinToString("\n") {"Synthetic long diagnostic line $it with wrapped provider/media data"}
        val collections=(0..299).map {VaultCollection("collection-$it","Collection $it with a long accessible name",0)}
        compose.setContent {
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,2f)) {
                PrivateGalleryTheme(theme) {
                    when(fixture) {
                        0 -> PublicCopyExportDialog(false,{dismissals++},{confirmations++})
                        1 -> PublicCopyExportDialog(true,{dismissals++},{confirmations++})
                        2 -> VaultTrashConfirmationDialog({dismissals++},{confirmations++})
                        3 -> VideoOpenErrorDialog(long,{retries++},{dismissals++})
                        4 -> HiddenCollectionChoiceDialog(collections,{chosen=it},{dismissals++})
                        5 -> PromptDiagnosticDialog(long,long,{dismissals++})
                        6 -> PredictionDiagnosticDialog(long,{dismissals++})
                        7 -> AiOperationProgressDialog("Synthetic provider",long,"99m 59s elapsed","Processed remotely with a synthetic provider",{cancellations++})
                        8 -> AiSaveProgressDialog(long,false,{cancellations++})
                        9 -> AiSaveProgressDialog("Saved to Hidden Vault",true,{cancellations++})
                        10 -> RecordingDetailsContentDialog(long,"Synthetic integrity state",verifying,{verifications++;verifying=true},{dismissals++})
                        11 -> Surface {MediaSortButton(sort ?: MediaSort.entries.first()){sort=it}}
                        12 -> Box(Modifier.fillMaxSize().background(Color.Black)) {VideoPlaybackSpeedMenu(speed ?: 1f){speed=it}}
                        13 -> HiddenCollectionChoiceDialog(emptyList(),{chosen=it},{dismissals++})
                    }
                }
            }
        }
        capture(theme,0);action("Export copy");action("Cancel").performClick()
        compose.runOnIdle {assertEquals(0,confirmations);assertEquals(1,dismissals);fixture=1}
        capture(theme,1);action("Open Gallery").performClick()
        compose.runOnIdle {assertEquals(1,confirmations);fixture=2}
        capture(theme,2);action("Cancel");action("Move").performClick()
        compose.runOnIdle {assertEquals(2,confirmations);fixture=3}
        scrollToEnd();capture(theme,3);action("Close");action("Retry").performClick()
        compose.runOnIdle {assertEquals(1,retries);fixture=4}
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(collections.last().name))
        capture(theme,4);action(collections.last().name).performClick()
        compose.runOnIdle {assertEquals("collection-299",chosen);fixture=5}
        scrollToEnd();capture(theme,5);action("Close").performClick()
        compose.runOnIdle {fixture=6}
        scrollToEnd();capture(theme,6);action("Close").performClick()
        compose.runOnIdle {fixture=7}
        scrollToEnd();capture(theme,7);action("Cancel").performClick()
        compose.runOnIdle {assertEquals(1,cancellations);fixture=8}
        scrollToEnd();capture(theme,8);action("Cancel").performClick()
        compose.runOnIdle {assertEquals(2,cancellations);fixture=9}
        compose.onNodeWithText("Your encrypted copy is saved.").assertIsDisplayed();capture(theme,9)
        compose.onNodeWithText("Cancel").assertDoesNotExist()
        compose.runOnIdle {fixture=10}
        compose.onNodeWithText("Verify integrity").performScrollTo();action("Verify integrity").performClick()
        compose.onNodeWithText("Verifying…").assertIsNotEnabled()
        action("Done");capture(theme,10)
        compose.runOnIdle {assertEquals(1,verifications);fixture=11}
        compose.onNodeWithContentDescription("Sort media").assertHeightIsAtLeast(48.dp).performClick()
        compose.onNodeWithText(MediaSort.entries.last().label).performScrollTo();menuSurface(theme,"media-sort-menu");action(MediaSort.entries.last().label).performClick()
        compose.runOnIdle {assertEquals(MediaSort.entries.last(),sort)}
        compose.onNodeWithContentDescription("Sort media").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,MediaSort.entries.last().label))
        compose.runOnIdle {fixture=12}
        compose.onNodeWithContentDescription("Playback speed").assertHeightIsAtLeast(48.dp).performClick()
        menuSurface(theme,"media-speed-menu");action("2.0×").performClick()
        compose.runOnIdle {assertEquals(2f,speed)}
        compose.onNodeWithContentDescription("Playback speed").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,"2.0×"))
        compose.runOnIdle {fixture=13}
        compose.onNodeWithText("Create a collection in Hidden first.").assertIsDisplayed();capture(theme,13);action("Close")
    }
    @Test fun narrowDarkTwoTimesText()=verify(AppTheme.DARK)
    @Test fun narrowLightTwoTimesText()=verify(AppTheme.LIGHT)
    @Test fun shortLandscapeDarkTwoTimesText()=verify(AppTheme.DARK)
    @Test fun shortLandscapeLightTwoTimesText()=verify(AppTheme.LIGHT)
}
