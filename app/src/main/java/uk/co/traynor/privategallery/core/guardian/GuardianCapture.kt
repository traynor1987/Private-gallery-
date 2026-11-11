package uk.co.traynor.privategallery.core.guardian

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

internal data class GuardianCaptureHealth(
    val enabled: Boolean = false,
    val retentionDays: Int = 7,
    val retained: Int? = null,
    val limited: Boolean = false,
    val busy: Boolean = false,
    val inbox: GuardianInboxCounts? = null,
    val learningCount: Int? = null,
    val includesMain: Boolean = false,
)

/** Serialises settings, archive writes and deletion without blocking Android's listener callbacks. */
internal object GuardianCapture {
    private val worker = Executors.newSingleThreadExecutor()
    private val pendingEvents = AtomicInteger()
    private val generation = AtomicInteger()
    private val status = MutableStateFlow(GuardianCaptureHealth())
    val state = status.asStateFlow()
    private val stage = MutableStateFlow<GuardianCaptureStage?>(null)
    val mainCaptureStage = stage.asStateFlow()

    fun noteMainCapture(result: GuardianCaptureStage) { stage.value = result }

    private val found = MutableStateFlow<List<GuardianContact>>(emptyList())
    val contactCandidates = found.asStateFlow()
    private var discoveryUntil = 0L
    private val main = Handler(Looper.getMainLooper())
    private val endDiscovery = Runnable { stopContactDiscovery() }

    fun startContactDiscovery() {
        stopContactDiscovery()
        discoveryUntil = SystemClock.elapsedRealtime() + 90_000L
        main.postDelayed(endDiscovery, 90_000L)
    }

    fun stopContactDiscovery() {
        discoveryUntil = 0L
        found.value = emptyList()
        main.removeCallbacks(endDiscovery)
    }

    fun isDiscoveringContacts() = SystemClock.elapsedRealtime() < discoveryUntil

    fun offerContact(contact: GuardianContact) {
        if (isDiscoveringContacts()) found.update { (listOf(contact) + it.filterNot { row -> row.key == contact.key }).take(20) }
    }

    fun invalidatePending() { generation.incrementAndGet() }

    fun isEnabled(context: Context) = prefs(context).getBoolean("enabled", false)

    fun refresh(context: Context) = manage(context) { _, _ -> }

    private fun includesMain(context: Context) = prefs(context).getBoolean("include_main_whatsapp", false)

    fun setEnabled(context: Context, enabled: Boolean, includeMain: Boolean = false) {
        generation.incrementAndGet()
        manage(context) { app, _ ->
            check(!enabled || GuardianNotificationListener.hasAccess(app))
            check(prefs(app).edit().putBoolean("enabled", enabled).putBoolean("include_main_whatsapp", enabled && includeMain).commit())
            if (!enabled) GuardianNotificationListener.setAutomaticDismissal(app, false)
        }
    }

    fun setRetention(context: Context, days: Int) {
        require(days in GuardianCaptureStore.RETENTION_DAYS)
        generation.incrementAndGet()
        manage(context) { app, _ -> check(prefs(app).edit().putInt("retention", days).commit()) }
    }

    fun clear(context: Context) {
        generation.incrementAndGet()
        manage(context) { _, store -> store.clear() }
    }

    fun record(context: Context, event: GuardianCaptureEvent, monitored: GuardianConversationKey, onDurable: (() -> Unit)? = null) {
        val app = context.applicationContext
        val expected = generation.get()
        if (pendingEvents.incrementAndGet() > 16) {
            pendingEvents.decrementAndGet()
            status.update { it.copy(limited = true) }
            if (event.app == GUARDIAN_WHATSAPP) noteMainCapture(GuardianCaptureStage.QUEUE)
            return
        }
        worker.execute {
            try {
                if (generation.get() != expected || !isEnabled(app) || !GuardianNotificationListener.hasAccess(app)) {
                    return@execute
                }
                val days = retention(app)
                val mainMessage = event.app == GUARDIAN_WHATSAPP
                val includeMain = includesMain(app)
                val store = GuardianCaptureStore(app)
                val now = guardianInboxClock(app)
                val result = runCatching {
                    val contacts = store.monitoredContacts()
                    val contact = contacts.singleOrNull { it.key == monitored }
                    // A transient notification key cannot prove it is outside a contact's explicit exclusion.
                    val hasExclusion = mainMessage && monitored.conversation.startsWith("notification:") &&
                        contacts.any { it.key.app == monitored.app && it.key.profile == monitored.profile &&
                            (!it.enabled || !it.retain) }
                    if (hasExclusion || !guardianCaptureAllowed(includeMain && !event.requiresContactConsent, monitored, contact)) {
                        if (mainMessage) noteMainCapture(GuardianCaptureStage.CONSENT)
                        return@execute
                    }
                    val selected = if (contact == null) event else event.copy(
                        sensitivity = contact.sensitivity, holdUncertain = contact.holdUncertain)
                    store.append(selected, days, now.wall, assess = false, clock = now)
                }
                val captured = result.getOrNull()
                if (captured?.allDurable == true && onDurable != null && generation.get() == expected) {
                    Handler(Looper.getMainLooper()).post {
                        if (generation.get() == expected && isEnabled(app)) onDurable()
                    }
                }
                if (captured != null && captured.added > 0) {
                    // Classification may fail independently. A durable row remains Awaiting Analysis.
                    runCatching { store.assessCaptured(event, days, now) }
                }
                val counts = runCatching { store.inboxCounts(days, now) }.getOrNull()
                if (mainMessage) noteMainCapture(when {
                    captured == null || counts == null -> GuardianCaptureStage.STORAGE
                    captured.partial -> GuardianCaptureStage.PARTIAL
                    captured.added == 0 -> GuardianCaptureStage.DUPLICATE
                    else -> GuardianCaptureStage.STORED
                })
                status.value = GuardianCaptureHealth(
                    enabled = true, retentionDays = days, retained = captured?.retained,
                    limited = captured == null || captured.partial || counts == null,
                    inbox = counts,
                    learningCount = runCatching { store.learningCount() }.getOrNull(),
                    includesMain = includeMain,
                )
            } finally {
                pendingEvents.decrementAndGet()
            }
        }
    }

    fun verifyFixture(context: Context, done: (Boolean) -> Unit) {
        val app = context.applicationContext
        worker.execute {
            val secured = runCatching { GuardianCaptureStore(app).verifyFixture() }.getOrDefault(false)
            Handler(Looper.getMainLooper()).post { done(secured) }
        }
    }

    private fun manage(context: Context, change: (Context, GuardianCaptureStore) -> Unit) {
        val app = context.applicationContext
        status.update { it.copy(busy = true) }
        worker.execute {
            val store = GuardianCaptureStore(app)
            val result = runCatching {
                change(app, store)
                store.inboxCounts(retention(app), guardianInboxClock(app))
            }
            val counts = result.getOrNull()
            val retained = counts?.let { it.pending + it.ready + it.cooling + it.protected + it.history }
            status.value = GuardianCaptureHealth(isEnabled(app), retention(app), retained, result.isFailure, inbox = counts,
                learningCount = runCatching { store.learningCount() }.getOrNull(), includesMain = includesMain(app))
        }
    }

    fun retention(context: Context) = prefs(context).getInt("retention", 7)

    private fun prefs(context: Context) = context.getSharedPreferences("nova_guardian_capture", Context.MODE_PRIVATE)
}
