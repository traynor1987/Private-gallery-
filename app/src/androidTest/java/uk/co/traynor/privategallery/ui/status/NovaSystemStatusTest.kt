package uk.co.traynor.privategallery.ui.status

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.ViewCompat
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.function.Consumer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.traynor.privategallery.core.ui.status.SystemStatusProvider
import uk.co.traynor.privategallery.core.ui.status.SystemStatusSnapshot

@RunWith(AndroidJUnit4::class)
class NovaSystemStatusTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun exactBatteryAndRadioChangesFitFoldHeaderAndLandscape() {
        val provider = FakeStatusProvider()
        setBarsVisible(false)
        compose.setContent {
            MaterialTheme {
                Box(Modifier.requiredSize(196.dp, 32.dp).testTag("status-slot")) {
                    NovaSystemStatus(provider = provider)
                }
            }
        }
        compose.waitUntil { provider.listener != null }
        compose.runOnIdle { provider.send(snapshot(0, false, false, "5G")) }
        compose.onNodeWithTag("nova-status-percent", useUnmergedTree = true).assertTextEquals("0%")
        compose.onNodeWithTag("nova-status-radio", useUnmergedTree = true).assertTextEquals("5G")
        compose.runOnIdle { provider.send(snapshot(100, true, true, "LTE")) }
        compose.onNodeWithTag("nova-status-percent", useUnmergedTree = true).assertTextEquals("100%")
        compose.onNodeWithTag("nova-status-radio", useUnmergedTree = true).assertTextEquals("LTE")
        compose.onNodeWithTag("nova-system-status").assertIsDisplayed()
        val slot = compose.onNodeWithTag("status-slot").fetchSemanticsNode().boundsInRoot
        val row = compose.onNodeWithTag("nova-system-status").fetchSemanticsNode().boundsInRoot
        assertTrue(row.left >= slot.left && row.right <= slot.right)
        assertTrue(row.top >= slot.top && row.bottom <= slot.bottom)
    }

    @Test fun batteryGlyphActuallyFillsFromZeroToFullAndOmitsUnknown() {
        val provider = FakeStatusProvider()
        setBarsVisible(false)
        compose.setContent { MaterialTheme { NovaSystemStatus(provider = provider) } }
        compose.waitUntil { provider.listener != null }
        compose.runOnIdle { provider.send(snapshot(0, false, false, null)) }
        val empty = compose.onNodeWithTag("nova-status-battery", useUnmergedTree = true).captureToImage()
        val emptyInterior = empty.toPixelMap()[empty.width / 2, empty.height / 2]
        compose.runOnIdle { provider.send(snapshot(100, false, false, null)) }
        val full = compose.onNodeWithTag("nova-status-battery", useUnmergedTree = true).captureToImage()
        val fullInterior = full.toPixelMap()[full.width / 2, full.height / 2]
        assertEquals(empty.width, full.width)
        assertEquals(empty.height, full.height)
        assertNotEquals("Battery interior must reflect the actual level", emptyInterior, fullInterior)
        compose.runOnIdle { provider.send(SystemStatusSnapshot(null, false, false, false, false,
            SystemStatusSnapshot.Transport.UNKNOWN, false, null)) }
        compose.onNodeWithTag("nova-status-battery", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun allIndicatorsFitCompactHeaderAtLargeFontScale() {
        val provider = FakeStatusProvider()
        setBarsVisible(false)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.6f)) {
                MaterialTheme {
                    Box(Modifier.requiredSize(196.dp, 32.dp).testTag("status-slot")) {
                        NovaSystemStatus(provider = provider)
                    }
                }
            }
        }
        compose.waitUntil { provider.listener != null }
        compose.runOnIdle { provider.send(SystemStatusSnapshot(100, true, true, true, false,
            SystemStatusSnapshot.Transport.MOBILE, false, "LTE+")) }
        compose.onNodeWithTag("nova-status-percent", useUnmergedTree = true).assertTextEquals("100%")
        val slot = compose.onNodeWithTag("status-slot").fetchSemanticsNode().boundsInRoot
        val row = compose.onNodeWithTag("nova-system-status").fetchSemanticsNode().boundsInRoot
        assertTrue(row.left >= slot.left && row.right <= slot.right)
        assertTrue(row.top >= slot.top && row.bottom <= slot.bottom)
    }

    @Test fun noInternetAndAirplaneRemainDistinctFromWifiAssociation() {
        val provider = FakeStatusProvider()
        setBarsVisible(false)
        compose.setContent { MaterialTheme { NovaSystemStatus(provider = provider) } }
        compose.waitUntil { provider.listener != null }
        compose.runOnIdle { provider.send(SystemStatusSnapshot(73, true, false, true, false,
            SystemStatusSnapshot.Transport.WIFI, false, null)) }
        compose.onNodeWithTag("nova-system-status").assertContentDescriptionEquals(
            "Battery 73 percent, Charger connected, Battery saver, Wi-Fi · No internet")
        compose.runOnIdle { provider.send(SystemStatusSnapshot(73, false, false, false, true,
            SystemStatusSnapshot.Transport.DISCONNECTED, false, null)) }
        compose.onNodeWithTag("nova-system-status").assertContentDescriptionEquals(
            "Battery 73 percent, Airplane mode")
    }

    @Test fun actualAndroidStatusBarAndCoveredSurfaceStopSubscriptions() {
        val provider = FakeStatusProvider()
        val enabled = mutableStateOf(true)
        setBarsVisible(false)
        compose.setContent { MaterialTheme { NovaSystemStatus(enabled = enabled.value, provider = provider) } }
        compose.waitUntil { provider.opens == 1 }
        compose.runOnIdle { provider.send(snapshot(73, false, false, null)) }
        compose.onNodeWithTag("nova-system-status").assertIsDisplayed()
        setBarsVisible(true)
        compose.onNodeWithTag("nova-system-status").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, provider.closes) }
        setBarsVisible(false)
        compose.waitUntil { provider.opens == 2 }
        compose.runOnIdle { enabled.value = false }
        compose.onNodeWithTag("nova-system-status").assertDoesNotExist()
        compose.runOnIdle { assertEquals(2, provider.closes) }
    }

    @Test fun backgroundClosesProviderAndResumeResubscribes() {
        val provider = FakeStatusProvider()
        setBarsVisible(false)
        compose.setContent { MaterialTheme { NovaSystemStatus(provider = provider) } }
        compose.waitUntil { provider.opens == 1 }
        repeat(3) { cycle ->
            // Do not wait for a Compose idle frame between lifecycle events: Android may
            // resume before a paused composition commits, and must still re-subscribe.
            compose.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
            compose.waitUntil { provider.closes == cycle + 1 }
            compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            compose.waitUntil { provider.opens == cycle + 2 }
            compose.runOnIdle {
                assertEquals(cycle + 2, provider.opens)
                assertEquals(cycle + 1, provider.closes)
                provider.send(snapshot(73, false, false, null))
            }
            compose.onNodeWithTag("nova-system-status").assertIsDisplayed()
        }
        // Also cover the non-coalesced path: the paused observer effect has been disposed.
        compose.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        compose.waitUntil { provider.closes == 4 }
        compose.waitForIdle()
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.waitUntil { provider.opens == 5 }
        compose.runOnIdle {
            assertEquals(5, provider.opens)
            assertEquals(4, provider.closes)
            provider.send(snapshot(42, true, false, null))
        }
        compose.onNodeWithTag("nova-status-percent", useUnmergedTree = true).assertTextEquals("42%")
    }

    private fun setBarsVisible(visible: Boolean) {
        compose.runOnUiThread {
            val view = compose.activity.window.decorView
            val controller = WindowCompat.getInsetsController(compose.activity.window, view)
            if (visible) controller.show(WindowInsetsCompat.Type.statusBars())
            else controller.hide(WindowInsetsCompat.Type.statusBars())
            ViewCompat.requestApplyInsets(view)
        }
        compose.waitUntil {
            ViewCompat.getRootWindowInsets(compose.activity.window.decorView)
                ?.isVisible(WindowInsetsCompat.Type.statusBars()) == visible
        }
        compose.waitForIdle()
    }

    private fun snapshot(percent: Int, plugged: Boolean, saver: Boolean, radio: String?) =
        SystemStatusSnapshot(percent, plugged, plugged, saver, false,
            SystemStatusSnapshot.Transport.MOBILE, true, radio)

    private class FakeStatusProvider : SystemStatusProvider {
        @Volatile var opens = 0
        @Volatile var closes = 0
        @Volatile var listener: Consumer<SystemStatusSnapshot>? = null
        override fun subscribe(observer: Consumer<SystemStatusSnapshot>): AutoCloseable {
            opens++
            listener = observer
            return AutoCloseable { closes++; listener = null }
        }
        fun send(value: SystemStatusSnapshot) = listener!!.accept(value)
    }
}
