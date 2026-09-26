package uk.co.traynor.privategallery.core.browser.v2

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.Clock
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSourceBitmapLoader
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
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
    cancelled: () -> Boolean, progress: (Int?) -> Unit, onFailure: (MediaSaveReason) -> Unit = {}): VaultImportSource {
    require(candidate.kind == MediaSaveKind.STREAM && candidate.mime != null)
    val origin = runCatching { URI(page) }.getOrNull()?.takeIf { it.scheme == "https" && !it.host.isNullOrBlank() }
        ?.let { "https://${it.host}/" }
    return VaultImportSource("browser-video.mp4", "video/mp4", openStream = {
        if (cancelled()) throw IOException("Video save cancelled")
        try {
            if (BrowserMediaProbe.protectedManifest(URI(candidate.url), userAgent, origin))
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
        val sourceFactory = DataSource.Factory {
            SessionVideoDataSource(userAgent, origin, cancelled)
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
            if (output.length() !in 16..MAX_STREAM_BYTES) throw IOException("Invalid stream export")
            FileInputStream(output).use { stream ->
                val header = ByteArray(16)
                if (!validHeader("video/mp4", header, stream.read(header))) throw IOException("Invalid MP4 export")
            }
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
                "application/dash+xml" -> MediaSaveReason.DASH_UNSUPPORTED
                else -> MediaSaveReason.HLS_UNSUPPORTED
            })
            throw error
        }
    }, isCancelled = cancelled)
}

/** Each segment gets only its own WebView cookie, plus the user agent and page origin. */
@OptIn(UnstableApi::class)
private class SessionVideoDataSource(private val userAgent: String, private val origin: String?,
    private val cancelled: () -> Boolean) : DataSource {
    private val delegate = DefaultHttpDataSource.Factory().setUserAgent(userAgent).createDataSource()
    override fun addTransferListener(transferListener: TransferListener) = delegate.addTransferListener(transferListener)
    override fun open(dataSpec: DataSpec): Long {
        if (cancelled()) throw IOException("Video save cancelled")
        val target = URI(dataSpec.uri.toString())
        if (target.scheme != "https" || target.host.isNullOrBlank() || target.rawUserInfo != null || target.port !in setOf(-1, 443))
            throw IOException("Unsupported media transport")
        if (target.path.orEmpty().endsWith(".m3u8", true) || target.path.orEmpty().endsWith(".mpd", true)) {
            if (BrowserMediaProbe.protectedManifest(target, userAgent, origin))
                throw BrowserVideoUnavailableException(MediaSaveReason.DRM_DETECTED)
        }
        val headers = dataSpec.httpRequestHeaders.toMutableMap()
        origin?.let { headers["Referer"] = it; headers["Origin"] = it.removeSuffix("/") }
        CookieManager.getInstance().getCookie(target.toString())?.let { headers["Cookie"] = it }
        return delegate.open(dataSpec.withRequestHeaders(headers))
    }
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (cancelled()) throw IOException("Video save cancelled")
        return delegate.read(buffer, offset, length)
    }
    override fun getUri() = delegate.uri
    override fun getResponseHeaders(): Map<String, List<String>> = delegate.responseHeaders
    override fun close() = delegate.close()
}

private const val MAX_STREAM_BYTES = 512L * 1024 * 1024
