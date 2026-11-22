package uk.co.traynor.privategallery.home2

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.AtomicFile
import java.io.File
import java.security.KeyStore
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONArray
import org.json.JSONObject
import uk.co.traynor.privategallery.core.home2.NovaCoreApp
import uk.co.traynor.privategallery.core.home2.SpaceContext
import uk.co.traynor.privategallery.intelligence.IntelligenceBriefings

internal object NovaReminders {
    private const val CHANNEL = "nova-core-reminders"
    private const val NOTIFICATION_ID = 81001
    internal const val ALARM = "uk.co.traynor.privategallery.CORE_REMINDER_ALARM"
    private const val KEY = "novaos-core-reminder-local-v1"
    private val lateWindow = Duration.ofHours(12)
    private val lock = Any()

    private data class Entry(val schedule: NovaReminderSchedule, val cursor: Instant)

    fun canNotify(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED &&
            context.getSystemService(NotificationManager::class.java).areNotificationsEnabled()

    fun syncTasks(space: SpaceContext, tasks: List<NovaTask>) = update(space, NovaCoreApp.TASKS,
        tasks.mapNotNull { task -> taskReminderSchedule(task)?.let { task.id to it } }.toMap())

    fun syncCalendar(space: SpaceContext, events: List<NovaCalendarEvent>) = update(space, NovaCoreApp.CALENDAR,
        events.mapNotNull { event -> calendarReminderSchedule(event)?.let { event.id to it } }.toMap())

    fun update(space: SpaceContext, app: NovaCoreApp, schedules: Map<String, NovaReminderSchedule>) =
        synchronized(lock) {
            check(space.spaceId == "primary" && space.current())
            val context = space.baseContext.applicationContext
            val records = read(context)
            val prefix = "${app.storageId}:"
            val merged = records.filterKeys { !it.startsWith(prefix) }.toMutableMap()
            val now = Instant.now()
            for ((id, schedule) in schedules) {
                val key = prefix + id
                merged[key] = records[key]?.takeIf { it.schedule == schedule } ?: Entry(schedule, now)
            }
            space.commitAuthorizedWrite {
                if (records != merged) write(context, merged)
                arm(context, merged)
            }
        }

    fun dispatch(context: Context, shouldNotify: Boolean) = synchronized(lock) {
        val records = read(context)
        val now = Instant.now()
        val zone = ZoneId.systemDefault()
        val due = records.filterValues { it.schedule.nextAfter(it.cursor, zone)?.let { next -> next <= now } == true }
        if (due.isEmpty()) {
            arm(context, records)
            return@synchronized
        }
        val updated = records.mapValues { (id, entry) -> if (id in due) entry.copy(cursor = now) else entry }
        write(context, updated)
        arm(context, updated)
        val recent = due.values.any { entry ->
            val start = maxOf(entry.cursor, now.minus(lateWindow))
            entry.schedule.nextAfter(start, zone)?.let { it <= now } == true
        }
        if (shouldNotify && recent && canNotify(context)) post(context)
    }

    internal fun nextScheduledAt(context: Context, zone: ZoneId): Instant? = synchronized(lock) {
        nextAt(read(context), zone)
    }

    private fun nextAt(records: Map<String, Entry>, zone: ZoneId): Instant? =
        records.values.mapNotNull { it.schedule.nextAfter(it.cursor, zone) }.minOrNull()

    private fun arm(context: Context, records: Map<String, Entry>) {
        val manager = context.getSystemService(AlarmManager::class.java)
        val next = nextAt(records, ZoneId.systemDefault())
        val flags = PendingIntent.FLAG_IMMUTABLE or if (next == null) PendingIntent.FLAG_NO_CREATE else 0
        val alarm = PendingIntent.getBroadcast(context, 0,
            Intent(context, NovaReminderReceiver::class.java).setAction(ALARM), flags) ?: return
        if (next == null) {
            manager.cancel(alarm)
            alarm.cancel()
        } else manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.toEpochMilli(), alarm)
    }

    @SuppressLint("MissingPermission")
    private fun post(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Nova task and calendar reminders",
            NotificationManager.IMPORTANCE_DEFAULT))
        val builder = Notification.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("NovaOS reminder")
            .setContentText("Open NovaOS to check your tasks and calendar.")
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
        context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
            builder.setContentIntent(PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE))
        }
        manager.notify(NOTIFICATION_ID, builder.build())
    }

    private fun file(context: Context) = AtomicFile(File(context.noBackupFilesDir, "nova-core-reminders"))

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return store.getKey(KEY, null) as? SecretKey ?: KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore",
        ).run {
            init(KeyGenParameterSpec.Builder(KEY, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build())
            generateKey()
        }
    }

    private fun read(context: Context): Map<String, Entry> {
        val index = file(context)
        val bytes = try { index.readFully() } catch (_: java.io.FileNotFoundException) { return emptyMap() }
        require(bytes.size > 28)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 0, 12))
        val plaintext = cipher.doFinal(bytes, 12, bytes.size - 12)
        try {
            val records = JSONObject(plaintext.decodeToString())
            return records.keys().asSequence().associateWith { id ->
                val entry = records.getJSONObject(id)
                val skips = entry.getJSONArray("skip")
                Entry(NovaReminderSchedule(
                    LocalDate.parse(entry.getString("date")), LocalTime.parse(entry.getString("time")),
                    entry.optString("zone").takeIf(String::isNotEmpty)?.let(ZoneId::of),
                    NovaEventRepeat.valueOf(entry.getString("repeat")),
                    (0 until skips.length()).map { LocalDate.parse(skips.getString(it)) }.toSet(),
                    entry.getInt("lead"),
                ), Instant.parse(entry.getString("cursor")))
            }
        } finally {
            plaintext.fill(0)
        }
    }

    private fun write(context: Context, records: Map<String, Entry>) {
        val snapshot = JSONObject()
        for ((id, entry) in records) {
            val schedule = entry.schedule
            snapshot.put(id, JSONObject().put("date", schedule.date.toString()).put("time", schedule.time.toString())
                .put("zone", schedule.zone?.id).put("repeat", schedule.repeat.name).put("lead", schedule.minutesBefore)
                .put("skip", JSONArray(schedule.skippedDates.map(LocalDate::toString))).put("cursor", entry.cursor.toString()))
        }
        val plaintext = snapshot.toString().encodeToByteArray()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val bytes = try {
            cipher.init(Cipher.ENCRYPT_MODE, key())
            check(cipher.iv.size == 12)
            cipher.iv + cipher.doFinal(plaintext)
        } finally {
            plaintext.fill(0)
        }
        val index = file(context)
        val output = index.startWrite()
        try {
            output.write(bytes)
            index.finishWrite(output)
        } catch (failure: Exception) {
            index.failWrite(output)
            throw failure
        }
    }
}

class NovaReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(NovaReminders.ALARM, IntelligenceBriefings.ALARM,
                Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED)) return
        val pending = goAsync()
        Thread {
            try {
                if (intent.action != IntelligenceBriefings.ALARM) {
                    runCatching { NovaReminders.dispatch(context, shouldNotify = true) }
                }
                if (intent.action != NovaReminders.ALARM) {
                    runCatching { IntelligenceBriefings.refresh(context) }
                }
            } finally {
                pending.finish()
            }
        }.start()
    }
}
