package uk.co.traynor.privategallery.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import uk.co.traynor.privategallery.core.hidden.*

class HiddenRestoreDialogTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private fun fixture(block:(HiddenSession,HiddenRestoreAccess,()->Int)->Unit) {
        val session=HiddenSession(android.os.SystemClock::elapsedRealtime)
        session.complete(session.beginAuthentication(),ByteArray(32){7})
        val access=HiddenRestoreAccess(InstrumentationRegistry.getInstrumentation().targetContext,session)
        var deliveries=0
        compose.setContent {MaterialTheme {HiddenRestoreDialog(access,listOf("synthetic-id")){deliveries++}}}
        try {block(session,access){deliveries}}finally{compose.runOnIdle {access.close();session.lock()}}
    }
    @Test fun choicesAreExplicitAndPrimaryRequiresFreshPinWithoutChangingHidden()=fixture {session,access,count ->
        listOf("Copy to Phone Gallery","Move to Phone Gallery","Copy to Primary Vault","Move to Primary Vault").forEach {compose.onNodeWithText(it).performScrollTo().assertIsDisplayed()}
        compose.onNodeWithText("Copy to Primary Vault").performScrollTo().performClick()
        compose.onNodeWithText("Enter passcode").assertIsDisplayed()
        compose.onNodeWithText("Restore to Primary Vault").assertIsDisplayed()
        compose.runOnIdle {assertTrue(session.isUnlocked);assertTrue(access.isActive())}
        compose.onNodeWithText("Cancel").performClick()
        compose.waitUntil(3000){count()==1}
        compose.runOnIdle {assertFalse(access.isActive());assertEquals(1,count());assertTrue(session.isUnlocked)}
    }
    @Test fun moveRequiresSeparateConfirmationAndLockRevokesDialogOnce()=fixture {session,access,count ->
        compose.onNodeWithText("Move to Phone Gallery").performScrollTo().performClick()
        compose.onNodeWithText("Confirm Move to Phone Gallery").assertIsDisplayed()
        compose.runOnIdle {session.lock()}
        compose.waitUntil(3000){count()==1}
        compose.runOnIdle {assertFalse(access.isActive());assertEquals(1,count())}
    }
}
