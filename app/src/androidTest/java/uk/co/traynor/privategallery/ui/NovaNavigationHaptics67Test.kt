package uk.co.traynor.privategallery.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import uk.co.traynor.privategallery.core.backup.BackupFixture62
import uk.co.traynor.privategallery.core.home2.SpaceContext

class NovaNavigationHaptics67Test {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun hapticPreferenceDefaultsOnAndControlsRemainUsableWithItOff() {
        val settings = compose.activity.getSharedPreferences("nova-navigation", 0)
        val old = settings.all["haptics"] as? Boolean
        settings.edit().remove("haptics").commit()
        val fixtureRoot = BackupFixture62.temporary("nova-navigation-haptics")
        try {
            BackupFixture62(fixtureRoot).use { fixture ->
                val homeContext = compose.runOnIdle { SpaceContext(compose.activity, fixture.access) }
                homeContext.use {
                    var clicks = 0
                    compose.setContent {
                        androidx.compose.foundation.layout.Column {
                            CompositionLocalProvider(LocalContext provides homeContext) {
                                NovaNavigationHapticsSetting()
                            }
                            NovaNavigationRow({}, { clicks++ }, { clicks++ })
                        }
                    }
                    val toggle = compose.onNodeWithTag("nova-navigation-haptics")
                    toggle.assertIsOn().performClick().assertIsOff()
                    compose.onNodeWithTag("nova-home").performClick()
                    compose.onNodeWithTag("nova-back").performClick()
                    compose.runOnIdle {
                        assertEquals(false, settings.getBoolean("haptics", true))
                        assertEquals(2, clicks)
                    }
                }
            }
        } finally {
            fixtureRoot.deleteRecursively()
            val edit = settings.edit()
            if (old == null) edit.remove("haptics") else edit.putBoolean("haptics", old)
            edit.commit()
        }
    }
}
