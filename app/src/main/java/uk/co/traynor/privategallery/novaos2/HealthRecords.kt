package uk.co.traynor.privategallery.novaos2

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.util.Locale
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject
import uk.co.traynor.privategallery.core.home2.SpaceContext

internal enum class HealthMetric(
    val label: String,
    val unit: String,
    private val lower: Int,
    private val upper: Int,
    val decimalPlaces: Int = 0,
) {
    WEIGHT("Weight", "kg", 1, 1000, 2),
    SLEEP("Sleep duration", "min", 0, 1440),
    HYDRATION("Water logged", "mL", 0, 20_000),
    STEPS("Steps", "steps", 0, 200_000),
    EXERCISE("Exercise duration", "min", 0, 1440),
    EXERCISE_SESSIONS("Exercise sessions", "sessions", 0, 24),
    HEART_RATE("Heart rate", "bpm", 1, 500),
    RESTING_HEART_RATE("Resting heart rate", "bpm", 1, 500),
    RECOVERY("Self-reported recovery", "%", 0, 100),
    ;

    fun parse(input: String): BigDecimal? {
        val normalized = input.trim().replace(',', '.')
        if (normalized.isEmpty() || normalized.length > 10 || !measurementPattern.matches(normalized)) return null
        val value = normalized.toBigDecimalOrNull()?.stripTrailingZeros() ?: return null
        return value.takeIf { it in lower.toBigDecimal()..upper.toBigDecimal() && it.scale() <= decimalPlaces }
    }

    fun format(value: BigDecimal): String = "${value.stripTrailingZeros().toPlainString()} $unit"

    companion object {
        private val measurementPattern = Regex("[0-9]+(?:\\.[0-9]+)?")
    }
}

internal data class HealthHabit(val id: UUID, val name: String)

internal data class HealthDay(
    val measurements: Map<HealthMetric, BigDecimal> = emptyMap(),
    val habits: Set<UUID> = emptySet(),
) {
    fun isEmpty() = measurements.isEmpty() && habits.isEmpty()
}

internal data class HealthWeek(
    val recordedDays: Int,
    val activeDays: Int,
    val sleepAverage: BigDecimal?,
    val sleepDays: Int,
    val hydrationTotal: BigDecimal?,
    val hydrationDays: Int,
    val habitDays: Map<UUID, Int>,
)

internal data class HealthRecords(
    val days: Map<LocalDate, HealthDay> = emptyMap(),
    val habits: List<HealthHabit> = emptyList(),
    val goal: String = "",
) {
    fun withMeasurement(date: LocalDate, metric: HealthMetric, value: BigDecimal?): HealthRecords = changeDay(date) { day ->
        day.copy(measurements = if (value == null) day.measurements - metric else day.measurements + (metric to value))
    }

    fun withHabit(name: String, id: UUID = UUID.randomUUID()): HealthRecords {
        val trimmed = name.trim()
        require(trimmed.length in 1..MAX_HABIT_LENGTH && habits.size < MAX_HABITS)
        require(habits.none { it.name.equals(trimmed, ignoreCase = true) || it.id == id })
        return copy(habits = habits + HealthHabit(id, trimmed))
    }

    fun toggleHabit(date: LocalDate, id: UUID): HealthRecords {
        require(habits.any { it.id == id })
        return changeDay(date) { day ->
            day.copy(habits = if (id in day.habits) day.habits - id else day.habits + id)
        }
    }

    fun withoutHabit(id: UUID): HealthRecords = copy(
        habits = habits.filterNot { it.id == id },
        days = days.mapValues { (_, day) -> day.copy(habits = day.habits - id) }.filterValues { !it.isEmpty() },
    )

    fun lastMeasurements(metric: HealthMetric, through: LocalDate, max: Int): List<Pair<LocalDate, BigDecimal>> =
        days.entries.asSequence().filter { it.key <= through }.mapNotNull { (date, day) ->
            day.measurements[metric]?.let { date to it }
        }.sortedByDescending { it.first }.take(max).toList()

    fun week(through: LocalDate): HealthWeek {
        val recorded = days.filterKeys { it in through.minusDays(6)..through }.values
        val sleep = recorded.mapNotNull { it.measurements[HealthMetric.SLEEP] }
        val water = recorded.mapNotNull { it.measurements[HealthMetric.HYDRATION] }
        return HealthWeek(
            recordedDays = recorded.size,
            activeDays = recorded.count { day ->
                listOf(HealthMetric.STEPS, HealthMetric.EXERCISE, HealthMetric.EXERCISE_SESSIONS).any { metric ->
                    (day.measurements[metric]?.signum() ?: 0) > 0
                }
            },
            sleepAverage = sleep.takeIf { it.isNotEmpty() }?.sumOf { it }
                ?.divide(sleep.size.toBigDecimal(), 0, RoundingMode.HALF_UP),
            sleepDays = sleep.size,
            hydrationTotal = water.takeIf { it.isNotEmpty() }?.sumOf { it },
            hydrationDays = water.size,
            habitDays = habits.associate { habit -> habit.id to recorded.count { habit.id in it.habits } },
        )
    }

    private fun changeDay(date: LocalDate, change: (HealthDay) -> HealthDay): HealthRecords {
        val day = change(days[date] ?: HealthDay())
        val updated = if (day.isEmpty()) days - date else days + (date to day)
        require(updated.size <= MAX_DAYS)
        return copy(days = updated)
    }

    companion object {
        const val MAX_DAYS = 1096
        const val MAX_HABITS = 24
        const val MAX_HABIT_LENGTH = 80
        const val MAX_GOAL_LENGTH = 240
    }
}

internal object HealthRecordCodec {
    fun encode(records: HealthRecords): String = JSONObject()
        .put("version", 1)
        .put("goal", records.goal)
        .put("habits", JSONArray(records.habits.map { habit ->
            JSONObject().put("id", habit.id.toString()).put("name", habit.name)
        }))
        .put("days", JSONArray(records.days.toSortedMap().map { (date, day) ->
            val measurements = JSONObject()
            HealthMetric.entries.forEach { metric ->
                day.measurements[metric]?.let { measurements.put(metric.name, it.toPlainString()) }
            }
            JSONObject().put("date", date.toString()).put("measurements", measurements)
                .put("habits", JSONArray(day.habits.map(UUID::toString).sorted()))
        })).toString()

    fun decode(raw: String): HealthRecords {
        require(raw.length <= MAX_JSON_CHARS)
        val root = JSONObject(raw)
        root.requireKeys("version", "goal", "habits", "days")
        require(root.get("version") == 1)
        val goals = root.getString("goal").also { require(it.length <= HealthRecords.MAX_GOAL_LENGTH) }
        val habitArray = root.getJSONArray("habits")
        require(habitArray.length() <= HealthRecords.MAX_HABITS)
        val habits = List(habitArray.length()) { index ->
            val item = habitArray.getJSONObject(index)
            item.requireKeys("id", "name")
            val name = item.getString("name")
            require(name.isNotBlank() && name == name.trim() && name.length <= HealthRecords.MAX_HABIT_LENGTH)
            HealthHabit(parseId(item.getString("id")), name)
        }
        val uniqueIds = habits.mapTo(hashSetOf()) { it.id }
        require(uniqueIds.size == habits.size && habits.map { it.name.lowercase(Locale.ROOT) }.distinct().size == habits.size)
        val dayArray = root.getJSONArray("days")
        require(dayArray.length() <= HealthRecords.MAX_DAYS)
        val days = buildMap {
            for (index in 0 until dayArray.length()) {
                val item = dayArray.getJSONObject(index)
                item.requireKeys("date", "measurements", "habits")
                val date = LocalDate.parse(item.getString("date"))
                require(date.year in 1..9999 && !containsKey(date))
                val data = item.getJSONObject("measurements")
                val measurements = data.keys().asSequence().associate { key ->
                    val metric = HealthMetric.valueOf(key)
                    metric to checkNotNull(metric.parse(data.getString(key)))
                }
                val completed = item.getJSONArray("habits")
                require(completed.length() <= uniqueIds.size)
                val completedIds = buildSet {
                    for (habit in 0 until completed.length()) {
                        val id = parseId(completed.getString(habit))
                        require(id in uniqueIds && add(id))
                    }
                }
                put(date, HealthDay(measurements, completedIds).also { require(!it.isEmpty()) })
            }
        }
        return HealthRecords(days, habits, goals)
    }

    private fun JSONObject.requireKeys(vararg keys: String) {
        require(this.keys().asSequence().toSet() == keys.toSet())
    }

    private fun parseId(raw: String): UUID = UUID.fromString(raw).also { require(it.toString() == raw) }

    private const val MAX_JSON_CHARS = 2_000_000
}

/** Authenticated Primary settings, with one transaction per edit. Access retirement protects each commit. */
internal class HealthRecordStore(private val space: SpaceContext) {
    init { require(space.spaceId == "primary") }

    fun read(): HealthRecords = synchronized(lock) { readLocked() }

    fun update(change: (HealthRecords) -> HealthRecords): HealthRecords = synchronized(lock) {
        val changed = change(readLocked())
        val raw = HealthRecordCodec.encode(changed)
        HealthRecordCodec.decode(raw)
        check(space.getSharedPreferences(NAMESPACE, 0).edit().putString(KEY, raw).commit())
        changed
    }

    fun delete() = synchronized(lock) {
        check(space.getSharedPreferences(NAMESPACE, 0).edit().remove(KEY).commit())
    }

    private fun readLocked(): HealthRecords = space.getSharedPreferences(NAMESPACE, 0).getString(KEY, null)
        ?.let(HealthRecordCodec::decode) ?: HealthRecords()

    companion object {
        private val lock = Any()
        internal const val NAMESPACE = "nova-health-mind-v1"
        internal const val KEY = "records"
    }
}
