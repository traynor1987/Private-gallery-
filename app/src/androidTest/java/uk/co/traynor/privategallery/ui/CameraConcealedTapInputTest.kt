package uk.co.traynor.privategallery.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CameraConcealedTapInputTest {
    @get:Rule val compose=createComposeRule()
    @Test fun swipeThenRapidTapsUseFreshGestureAndLatestCallback() {
        var captures=0
        var enabled by mutableStateOf(false)
        var revision by mutableIntStateOf(1)
        compose.setContent {
            val current=revision
            Box(Modifier.size(320.dp,500.dp).testTag("black-tap-area")
                .cameraConcealedTapInput(enabled) {captures+=current})
        }
        val area=compose.onNodeWithTag("black-tap-area")
        area.performTouchInput {click()}
        compose.runOnIdle {assertEquals(0,captures);enabled=true}
        area.performTouchInput {swipeLeft()}
        compose.runOnIdle {assertEquals(0,captures);revision=2}
        area.performTouchInput {click();click()}
        compose.runOnIdle {assertEquals(4,captures);enabled=false}
        area.performTouchInput {click()}
        compose.runOnIdle {assertEquals(4,captures)}
    }
}
