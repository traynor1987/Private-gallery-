package uk.co.traynor.privategallery.core.sanctuary

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SanctuaryAppIdentityTest {
    private val main = "com.whatsapp/com.whatsapp.Main"
    private val friends = "$main#95"
    private val work = "$main#12"
    private val legacy = setOf("com.whatsapp", "com.example.reader")

    @Test fun whatsappRequiresExactEnrolledIdentityEvenWithLegacyApproval() {
        val settings = SanctuaryLauncherSettings(friendsWhatsApp = friends)
        assertFalse(settings.allows(main, "com.whatsapp", null, legacy))
        assertTrue(settings.allows(friends, "com.whatsapp", 95, legacy))
        assertFalse(settings.allows(work, "com.whatsapp", 12, legacy))
        assertFalse(settings.allows(friends, "com.whatsapp", 12, legacy))
        assertFalse(settings.allows(friends, "com.example.reader", 95, legacy))
        assertFalse(settings.allows("$main#96", "com.whatsapp", 96, legacy))
    }

    @Test fun explicitRulesAreIndependentAndOtherPersonalApprovalsArePreserved() {
        val settings = SanctuaryLauncherSettings(
            friendsWhatsApp = friends,
            appRules = mapOf(main to true, friends to false),
        )
        assertTrue(settings.allows(main, "com.whatsapp", null, legacy))
        assertFalse(settings.allows(friends, "com.whatsapp", 95, legacy))
        val reader = "com.example.reader/.Reader"
        assertTrue(settings.allows(reader, "com.example.reader", null, legacy))
        assertFalse(settings.allows("$reader#95", "com.example.reader", 95, legacy))
    }

    @Test fun guardianNeverTreatsACloneRuleAsPersonalPackageApproval() {
        val session = SanctuarySession("session", SanctuaryMode.SELF_CARE, 1, 2, legacy, emptySet(), 0)
        val settings = SanctuaryLauncherSettings(friendsWhatsApp = friends, appRules = mapOf(friends to true))
        assertEquals(setOf("com.example.reader"), approvedAndroidApps(session, settings))
        assertEquals(legacy, approvedAndroidApps(session, settings.copy(appRules = mapOf(main to true))))
        assertEquals(emptySet<String>(), approvedAndroidApps(session.copy(allowExternalApps = false), settings))
    }

    @Test fun malformedProfileRulesKeepAnExistingSessionAndDisableAutomaticEnrolment() {
        val persistence = object : SanctuaryPersistence {
            var snapshot: SanctuarySnapshot? = null
            override fun read() = snapshot
            override fun write(value: SanctuarySnapshot): Boolean {
                snapshot = value
                return true
            }
        }
        val store = SanctuaryStore(persistence) { SanctuaryTime(100_000, 20_000, 1) }
        assertTrue(store.start(SanctuaryMode.SELF_CARE))
        val previous = checkNotNull(persistence.snapshot)
        val malformed = JSONObject(SanctuaryCodec.encode(previous).decodeToString())
            .put("launcherSettings", JSONObject().put("friendsWhatsApp", main))
        val decoded = SanctuaryCodec.decode(malformed.toString().encodeToByteArray())
        assertEquals(previous.session, decoded.session)
        assertNull(decoded.launcherSettings.friendsWhatsApp)
        assertFalse(decoded.launcherSettings.autoEnrolFriends)
        assertFalse(decoded.launcherSettings.allows(friends, "com.whatsapp", 95, legacy))
    }

    @Test fun rulesVisibilityAndEnrolmentSurviveStoreReconstructionWithoutRetargeting() {
        val persistence = object : SanctuaryPersistence {
            private var data: ByteArray? = null
            override fun read() = data?.let(SanctuaryCodec::decode)
            override fun write(value: SanctuarySnapshot): Boolean {
                data = SanctuaryCodec.encode(value)
                return true
            }
        }
        val clock = { SanctuaryTime(100_000, 20_000, 1) }
        val store = SanctuaryStore(persistence, clock)
        assertEquals(SanctuaryAppVisibility(), store.launcherSettings().visibility)
        assertTrue(store.enrolFriendsWhatsApp(friends))
        assertTrue(store.saveLauncherAppRule(main, false))
        assertTrue(store.saveLauncherVisibility(SanctuaryAppVisibility(hideFromSearch = false)))
        assertTrue(store.start(SanctuaryMode.SELF_CARE))

        val reopened = SanctuaryStore(persistence, clock)
        assertFalse(reopened.enrolFriendsWhatsApp("$main#96"))
        assertEquals(friends, reopened.launcherSettings().friendsWhatsApp)
        assertEquals(mapOf(main to false), reopened.launcherSettings().appRules)
        assertFalse(reopened.launcherSettings().visibility.hideFromSearch)
        assertTrue(reopened.launcherSettings().visibility.hideFromSuggestions)
        assertEquals(SanctuaryMode.SELF_CARE, reopened.current.value?.mode)
        assertTrue(reopened.selectFriendsWhatsApp("$main#96"))
        assertFalse(reopened.launcherSettings().allows(friends, "com.whatsapp", 95, legacy))
        assertTrue(reopened.launcherSettings().allows("$main#96", "com.whatsapp", 96, legacy))
    }
}
