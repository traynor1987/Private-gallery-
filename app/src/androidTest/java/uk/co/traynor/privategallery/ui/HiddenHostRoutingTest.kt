package uk.co.traynor.privategallery.ui

import androidx.compose.runtime.MutableState
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import uk.co.traynor.privategallery.MainActivity
import uk.co.traynor.privategallery.ProtectedSessionState
import uk.co.traynor.privategallery.Route

class HiddenHostRoutingTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun returnToNormalVaultRevokesHiddenAndChecksPrimaryAuthority() {
        val state=ViewModelProvider(compose.activity)[ProtectedSessionState::class.java]
        compose.runOnIdle {
            val returnToPrimary=MainActivity::class.java.getDeclaredMethod("returnToPrimaryVault").apply {isAccessible=true}
            @Suppress("UNCHECKED_CAST")
            val route=MainActivity::class.java.getDeclaredField("route\$delegate").apply {isAccessible=true}.get(compose.activity) as MutableState<Route>
            try {
                state.key=ByteArray(32){9};state.session.unlock()
                state.hidden.select();state.hidden.session.complete(state.hidden.session.beginAuthentication(),ByteArray(32){7})
                val op=state.hidden.session.beginOperation()
                try {
                    returnToPrimary.invoke(compose.activity)
                    assertFalse(state.hidden.session.isUnlocked);assertFalse(state.hidden.selected)
                    assertFalse(state.hidden.session.isCurrent(op));assertEquals(Route.VAULT,route.value)
                } finally {op.close()}
                state.key?.fill(0);state.key=null;state.session.lock()
                state.hidden.select();state.hidden.session.complete(state.hidden.session.beginAuthentication(),ByteArray(32){7})
                returnToPrimary.invoke(compose.activity)
                assertFalse(state.hidden.session.isUnlocked);assertFalse(state.hidden.selected)
                assertEquals(if(uk.co.traynor.privategallery.core.security.PinVaultKeyStore(compose.activity).isConfigured) Route.LOCK else Route.SETUP,route.value)
            } finally {state.hidden.leave();state.key?.fill(0);state.key=null;state.session.lock()}
        }
    }
    @Test fun restoredVaultRouteInHiddenModeNeverComposesPrimaryVault() {
        val state=ViewModelProvider(compose.activity)[ProtectedSessionState::class.java]
        try {
            compose.runOnIdle {
                state.key=ByteArray(32){9};state.session.unlock()
                state.hidden.selected=true;state.hidden.visible=false
                @Suppress("UNCHECKED_CAST")
                val route=MainActivity::class.java.getDeclaredField("route\$delegate").apply {isAccessible=true}.get(compose.activity) as MutableState<Route>
                route.value=Route.VAULT
            }
            compose.waitUntil(10_000){compose.onAllNodesWithText("Hidden PIN").fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithContentDescription("Vault menu").assertDoesNotExist()
            compose.runOnIdle {assertTrue(state.hidden.visible);assertFalse(state.hidden.session.isUnlocked)}
        } finally {compose.runOnIdle {state.hidden.leave();state.key?.fill(0);state.key=null;state.session.lock()}}
    }
    @Test fun explicitPrimaryLockImmediatelyRevokesIndependentHiddenSession() {
        val state=ViewModelProvider(compose.activity)[ProtectedSessionState::class.java]
        compose.runOnIdle {
            state.hidden.select()
            state.hidden.session.complete(state.hidden.session.beginAuthentication(),ByteArray(32){7})
            val op=state.hidden.session.beginOperation()
            try {
                MainActivity::class.java.getDeclaredMethod("lock").apply {isAccessible=true}.invoke(compose.activity)
                assertFalse(state.hidden.session.isUnlocked)
                assertFalse(state.hidden.session.isCurrent(op))
            } finally {op.close();state.hidden.leave()}
        }
    }
    @Test fun automaticPrimaryTimeoutDoesNotOverrideHiddenPolicyButExplicitLockDoes() {
        val state=ViewModelProvider(compose.activity)[ProtectedSessionState::class.java]
        try {
            compose.runOnIdle {
                state.key=ByteArray(32){9};state.session.unlock()
                @Suppress("UNCHECKED_CAST")
                val route=MainActivity::class.java.getDeclaredField("route\$delegate").apply {isAccessible=true}.get(compose.activity) as MutableState<Route>
                route.value=Route.GALLERY
                state.hidden.select()
                state.hidden.session.complete(state.hidden.session.beginAuthentication(),ByteArray(32){7})
            }
            compose.waitForIdle()
            compose.runOnIdle {MainActivity::class.java.getDeclaredMethod("lockPrimary",Boolean::class.javaPrimitiveType).apply {isAccessible=true}.invoke(compose.activity,false)}
            compose.waitForIdle() // Include visibility effects; a synchronous-only assertion misses accidental revocation.
            compose.runOnIdle {
                assertTrue(state.hidden.session.isUnlocked)
                MainActivity::class.java.getDeclaredMethod("lock").apply {isAccessible=true}.invoke(compose.activity)
                assertFalse(state.hidden.session.isUnlocked)
            }
        } finally {compose.runOnIdle {state.hidden.leave();state.key?.fill(0);state.key=null;state.session.lock()}}
    }
}
