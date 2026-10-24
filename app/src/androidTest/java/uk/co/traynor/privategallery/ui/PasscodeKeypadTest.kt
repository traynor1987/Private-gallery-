package uk.co.traynor.privategallery.ui

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PasscodeKeypadTest {
 @get:Rule val compose=createComposeRule()
 @Test fun digitsAreMaskedBackspaceWorksAndVariablePinRequiresConfirmation() {
  var pin by mutableStateOf("");var submitted=""
  compose.setContent {PasscodeKeypad(pin,{pin=it},{submitted=pin})}
  compose.onNodeWithText("1").performClick();compose.onNodeWithText("2").performClick()
  compose.onNodeWithTag("passcode-indicator").assertTextEquals("••")
  compose.onNodeWithContentDescription("Delete last digit").performClick()
  compose.runOnIdle {assertEquals("1",pin);assertEquals("",submitted)}
  compose.onNodeWithText("Unlock").performClick()
  compose.runOnIdle {assertEquals("1",submitted)}
  compose.onNode(hasSetTextAction()).assertDoesNotExist()
 }
 @Test fun keypadControlsFitFoldCoverInnerAndLandscapeViewports() {
  var viewport by mutableStateOf(320 to 640)
  compose.setContent {Box(Modifier.requiredSize(viewport.first.dp,viewport.second.dp)){PasscodeKeypad("",{}, {})}}
  listOf(320 to 640,840 to 700,640 to 320).forEach {size ->
   compose.runOnIdle {viewport=size}
   (0..9).forEach {compose.onNodeWithText(it.toString(),substring=false).assertIsDisplayed()}
   compose.onNodeWithContentDescription("Delete last digit").assertIsDisplayed()
   compose.onNodeWithText("Enter passcode").assertIsDisplayed()
  }
 }
 @Test fun knownFixedLengthSubmitsOnceAtBoundary() {
  var pin by mutableStateOf("");var submits=0
  compose.setContent {PasscodeKeypad(pin,{pin=it},{submits++},expectedLength=4)}
  listOf("1","2","3").forEach {compose.onNodeWithText(it).performClick()}
  compose.runOnIdle {assertEquals(0,submits)}
  compose.onNodeWithText("4").performClick()
  compose.runOnIdle {assertEquals(1,submits)}
 }
}
