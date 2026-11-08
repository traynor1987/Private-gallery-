package uk.co.traynor.privategallery.core.sanctuary

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject

class SanctuaryStoreTest {
    private class Disk : SanctuaryPersistence {
        var snapshot: SanctuarySnapshot? = null
        var writable = true
        override fun read() = snapshot
        override fun write(value: SanctuarySnapshot): Boolean {
            if (!writable) return false
            snapshot = value
            return true
        }
    }

    private class Fixture {
        val disk = Disk()
        var now = SanctuaryTime(1_000_000_000L, 120_000L, 1)
        fun open() = SanctuaryStore(disk) { now }
        fun advance(minutes: Int) {
            val millis = minutes * 60_000L
            now = now.copy(wall = now.wall + millis, elapsed = now.elapsed + millis)
        }
    }

    @Test fun presetsHaveDistinctDurationsAndAllowLists() {
        val store = Fixture().open()
        assertEquals(listOf(120, 60, 90, 240, 720), SanctuaryMode.entries.map { store.preset(it).minutes })
        assertEquals(5, SanctuaryMode.entries.map { store.preset(it).novaApps }.distinct().size)
        assertTrue("com.google.android.apps.maps" in store.preset(SanctuaryMode.WORK).androidApps)
        assertFalse(store.preset(SanctuaryMode.WORK).offerPinning)
        assertTrue("com.whatsapp" !in store.preset(SanctuaryMode.PANIC).androidApps)
        for (mode in SanctuaryMode.entries) {
            assertTrue(store.start(mode))
            val session = checkNotNull(store.current.value)
            assertEquals(mode, session.mode)
            assertEquals(mode.defaultMinutes * 60_000L, store.remainingMillis(session))
            assertTrue(store.end(SanctuaryStore.END_PHRASE))
        }
    }

    @Test fun persistsPresetAndSessionAcrossStoreRecreation() {
        val fixture = Fixture()
        val first = fixture.open()
        val preset = SanctuaryPreset(120, setOf("com.example.work"), setOf("arcade", "web:1234"))
        assertTrue(first.savePreset(SanctuaryMode.WORK, preset))
        assertTrue(first.start(SanctuaryMode.WORK))
        val session = checkNotNull(first.current.value)
        fixture.advance(10)
        val reopened = fixture.open()
        assertEquals(preset, reopened.preset(SanctuaryMode.WORK))
        assertEquals(session, reopened.current.value)
        assertEquals(110 * 60_000L, reopened.remainingMillis(session))
        assertFalse(reopened.start(SanctuaryMode.PANIC))
        assertEquals(session, reopened.current.value)
    }

    @Test fun countdownUsesElapsedTimeOnSameBootAndOriginalWallDeadlineAfterReboot() {
        val fixture = Fixture()
        val store = fixture.open()
        assertTrue(store.start(SanctuaryMode.LEARNING))
        val session = checkNotNull(store.current.value)
        fixture.advance(10)
        fixture.now = fixture.now.copy(wall = fixture.now.wall + 900_000)
        assertEquals(50 * 60_000L, store.remainingMillis(session))
        fixture.now = fixture.now.copy(boot = 2, elapsed = 500)
        assertEquals(35 * 60_000L, fixture.open().remainingMillis(session))
        assertEquals(session.expiresAt, fixture.open().current.value?.expiresAt)
    }

    @Test fun expirationAndEarlyEndHaveDistinctDurableOutcomes() {
        val fixture = Fixture()
        val store = fixture.open()
        assertTrue(store.start(SanctuaryMode.LEARNING))
        val session = checkNotNull(store.current.value)
        assertFalse(store.end("I CHOOSE TO END"))
        assertEquals(session, store.current.value)
        assertTrue(store.end(SanctuaryStore.END_PHRASE))
        assertNull(fixture.open().current.value)
        assertEquals(SanctuaryOutcome.ENDED, fixture.open().history().single().outcome)
        assertTrue(store.start(SanctuaryMode.GAMING))
        fixture.advance(120)
        assertNull(store.refresh())
        assertEquals(listOf(SanctuaryOutcome.EXPIRED, SanctuaryOutcome.ENDED), fixture.open().history().map { it.outcome })
    }

    @Test fun panicExtensionIsExplicitAndKeepsOriginalDeadlineAsItsBase() {
        val fixture = Fixture()
        val store = fixture.open()
        assertTrue(store.start(SanctuaryMode.PANIC))
        val initial = checkNotNull(store.current.value)
        fixture.advance(120)
        assertFalse(store.extend(2))
        assertEquals(initial, store.current.value)
        assertTrue(store.extend(3))
        val extended = checkNotNull(fixture.open().current.value)
        assertEquals(initial.expiresAt + 3 * 3_600_000L, extended.expiresAt)
        assertEquals(1, extended.extensions)
        assertEquals(13 * 3_600_000L, store.remainingMillis(extended))
    }

    @Test fun failedStorageCommitNeverClaimsStartOrChangesExistingSession() {
        val fixture = Fixture()
        val store = fixture.open()
        fixture.disk.writable = false
        assertFalse(store.start(SanctuaryMode.PANIC))
        assertNull(store.current.value)
        fixture.disk.writable = true
        assertTrue(store.start(SanctuaryMode.PANIC))
        val initial = store.current.value
        fixture.disk.writable = false
        assertFalse(store.extend(3))
        assertFalse(store.end(SanctuaryStore.END_PHRASE))
        assertEquals(initial, store.current.value)
    }

    @Test fun runningSessionKeepsItsOptionsAfterPresetEditsAndProcessRecreation() {
        val fixture = Fixture()
        val store = fixture.open()
        val defaults = store.preset(SanctuaryMode.PANIC)
        val options = defaults.copy(
            enableDnd = true, offerPinning = true, enableGuardian = false, allowExternalApps = false,
        )
        assertTrue(store.savePreset(SanctuaryMode.PANIC, options))
        assertTrue(store.start(SanctuaryMode.PANIC))
        assertTrue(store.savePreset(SanctuaryMode.PANIC, defaults))
        assertTrue(store.extend(1))
        fixture.disk.snapshot = SanctuaryCodec.decode(SanctuaryCodec.encode(checkNotNull(fixture.disk.snapshot)))
        val restored = fixture.open()
        val active = checkNotNull(restored.current.value)
        assertEquals(defaults, restored.preset(SanctuaryMode.PANIC))
        assertTrue(active.enableDnd)
        assertTrue(active.offerPinning)
        assertFalse(active.enableGuardian)
        assertFalse(active.allowExternalApps)
    }

    @Test fun legacyRecordsKeepTheirSessionAndUseCompatibleOptionDefaults() {
        val fixture = Fixture()
        val store = fixture.open()
        val legacyPreset = store.preset(SanctuaryMode.WORK)
        assertTrue(store.savePreset(SanctuaryMode.WORK, legacyPreset))
        assertTrue(store.start(SanctuaryMode.WORK))
        val original = checkNotNull(fixture.disk.snapshot)
        val legacyJson = JSONObject(SanctuaryCodec.encode(original).decodeToString())
        val preset = legacyJson.getJSONObject("presets").getJSONObject(SanctuaryMode.WORK.name)
        val session = legacyJson.getJSONObject("session")
        for (field in listOf("enableDnd", "offerPinning", "enableGuardian", "allowExternalApps")) {
            preset.remove(field)
            session.remove(field)
        }
        assertEquals(original, SanctuaryCodec.decode(legacyJson.toString().encodeToByteArray()))
    }

    @Test fun foldTestUsesFiveMinutesAndLeavesSavedModeAndItsRestrictionsIntact() {
        val fixture = Fixture()
        val store = fixture.open()
        val preset = store.preset(SanctuaryMode.WORK).copy(
            enableDnd = true,
            offerPinning = true,
            enableGuardian = false,
            blockedNovaApps = setOf("web:distraction"),
            allowWhatsApp = true,
        )
        assertTrue(store.savePreset(SanctuaryMode.WORK, preset))
        assertTrue(store.startFoldTest(SanctuaryMode.WORK))
        val session = checkNotNull(store.current.value)
        assertEquals(300_000L, store.remainingMillis(session))
        assertFalse(session.enableDnd)
        assertFalse(session.offerPinning)
        assertTrue(session.enableGuardian)
        assertTrue(session.allowWhatsApp)
        assertFalse(session.allowsNovaApp("web:distraction"))
        assertTrue(session.allowsNovaApp("web:installed-after-start"))
        assertEquals(preset, fixture.open().preset(SanctuaryMode.WORK))
        fixture.advance(5)
        assertNull(fixture.open().current.value)
    }

    @Test fun legacyExplicitRemovalsArePreservedWithoutDenyingOtherInternalApps() {
        val fixture = Fixture()
        val store = fixture.open()
        val previous = store.preset(SanctuaryMode.WORK).copy(novaApps = emptySet())
        assertTrue(store.savePreset(SanctuaryMode.WORK, previous))
        assertTrue(store.start(SanctuaryMode.WORK))
        val legacy = JSONObject(SanctuaryCodec.encode(checkNotNull(fixture.disk.snapshot)).decodeToString())
        listOf(legacy.getJSONObject("presets").getJSONObject("WORK"), legacy.getJSONObject("session")).forEach {
            it.remove("blockedNovaApps")
            it.remove("allowWhatsApp")
        }
        val migrated = SanctuaryCodec.decode(legacy.toString().encodeToByteArray())
        assertEquals(setOf("nova-calculator"), migrated.presets.getValue(SanctuaryMode.WORK).blockedNovaApps)
        val session = checkNotNull(migrated.session)
        assertFalse(session.allowsNovaApp("nova-calculator"))
        assertTrue(session.allowsNovaApp("gallery"))
        assertFalse(session.allowsNovaApp("chat"))
    }

    @Test fun serializedStateIsBoundedAndCarriesOnlySessionMetadata() {
        val fixture = Fixture()
        val store = fixture.open()
        repeat(35) {
            assertTrue(store.start(SanctuaryMode.GAMING))
            assertTrue(store.end(SanctuaryStore.END_PHRASE))
        }
        assertTrue(store.start(SanctuaryMode.WORK))
        val snapshot = checkNotNull(fixture.disk.snapshot)
        assertEquals(30, snapshot.history.size)
        assertEquals(snapshot, SanctuaryCodec.decode(SanctuaryCodec.encode(snapshot)))
    }
}
