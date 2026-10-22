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
    @Test fun actualPrimaryLockRevokesCopyAccessEvenWhenHiddenGraceIsRetained() {
        val state=ViewModelProvider(compose.activity)[ProtectedSessionState::class.java]
        compose.runOnIdle {
            state.key=ByteArray(32){9};state.session.unlock()
            state.hidden.session.complete(state.hidden.session.beginAuthentication(),ByteArray(32){7})
            val factory=MainActivity::class.java.getDeclaredMethod("createPrimaryCopy",uk.co.traynor.privategallery.core.hidden.HiddenSession::class.java).apply {isAccessible=true}
            val access=factory.invoke(compose.activity,state.hidden.session) as uk.co.traynor.privategallery.core.hidden.PrimaryCopyAccess
            try {
                assertTrue(access.isActive())
                MainActivity::class.java.getDeclaredMethod("lockPrimary",Boolean::class.javaPrimitiveType).apply {isAccessible=true}.invoke(compose.activity,false)
                assertFalse(access.isActive());assertTrue(state.hidden.session.isUnlocked)
            } finally {access.close();state.hidden.leave();state.key?.fill(0);state.key=null;state.session.lock()}
        }
    }
    @Test fun actualActivityStopRevokesCopyAccess() {
        val state=ViewModelProvider(compose.activity)[ProtectedSessionState::class.java]
        lateinit var access:uk.co.traynor.privategallery.core.hidden.PrimaryCopyAccess
        compose.runOnIdle {
            state.key=ByteArray(32){9};state.session.unlock()
            state.hidden.session.complete(state.hidden.session.beginAuthentication(),ByteArray(32){7})
            access=MainActivity::class.java.getDeclaredMethod("createPrimaryCopy",uk.co.traynor.privategallery.core.hidden.HiddenSession::class.java).apply {isAccessible=true}.invoke(compose.activity,state.hidden.session) as uk.co.traynor.privategallery.core.hidden.PrimaryCopyAccess
        }
        try {
            compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
            assertFalse(access.isActive())
            compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
            assertFalse(access.isActive())
        } finally {compose.runOnIdle {access.close();state.hidden.leave();state.key?.fill(0);state.key=null;state.session.lock()}}
    }
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
                    assertFalse(state.hidden.session.isCurrent(op))
                    assertFalse(state.session.isUnlocked);assertNull(state.key)
                    assertEquals(if(uk.co.traynor.privategallery.core.security.PinVaultKeyStore(compose.activity).isConfigured) Route.LOCK else Route.SETUP,route.value)
                } finally {op.close()}
                state.key?.fill(0);state.key=null;state.session.lock()
                state.hidden.select();state.hidden.session.complete(state.hidden.session.beginAuthentication(),ByteArray(32){7})
                returnToPrimary.invoke(compose.activity)
                assertFalse(state.hidden.session.isUnlocked);assertFalse(state.hidden.selected)
                assertEquals(if(uk.co.traynor.privategallery.core.security.PinVaultKeyStore(compose.activity).isConfigured) Route.LOCK else Route.SETUP,route.value)
            } finally {state.hidden.leave();state.key?.fill(0);state.key=null;state.session.lock()}
        }
    }
    @Test fun primaryGalleryFromHiddenRevokesBothAuthoritiesBeforeDisplayingAnything() {
        val state=ViewModelProvider(compose.activity)[ProtectedSessionState::class.java]
        compose.runOnIdle {
            try {
                state.key=ByteArray(32){9};state.session.unlock()
                state.hidden.select();state.hidden.session.complete(state.hidden.session.beginAuthentication(),ByteArray(32){7})
                MainActivity::class.java.getDeclaredMethod("selectGallerySource",Boolean::class.javaPrimitiveType).apply {isAccessible=true}.invoke(compose.activity,true)
                assertTrue(state.primaryGallery)
                assertEquals(Route.GALLERY,state.afterAuthentication)
                assertFalse(state.session.isUnlocked);assertNull(state.key)
                assertFalse(state.hidden.selected);assertFalse(state.hidden.session.isUnlocked)
            } finally {state.hidden.leave();state.key?.fill(0);state.key=null;state.session.lock();state.primaryGallery=false;state.afterAuthentication=Route.VAULT}
        }
        compose.onNodeWithContentDescription("Vault menu").assertDoesNotExist()
    }
    @Test fun gallerySwitchReusesPrimaryGridAndReturnsToPhoneWithoutSourcePopup() {
        val state=ViewModelProvider(compose.activity)[ProtectedSessionState::class.java]
        try {
            compose.runOnIdle {
                state.key=ByteArray(32){9};state.session.unlock();state.hidden.leave();state.primaryGallery=false
                @Suppress("UNCHECKED_CAST")
                val route=MainActivity::class.java.getDeclaredField("route\$delegate").apply {isAccessible=true}.get(compose.activity) as MutableState<Route>
                route.value=Route.GALLERY
            }
            compose.onNodeWithText("Phone Gallery").assertIsSelected()
            compose.onNodeWithText("Primary Vault").performClick()
            compose.onNodeWithText("Primary Vault").assertIsSelected()
            compose.onNodeWithContentDescription("Vault menu").assertIsDisplayed()
            compose.onNodeWithText("Phone Gallery").performClick()
            compose.onNodeWithContentDescription("Vault menu").assertDoesNotExist()
            compose.onNodeWithContentDescription("Gallery menu").assertIsDisplayed()
        } finally {compose.runOnIdle {state.hidden.leave();state.key?.fill(0);state.key=null;state.session.lock();state.primaryGallery=false}}
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
