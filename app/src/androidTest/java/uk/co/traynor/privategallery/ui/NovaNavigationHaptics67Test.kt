package uk.co.traynor.privategallery.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class NovaNavigationHaptics67Test {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun hapticPreferenceDefaultsOnAndControlsRemainUsableWithItOff() {
        val settings = compose.activity.getSharedPreferences("nova-navigation", 0)
        val old = settings.all["haptics"] as? Boolean
        settings.edit().remove("haptics").commit()
        try {
            var clicks = 0
            compose.setContent {
                androidx.compose.foundation.layout.Column {
                    NovaNavigationHapticsSetting()
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
        } finally {
            val edit = settings.edit()
            if (old == null) edit.remove("haptics") else edit.putBoolean("haptics", old)
            edit.commit()
        }
    }
}
