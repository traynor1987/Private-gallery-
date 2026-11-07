package uk.co.traynor.privategallery.core.sanctuary

import android.app.NotificationManager.Policy
import android.service.notification.Condition
import android.service.notification.ZenPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SanctuaryDndTest {
    @Test fun deniedOrRevokedAccessNeverClaimsDndIsActive() {
        assertEquals(
            SanctuaryDndState.UNAVAILABLE,
            sanctuaryDndState(true, false, true, Condition.STATE_TRUE),
        )
    }

    @Test fun anUnrelatedAndroidModeDoesNotCountAsSanctuaryDnd() {
        assertEquals(SanctuaryDndState.OFF, sanctuaryDndState(false, true, false, Condition.STATE_TRUE))
        assertEquals(SanctuaryDndState.PAUSED, sanctuaryDndState(true, true, false, Condition.STATE_TRUE))
    }

    @Test fun onlyActiveOwnedRuleReportsOnAndManualOverrideRemainsPaused() {
        assertEquals(SanctuaryDndState.ON, sanctuaryDndState(true, true, true, Condition.STATE_TRUE))
        for (state in listOf(null, Condition.STATE_FALSE, Condition.STATE_UNKNOWN)) {
            assertEquals(SanctuaryDndState.PAUSED, sanctuaryDndState(true, true, true, state))
        }
    }

    @Test fun configuredPeopleExceptionsArePreservedWithStarredAsTheMinimum() {
        val calls = Policy.PRIORITY_CATEGORY_CALLS
        assertEquals(ZenPolicy.PEOPLE_TYPE_ANYONE, sanctuaryDndPeople(calls, calls, Policy.PRIORITY_SENDERS_ANY))
        assertEquals(ZenPolicy.PEOPLE_TYPE_CONTACTS, sanctuaryDndPeople(calls, calls, Policy.PRIORITY_SENDERS_CONTACTS))
        assertEquals(ZenPolicy.PEOPLE_TYPE_STARRED, sanctuaryDndPeople(calls, calls, Policy.PRIORITY_SENDERS_STARRED))
        assertEquals(ZenPolicy.PEOPLE_TYPE_STARRED, sanctuaryDndPeople(0, calls, Policy.PRIORITY_SENDERS_ANY))
    }

    @Test fun cleanupReleasesOnlyEndedExpiredOrReplacedSession() {
        assertTrue(sanctuaryDndShouldRelease("owned", null, false))
        assertTrue(sanctuaryDndShouldRelease("owned", "replacement", true))
        assertTrue(sanctuaryDndShouldRelease("owned", "owned", false))
        assertFalse(sanctuaryDndShouldRelease("owned", "owned", true))
    }
}
