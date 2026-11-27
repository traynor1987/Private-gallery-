package uk.co.traynor.privategallery.novaos2.working

import java.time.DayOfWeek
import java.time.ZonedDateTime
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import uk.co.traynor.privategallery.launcher.LauncherWidget

class WorkingStateTest {
    @Test fun verifiedVehicleCanOpenDrivingOnlyWithSeparateConsentAndRespectsManualExit() {
        val now = 1_000L
        val legacy = WorkingState().setVehicle("02:01:02:03:04:05", automatic = true)
        val encodedLegacy = legacy.encode().also { it.getJSONObject("vehicle").remove("driveOnConnect") }
        assertFalse(checkNotNull(WorkingState.decode(encodedLegacy).vehicle).driveOnConnect)
        val configured = legacy.setVehicle("02:01:02:03:04:05", automatic = true, driveOnConnect = true)
        assertEquals(configured, WorkingState.decode(configured.encode()))
        assertEquals(configured, configured.syncVehicle(WorkingVehicleSignal.CHECKING, now, now, null))
        val started = configured.syncVehicle(WorkingVehicleSignal.CONNECTED, now, now, null)
        assertEquals(true, started.active?.driving)
        val manualExit = started.copy(active = checkNotNull(started.active).copy(driving = false))
        assertEquals(manualExit, manualExit.syncVehicle(WorkingVehicleSignal.CONNECTED, now + 1, now + 1, null))
        val rearmed = manualExit.syncVehicle(WorkingVehicleSignal.DISCONNECTED, now + 2, now + 2, null)
        assertEquals(true, rearmed.syncVehicle(WorkingVehicleSignal.CONNECTED, now + 3, now + 3, null).active?.driving)
        assertEquals(now, rearmed.active?.startedAtMillis)
        assertThrows(IllegalArgumentException::class.java) {
            configured.setVehicle("02:01:02:03:04:05", automatic = false, driveOnConnect = true)
        }
    }

    @Test fun trackerSummaryUsesOnlyExactSupportedProvidersAndPrefersTheActiveDisplay() {
        val namespace = "site.chatgpt.traynor1987.dominosshifttracker"
        val medium = LauncherWidget(1, "$namespace.stable/$namespace.MediumShiftWidget", wide = false)
        val large = LauncherWidget(2, "$namespace.stable/$namespace.LargeShiftWidget", wide = true)
        assertEquals(medium, trackerSummaryWidget(listOf(large, medium), wide = false))
        assertEquals(large, trackerSummaryWidget(listOf(medium, large), wide = true))
        assertNull(trackerSummaryWidget(listOf(medium.copy(provider = medium.provider.replace(".stable/", ".stable.debug/")),
            medium.copy(provider = medium.provider.replace("MediumShiftWidget", "SmallShiftWidget"))), wide = false))
        assertEquals(setOf(SHIFT_TRACKER_PACKAGE), WorkingShortcut.SHIFT_TRACKER.knownPackages)
    }

    @Test fun speedUsesOnlyFreshFiniteSensorValues() {
        val fix = WorkingSpeed(10_000L, 13.41f, 8.8f, 0.85f)
        assertEquals(30, fix.mph(10_000L))
        assertEquals(9, fix.positionAccuracy(10_000L))
        assertEquals(2, fix.accuracy(10_000L))
        assertNull(fix.mph(15_001L))
        assertNull(fix.mph(9_999L))
        assertNull(fix.copy(metersPerSecond = Float.NaN).mph(10_000L))
        assertNull(fix.copy(metersPerSecond = null).mph(10_000L))
        assertEquals(0, fix.copy(metersPerSecond = 0f).mph(10_000L))
    }

    @Test fun shiftSurvivesRoundTripAndRetainsTotalsWhenClockCannotBeValidated() {
        val totals = WorkTotals(3, earningsPence = 1250, fuelPence = 300, mileageTenths = 92)
        val active = WorkingSession(100_000L, 10_000L, true, DeliveryStatus.DELIVERING, totals)
        val restored = WorkingState.decode(WorkingState(active).encode())
        assertEquals(active, restored.active)
        assertEquals(60_000L, active.duration(160_000L, 70_000L))

        val endedAfterReboot = restored.finish(160_000L, 5_000L)
        assertNull(endedAfterReboot.active)
        assertNull(endedAfterReboot.previous?.durationMillis)
        assertEquals(totals, endedAfterReboot.previous?.totals)
        assertEquals(endedAfterReboot, WorkingState.decode(JSONObject(endedAfterReboot.encode().toString())))
    }

    @Test fun shiftDurationCapsAndMoneyKeepsExactDecimalPrecision() {
        val active = WorkingSession(10_000L, 1_000L)
        val afterLimit = MAX_SHIFT_MILLIS + 1000L
        assertEquals(MAX_SHIFT_MILLIS, active.duration(10_000L + afterLimit, 1_000L + afterLimit))
        assertEquals(1230, scaledWorkNumber("12.30", 2))
        assertNull(scaledWorkNumber(" ", 1))
        assertThrows(IllegalArgumentException::class.java) { scaledWorkNumber("2.999", 2) }
        assertThrows(IllegalArgumentException::class.java) { WorkTotals(1, fuelPence = -1) }
    }

    @Test fun configuredShortcutsPersistWithoutAProfileBypass() {
        val state = WorkingState(shortcuts = mapOf(WorkingShortcut.NAVIGATION to "com.example.maps/.Maps"))
        assertEquals(state, WorkingState.decode(state.encode()))
        val corrupted = state.encode().apply {
            getJSONObject("shortcuts").put("NAVIGATION", "com.example.maps/.Maps#99")
        }
        assertThrows(IllegalArgumentException::class.java) { WorkingState.decode(corrupted) }
        assertFalse(state.encode().has("location"))
    }

    @Test fun overnightScheduleStartsOnceOnTheConfiguredDayAndNeverEntersDriving() {
        val monday = WorkingSchedule(22 * 60, 2 * 60, setOf(DayOfWeek.MONDAY))
        val before = ZonedDateTime.parse("2026-11-23T21:59:00Z[Europe/London]")
        val during = before.plusHours(3)
        val initial = WorkingState(schedule = monday)
        assertNull(initial.eligibleSchedule(before))
        val day = initial.eligibleSchedule(during)
        assertEquals(before.toLocalDate().toEpochDay(), day)
        val active = initial.start(1_000L, 10L, day)
        assertFalse(checkNotNull(active.active).driving)
        val ended = active.finish(2_000L, 1_010L)
        assertNull(ended.eligibleSchedule(during))
        assertNull(initial.eligibleSchedule(during.plusHours(2)))
        assertEquals(monday, WorkingState.decode(ended.encode()).schedule)
        assertNull(workingScheduleMinute("24:00"))
        assertEquals(60, workingScheduleMinute("01:00"))
    }

    @Test fun vehicleRequiresVerifiedProfilesAndAnExplicitOfferAcceptanceByDefault() {
        val initial = WorkingState().setVehicle("02:01:02:03:04:05", automatic = false)
        assertEquals(WorkingVehicleSignal.UNAVAILABLE, verifiedVehicleSignal(false, null))
        assertEquals(WorkingVehicleSignal.DISCONNECTED, verifiedVehicleSignal(false, false))
        assertEquals(WorkingVehicleSignal.CONNECTED, verifiedVehicleSignal(null, true))
        assertEquals(initial, initial.syncVehicle(WorkingVehicleSignal.UNAVAILABLE, 1, 1, null, acceptOffer = true))
        assertNull(initial.syncVehicle(WorkingVehicleSignal.CONNECTED, 1, 1, null).active)
        val accepted = initial.syncVehicle(WorkingVehicleSignal.CONNECTED, 1, 1, null, acceptOffer = true)
        assertFalse(checkNotNull(accepted.active).driving)
        assertFalse(accepted.toString().contains("02:01:02:03:04:05"))
        assertEquals(accepted, WorkingState.decode(accepted.encode()))
    }

    @Test fun manuallyEndedAutomaticShiftWaitsForVerifiedDisconnectBeforeStartingAgain() {
        val initial = WorkingState().setVehicle("02:01:02:03:04:05", automatic = true)
        val start = initial.syncVehicle(WorkingVehicleSignal.CONNECTED, 1_000, 1_000, 10L)
        assertFalse(checkNotNull(start.active).driving)
        assertNull(initial.handleVehicle().syncVehicle(WorkingVehicleSignal.CONNECTED, 1_000, 1_000, null).active)
        val stopped = start.finish(2_000, 2_000)
        val restored = WorkingState.decode(stopped.encode())
        assertEquals(restored, restored.syncVehicle(WorkingVehicleSignal.CONNECTED, 3_000, 3_000, null))
        assertEquals(restored, restored.syncVehicle(WorkingVehicleSignal.UNAVAILABLE, 3_000, 3_000, null))
        val disconnected = restored.syncVehicle(WorkingVehicleSignal.DISCONNECTED, 3_000, 3_000, null)
        val resumed = disconnected.syncVehicle(WorkingVehicleSignal.CONNECTED, 4_000, 4_000, null)
        assertEquals(4_000L, resumed.active?.startedElapsedMillis)
        assertEquals(start.active, start.syncVehicle(WorkingVehicleSignal.DISCONNECTED, 3_000, 3_000, null).active)
        val stoppedWithAnUncertainSignal = initial.start(1_000, 1_000, null)
            .finish(2_000, 2_000).manualStopVehicle(WorkingVehicleSignal.UNAVAILABLE)
        assertNull(stoppedWithAnUncertainSignal.syncVehicle(WorkingVehicleSignal.CONNECTED, 3_000, 3_000, null).active)
    }
}
