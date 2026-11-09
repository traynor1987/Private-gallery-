package uk.co.traynor.privategallery.launcher

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import uk.co.traynor.privategallery.ui.BrowserViewportRule

class NovaLauncher72GesturesTest {
    private val compose = createAndroidComposeRule<ComponentActivity>()
    private val viewport = BrowserViewportRule { method ->
        if (method.startsWith("fold")) 820 to 920 else 375 to 850
    }
    @get:Rule val rules: TestRule = RuleChain.outerRule(viewport).around(compose)
    private val phone = LauncherApp("example.phone/.Main", "example.phone", "Phone test app")
    private val music = LauncherApp("example.music/.Main", "example.music", "Music test app")
    private val phoneItem = LauncherItem.App(phone.component)
    private val musicItem = LauncherItem.App(music.component)
    private var state by mutableStateOf(NovaLauncherState(
        configured = true,
        dockOptions = LauncherDockOptions(locked = false),
        cover = LauncherLayout(pages = listOf(listOf(phoneItem, musicItem))),
    ))
    private val actions = LauncherUiActions(
        save = { state = it; true }, launchApp = {}, launchShortcut = {}, openWorkspace = {},
        selectHome = {}, continueWithCurrentHome = {}, emergencyPhone = {}, reviewSanctuary = {},
        setOutsideProtection = {}, chooseWallpaper = {}, addWidget = {},
        removeWidget = {}, exportLayout = {}, importLayout = {}, renderWidget = { _, _ -> },
    )

    @Test fun stationaryHoldOpensCompactContextMenu() {
        showLauncher()
        app(phone).performTouchInput { longClick() }
        compose.onNodeWithTag("launcher-icon-menu").assertIsDisplayed()
        compose.onNodeWithText("Remove from this page").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(listOf(musicItem), state.cover.pages.single()) }
    }

    @Test fun iconExposesItsContextMenuAsAnAccessibilityAction() {
        showLauncher()
        app(phone).performSemanticsAction(SemanticsActions.OnLongClick) { assertTrue(it()) }
        compose.onNodeWithTag("launcher-icon-menu").assertIsDisplayed()
        compose.onNodeWithText("Move to dock").assertIsDisplayed()
    }

    @Test fun holdAndMoveReordersDirectlyOnHomeAndSurvivesStoreReopen() {
        val store = NovaLauncherStore(compose.activity)
        val before = store.read()
        try {
            showLauncher(actions.copy(save = { next ->
                store.save(next).also { if (it) state = next }
            }))
            val source = app(phone).fetchSemanticsNode().boundsInRoot
            val target = app(music).fetchSemanticsNode().boundsInRoot
            app(phone).performTouchInput {
                down(center)
                advanceEventTime(800)
                moveBy(Offset(target.right - target.width * .10f, target.center.y) - source.center)
                up()
            }
            compose.onNodeWithTag("launcher-icon-menu").assertDoesNotExist()
            compose.runOnIdle {
                assertEquals(listOf(musicItem, phoneItem), NovaLauncherStore(compose.activity).read().cover.pages.single())
            }
        } finally {
            assertTrue(store.save(before))
        }
    }

    @Test fun rightToLeftInsertionFollowsTheDisplayedOrder() {
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                NovaLauncherScreen(state, listOf(phone, music), emptyList(), emptyMap(), true, false, actions)
            }
        }
        val source = app(phone).fetchSemanticsNode().boundsInRoot
        val target = app(music).fetchSemanticsNode().boundsInRoot
        assertTrue(source.center.x > target.center.x)
        app(phone).performTouchInput {
            down(center)
            advanceEventTime(800)
            moveBy(Offset(target.left + target.width * .10f, target.center.y) - source.center)
            up()
        }
        compose.runOnIdle { assertEquals(listOf(musicItem, phoneItem), state.cover.pages.single()) }
    }

    @Test fun drawerHoldAndMoveCanDropOntoAnEmptyHomePage() {
        state = state.copy(cover = LauncherLayout())
        showLauncher()
        val home = compose.onNodeWithTag("launcher-home-pages").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("launcher-open-drawer").performClick()
        val source = app(phone).fetchSemanticsNode().boundsInRoot
        app(phone).performTouchInput {
            down(center)
            advanceEventTime(800)
            moveBy(home.center - source.center)
        }
        compose.onNodeWithTag("launcher-drag-preview").assertIsDisplayed()
        compose.onNodeWithTag("launcher-home-pages").performTouchInput { up() }
        compose.runOnIdle { assertEquals(listOf(phoneItem), state.cover.pages.single()) }
    }

    @Test fun droppingInTheMiddleOfAnAppGroupsTheTwoIcons() {
        showLauncher()
        val source = app(phone).fetchSemanticsNode().boundsInRoot
        val target = app(music).fetchSemanticsNode().boundsInRoot
        app(phone).performTouchInput {
            down(center)
            advanceEventTime(800)
            moveBy(target.center - source.center)
            up()
        }
        compose.runOnIdle {
            assertEquals(listOf(LauncherItem.Folder("Folder", listOf(music.component, phone.component))), state.cover.pages.single())
        }
    }

    @Test fun droppingAtTheDockMovesTheShortcutOffItsHomePage() {
        showLauncher()
        val source = app(phone).fetchSemanticsNode().boundsInRoot
        val target = compose.onNodeWithContentDescription("Open NovaOS", substring = false)
            .fetchSemanticsNode().boundsInRoot
        app(phone).performTouchInput {
            down(center)
            advanceEventTime(800)
            moveBy(Offset(target.left + target.width * .10f, target.center.y) - source.center)
            up()
        }
        compose.runOnIdle {
            assertEquals(listOf(musicItem), state.cover.pages.single())
            assertEquals(listOf(phoneItem, LauncherItem.Workspace), state.cover.dock)
        }
    }

    @Test fun holdingNearPageEdgeMovesTheDropToTheNextPage() {
        state = state.copy(cover = LauncherLayout(pages = listOf(listOf(phoneItem), emptyList())))
        showLauncher()
        val page = compose.onNodeWithTag("launcher-home-pages")
        val bounds = page.fetchSemanticsNode().boundsInRoot
        val source = app(phone).fetchSemanticsNode().boundsInRoot
        app(phone).performTouchInput {
            down(center)
            advanceEventTime(800)
            moveBy(Offset(bounds.right - 2.dp.toPx(), bounds.center.y) - source.center)
        }
        compose.waitUntil(timeoutMillis = 3_000) {
            compose.onAllNodes(hasContentDescription("Home page 2") and isSelected()).fetchSemanticsNodes().isNotEmpty()
        }
        page.performTouchInput {
            moveTo(center)
            up()
        }
        compose.runOnIdle { assertEquals(listOf(emptyList(), listOf(phoneItem)), state.cover.pages) }
    }

    @Test fun foldExpansionCancelsTheActiveCoverDragWithoutSaving() {
        val original = state.cover
        var width by mutableStateOf(375.dp)
        compose.setContent {
            Box(Modifier.width(width).fillMaxHeight()) {
                NovaLauncherScreen(state, listOf(phone, music), emptyList(), emptyMap(), true, false, actions)
            }
        }
        compose.onNodeWithTag("nova-launcher-cover").assertIsDisplayed()
        app(phone).performTouchInput {
            down(center)
            advanceEventTime(800)
            moveBy(Offset(55.dp.toPx(), 0f))
        }
        compose.onNodeWithTag("launcher-drag-preview").assertExists()
        compose.runOnIdle { width = 820.dp }
        compose.onNodeWithTag("nova-launcher-inner").assertIsDisplayed()
        compose.onNodeWithTag("launcher-drag-preview").assertDoesNotExist()
        compose.onNodeWithTag("launcher-home-pages").performTouchInput { up() }
        compose.runOnIdle {
            assertEquals(original, state.cover)
            assertEquals(LauncherLayout(), state.inner)
        }
    }

    private fun showLauncher(uiActions: LauncherUiActions = actions) = compose.setContent {
        NovaLauncherScreen(state, listOf(phone, music), emptyList(), emptyMap(), true, false, uiActions)
    }

    private fun app(app: LauncherApp) = compose.onNodeWithTag("launcher-app-${app.component}")
}
