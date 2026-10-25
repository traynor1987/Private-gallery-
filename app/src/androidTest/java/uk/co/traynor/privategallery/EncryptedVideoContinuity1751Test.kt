@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
package uk.co.traynor.privategallery

import android.content.ContextWrapper
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableIntStateOf
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import uk.co.traynor.privategallery.core.hidden.*
import uk.co.traynor.privategallery.core.vault.*
import uk.co.traynor.privategallery.ui.*
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class EncryptedVideoContinuity1751Test {
    @Test fun shortEncryptedVideoAdvancesBeforePanic() = withEncryptedVideoFixture(false, short = true) { fixture ->
        fixture.awaitReadyAndProgress()
        fixture.panicAndAssert()
    }
    @Test fun primaryEncryptedSessionSurvivesBufferingControlsAndRecomposition() = continuity(false, false)
    @Test fun hiddenEncryptedSessionSurvivesBufferingControlsAndRecomposition() = continuity(true, false)
    @Test fun boundedMemoryFallbackKeepsOnePlayerAcrossRecomposition() = continuity(false, true)

    private fun continuity(hidden: Boolean, fallback: Boolean) = withEncryptedVideoFixture(hidden, fallback) { fixture ->
        fixture.awaitReadyAndProgress()
        val buffering = AtomicInteger()
        val steady = java.util.concurrent.atomic.AtomicBoolean(true)
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (steady.get() && state == Player.STATE_BUFFERING) buffering.incrementAndGet()
            }
        }
        fixture.scenario.onActivity { fixture.player!!.addListener(listener) }
        val started = android.os.SystemClock.uptimeMillis()
        var lastRevision = -1L
        var steadyFailure: Throwable? = null
        try {
            while (android.os.SystemClock.uptimeMillis() - started < 30_000) {
                val revision = (android.os.SystemClock.uptimeMillis() - started) / 2000
                fixture.scenario.onActivity { activity ->
                    val player = checkNotNull(fixture.player)
                    assertSame("Ordinary playback must retain player identity", player, findContinuityPlayer(activity.window.decorView)?.player)
                    assertNull("No decoder/source error", player.playerError)
                    assertTrue("Loading UI must never steal native focus", activity.window.decorView.hasWindowFocus())
                    assertFalse("Playback must not conceal itself", activity.privacyCoverVisible())
                    assertEquals("No privacy epoch churn during foreground playback", fixture.epoch, activity.previewConcealmentEpoch)
                    assertTrue("Playback must not pause itself", player.playWhenReady)
                    assertEquals("Steady playback must not return to loading", Player.STATE_READY, player.playbackState)
                    assertTrue("Steady playback must actually play", player.isPlaying)
                    assertEquals("No transient buffering may be hidden between samples", 0, buffering.get())
                    assertEquals("Progress UI must not create a native focus handoff", 0, fixture.focusLosses.get())
                    if (revision != lastRevision) { fixture.revision.intValue++; lastRevision = revision }
                }
                Thread.sleep(100)
            }
        } catch (failure: Throwable) {
            steadyFailure = failure
            throw failure
        } finally {
            steady.set(false)
            runCatching { fixture.scenario.onActivity { fixture.player?.removeListener(listener) } }
                .exceptionOrNull()?.let { failure -> if (steadyFailure != null) steadyFailure!!.addSuppressed(failure) else throw failure }
        }
        fixture.scenario.onActivity {
            assertEquals("Recomposition must not reauthenticate/redecrypt the source", 1, fixture.loads.get())
            assertTrue("Real encrypted playback must advance continuously", checkNotNull(fixture.player).currentPosition >= 29_000)
        }
        fixture.scenario.onActivity {
            android.util.Log.i("PGVideoContinuity", "synthetic hidden=$hidden fallback=$fallback steadyMs=${android.os.SystemClock.uptimeMillis()-started} positionMs=${fixture.player!!.currentPosition} buffering=${buffering.get()} loads=${fixture.loads.get()} opens=${fixture.opens.get()} reads=${fixture.reads.get()} bytes=${fixture.readBytes.get()}")
        }
        for (position in listOf(10_000L, 5_000L)) {
            fixture.scenario.onActivity { fixture.player!!.seekTo(position) }
            fixture.awaitReadyAndProgress()
        }
        fixture.scenario.onActivity {
            val player = fixture.player!!
            for (speed in listOf(.5f, 1f, 1.5f, 2f)) {
                player.setPlaybackSpeed(speed)
                assertEquals(speed, player.playbackParameters.speed, .001f)
            }
            player.setPlaybackSpeed(1f)
            player.pause(); assertFalse(player.playWhenReady)
            player.play(); assertTrue(player.playWhenReady)
            assertEquals(1, fixture.loads.get())
        }
        fixture.panicAndAssert()
    }
}

internal fun findContinuityPlayer(view: View): PlayerView? {
    if (view is PlayerView) return view
    if (view is ViewGroup) for (index in 0 until view.childCount) findContinuityPlayer(view.getChildAt(index))?.let { return it }
    return null
}

internal class EncryptedVideoFixture(val scenario: ActivityScenario<MainActivity>) {
    val loads = AtomicInteger()
    val focusLosses = AtomicInteger()
    val opens = AtomicInteger()
    val reads = AtomicInteger()
    val readBytes = java.util.concurrent.atomic.AtomicLong()
    val revision = mutableIntStateOf(0)
    var player: Player? = null
    var epoch = 0
    fun diagnostic(activity: MainActivity): String = "focusLosses=${focusLosses.get()} focus=${activity.window.decorView.hasWindowFocus()} cover=${activity.privacyCoverVisible()} epoch=${activity.previewConcealmentEpoch} lifecycle=${activity.lifecycle.currentState} state=${findContinuityPlayer(activity.window.decorView)?.player?.playbackState} error=${findContinuityPlayer(activity.window.decorView)?.player?.playerError?.errorCode}\n${VaultPlaybackDiagnostics.summary()}"
    fun awaitReadyAndProgress() {
        val deadline = android.os.SystemClock.uptimeMillis() + 15_000
        var ready = false
        var evidence = ""
        while (!ready && android.os.SystemClock.uptimeMillis() < deadline) {
            scenario.onActivity { activity ->
                evidence = diagnostic(activity)
                val current = findContinuityPlayer(activity.window.decorView)?.player
                ready = current?.playbackState == Player.STATE_READY && current.currentPosition >= 500 && current.playerError == null && VaultPlaybackDiagnostics.summary().contains("FIRST_FRAME")
                if (ready) {
                    if (player == null) { player = current; epoch = activity.previewConcealmentEpoch }
                    else assertSame("Seeking must retain the prepared player", player, current)
                }
            }
            if (!ready) Thread.sleep(50)
        }
        assertTrue("Real player must prepare before Panic and advance: $evidence", ready)
    }
    fun panicAndAssert() {
        scenario.onActivity { activity ->
            val current = checkNotNull(player)
            current.play(); assertTrue(current.playWhenReady)
            activity.panicExit()
            assertFalse("Panic must synchronously stop playback", current.playWhenReady)
            val state = ViewModelProvider(activity)[ProtectedSessionState::class.java]
            assertNull(state.key); assertFalse(state.session.isUnlocked)
            assertFalse(state.hidden.session.isUnlocked); assertFalse(state.hidden.selected)
            assertTrue(state.automaticBiometricPromptAttempted)
            assertTrue("Panic must synchronously conceal frames", activity.privacyCoverVisible())
        }
        val deadline = android.os.SystemClock.uptimeMillis() + 5_000
        var detached = false
        while (!detached && android.os.SystemClock.uptimeMillis() < deadline) {
            scenario.onActivity { activity ->
                detached = findContinuityPlayer(activity.window.decorView) == null
                val state = ViewModelProvider(activity)[ProtectedSessionState::class.java]
                assertFalse("Fresh Primary authentication is required", state.session.isUnlocked)
                assertFalse("Fresh Hidden authentication is required", state.hidden.session.isUnlocked)
            }
            if (!detached) Thread.sleep(25)
        }
        assertTrue("Panic must remove the sensitive video surface", detached)
    }
}

internal fun withEncryptedVideoFixture(hidden: Boolean, fallback: Boolean = false, short: Boolean = false, test: (EncryptedVideoFixture) -> Unit) {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val context = instrumentation.targetContext
    val root = File(context.cacheDir, "continuity-${java.util.UUID.randomUUID()}").apply { mkdirs() }
    val isolated = object : ContextWrapper(context) {
        override fun getFilesDir() = File(root, "files").apply { mkdirs() }
        override fun getNoBackupFilesDir() = File(root, "no-backup").apply { mkdirs() }
    }
    val bytes = if (short) uk.co.traynor.privategallery.core.media.SyntheticVideo.bytes() else instrumentation.context.assets.open("video-continuity.mp4").use { it.readBytes() }
    val key = ByteArray(32) { if (hidden) 9 else 7 }
    val executor = Executors.newSingleThreadExecutor()
    val sessions = java.util.Collections.synchronizedList(mutableListOf<ProtectedVideoSession>())
    var primaryFailure: Throwable? = null
    try {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val fixture = EncryptedVideoFixture(scenario)
            // Retire the real app shell before creating fixture authentication/operation authority.
            // HiddenFoundation disposal legitimately revokes old presentation operations.
            val neutralCommitted = java.util.concurrent.CountDownLatch(1)
            scenario.onActivity { it.setContent { androidx.compose.runtime.SideEffect { neutralCommitted.countDown() } } }
            assertTrue("Old app composition must be disposed before fixture authentication", neutralCommitted.await(5, java.util.concurrent.TimeUnit.SECONDS))
            instrumentation.waitForIdleSync()
            val focusDeadline = android.os.SystemClock.uptimeMillis() + 5_000
            var focused = false
            while (!focused && android.os.SystemClock.uptimeMillis() < focusDeadline) {
                scenario.onActivity { focused = it.window.decorView.hasWindowFocus() && !it.privacyCoverVisible() }
                if (!focused) Thread.sleep(25)
            }
            assertTrue("Synthetic authenticated fixture requires actual native foreground focus", focused)
            scenario.onActivity { activity ->
                activity.window.decorView.viewTreeObserver.addOnWindowFocusChangeListener { hasFocus ->
                    if (!hasFocus) fixture.focusLosses.incrementAndGet()
                }
            }
            lateinit var owner: ProtectedSessionState
            scenario.onActivity { activity ->
                owner = ViewModelProvider(activity)[ProtectedSessionState::class.java]
                if (hidden) { assertTrue(owner.hidden.session.complete(owner.hidden.session.beginAuthentication(), key.copyOf())); owner.hidden.select() }
                else { owner.key = key.copyOf(); owner.session.unlock() }
            }
            val primary = if (!hidden) AndroidVaultRepository(isolated, key) else null
            val hiddenRepository = if (hidden) HiddenRepository(isolated, owner.hidden.session) else null
            val item = hiddenRepository?.importMedia("synthetic.mp4", "video/mp4", bytes.inputStream()) ?: (VaultImportCoordinator(checkNotNull(primary)).acquire(VaultImportSource("synthetic.mp4", "video/mp4", { bytes.inputStream() })) as ImportResult.Imported).item
            if (hidden) assertEquals("Fixture must hold current Hidden operation authority before playback", bytes.size.toLong(), checkNotNull(hiddenRepository).mediaSize(item.id))
            val authenticatedKey = owner.key
            val allowed = { if (hidden) owner.hidden.session.isUnlocked else owner.session.isUnlocked && owner.key === authenticatedKey }
            scenario.onActivity { activity -> activity.setContent {
                val revision = fixture.revision.intValue
                FullscreenMediaViewer(listOf(ViewerMediaEntry(item.id, "video/mp4")), uk.co.traynor.privategallery.core.ui.MediaViewerSource.VAULT, 0, {},
                    onLoadFavouriteState = { _, done -> if (revision >= 0) done(false) },
                    onLoadProtectedBytes = if (!fallback) null else { _, done -> fixture.loads.incrementAndGet(); done(Result.success(bytes.copyOf())) },
                    onLoadVideoSession = if (fallback) null else { _, cancelled, done ->
                        fixture.loads.incrementAndGet()
                        executor.submit {
                            val result = runCatching<ProtectedVideoSession> {
                                if (hidden) {
                                    HiddenVideoSession(checkNotNull(hiddenRepository), item.id) { cancelled() || !allowed() }
                                }
                                else checkNotNull(primary).openVideoSession(item, allowed, cancelled)
                            }
                            val measured = result.map { real -> object : ProtectedVideoSession {
                                override val sourceFactory = androidx.media3.datasource.DataSource.Factory {
                                    val source = real.sourceFactory.createDataSource()
                                    object : androidx.media3.datasource.DataSource by source {
                                        override fun open(spec: androidx.media3.datasource.DataSpec): Long {
                                            fixture.opens.incrementAndGet(); return source.open(spec)
                                        }
                                        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                                            fixture.reads.incrementAndGet()
                                            return source.read(buffer, offset, length).also { if (it > 0) fixture.readBytes.addAndGet(it.toLong()) }
                                        }
                                    }
                                }
                                override fun close() = real.close()
                            } }
                            measured.getOrNull()?.let { sessions.add(it) }
                            activity.runOnUiThread { done(measured) }
                        }
                    })
            } }
            test(fixture)
        }
    } catch (failure: Throwable) {
        primaryFailure = failure
        throw failure
    } finally {
        var cleanupFailure: Throwable? = null
        fun cleanup(action: () -> Unit) {
            try { action() } catch (failure: Throwable) {
                if (cleanupFailure == null) cleanupFailure = failure else cleanupFailure!!.addSuppressed(failure)
            }
        }
        executor.shutdownNow()
        var terminated = false
        cleanup { terminated = executor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS); check(terminated) { "Synthetic reader did not terminate" } }
        val retiring = synchronized(sessions) { sessions.toList() }
        retiring.forEach { session -> cleanup { session.close() } }
        bytes.fill(0); key.fill(0)
        if (terminated) cleanup { check(root.deleteRecursively()) { "Synthetic encrypted fixture cleanup failed" } }
        cleanupFailure?.let { failure -> if (primaryFailure != null) primaryFailure!!.addSuppressed(failure) else throw failure }
    }
}
