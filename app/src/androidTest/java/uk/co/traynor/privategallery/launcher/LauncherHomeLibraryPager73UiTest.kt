package uk.co.traynor.privategallery.launcher

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import uk.co.traynor.privategallery.ui.BrowserViewportRule
import uk.co.traynor.privategallery.core.guardian.GuardianAssessment
import uk.co.traynor.privategallery.core.guardian.GuardianInboxRow
import uk.co.traynor.privategallery.core.guardian.GuardianInboxSection
import uk.co.traynor.privategallery.core.sanctuary.SanctuaryAppVisibility
import uk.co.traynor.privategallery.core.sanctuary.SanctuaryMode
import uk.co.traynor.privategallery.core.sanctuary.SanctuaryLauncherPolicy.Decision

class LauncherHomeLibraryPager73UiTest {
    private val compose = createAndroidComposeRule<ComponentActivity>()
    private val viewport = BrowserViewportRule { method ->
        when {
            method.startsWith("landscape") -> 760 to 440
            method.startsWith("fold") -> 820 to 920
            else -> 375 to 850
        }
    }
    @get:Rule val rules: TestRule = RuleChain.outerRule(viewport).around(compose)

    private val apps = (1..4).map { index ->
        LauncherApp("example.app$index/.Main", "example.app$index", "Sample app $index")
    }
    private val items = apps.map { LauncherItem.App(it.component) }
    private var state by mutableStateOf(NovaLauncherState(
        configured = true,
        cover = LauncherLayout(pages = listOf(listOf(items[0]), listOf(items[1]), listOf(items[2]))),
        inner = LauncherLayout(pages = listOf(listOf(items[1]), listOf(items[2]))),
        separateDisplays = true,
    ))
    private val actions = LauncherUiActions(
        save = { state = it; true }, launchApp = {}, launchShortcut = {}, openWorkspace = {},
        selectHome = {}, continueWithCurrentHome = {}, emergencyPhone = {}, reviewSanctuary = {},
        setOutsideProtection = {}, chooseWallpaper = {}, addWidget = {}, removeWidget = {},
        exportLayout = {}, importLayout = {}, renderWidget = { _, _ -> },
    )
    private val pager get() = compose.onNodeWithTag("launcher-home-pages")
    private val library get() = compose.onNodeWithTag("launcher-app-library")

    @Test fun coverGuardianIsLeftOfHomeAndDoesNotExposeAnInboxOrDock() {
        state = state.copy(cover = state.cover.copy(dock = listOf(items[3])))
        val before = state
        showLauncher()
        val dock = compose.onNodeWithTag("launcher-dock-slot-0")
        homeApp(0).assertIsDisplayed()
        dock.assertIsDisplayed()
        pager.performTouchInput { swipeRight() }
        compose.onNodeWithTag("launcher-guardian-home").assertIsDisplayed()
        dock.assertIsNotDisplayed()
        compose.onNodeWithText("Analysis pending").assertIsDisplayed()
        val ready = compose.onNodeWithTag("guardian-ready-tile").assertIsDisplayed()
        val cooling = compose.onNodeWithTag("guardian-cooling-tile").assertIsDisplayed()
        compose.waitUntil(5_000) {
            runCatching { ready.assertTextContains("0"); cooling.assertTextContains("0") }.isSuccess
        }
        val readyBounds = ready.fetchSemanticsNode().boundsInRoot
        val coolingBounds = cooling.fetchSemanticsNode().boundsInRoot
        assertTrue(kotlin.math.abs(readyBounds.width - coolingBounds.width) <= 1f)
        assertEquals(readyBounds.height, coolingBounds.height)
        saveGuardianCheckpoint("cover")
        compose.onNodeWithText("View Diagnostics").performScrollTo().performClick()
        compose.onNodeWithText("Notification compatibility").assertIsDisplayed()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("launcher-guardian-home").assertIsDisplayed()
        pager.performTouchInput { swipeLeft() }
        homeApp(0).assertIsDisplayed()
        dock.assertIsDisplayed()
        pager.performTouchInput { swipeLeft() }
        homeApp(1).assertIsDisplayed()
        compose.runOnIdle { assertEquals(before, state) }
    }

    @Test fun foldGuardianDisplaysTheRealPanicSnapshotAndCanBeDisabled() {
        var status by mutableStateOf<LauncherSanctuaryStatus?>(LauncherSanctuaryStatus(SanctuaryMode.PANIC, 3_660_000, false))
        compose.setContent {
            NovaLauncherScreen(state, apps, emptyList(), emptyMap(), true, false, actions, sanctuaryStatus = status)
        }
        try {
            homeApp(1).assertIsDisplayed()
            pager.performTouchInput { swipeRight() }
            compose.onNodeWithText("Panic Protection").assertIsDisplayed()
            compose.onNodeWithText("1:01:00 remaining").assertIsDisplayed()
            saveGuardianCheckpoint("unfolded")
            compose.runOnIdle { status = null }
            compose.onNodeWithText("Protection inactive").assertIsDisplayed()
            compose.onNodeWithContentDescription("Settings").performClick()
            compose.onNodeWithTag("guardian-show-page").performClick()
            assertTrue(!GuardianHomeOptions.read(compose.activity).showPage)
            compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
            compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
            homeApp(1).assertIsDisplayed()
            pager.performTouchInput { swipeRight() }
            compose.onNodeWithTag("launcher-guardian-home").assertDoesNotExist()
        } finally {
            GuardianHomeOptions().save(compose.activity)
        }
    }

    @Test fun landscapeGuardianRemainsReachableWithLargeText() {
        compose.setContent {
            val density = androidx.compose.ui.platform.LocalDensity.current
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density, 2f),
            ) {
                GuardianHome(GuardianHomeOptions(), null, emptyList(), emptyMap(), {}, {}, {})
            }
        }
        compose.onNodeWithText("Nova Guardian").assertIsDisplayed()
        val ready = compose.onNodeWithTag("guardian-ready-tile").performScrollTo()
        val cooling = compose.onNodeWithTag("guardian-cooling-tile")
        ready.assertIsDisplayed()
        cooling.assertIsDisplayed()
        assertTrue(kotlin.math.abs(ready.fetchSemanticsNode().boundsInRoot.width -
            cooling.fetchSemanticsNode().boundsInRoot.width) <= 1f)
        compose.onNodeWithText("View Diagnostics").performScrollTo().assertIsDisplayed()
    }

    @Test fun coverSwipesAcrossEveryHomePageBeforeLibraryAndBack() {
        showLauncher()
        homeApp(0).assertIsDisplayed()
        pager.performTouchInput { swipeLeft() }
        homeApp(1).assertIsDisplayed()
        library.assertIsNotDisplayed()
        pager.performTouchInput { swipeLeft() }
        homeApp(2).assertIsDisplayed()
        library.assertIsNotDisplayed()
        val cover = compose.onNodeWithTag("nova-launcher-cover")
        val homeWallpaper = cover.captureToImage().toPixelMap().let { it[2, it.height / 2] }

        pager.performTouchInput { swipeLeft() }
        library.assertIsDisplayed()
        assertEquals(homeWallpaper, cover.captureToImage().toPixelMap().let { it[2, it.height / 2] })
        compose.onNodeWithTag("launcher-open-drawer").assertIsNotDisplayed()

        library.performTouchInput { swipeRight() }
        homeApp(2).assertIsDisplayed()
        pager.performTouchInput { swipeRight() }
        homeApp(1).assertIsDisplayed()
    }

    @Test fun foldSwipesBetweenItsLastHomePageAndLibrary() {
        var width by mutableStateOf(375.dp)
        compose.setContent {
            Box(Modifier.width(width).fillMaxHeight()) {
                NovaLauncherScreen(state, apps, emptyList(), emptyMap(), true, false, actions)
            }
        }
        compose.onNodeWithTag("nova-launcher-cover").assertIsDisplayed()
        repeat(3) { pager.performTouchInput { swipeLeft() } }
        library.assertIsDisplayed()

        compose.runOnIdle { width = 820.dp }
        compose.onNodeWithTag("nova-launcher-inner").assertIsDisplayed()
        homeApp(1).assertIsDisplayed()
        pager.performTouchInput { swipeLeft() }
        homeApp(2).assertIsDisplayed()
        pager.performTouchInput { swipeLeft() }
        library.assertIsDisplayed()
        library.performTouchInput { swipeRight() }
        homeApp(2).assertIsDisplayed()

        compose.runOnIdle { width = 375.dp }
        library.assertIsDisplayed()
        library.performTouchInput { swipeRight() }
        homeApp(2).assertIsDisplayed()
    }

    @Test fun coverBackLeavesLibraryForLastHomePageAndDrawerStillOpens() {
        showLauncher()
        repeat(3) { pager.performTouchInput { swipeLeft() } }
        library.assertIsDisplayed()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        homeApp(2).assertIsDisplayed()
        compose.onNodeWithTag("launcher-open-drawer").performClick()
        compose.onNodeWithTag("launcher-drawer-search").assertIsDisplayed()
    }

    @Test fun coverDrawerShortcutOpensPagerLibraryAndBackReturnsToDrawer() {
        showLauncher()
        compose.onNodeWithTag("launcher-open-drawer").performClick()
        compose.onNodeWithTag("launcher-open-library").performClick()
        library.assertIsDisplayed()
        pager.assertIsDisplayed()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("launcher-drawer-search").assertIsDisplayed()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        homeApp(0).assertIsDisplayed()
    }

    @Test fun coverDragsFromTrailingLibraryOntoLastHomePage() {
        showLauncher()
        repeat(2) { pager.performTouchInput { swipeLeft() } }
        homeApp(2).assertIsDisplayed()
        pager.performTouchInput { swipeLeft() }
        library.assertIsDisplayed()
        compose.onNodeWithTag("launcher-library-search").performTextInput(apps.last().label)
        val tile = compose.onNode(hasTestTag("launcher-app-${apps[3].component}") and
            hasAnyAncestor(hasTestTag("launcher-app-library")))
        val source = tile.fetchSemanticsNode().boundsInRoot.center
        val startDrag = Offset(0f, 52f)
        tile.performTouchInput {
            down(center)
            advanceEventTime(800)
            moveBy(startDrag)
        }
        compose.onNodeWithTag("launcher-drag-preview").assertIsDisplayed()
        try {
            compose.waitUntil(5_000) { homeApp(2).isDisplayed() }
        } catch (failure: ComposeTimeoutException) {
            throw AssertionError("Held Library drag did not reach the last Home page. " +
                "Library visible: ${library.isDisplayed()}, drag visible: " +
                "${compose.onNodeWithTag("launcher-drag-preview").isDisplayed()}\n" +
                pager.printToString(maxDepth = 5), failure)
        }
        val homeBounds = pager.fetchSemanticsNode().boundsInRoot
        val destination = Offset(homeBounds.center.x, homeBounds.top + homeBounds.height * .64f)
        pager.performTouchInput { moveBy(destination - source - startDrag) }
        compose.onNodeWithTag("launcher-drop-target").assertIsDisplayed()
        pager.performTouchInput { up() }
        compose.runOnIdle {
            assertEquals(listOf(listOf(items[0]), listOf(items[1]), listOf(items[2], items[3])), state.cover.pages)
        }
    }

    @Test fun coverHomeVerticalGesturesStillOpenDrawerAndWorkspace() {
        state = state.copy(cover = LauncherLayout(), workspaceGesture = LauncherGesture.SWIPE_DOWN)
        var workspaceOpens = 0
        showLauncher(actions.copy(openWorkspace = { workspaceOpens++ }))
        val gestures = compose.onNodeWithTag("launcher-home-gestures")
        gestures.performTouchInput { swipeUp(startY = height * .85f, endY = height * .15f) }
        compose.onNodeWithTag("launcher-drawer-search").assertIsDisplayed()

        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        gestures.performTouchInput { swipeDown(startY = height * .15f, endY = height * .85f) }
        compose.runOnIdle { assertEquals(1, workspaceOpens) }
        library.assertIsNotDisplayed()
    }

    @Test fun coverDockRemainsStationaryDuringAndAfterSwipeAndLibraryScroll() {
        state = state.copy(cover = state.cover.copy(dock = listOf(items[3])))
        var launched: String? = null
        showLauncher(actions.copy(launchApp = { launched = it }))
        val dock = compose.onNodeWithTag("launcher-shared-dock")
        val initial = dock.fetchSemanticsNode().boundsInRoot
        pager.performTouchInput {
            down(center)
            moveTo(center - Offset(width * .4f, 0f), 60)
        }
        assertEquals(initial, dock.fetchSemanticsNode().boundsInRoot)
        pager.performTouchInput { moveBy(Offset(-width * .3f, 0f), 80); up() }
        assertEquals(initial, dock.fetchSemanticsNode().boundsInRoot)
        pager.performTouchInput { swipeLeft() }
        assertEquals(initial, dock.fetchSemanticsNode().boundsInRoot)

        pager.performTouchInput { swipeLeft() }
        library.assertIsDisplayed()
        dock.assertIsNotDisplayed()
        library.performTouchInput { swipeUp() }
        dock.assertIsNotDisplayed()
        compose.onNodeWithContentDescription("See all Other apps").performClick()
        dock.assertIsNotDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()

        library.performTouchInput { swipeRight() }
        assertEquals(initial, dock.fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithTag("launcher-open-drawer").performClick()
        compose.onNodeWithTag("launcher-drawer-search").assertIsDisplayed()
        dock.assertIsNotDisplayed()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        assertEquals(initial, dock.fetchSemanticsNode().boundsInRoot)
        compose.onNode(hasTestTag("launcher-app-${apps[3].component}") and
            hasAnyAncestor(hasTestTag("launcher-dock-slot-0"))).performClick()
        compose.runOnIdle { assertEquals(apps[3].component, launched) }
    }

    @Test fun coverDrawerReturnRevealsDockOverMultipleFrames() {
        state = state.copy(cover = state.cover.copy(dock = listOf(items[3])))
        showLauncher()
        val dock = compose.onNodeWithTag("launcher-shared-dock")
        val homeBounds = dock.fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("launcher-open-drawer").performClick()
        dock.assertIsNotDisplayed()

        compose.mainClock.autoAdvance = false
        try {
            compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
            compose.mainClock.advanceTimeBy(96)
            val movingBounds = dock.fetchSemanticsNode().boundsInRoot
            assertTrue("Dock should still be entering Home", movingBounds.top > homeBounds.top)
            compose.mainClock.advanceTimeBy(240)
            assertEquals(homeBounds, dock.fetchSemanticsNode().boundsInRoot)
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }

    @Test fun coverLibraryHonorsSavedDockVisibilityWithoutShowingItInDrawer() {
        state = state.copy(dockOptions = LauncherDockOptions(showInLibrary = true))
        showLauncher()
        val dock = compose.onNodeWithTag("launcher-shared-dock")
        val homeBounds = dock.fetchSemanticsNode().boundsInRoot
        repeat(3) { pager.performTouchInput { swipeLeft() } }
        library.assertIsDisplayed()
        assertEquals(homeBounds, dock.fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithTag("launcher-library-drawer").performClick()
        dock.assertIsNotDisplayed()
    }

    @Test fun foldNativeArtworkScalesAcrossAllSizesAndKeepsAppLaunchWorking() {
        var launched: String? = null
        showLauncher(actions.copy(launchApp = { launched = it }))
        val active = items[1] as LauncherItem.App
        val artwork = compose.onNodeWithTag("launcher-app-artwork-${active.component}", useUnmergedTree = true)
        val full = LauncherIconSize.entries.map { size ->
            compose.runOnIdle { state = state.copy(appearance = state.appearance.copy(iconSize = size)) }
            artwork.fetchSemanticsNode().boundsInRoot.width
        }
        assertTrue(full.zipWithNext().all { (smaller, larger) -> smaller < larger })
        compose.runOnIdle {
            state = state.copy(appearance = state.appearance.copy(iconStyle = LauncherIconStyle.NOVA_TILES))
        }
        assertTrue(artwork.fetchSemanticsNode().boundsInRoot.width < full.last())
        homeApp(1).performClick()
        compose.runOnIdle { assertEquals(active.component, launched) }
    }

    @Test fun coverDockLockKeepsNormalLaunchAndBlocksLayoutEdits() {
        state = state.copy(cover = state.cover.copy(dock = listOf(items[3])))
        var launched: String? = null
        showLauncher(actions.copy(launchApp = { launched = it }))
        homeApp(0).performTouchInput { longClick() }
        compose.onNodeWithTag("launcher-icon-menu").assertIsDisplayed()
        compose.onNodeWithText("Move to dock").assertDoesNotExist()
        compose.onNodeWithText("Open app").performClick()
        homeApp(3).performClick()
        compose.runOnIdle {
            assertEquals(apps[3].component, launched)
            assertEquals(listOf(items[3]), state.cover.dock)
        }
    }

    @Test fun coverSanctuaryHidesOnlyRestrictedProfileAndRestoresItsSlots() {
        val main = LauncherApp("com.whatsapp/.Main", "com.whatsapp", "WhatsApp Main")
        val friends = LauncherApp("com.whatsapp/.Main#25", "com.whatsapp", "WhatsApp Friends")
        val restricted = LauncherItem.App(main.component)
        val allowed = LauncherItem.App(friends.component)
        state = state.copy(cover = LauncherLayout(pages = listOf(listOf(restricted, allowed)),
            dock = listOf(restricted, allowed)))
        var protectionActive by mutableStateOf(false)
        var launched: String? = null
        compose.setContent {
            NovaLauncherScreen(state, listOf(main, friends), emptyList(),
                mapOf(main.component to Decision.UNAPPROVED, friends.component to Decision.APPROVED),
                true, false, actions.copy(launchApp = { launched = it }), launcherProtected = protectionActive,
                appVisibility = SanctuaryAppVisibility())
        }
        val dockSlot = compose.onNodeWithTag("launcher-dock-slot-0")
        val initial = dockSlot.fetchSemanticsNode().boundsInRoot
        compose.runOnIdle { protectionActive = true }
        compose.onAllNodesWithContentDescription(main.label, substring = true).assertCountEquals(0)
        assertEquals(initial, dockSlot.fetchSemanticsNode().boundsInRoot)
        pager.performTouchInput { swipeLeft() }
        library.assertIsDisplayed()
        compose.onAllNodesWithContentDescription(main.label, substring = true).assertCountEquals(0)
        compose.onNodeWithTag("launcher-library-search").performTextInput("WhatsApp")
        compose.onAllNodesWithText(main.label, substring = true).assertCountEquals(0)
        compose.onNodeWithText(friends.label).performClick()
        compose.runOnIdle {
            assertEquals(friends.component, launched)
            protectionActive = false
        }
        library.performTouchInput { swipeRight() }
        assertEquals(initial, dockSlot.fetchSemanticsNode().boundsInRoot)
        compose.onNode(hasTestTag("launcher-app-${main.component}") and
            hasAnyAncestor(hasTestTag("launcher-dock-slot-0"))).assertIsDisplayed()
        compose.runOnIdle { assertEquals(listOf(restricted, allowed), state.cover.dock) }
    }

    @Test fun coverProtectedCardAtLargeTextShowsDeadlineWithoutPreview() = captureProtectedCard("cover")

    @Test fun foldProtectedCardAtLargeTextShowsDeadlineWithoutPreview() = captureProtectedCard("unfolded")

    private fun captureProtectedCard(display: String) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.6f)) {
                GuardianTheme {
                    Surface(Modifier.fillMaxWidth().testTag("guardian-held-fixture"),
                        color = GuardianGlass, contentColor = GuardianPrimary) {
                        Box(Modifier.padding(16.dp)) {
                            GuardianCooldownCopy(1_800_000_000_000L, potentiallyUpsetting = true)
                        }
                    }
                }
            }
        }
        compose.onNodeWithText("Message protected").assertIsDisplayed()
        compose.onNodeWithText("Potentially upsetting content is cooling down.").assertIsDisplayed()
        compose.onNodeWithText("Preview available after", substring = true).assertIsDisplayed()
        val capture = compose.onNodeWithTag("guardian-held-fixture").captureToImage()
        assertEquals(GuardianGlass, capture.toPixelMap()[2, 2])
        val output = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir") ?: return
        val folder = File(output, "guardian-held-77").apply { mkdirs() }
        val bitmap = capture.asAndroidBitmap()
        try {
            File(folder, "$display.png").outputStream().use { stream ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream))
            }
        } finally {
            bitmap.recycle()
        }
    }

    @Test fun conversationTrainingCardRequiresReviewAndConcealsHeldText() {
        var row by mutableStateOf(GuardianInboxRow("fixture", 1_800_000_000_000L, GuardianInboxSection.PROTECTED, 0))
        var preview by mutableStateOf<String?>(null)
        var label: GuardianAssessment? = null
        val message = "Synthetic authorised preview"
        compose.setContent {
            GuardianTheme {
                GuardianConversationCard(row, preview, busy = false,
                    onReview = { preview = message }, onLabel = { label = it })
            }
        }
        compose.onNodeWithText(message).assertDoesNotExist()
        compose.onNodeWithText("Upsetting").assertDoesNotExist()
        compose.onNodeWithText("Review message").performClick()
        compose.onNodeWithText(message).assertIsDisplayed()
        compose.onNodeWithText("Not Upsetting").performClick()
        compose.runOnIdle { assertEquals(null, label) }
        compose.onNodeWithText("Save learning example").performClick()
        compose.runOnIdle {
            assertEquals(GuardianAssessment.LOW_RISK, label)
            row = row.copy(section = GuardianInboxSection.COOLING, remainingMillis = 60_000L)
        }
        compose.onNodeWithText("Message protected").assertIsDisplayed()
        compose.onNodeWithText(message).assertDoesNotExist()
        compose.onNodeWithText("Not Upsetting").assertDoesNotExist()
        compose.onNodeWithText("Review message").assertDoesNotExist()
    }

    private fun saveGuardianCheckpoint(display: String) {
        val dashboard = compose.onNodeWithTag("launcher-guardian-home")
        val capture = dashboard.captureToImage()
        assertEquals(Color(0xFF0D1727), capture.toPixelMap()[2, 2])
        val output = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir") ?: return
        val folder = File(output, "guardian-home-77").apply { mkdirs() }
        val bitmap = capture.asAndroidBitmap()
        try {
            File(folder, "$display.png").outputStream().use { stream ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream))
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun showLauncher(uiActions: LauncherUiActions = actions) = compose.setContent {
        NovaLauncherScreen(state, apps, emptyList(), emptyMap(), true, false, uiActions)
    }

    private fun homeApp(index: Int) = compose.onNode(hasTestTag("launcher-app-${apps[index].component}") and
        !hasAnyAncestor(hasTestTag("launcher-app-library")))
}
