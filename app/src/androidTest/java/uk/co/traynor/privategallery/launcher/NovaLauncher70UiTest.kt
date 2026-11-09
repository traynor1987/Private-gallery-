package uk.co.traynor.privategallery.launcher

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import uk.co.traynor.privategallery.core.sanctuary.SanctuaryLauncherPolicy
import uk.co.traynor.privategallery.ui.BrowserViewportRule

class NovaLauncher70UiTest {
    private val compose = createAndroidComposeRule<ComponentActivity>()
    private val viewport = BrowserViewportRule { method ->
        when {
            method.startsWith("cover") -> 375 to 850
            method.startsWith("inner") -> 820 to 920
            else -> null
        }
    }
    @get:Rule val rules: TestRule = RuleChain.outerRule(viewport).around(compose)

    private val phone = LauncherApp("example.phone/.Main", "example.phone", "Phone test app")
    private val music = LauncherApp("example.music/.Main", "example.music", "Music test app")
    private val apps = listOf(phone, music)

    @Test fun wizardPreviewsTemplateBeforeExplicitDefaultHomeSelection() {
        var state by mutableStateOf(NovaLauncherState())
        var selectedHome = false
        compose.setContent {
            NovaLauncherScreen(state, apps, emptyList(), emptyMap(), false, true, actions(
                save = { state = it; true },
                selectHome = { selectedHome = true },
            ))
        }

        compose.onNodeWithText("Choose Template").performClick()
        compose.onNodeWithText("Gaming").performScrollTo().performClick()
        next()
        compose.onNodeWithText("Shared responsive layout").performScrollTo().performClick()
        next()
        compose.onNodeWithText("Appearance").assertExists()
        next()
        compose.onNodeWithText("App drawer and search").assertExists()
        next()
        compose.onNodeWithText("Sanctuary protection").assertExists()
        next()
        compose.onNodeWithText("Preview and activate").assertExists()
        compose.runOnIdle {
            assertEquals(false, state.configured)
            assertEquals(false, selectedHome)
        }
        compose.onNodeWithText("Use Nova Launcher").performScrollTo().performClick()
        compose.runOnIdle {
            assertTrue(state.configured)
            assertEquals(false, state.separateDisplays)
            assertTrue(selectedHome)
        }
    }

    @Test fun drawerSearchExposesBlockedAppForPlatformDecisionAndWorkspaceShortcut() {
        var requested: String? = null
        var workspace = false
        val state = NovaLauncherState(configured = true)
        compose.setContent {
            NovaLauncherScreen(state, apps, emptyList(),
                mapOf(phone.packageName to SanctuaryLauncherPolicy.Decision.ESSENTIAL,
                    music.packageName to SanctuaryLauncherPolicy.Decision.UNAPPROVED),
                true, true, actions(
                    launchApp = { requested = it },
                    openWorkspace = { workspace = true },
                ))
        }

        compose.onNodeWithContentDescription("Open NovaOS workspace").performClick()
        compose.runOnIdle { assertTrue(workspace) }
        compose.onNodeWithTag("launcher-open-drawer").performClick()
        compose.onNodeWithTag("launcher-drawer-search").performTextInput("Music")
        compose.onNodeWithTag("launcher-app-${music.component}")
            .assert(hasContentDescription("Music test app, blocked by Sanctuary")).performClick()
        compose.onNodeWithTag("launcher-app-${phone.component}").assertDoesNotExist()
        compose.runOnIdle { assertEquals(music.component, requested) }
    }

    @Test fun approvedOnlyDrawerHidesBlockedSearchResultsWhileProtectionIsActive() {
        var protectionActive by mutableStateOf(true)
        compose.setContent {
            NovaLauncherScreen(
                NovaLauncherState(configured = true, hideBlocked = true), apps, emptyList(),
                mapOf(phone.packageName to SanctuaryLauncherPolicy.Decision.ESSENTIAL,
                    music.packageName to SanctuaryLauncherPolicy.Decision.UNAPPROVED),
                true, true, actions(), launcherProtected = protectionActive,
            )
        }
        compose.onNodeWithTag("launcher-open-drawer").performClick()
        compose.onNodeWithTag("launcher-drawer-search").performTextInput("Music")
        compose.onNodeWithTag("launcher-app-${music.component}").assertDoesNotExist()
        compose.onNodeWithText("No apps match your search").assertIsDisplayed()
        compose.runOnIdle { protectionActive = false }
        compose.onNodeWithTag("launcher-app-${music.component}").assertIsDisplayed()
    }

    @Test fun copyWizardExplainsSamsungLayoutLimitBeforePreview() {
        var state by mutableStateOf(NovaLauncherState())
        compose.setContent {
            NovaLauncherScreen(state, apps, emptyList(), emptyMap(), false, true, actions(
                save = { state = it; true },
            ))
        }
        compose.onNodeWithText("Copy current Home").performScrollTo().performClick()
        compose.onNodeWithText("Samsung One UI", substring = true).assertExists()
        compose.onNodeWithText("exact", substring = true).assertExists()
        compose.onNodeWithTag("launcher-copy-${phone.component}").performScrollTo().assertIsOff()
        compose.onNodeWithTag("launcher-copy-${music.component}").performScrollTo().assertIsOff()
            .performClick().assertIsOn()
        repeat(5) { next() }
        compose.onNodeWithText("Continue using Samsung Home").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(listOf(LauncherItem.App(music.component)), state.cover.pages.single())
            assertEquals(listOf(LauncherItem.App(music.component)), state.inner.pages.single())
            assertEquals(listOf(LauncherItem.Workspace), state.cover.dock)
        }
    }

    @Test fun coverShowsAndEditsItsOwnPageWithoutChangingInnerLayout() = exerciseDisplay(wide = false)

    @Test fun innerShowsAndEditsItsOwnPageWithoutChangingCoverLayout() = exerciseDisplay(wide = true)

    @Test fun coverDragReordersShortcutsAndReviewedCleanupCanBeUndone() {
        val phoneItem = LauncherItem.App(phone.component)
        val musicItem = LauncherItem.App(music.component)
        val original = LauncherLayout(pages = listOf(listOf(phoneItem, musicItem)))
        var state by mutableStateOf(NovaLauncherState(configured = true, cover = original))
        compose.setContent {
            NovaLauncherScreen(state, apps, emptyList(), emptyMap(), true, false, actions(
                save = { state = it; true },
            ))
        }
        compose.onNodeWithContentDescription("Launcher menu").performClick()
        compose.onNodeWithText("Edit Home").performClick()
        val editor = compose.onNodeWithTag("launcher-layout-editor")
        editor.performScrollToNode(hasTestTag("launcher-drag-page-0"))
        compose.onNodeWithTag("launcher-drag-page-0").performTouchInput {
            down(center)
            advanceEventTime(800L)
            moveBy(Offset(0f, 110.dp.toPx()))
            up()
        }
        compose.runOnIdle { assertEquals(listOf(musicItem, phoneItem), state.cover.pages.single()) }

        editor.performScrollToNode(hasText("Review shortcuts"))
        compose.onNodeWithText("Review shortcuts").performClick()
        compose.onNodeWithTag("launcher-cleanup-0-0").assertIsOff().performClick().assertIsOn()
        compose.onNodeWithText("Remove 1 shortcut").performClick()
        compose.runOnIdle { assertEquals(listOf(phoneItem), state.cover.pages.single()) }
        editor.performScrollToNode(hasText("Undo cleanup"))
        compose.onNodeWithText("Undo cleanup").performClick()
        compose.runOnIdle { assertEquals(listOf(musicItem, phoneItem), state.cover.pages.single()) }
    }

    private fun exerciseDisplay(wide: Boolean) {
        val cover = LauncherLayout(pages = listOf(listOf(LauncherItem.App(phone.component))))
        val inner = LauncherLayout(pages = listOf(listOf(LauncherItem.App(music.component))))
        var state by mutableStateOf(NovaLauncherState(configured = true, cover = cover, inner = inner))
        compose.setContent {
            NovaLauncherScreen(state, apps, emptyList(), emptyMap(), true, false, actions(
                save = { state = it; true },
            ))
        }
        val shown = if (wide) music else phone
        val hidden = if (wide) phone else music
        compose.onNodeWithTag(if (wide) "nova-launcher-inner" else "nova-launcher-cover").assertIsDisplayed()
        compose.onNodeWithTag("launcher-app-${shown.component}").assertIsDisplayed()
        compose.onNodeWithTag("launcher-app-${hidden.component}").assertDoesNotExist()
        compose.onNodeWithContentDescription("Launcher menu").performClick()
        compose.onNodeWithText("Edit Home").performClick()
        compose.onNodeWithText("Add page").performClick()
        compose.onNodeWithTag("launcher-layout-page").assertTextEquals("Page 2 of 2")
        compose.runOnIdle {
            assertEquals(2, state.layout(wide).pages.size)
            assertEquals(if (wide) cover else inner, state.layout(!wide))
        }
    }

    private fun next() = compose.onNodeWithTag("launcher-setup-next").performClick()

    private fun actions(
        save: (NovaLauncherState) -> Boolean = { true },
        selectHome: () -> Unit = {},
        launchApp: (String) -> Unit = {},
        openWorkspace: () -> Unit = {},
    ) = LauncherUiActions(
        save = save, launchApp = launchApp, launchShortcut = {}, openWorkspace = openWorkspace,
        selectHome = selectHome, emergencyPhone = {}, reviewSanctuary = {}, pauseSanctuary = {},
        setOutsideProtection = {}, chooseWallpaper = {}, addWidget = {}, removeWidget = {},
        exportLayout = {}, importLayout = {}, renderWidget = { _, _ -> }, continueWithCurrentHome = {},
    )
}
