package uk.co.traynor.privategallery.launcher

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import uk.co.traynor.privategallery.novaos2.captureNovaSystem
import uk.co.traynor.privategallery.ui.BrowserViewportRule

class LauncherThemesStudioUiTest {
    private val compose = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rule = RuleChain.outerRule(BrowserViewportRule {
        if (it.startsWith("inner")) 820 to 920 else 375 to 850
    }).around(compose)

    private val original = NovaLauncherState(
        configured = true,
        cover = LauncherLayout(pages = listOf(listOf(LauncherItem.App("example.calendar/.Main")))),
        inner = LauncherLayout(pages = listOf(listOf(LauncherItem.Workspace))),
    )
    private val state = mutableStateOf(original)

    @Test fun coverPreviewsOriginalAndAppliesRoundedWithoutChangingLayout() {
        show()
        compose.onNodeWithTag("theme-preview-cover").assertIsDisplayed()
        compose.onNodeWithText("Apply theme").assertIsNotEnabled()
        compose.onNodeWithText("NovaOS Original").performClick()
        compose.onNodeWithText("Apply theme").performScrollTo().assertIsEnabled().performClick()
        compose.onNodeWithTag("launcher-theme-clock-nova").assertIsDisplayed()
        captureNovaSystem("themes-cover-original")
        compose.onNodeWithText("One UI-inspired").performScrollTo().performClick()
        compose.onNodeWithText("Apply theme").performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals(LauncherLook.ROUNDED, state.value.appearance.studio.look)
            assertEquals(original, state.value.copy(appearance = original.appearance))
        }
        captureNovaSystem("themes-cover")
    }

    @Test fun innerPreviewAndDefaultRestoreKeepBothDisplaysIntact() {
        show()
        compose.onNodeWithTag("theme-studio-two-pane").assertIsDisplayed()
        compose.onNodeWithTag("theme-preview-inner").assertIsDisplayed()
        compose.onNodeWithText("Cover preview").performClick()
        compose.onNodeWithTag("theme-preview-cover").assertIsDisplayed()
        compose.onNodeWithText("Inner preview").performClick()
        compose.onNodeWithText("iOS-inspired").performClick()
        compose.onNodeWithText("Light").performScrollTo().performClick()
        compose.onNodeWithText("Apply theme").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(LauncherLook.FLOATING, state.value.appearance.studio.look)
            assertEquals(LauncherTheme.LIGHT, state.value.appearance.theme)
        }
        captureNovaSystem("themes-inner")
        compose.onNodeWithText("Restore default").performClick()
        compose.onNodeWithText("Apply theme").performClick()
        compose.runOnIdle {
            assertEquals(LauncherLook.NOVA, state.value.appearance.studio.look)
            assertEquals(original, state.value.copy(appearance = original.appearance))
        }
    }

    @Test fun coverHomeRendersAllThreeThemesAndKeepsItsFoldersAndWidgets() {
        val apps = previewApps()
        val snapshot = homeState(apps)
        showHome(apps, snapshot, reducedWindow = mutableStateOf(false))
        for (look in LauncherLook.entries) {
            applyHomeLook(look, snapshot)
            compose.onNodeWithTag("nova-launcher-cover").assertIsDisplayed()
            compose.onNodeWithContentDescription("Everyday").assertIsDisplayed()
            compose.onNodeWithText("Focus widget").assertIsDisplayed()
            compose.onNodeWithTag("launcher-dock-slot-0").assertIsDisplayed()
            captureNovaSystem("themes-home-cover-${look.name.lowercase(Locale.ROOT)}")
        }
    }

    @Test fun innerHomeRendersAllThreeThemesAndRetainsAppearanceOnWindowResize() {
        val apps = previewApps()
        val snapshot = homeState(apps)
        val reducedWindow = mutableStateOf(false)
        showHome(apps, snapshot, reducedWindow)
        for (look in LauncherLook.entries) {
            applyHomeLook(look, snapshot)
            compose.onNodeWithTag("nova-launcher-inner").assertIsDisplayed()
            compose.onNodeWithContentDescription("Productivity").assertIsDisplayed()
            compose.onNodeWithText("Focus widget").assertIsDisplayed()
            captureNovaSystem("themes-home-inner-${look.name.lowercase(Locale.ROOT)}")
        }
        compose.onNodeWithTag("launcher-widget-stack").assertIsDisplayed()
        compose.onNodeWithContentDescription("Next widget").performClick()
        compose.onNodeWithText("2 of 2").assertIsDisplayed()
        compose.onNodeWithText("Schedule widget").assertIsDisplayed()
        compose.runOnIdle { reducedWindow.value = true }
        compose.onNodeWithTag("nova-launcher-cover").assertIsDisplayed()
        compose.onNodeWithContentDescription("Everyday").assertIsDisplayed()
        compose.onNodeWithTag("launcher-theme-clock-floating").assertIsDisplayed()
        compose.runOnIdle { reducedWindow.value = false }
        compose.onNodeWithTag("nova-launcher-inner").assertIsDisplayed()
        compose.onNodeWithContentDescription("Productivity").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(LauncherLook.FLOATING, state.value.appearance.studio.look)
            assertEquals(snapshot, state.value.copy(appearance = snapshot.appearance))
        }
    }

    private fun previewApps(): List<LauncherApp> = listOf(
        LauncherBuiltIn.NOTES, LauncherBuiltIn.TASKS, LauncherBuiltIn.CALENDAR,
        LauncherBuiltIn.FILES, LauncherBuiltIn.CALCULATOR,
    ).map { it.app(compose.activity.packageName) }

    private fun homeState(apps: List<LauncherApp>): NovaLauncherState {
        val items = apps.map { LauncherItem.App(it.component) }
        return original.copy(
            cover = LauncherLayout(
                pages = listOf(listOf(items[0], items[1], LauncherItem.Folder("Everyday", items.take(3).map { it.component }))),
                dock = listOf(LauncherItem.Workspace, items[2], items[3], items[4]),
            ),
            inner = LauncherLayout(
                pages = listOf(items + LauncherItem.Folder("Productivity", items.take(4).map { it.component })),
                dock = listOf(LauncherItem.Workspace, items[0], items[3], items[4]),
            ),
            widgets = listOf(
                LauncherWidget(1, "example.widgets/.Focus", false),
                LauncherWidget(2, "example.widgets/.Focus", true),
                LauncherWidget(3, "example.widgets/.Schedule", true),
            ),
        )
    }

    private fun applyHomeLook(look: LauncherLook, snapshot: NovaLauncherState) {
        compose.runOnIdle {
            state.value = state.value.copy(appearance = look.preset(state.value.appearance))
            assertEquals(snapshot, state.value.copy(appearance = snapshot.appearance))
        }
        compose.onNodeWithTag("launcher-theme-clock-${look.name.lowercase(Locale.ROOT)}").assertIsDisplayed()
    }

    private fun showHome(
        apps: List<LauncherApp>,
        snapshot: NovaLauncherState,
        reducedWindow: State<Boolean>,
    ) {
        state.value = snapshot
        val actions = LauncherUiActions(
            save = { false }, launchApp = {}, launchShortcut = {}, openWorkspace = {}, selectHome = {},
            continueWithCurrentHome = {}, emergencyPhone = {}, reviewSanctuary = {}, setOutsideProtection = {},
            chooseWallpaper = {}, addWidget = {}, removeWidget = {}, exportLayout = {}, importLayout = {},
            renderWidget = { widget, modifier ->
                val title = if (widget.provider.endsWith(".Schedule")) "Schedule widget" else "Focus widget"
                Surface(modifier, color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.large) {
                    Box(contentAlignment = Alignment.CenterStart) {
                        Text(title, Modifier.padding(18.dp), style = MaterialTheme.typography.titleMedium)
                    }
                }
            },
        )
        compose.setContent {
            val current = state.value
            LauncherStudioTheme(current.appearance) {
                Box(
                    Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    BoxWithConstraints(
                        (if (reducedWindow.value) Modifier.width(375.dp) else Modifier.fillMaxSize()).fillMaxHeight(),
                    ) {
                        val wide = maxWidth >= LauncherExpandedWidth.dp
                        LauncherHome(
                            state = current, layout = if (wide) current.inner else current.cover, wide = wide,
                            currentPage = 0, apps = apps.associateBy(LauncherApp::component), decisions = emptyMap(),
                            actions = actions, protectedBySanctuary = false, onPage = {}, onDrawer = {},
                            onSettings = {}, onEdit = {}, onItem = {},
                            gestures = remember { LauncherIconGestures(rightToLeft = false) { null } },
                            dragPageRequest = null,
                        )
                    }
                }
            }
        }
    }

    private fun show() {
        val apps = previewApps()
        compose.setContent {
            LauncherStudioTheme(state.value.appearance) {
                Surface(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                        LauncherThemesStudio(state.value.appearance, apps, {
                            state.value = state.value.copy(appearance = it)
                            true
                        }, onDeviceWallpaper = {})
                    }
                }
            }
        }
    }
}
