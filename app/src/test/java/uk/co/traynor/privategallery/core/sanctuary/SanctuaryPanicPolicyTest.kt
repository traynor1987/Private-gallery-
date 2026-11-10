package uk.co.traynor.privategallery.core.sanctuary

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject

class SanctuaryPanicPolicyTest {
    private val main = "com.whatsapp/.Main"
    private val friends = "$main#95"
    private val personalApps = setOf(
        "com.google.android.apps.youtube.music",
        "com.google.android.youtube",
        "com.openai.chatgpt",
        "com.sec.android.app.sbrowser",
    )

    @Test fun panicDefaultsApproveOnlyTheRequestedPersonalAppsForTwelveHours() {
        val preset = SanctuaryMode.PANIC.defaultPreset()
        assertEquals(720, preset.minutes)
        assertEquals(personalApps, preset.androidApps)
    }

    @Test fun olderPanicSessionCannotCarryUnrelatedPackageOrProfileApprovalsIntoGuardian() {
        val session = SanctuarySession(
            "existing", SanctuaryMode.PANIC, 1, 2,
            setOf("com.google.android.apps.maps", "com.whatsapp"), emptySet(), 0,
        )
        val settings = SanctuaryLauncherSettings(
            friendsWhatsApp = friends,
            appRules = mapOf(main to true, friends to true, "com.example.unrelated/.Main" to true),
        )
        assertEquals(personalApps, approvedAndroidApps(session, settings))
        assertEquals(emptySet<String>(), approvedAndroidApps(session.copy(allowExternalApps = false), settings))
    }

    @Test fun aSavedRuleCannotApproveMainWhatsAppDuringSanctuary() {
        val settings = SanctuaryLauncherSettings(
            friendsWhatsApp = friends,
            appRules = mapOf(main to true),
        )
        assertFalse(settings.allows(main, "com.whatsapp", null, setOf("com.whatsapp")))
        assertTrue(settings.allows(friends, "com.whatsapp", 95, setOf("com.whatsapp")))
        assertFalse(settings.allows("$main#96", "com.whatsapp", 96, setOf("com.whatsapp")))
    }

    @Test fun panicAllowsOnlyTheSelectedFriendsIdentityAndPersonalAllowlist() {
        val unrelated = "com.example.unrelated/.Main"
        val youtube = "com.google.android.youtube/.Main"
        val otherProfile = "$main#96"
        val settings = SanctuaryLauncherSettings(
            friendsWhatsApp = friends,
            appRules = mapOf(unrelated to true, otherProfile to true, friends to false, youtube to false),
        )
        val savedPackages = setOf("com.example.unrelated", "com.whatsapp")
        fun allows(component: String, serial: Long?) = settings.allows(
            component, component.substringBefore('/'), serial, savedPackages, SanctuaryMode.PANIC,
        )
        assertTrue(allows(youtube, null))
        assertFalse(allows("$youtube#95", 95))
        assertFalse(allows(unrelated, null))
        assertFalse(allows(main, null))
        assertTrue(allows(friends, 95))
        assertFalse(allows(friends, 96))
        assertFalse(allows(otherProfile, 96))
    }

    @Test fun panicVisibilityDefaultsToApprovedOnlyAndPersistsWithoutChangingOtherModes() {
        val otherModeVisibility = SanctuaryAppVisibility(hideFromSearch = false)
        val before = SanctuarySnapshot(launcherSettings = SanctuaryLauncherSettings(visibility = otherModeVisibility))
        val oldJson = JSONObject(SanctuaryCodec.encode(before).decodeToString())
        oldJson.getJSONObject("launcherSettings").remove("panicVisibility")
        val decoded = SanctuaryCodec.decode(oldJson.toString().encodeToByteArray())
        val store = SanctuaryStore(object : SanctuaryPersistence {
            private var value = decoded
            override fun read() = value
            override fun write(value: SanctuarySnapshot): Boolean {
                this.value = SanctuaryCodec.decode(SanctuaryCodec.encode(value))
                return true
            }
        }) { SanctuaryTime(100_000, 20_000, 1) }
        assertEquals(SanctuaryAppVisibility(), store.launcherSettings().visibilityFor(SanctuaryMode.PANIC))
        assertEquals(otherModeVisibility, store.launcherSettings().visibilityFor(SanctuaryMode.WORK))
        assertTrue(store.savePanicLauncherVisibility(PanicLauncherVisibility.SHOW_ALL_WITH_LOCKS))
        val saved = SanctuaryCodec.decode(SanctuaryCodec.encode(SanctuarySnapshot(launcherSettings = store.launcherSettings())))
        assertEquals(SanctuaryAppVisibility(false, false, false, false), saved.launcherSettings.visibilityFor(SanctuaryMode.PANIC))
        assertEquals(otherModeVisibility, saved.launcherSettings.visibilityFor(SanctuaryMode.WORK))
    }

    @Test fun panicDoesNotTreatAnOldWebWhatsAppApprovalAsTheFriendsProfile() {
        val session = SanctuarySession("existing", SanctuaryMode.PANIC, 1, 2, emptySet(), emptySet(), 0,
            allowWhatsApp = true)
        assertFalse(session.allowsNovaApp("chat"))
        assertFalse(session.allowsNovaApp("web:alias", "https://web.whatsapp.com"))
        assertTrue(session.allowsNovaApp("home"))
        assertTrue(session.copy(mode = SanctuaryMode.WORK).allowsNovaApp("chat"))
    }

}
