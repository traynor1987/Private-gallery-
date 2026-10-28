package uk.co.traynor.privategallery.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import uk.co.traynor.privategallery.core.browser.v2.BrowserFocusMode
import uk.co.traynor.privategallery.core.ui.AppTheme

/** Exercises the actual production browser modal components, with no network grants or egress. */
class BrowserModalSurfaces1762Test {
    private val compose=createAndroidComposeRule<ComponentActivity>()
    private val viewport=BrowserViewportRule(fontScale=2f) {name->if(name.contains("Landscape"))840 to 392 else 320 to 640}
    @get:Rule val rules:TestRule=RuleChain.outerRule(viewport).around(compose)
    private fun content(theme:AppTheme,body:@Composable ()->Unit) {
        compose.setContent {val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,2f)){PrivateGalleryTheme(theme){body()}}
        }
    }
    @Test fun coverDarkEveryCapabilityAndSaveConsentRequiresExplicitChoice() {
        var kind by mutableStateOf<BrowserConsentSurface?>(BrowserConsentSurface.CAPABILITY)
        var accepted=0;var cancelled=0
        content(AppTheme.DARK){kind?.let{BrowserConsentDialog(it,{accepted++;kind=null},{cancelled++;kind=null})}}
        for(surface in BrowserConsentSurface.entries) {
            compose.runOnIdle{kind=surface}
            compose.onNodeWithText(surface.title).assertIsDisplayed()
            compose.onNodeWithText(surface.acceptLabel).assertIsDisplayed()
            compose.runOnIdle{assertEquals(0,accepted)}
            compose.onNodeWithText(surface.cancelLabel).assertIsDisplayed().performClick()
        }
        compose.runOnIdle{assertEquals(BrowserConsentSurface.entries.size,cancelled);assertEquals(0,accepted)}
    }
    @Test fun landscapeLightEveryCapabilityAndSaveConsentDispatchesOnlyChosenAction() {
        var kind by mutableStateOf<BrowserConsentSurface?>(null)
        val accepted=mutableListOf<BrowserConsentSurface>();var denied=0
        content(AppTheme.LIGHT){kind?.let{surface->BrowserConsentDialog(surface,{accepted+=surface;kind=null},{denied++;kind=null})}}
        for(surface in BrowserConsentSurface.entries) {
            compose.runOnIdle{kind=surface}
            compose.onNodeWithText(surface.acceptLabel).assertIsDisplayed().performClick()
        }
        compose.runOnIdle{assertEquals(BrowserConsentSurface.entries.toList(),accepted);assertEquals(0,denied)}
    }
    @Test fun coverDarkPermissionBackDismissDeniesWithoutGrant() {
        var open by mutableStateOf(true);var accepted=0;var denied=0
        content(AppTheme.DARK){if(open)BrowserConsentDialog(BrowserConsentSurface.CAPABILITY,{accepted++},{denied++;open=false})}
        compose.onNodeWithText("Website permission").assertIsDisplayed()
        val instrumentation=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        val activity=compose.activity
        // Espresso.pressBack selected the unfocused BASE_APPLICATION root while the
        // real Dialog correctly owned focus. Send OS Back only after that owned window
        // is focused; this still exercises Dialog.onDismissRequest, not its callback.
        compose.waitUntil(10_000) {
            var dialogFocused=false
            instrumentation.runOnMainSync {
                dialogFocused=android.view.inspector.WindowInspector.getGlobalWindowViews().any {root ->
                    root!==activity.window.decorView && root.isAttachedToWindow && root.hasWindowFocus() &&
                        root.context.playerActivity()===activity
                }
            }
            dialogFocused
        }
        assertTrue("System Back must be dispatched to the focused permission Dialog",
            instrumentation.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK))
        compose.waitUntil(10_000){!open}
        compose.onNodeWithText("Website permission").assertDoesNotExist()
        compose.runOnIdle{assertEquals(0,accepted);assertEquals(1,denied);assertFalse(open);assertFalse(activity.isFinishing)}
    }
    @Test fun coverLightFindRetainsQueryAndKeyboardSearchUntilExplicitClose() {
        var text by mutableStateOf("");var forward=0;var previous=0;var closed=0
        content(AppTheme.LIGHT){BrowserFindDialog(text,{text=it},{if(it)forward++ else previous++},{closed++})}
        compose.onNodeWithTag("browser-find-query").performClick().performTextInput("needle")
        compose.onNodeWithTag("browser-find-query").performImeAction()
        compose.onNodeWithText("Previous").performScrollTo().performClick()
        compose.onNodeWithText("Close").assertIsDisplayed().performClick()
        compose.runOnIdle{assertEquals("needle",text);assertEquals(1,forward);assertEquals(1,previous);assertEquals(1,closed)}
    }
    @Test fun landscapeDarkDiagnosticsLongReportKeepsActionsReachableAndIndependent() {
        var clear=0;var copy=0;var closed=0;var armed=0;var captured=0
        var verbose by mutableStateOf(true);var focus:BrowserFocusMode?=null
        content(AppTheme.DARK){BrowserDiagnosticsDialog(("Synthetic structure-only diagnostic line\n").repeat(300),false,verbose,true,
            {armed++},{captured++},{focus=it},{verbose=!verbose},{copy++},{clear++},{closed++})}
        compose.onNodeWithText("Arm next interaction").performScrollTo().performClick()
        compose.onNodeWithText("Capture diagnostic snapshot").performScrollTo().performClick()
        compose.onNodeWithText("Explicit WebView focus").performScrollTo().performClick()
        compose.onNodeWithText("Copy all").performScrollTo().performClick()
        compose.onNodeWithText("Clear current session").performScrollTo().performClick()
        compose.onNodeWithText("Close").assertIsDisplayed().performClick()
        compose.runOnIdle{assertEquals(1,armed);assertEquals(1,captured);assertEquals(BrowserFocusMode.EXPLICIT_WEBVIEW_FOCUS,focus);assertEquals(1,copy);assertEquals(1,clear);assertEquals(1,closed)}
    }
    @Test fun coverDarkLongBrowserMessageScrollsWithoutHidingAcknowledgement() {
        var done=0
        content(AppTheme.DARK){BrowserMessageDialog(("Synthetic recoverable warning.\n").repeat(300),{done++})}
        compose.onNode(hasScrollAction()).assertExists().performTouchInput{swipeUp()}
        compose.onNodeWithText("OK").assertIsDisplayed().performClick()
        compose.runOnIdle{assertEquals(1,done)}
    }
    @Test fun landscapeLightVideoConsentProgressAndCancellationDoNotImplicitlySave() {
        var busy by mutableStateOf(false);var saved=0;var cancelled=0
        content(AppTheme.LIGHT){BrowserSaveVideoDialog(busy,73,"Checking encrypted copy…", "video/mp4",{saved++;busy=true},{cancelled++})}
        compose.onNodeWithText("Save to Vault").assertIsDisplayed().performClick()
        compose.onNodeWithText("Save to Vault").assertDoesNotExist()
        compose.onNodeWithText("Cancel").assertIsDisplayed().performClick()
        compose.runOnIdle{assertEquals(1,saved);assertEquals(1,cancelled)}
    }
    @Test fun coverDarkBusyVideoKeepsExplicitCancelWhenBackCannotDismissProgress() {
        var cancelled=0
        content(AppTheme.DARK){BrowserSaveVideoDialog(true,null,("Synthetic verification stage ").repeat(80),"video/mp4",{fail("Busy video must not admit a second save")},{cancelled++})}
        compose.onNodeWithText("Cancel").assertIsDisplayed()
        val instrumentation=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        val activity=compose.activity
        compose.waitUntil(10_000) {
            var dialogFocused=false
            instrumentation.runOnMainSync {
                dialogFocused=android.view.inspector.WindowInspector.getGlobalWindowViews().any { root ->
                    root!==activity.window.decorView && root.isAttachedToWindow && root.hasWindowFocus() &&
                        root.context.playerActivity()===activity
                }
            }
            dialogFocused
        }
        assertTrue("System Back must be dispatched to the focused busy-video Dialog",
            instrumentation.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK))
        compose.waitForIdle()
        compose.runOnIdle{assertEquals(0,cancelled);assertFalse(activity.isFinishing)}
        compose.onNodeWithText("Cancel").assertIsDisplayed().performClick()
        compose.runOnIdle{assertEquals(1,cancelled)}
    }
    @Test fun coverLightExternalNavigationPlansKeepCancelSeparateFromOpen() {
        var choice by mutableStateOf(BrowserExternalChoice.APP)
        val opened=mutableListOf<BrowserExternalChoice>();var cancelled=0
        content(AppTheme.LIGHT){BrowserExternalNavigationDialog(choice,{opened+=choice},{cancelled++})}
        for(plan in BrowserExternalChoice.entries) {
            compose.runOnIdle{choice=plan}
            compose.onNodeWithText(plan.acceptLabel).assertIsDisplayed().performClick()
            compose.onNodeWithText("Cancel").assertIsDisplayed().performClick()
        }
        compose.runOnIdle{assertEquals(BrowserExternalChoice.entries.toList(),opened);assertEquals(3,cancelled)}
    }
    @Test fun landscapeDarkInternalMediaKeepsExitUsableWhenNetworkingIsDenied() {
        var closed=0
        content(AppTheme.DARK){BrowserInternalMediaDialog("https://synthetic.invalid/video.mp4" to "video/mp4",{false},{closed++},{},{})}
        compose.onNodeWithContentDescription("Back").assertIsDisplayed().performClick()
        compose.runOnIdle{assertEquals(1,closed)}
    }
}
