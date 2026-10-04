package uk.co.traynor.privategallery.core.browser.v2

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.Clock
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSourceBitmapLoader
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultAssetLoaderFactory
import androidx.media3.transformer.DefaultDecoderFactory
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import uk.co.traynor.privategallery.core.vault.VaultImportSource
import java.io.File
import java.io.FileInputStream
import java.io.FilterInputStream
import java.io.IOException
import java.net.URI
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Media3 exports ordinary finite, non-DRM HLS/DASH to a single private MP4 before Vault import. */
@OptIn(UnstableApi::class)
internal fun streamVideoVaultSource(context: Context, candidate: MediaSaveCandidate, userAgent: String, page: String,
    cancelled: () -> Boolean, progress: (Int?) -> Unit, onFailure: (MediaSaveReason) -> Unit = {},
    onValidated: (VideoValidationFacts) -> Unit = {}): VaultImportSource {
    require(candidate.kind == MediaSaveKind.STREAM && candidate.mime != null)
    val origin = runCatching { URI(page) }.getOrNull()?.takeIf { it.scheme == "https" && !it.host.isNullOrBlank() }
        ?.let { "https://${it.host}/" }
    return VaultImportSource("browser-video.mp4", "video/mp4", openStream = { error("Primary network authority required") }, openScopedStream = { guard ->
        guard.check()
        if (cancelled()) throw IOException("Video save cancelled")
        try {
            if (BrowserMediaProbe.protectedManifest(URI(candidate.url), userAgent, origin, guard))
                throw BrowserVideoUnavailableException(MediaSaveReason.DRM_DETECTED)
        } catch (error: Throwable) {
            onFailure((error as? BrowserVideoUnavailableException)?.reason ?: MediaSaveReason.MEDIA_REQUEST_FAILED)
            throw error
        }
        val directory = File(context.cacheDir, "browser-video").apply { mkdirs() }
        val output = File(directory, "${UUID.randomUUID()}.mp4")
        val done = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>()
        val transformer = AtomicReference<Transformer?>()
        val main = Handler(Looper.getMainLooper())
        guard.own(AutoCloseable {
            failure.compareAndSet(null, IOException("Primary revoked"))
            main.post { transformer.getAndSet(null)?.cancel(); output.delete() }
            done.countDown()
        })
        val sourceFactory = DataSource.Factory {
            SessionVideoDataSource(userAgent, origin, cancelled, guard)
        }
        val tick = object : Runnable {
            override fun run() {
                val active = transformer.get() ?: return
                if (cancelled() || output.length() > MAX_STREAM_BYTES) {
                    failure.compareAndSet(null, IOException("Video save cancelled or too large"))
                    active.cancel(); transformer.set(null); done.countDown(); return
                }
                // Export progress is duration-based; the UI uses indeterminate download progress.
                main.postDelayed(this, 250)
            }
        }
        main.post {
            try {
                guard.check()
                if (cancelled()) throw IOException("Video save cancelled")
                val mediaSourceFactory = DefaultMediaSourceFactory(sourceFactory)
                val assetFactory = DefaultAssetLoaderFactory(context,
                    DefaultDecoderFactory.Builder(context).build(), Clock.DEFAULT,
                    mediaSourceFactory, DataSourceBitmapLoader(context))
                val active = Transformer.Builder(context).setAssetLoaderFactory(assetFactory)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            transformer.set(null); done.countDown()
                        }
                        override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                            failure.set(exportException); transformer.set(null); done.countDown()
                        }
                    }).build()
                transformer.set(active)
                progress(null)
                guard.check()
                active.start(MediaItem.Builder().setUri(candidate.url).setMimeType(candidate.mime).build(), output.absolutePath)
                main.postDelayed(tick, 250)
            } catch (error: Throwable) { failure.set(error); transformer.set(null); done.countDown() }
        }
        try {
            if (!done.await(5, TimeUnit.MINUTES) || cancelled()) {
                main.post { transformer.getAndSet(null)?.cancel() }
                throw IOException("Video save cancelled or timed out")
            }
            failure.get()?.let { throw IOException("Stream export failed", it) }
            guard.check()
            val facts = VideoFileValidator.inspect(output, "video/mp4", false)
            guard.check()
            onValidated(facts)
            FileInputStream(output).let { input ->
                object : FilterInputStream(input) {
                    override fun read(): Int { if (cancelled()) throw IOException("Video save cancelled"); return super.read() }
                    override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
                        if (cancelled()) throw IOException("Video save cancelled")
                        return super.read(bytes, offset, length)
                    }
                    override fun close() { try { super.close() } finally { output.delete() } }
                }
            }
        } catch (error: Throwable) {
            output.delete()
            val protected = generateSequence(error) { it.cause }.filterIsInstance<BrowserVideoUnavailableException>().firstOrNull()
            onFailure(protected?.reason ?: when (candidate.mime) {
                "application/dash+xml" -> MediaSaveReason.DASH_ASSEMBLY_FAILED
                else -> MediaSaveReason.HLS_ASSEMBLY_FAILED
            })
            throw error
        }
    }, isCancelled = cancelled)
}

/** Each segment gets only its own WebView cookie, plus the user agent and page origin. */
@OptIn(UnstableApi::class)
private class SessionVideoDataSource(private val userAgent: String, private val origin: String?,
    private val cancelled: () -> Boolean, private val guard: uk.co.traynor.privategallery.core.security.ScopedIoGuard) : DataSource {
    private var segment: OwnedVideoSegment? = null
    private var retiredSegment: OwnedVideoSegment? = null
    private var openedUri: android.net.Uri? = null
    override fun addTransferListener(transferListener: TransferListener) = Unit
    override fun open(dataSpec: DataSpec): Long {
        check(segment == null) { "Media source already open" }
        retiredSegment?.close()
        val opened = openOwnedVideoSegment(guard, URI(dataSpec.uri.toString()), dataSpec.position,
            dataSpec.length, dataSpec.httpMethod == DataSpec.HTTP_METHOD_GET, userAgent, origin, cancelled)
        try {
            openedUri = android.net.Uri.parse(opened.uri.toString())
            opened.checkAvailable()
            retiredSegment = null
            segment = opened
            return opened.remaining
        } catch (failure: Throwable) {
            openedUri = null
            try { opened.close() } catch (releaseFailure: Throwable) { failure.addSuppressed(releaseFailure) }
            throw failure
        }
    }
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val opened = segment
        if (opened != null) return opened.read(buffer, offset, length)
        guard.check()
        if (cancelled()) throw IOException("Video save cancelled")
        return if (length == 0) 0 else -1
    }
    override fun getUri(): android.net.Uri? { segment?.checkAvailable(); return openedUri }
    override fun getResponseHeaders(): Map<String, List<String>> = segment?.responseHeaders() ?: emptyMap()
    override fun close() {
        val opened = segment ?: retiredSegment
        retiredSegment = opened
        segment = null
        openedUri = null
        opened?.close()
    }
}

private const val MAX_STREAM_BYTES = 512L * 1024 * 1024
