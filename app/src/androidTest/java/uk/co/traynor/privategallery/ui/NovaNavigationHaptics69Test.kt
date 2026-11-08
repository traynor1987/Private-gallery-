package uk.co.traynor.privategallery.ui

import android.content.Context
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import uk.co.traynor.privategallery.home2.NovaNavigation
import uk.co.traynor.privategallery.home2.PreviewHeader
import uk.co.traynor.privategallery.home2.PreviewShellData

class NovaNavigationHaptics69Test {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private class RecordingView(context: Context) : View(context) {
        val calls = mutableListOf<Pair<Int, Int>>()

        override fun performHapticFeedback(feedbackConstant: Int, flags: Int): Boolean {
            calls += feedbackConstant to flags
            return true
        }
    }

    private val preferences get() = compose.activity.getSharedPreferences("nova-navigation", Context.MODE_PRIVATE)
    private var previous: Boolean? = null
    private lateinit var view: RecordingView
    private val keyFeedback = HapticFeedbackConstants.VIRTUAL_KEY to 0

    @Before fun setUp() {
        previous = preferences.all["haptics"] as? Boolean
        check(preferences.edit().putBoolean("haptics", true).commit())
        view = compose.runOnIdle { RecordingView(compose.activity).apply { isHapticFeedbackEnabled = true } }
    }

    @After fun restore() {
        val edit = preferences.edit()
        if (previous == null) edit.remove("haptics") else edit.putBoolean("haptics", checkNotNull(previous))
        check(edit.commit())
    }

    @Test fun bottomRowRequestsOnePlatformKeyEffectOnlyForHomeAndBack() {
        val actions = mutableListOf<String>()
        var enabled by mutableStateOf(true)
        compose.setContent {
            CompositionLocalProvider(LocalView provides view) {
                PrivateGalleryTheme {
                    NovaNavigationRow(
                        onRecents = { actions += "recents" },
                        onHome = { actions += "home" },
                        onBack = { actions += "back" },
                        navigationEnabled = enabled,
                    )
                }
            }
        }

        listOf("recents", "home", "back").forEach { action ->
            compose.onNodeWithTag("nova-$action").performTouchInput { click() }
        }
        compose.runOnIdle {
            assertEquals(listOf("recents", "home", "back"), actions)
            assertEquals(listOf(keyFeedback, keyFeedback), view.calls)
            enabled = false
        }
        compose.onNodeWithTag("nova-home").assertIsNotEnabled().performTouchInput { click() }
        compose.runOnIdle {
            assertEquals(listOf("recents", "home", "back"), actions)
            assertEquals(listOf(keyFeedback, keyFeedback), view.calls)
        }
    }

    @Test fun headersAndGestureStripUseTheSameEffect() {
        val actions = mutableListOf<String>()
        val shell = compose.runOnIdle {
            val activity = compose.activity
            val shellContext = object : android.content.ContextWrapper(activity) {
                override fun getSharedPreferences(name: String, mode: Int) =
                    activity.getSharedPreferences("nova-navigation-haptics69-test-shell", mode)
            }
            PreviewShellData(shellContext).also { it.configure(gestures = true) }
        }
        compose.setContent {
            CompositionLocalProvider(LocalView provides view) {
                PrivateGalleryTheme {
                    Column {
                        NovaAppHeader("Vault", onHome = { actions += "vault home" }, onBack = { actions += "vault back" })
                        PreviewHeader("Nova Settings", "Navigation", onBack = { actions += "settings back" })
                        NovaNavigation(shell, {}, { actions += "strip home" }, { actions += "strip back" })
                    }
                }
            }
        }

        compose.onNodeWithContentDescription("Nova Home").performTouchInput { click() }
        compose.onNodeWithContentDescription("Back").performTouchInput { click() }
        compose.onNodeWithContentDescription("Back to Home").performTouchInput { click() }
        compose.onNodeWithTag("nova-gesture-strip").performTouchInput { click() }
        compose.runOnIdle {
            assertEquals(listOf("vault home", "vault back", "settings back", "strip home"), actions)
            assertEquals(List(4) { keyFeedback }, view.calls)
        }
    }

    @Test fun settingsTestUsesTheSharedEffectAndRespectsPersistedToggleAndViewPolicy() {
        var showSettings by mutableStateOf(true)
        var navigations = 0
        compose.setContent {
            CompositionLocalProvider(LocalView provides view) {
                PrivateGalleryTheme {
                    Column {
                        if (showSettings) NovaNavigationHapticsSetting()
                        NovaNavigationRow({}, { navigations++ }, {})
                    }
                }
            }
        }

        val toggle = compose.onNodeWithTag("nova-navigation-haptics")
        val testAction = compose.onNodeWithTag("nova-navigation-haptics-test")
        toggle.assertIsOn()
        testAction.performTouchInput { click() }
        compose.runOnIdle {
            assertEquals(listOf(keyFeedback), view.calls)
            view.isHapticFeedbackEnabled = false
        }
        testAction.performTouchInput { click() }
        compose.onNodeWithTag("nova-home").performTouchInput { click() }
        compose.runOnIdle {
            assertEquals(listOf(keyFeedback), view.calls)
            assertEquals(1, navigations)
            view.isHapticFeedbackEnabled = true
        }

        toggle.performClick().assertIsOff()
        testAction.assertIsNotEnabled()
        compose.onNodeWithTag("nova-home").performTouchInput { click() }
        compose.runOnIdle {
            assertEquals(listOf(keyFeedback), view.calls)
            assertEquals(2, navigations)
            assertEquals(false, preferences.getBoolean("haptics", true))
            showSettings = false
        }
        toggle.assertDoesNotExist()
        compose.runOnIdle { showSettings = true }
        toggle.assertIsOff()
        testAction.assertIsNotEnabled()
    }
}
