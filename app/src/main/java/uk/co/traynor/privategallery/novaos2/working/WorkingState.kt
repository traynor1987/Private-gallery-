package uk.co.traynor.privategallery.novaos2.working

import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.Locale
import kotlin.math.roundToInt
import org.json.JSONObject

internal const val MAX_SHIFT_MILLIS = 18L * 60 * 60 * 1_000
private const val SPEED_FRESHNESS_MILLIS = 5_000L
private const val MPH_PER_MPS = 2.23693629

internal enum class WorkingShortcut(val label: String, val knownPackages: Set<String> = emptySet()) {
    NAVIGATION("Navigation", setOf("com.google.android.apps.maps")),
    DOMINOS("Domino’s delivery"),
    SHIFT_TRACKER("Shift Tracker", setOf(SHIFT_TRACKER_PACKAGE)),
    UBER("Uber Driver", setOf("com.ubercab.driver")),
    DELIVEROO("Deliveroo"),
    JUST_EAT("Just Eat"),
    AUDIO("Audio", setOf("com.google.android.apps.youtube.music")),
}

internal data class WorkTotals(
    val deliveries: Int = 0,
    val earningsPence: Int? = null,
    val fuelPence: Int? = null,
    val mileageTenths: Int? = null,
) {
    init {
        require(deliveries in 0..999)
        require(earningsPence == null || earningsPence in 0..999_999)
        require(fuelPence == null || fuelPence in 0..999_999)
        require(mileageTenths == null || mileageTenths in 0..9_999)
    }
}

internal enum class DeliveryStatus(val label: String) {
    BETWEEN_ORDERS("Available"), PICKING_UP("Picking up"), DELIVERING("Delivering"), ON_BREAK("On break")
}

internal data class WorkingSession(
    val startedAtMillis: Long,
    val startedElapsedMillis: Long,
    val driving: Boolean = false,
    val deliveryStatus: DeliveryStatus = DeliveryStatus.BETWEEN_ORDERS,
    val totals: WorkTotals = WorkTotals(),
) {
    init {
        require(startedAtMillis > 0 && startedElapsedMillis >= 0)
    }

    fun duration(wallMillis: Long, elapsedMillis: Long): Long? {
        if (elapsedMillis < startedElapsedMillis || wallMillis < startedAtMillis) return null
        val elapsed = elapsedMillis - startedElapsedMillis
        val wallElapsed = wallMillis - startedAtMillis
        if (kotlin.math.abs(elapsed - wallElapsed) > 5 * 60_000L) return null
        return elapsed.coerceAtMost(MAX_SHIFT_MILLIS)
    }
}

internal data class FinishedShift(val durationMillis: Long?, val totals: WorkTotals) {
    init { require(durationMillis == null || durationMillis in 0..MAX_SHIFT_MILLIS) }
}

internal data class WorkingSchedule(val startMinute: Int, val endMinute: Int, val days: Set<DayOfWeek>) {
    init {
        require(startMinute in 0..1439 && endMinute in 0..1439 && startMinute != endMinute)
        require(days.isNotEmpty())
    }

    fun occurrence(now: ZonedDateTime): LocalDate? {
        val minute = now.hour * 60 + now.minute
        val date = when {
            startMinute < endMinute -> now.toLocalDate().takeIf { minute in startMinute until endMinute }
            minute >= startMinute -> now.toLocalDate()
            minute < endMinute -> now.toLocalDate().minusDays(1)
            else -> null
        }
        return date?.takeIf { it.dayOfWeek in days }
    }

    fun encode() = JSONObject().put("start", startMinute).put("end", endMinute)
        .put("days", days.fold(0) { mask, day -> mask or (1 shl (day.value - 1)) })

    companion object {
        fun decode(json: JSONObject): WorkingSchedule {
            val mask = json.getInt("days")
            require(mask in 1..127)
            return WorkingSchedule(json.getInt("start"), json.getInt("end"),
                DayOfWeek.entries.filter { day -> (mask and (1 shl (day.value - 1))) != 0 }.toSet())
        }
    }
}

internal fun workingTimeLabel(minutes: Int): String = String.format(Locale.UK, "%02d:%02d", minutes / 60, minutes % 60)

internal fun workingScheduleMinute(text: String): Int? {
    if (!text.matches(Regex("[0-9]{2}:[0-9]{2}"))) return null
    val hour = text.take(2).toInt()
    val minute = text.takeLast(2).toInt()
    return if (hour in 0..23 && minute in 0..59) hour * 60 + minute else null
}

internal data class WorkingVehicle(val identifier: String, val automatic: Boolean, val handled: Boolean = false,
                                  val driveOnConnect: Boolean = false) {
    init { require(isWorkingVehicleIdentifier(identifier) && (!driveOnConnect || automatic)) }

    override fun toString() = "WorkingVehicle(<redacted>)"
}

private val vehicleIdentifierPattern = Regex("(?:[0-9A-F]{2}:){5}[0-9A-F]{2}")
internal fun isWorkingVehicleIdentifier(identifier: String) = vehicleIdentifierPattern.matches(identifier)

internal enum class WorkingVehicleSignal(val description: String) {
    OFF("Vehicle activation is off."),
    PERMISSION("Allow Nearby devices to use the paired vehicle."),
    RADIO_OFF("Bluetooth is off."),
    CHECKING("Checking the paired vehicle’s audio connection…"),
    UNAVAILABLE("Android cannot verify the paired vehicle’s audio connection."),
    UNPAIRED("The selected vehicle is no longer paired."),
    CONNECTED("Android confirmed the paired vehicle’s audio connection."),
    DISCONNECTED("The paired vehicle’s audio is disconnected."),
}

internal fun verifiedVehicleSignal(a2dp: Boolean?, headset: Boolean?): WorkingVehicleSignal = when {
    a2dp == true || headset == true -> WorkingVehicleSignal.CONNECTED
    a2dp == false && headset == false -> WorkingVehicleSignal.DISCONNECTED
    else -> WorkingVehicleSignal.UNAVAILABLE
}

internal data class WorkingState(
    val active: WorkingSession? = null,
    val previous: FinishedShift? = null,
    val shortcuts: Map<WorkingShortcut, String> = emptyMap(),
    val schedule: WorkingSchedule? = null,
    val lastScheduledDay: Long? = null,
    val vehicle: WorkingVehicle? = null,
) {
    fun eligibleSchedule(now: ZonedDateTime): Long? = schedule?.occurrence(now)?.toEpochDay()?.takeIf {
        active == null && (lastScheduledDay == null || it > lastScheduledDay)
    }

    fun start(wallMillis: Long, elapsedMillis: Long, day: Long?): WorkingState {
        if (active != null) return this
        return copy(active = WorkingSession(wallMillis, elapsedMillis), lastScheduledDay = laterDay(day))
    }

    fun skipScheduledDay(day: Long?) = copy(lastScheduledDay = laterDay(day))

    fun setVehicle(identifier: String, automatic: Boolean, driveOnConnect: Boolean = false): WorkingState = copy(vehicle = WorkingVehicle(
        identifier, automatic, handled = vehicle?.takeIf { it.identifier == identifier }?.handled ?: false,
        driveOnConnect = driveOnConnect,
    ))

    fun handleVehicle() = if (vehicle == null || vehicle.handled) this else copy(vehicle = vehicle.copy(handled = true))

    fun manualStopVehicle(signal: WorkingVehicleSignal): WorkingState = when (signal) {
        WorkingVehicleSignal.DISCONNECTED, WorkingVehicleSignal.RADIO_OFF -> this
        else -> handleVehicle()
    }

    fun syncVehicle(signal: WorkingVehicleSignal, wallMillis: Long, elapsedMillis: Long, day: Long?,
                    acceptOffer: Boolean = false): WorkingState {
        val selected = vehicle ?: return this
        return when (signal) {
            WorkingVehicleSignal.DISCONNECTED, WorkingVehicleSignal.RADIO_OFF ->
                if (selected.handled) copy(vehicle = selected.copy(handled = false)) else this
            WorkingVehicleSignal.CONNECTED -> when {
                selected.handled -> this
                selected.driveOnConnect -> {
                    val shift = start(wallMillis, elapsedMillis, day)
                    shift.copy(active = checkNotNull(shift.active).copy(driving = true)).handleVehicle()
                }
                active != null -> handleVehicle()
                selected.automatic || acceptOffer -> start(wallMillis, elapsedMillis, day).handleVehicle()
                else -> this
            }
            else -> this
        }
    }

    private fun laterDay(day: Long?): Long? = when {
        day == null -> lastScheduledDay
        lastScheduledDay == null -> day
        else -> maxOf(day, lastScheduledDay)
    }

    fun finish(wallMillis: Long, elapsedMillis: Long): WorkingState = active?.let {
        copy(active = null, previous = FinishedShift(it.duration(wallMillis, elapsedMillis), it.totals))
    } ?: this

    fun encode(): JSONObject = JSONObject().put("version", 1).apply {
        active?.let {
            put("active", JSONObject().put("startedAt", it.startedAtMillis).put("elapsed", it.startedElapsedMillis)
                .put("driving", it.driving).put("delivery", it.deliveryStatus.name).put("totals", it.totals.encode()))
        }
        previous?.let {
            put("previous", JSONObject().put("duration", it.durationMillis).put("totals", it.totals.encode()))
        }
        put("shortcuts", JSONObject().apply { shortcuts.forEach { (slot, component) -> put(slot.name, component) } })
        schedule?.let { put("schedule", it.encode()) }
        lastScheduledDay?.let { put("lastScheduledDay", it) }
        vehicle?.let {
            put("vehicle", JSONObject().put("identifier", it.identifier).put("automatic", it.automatic).put("handled", it.handled)
                .put("driveOnConnect", it.driveOnConnect))
        }
    }

    companion object {
        fun decode(json: JSONObject?): WorkingState {
            if (json == null) return WorkingState()
            require(json.getInt("version") == 1)
            val selections = json.getJSONObject("shortcuts")
            return WorkingState(
                active = json.optJSONObject("active")?.let {
                    WorkingSession(it.getLong("startedAt"), it.getLong("elapsed"), it.getBoolean("driving"),
                        DeliveryStatus.valueOf(it.getString("delivery")), decodeWorkTotals(it.getJSONObject("totals")))
                },
                previous = json.optJSONObject("previous")?.let {
                    FinishedShift(if (it.isNull("duration")) null else it.getLong("duration"),
                        decodeWorkTotals(it.getJSONObject("totals")))
                },
                shortcuts = WorkingShortcut.entries.mapNotNull { slot ->
                    if (!selections.has(slot.name)) return@mapNotNull null
                    val component = selections.getString(slot.name)
                    require(component.length in 1..512 && '#' !in component && '/' in component)
                    slot to component
                }.toMap(),
                schedule = json.optJSONObject("schedule")?.let { WorkingSchedule.decode(it) },
                lastScheduledDay = if (json.isNull("lastScheduledDay")) null else json.getLong("lastScheduledDay"),
                vehicle = json.optJSONObject("vehicle")?.let {
                    WorkingVehicle(it.getString("identifier"), it.getBoolean("automatic"), it.getBoolean("handled"),
                        it.optBoolean("driveOnConnect", false))
                },
            )
        }
    }
}

private fun WorkTotals.encode(): JSONObject = JSONObject().put("deliveries", deliveries)
    .put("earnings", earningsPence).put("fuel", fuelPence).put("miles", mileageTenths)

private fun decodeWorkTotals(json: JSONObject): WorkTotals = WorkTotals(
    deliveries = json.getInt("deliveries"),
    earningsPence = json.optionalInteger("earnings"),
    fuelPence = json.optionalInteger("fuel"),
    mileageTenths = json.optionalInteger("miles"),
)

private fun JSONObject.optionalInteger(key: String) = if (isNull(key)) null else getInt(key)

internal fun scaledWorkNumber(text: String, decimals: Int): Int? {
    if (text.isBlank()) return null
    val normalized = text.trim()
    require(normalized.matches(Regex("[0-9]{1,6}(?:[.][0-9]{1,$decimals})?")))
    return BigDecimal(normalized).movePointRight(decimals).intValueExact()
}

internal data class WorkingSpeed(
    val measuredElapsedMillis: Long,
    val metersPerSecond: Float?,
    val positionAccuracyMeters: Float?,
    val speedAccuracyMetersPerSecond: Float?,
) {
    fun isCurrent(now: Long) = measuredElapsedMillis in (now - SPEED_FRESHNESS_MILLIS)..now

    fun mph(now: Long): Int? = if (isCurrent(now)) metersPerSecond.toMph() else null

    fun accuracy(now: Long): Int? = if (isCurrent(now)) speedAccuracyMetersPerSecond.toMph() else null

    fun positionAccuracy(now: Long): Int? = if (isCurrent(now)) {
        positionAccuracyMeters?.takeIf { it.isFinite() && it >= 0f && it < 100_000 }?.roundToInt()
    } else null

    private fun Float?.toMph(): Int? = this?.takeIf { it.isFinite() && it in 0f..1_000f }
        ?.let { (it * MPH_PER_MPS).roundToInt() }
}
