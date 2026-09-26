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
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
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
    private var connection: HttpURLConnection? = null
    private var input: InputStream? = null
    private var openedUri: android.net.Uri? = null
    private var remaining = -1L
    override fun addTransferListener(transferListener: TransferListener) = Unit
    override fun open(dataSpec: DataSpec): Long {
        if (cancelled()) throw IOException("Video save cancelled")
        require(dataSpec.httpMethod == DataSpec.HTTP_METHOD_GET) { "Unsupported media request" }
        var target = URI(dataSpec.uri.toString())
        repeat(4) { attempt ->
            if (cancelled() || target.scheme != "https" || target.host.isNullOrBlank() ||
                target.rawUserInfo != null || target.port !in setOf(-1, 443)) throw IOException("Unsupported media transport")
            if (target.path.orEmpty().endsWith(".m3u8", true) || target.path.orEmpty().endsWith(".mpd", true)) {
                if (BrowserMediaProbe.protectedManifest(target, userAgent, origin))
                    throw BrowserVideoUnavailableException(MediaSaveReason.DRM_DETECTED)
            }
            val active = (URL(target.toString()).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                instanceFollowRedirects = false
                connectTimeout = 15_000
                readTimeout = 20_000
                useCaches = false
                setRequestProperty("User-Agent", userAgent)
                origin?.let { setRequestProperty("Referer", it); setRequestProperty("Origin", it.removeSuffix("/")) }
                CookieManager.getInstance().getCookie(target.toString())?.let { setRequestProperty("Cookie", it) }
                if (dataSpec.position > 0 || dataSpec.length >= 0) {
                    val end = if (dataSpec.length >= 0) (dataSpec.position + dataSpec.length - 1).toString() else ""
                    setRequestProperty("Range", "bytes=${dataSpec.position}-$end")
                }
            }
            val status = active.responseCode
            if (status in 300..399) {
                val next = BrowserMediaProbe.resolveSafeRedirect(target, active.getHeaderField("Location"))
                active.disconnect()
                if (attempt == 3 || next == null) throw IOException("Unsupported media redirect")
                target = next
                return@repeat
            }
            if (status == 401 || status == 403) { active.disconnect(); throw BrowserVideoUnavailableException(MediaSaveReason.SESSION_AUTH_FAILED) }
            if (status !in 200..299) { active.disconnect(); throw IOException("Media request failed") }
            connection = active
            openedUri = android.net.Uri.parse(target.toString())
            try {
                input = active.inputStream
                if (status == 200 && dataSpec.position > 0) {
                    if (dataSpec.position > 8L * 1024 * 1024) throw IOException("Range unsupported")
                    var skipped = 0L
                    while (skipped < dataSpec.position) {
                        val count = input!!.skip(dataSpec.position - skipped)
                        if (count <= 0) throw IOException("Media range unavailable")
                        skipped += count
                    }
                }
            } catch (error: Throwable) { close(); throw error }
            remaining = if (dataSpec.length >= 0) dataSpec.length else
                active.contentLengthLong.takeIf { it >= 0 }?.minus(if (status == 200) dataSpec.position else 0) ?: -1L
            return remaining
        }
        throw IOException("Media redirect limit exceeded")
    }
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (cancelled()) throw IOException("Video save cancelled")
        if (length == 0) return 0
        if (remaining == 0L) return -1
        val count = input?.read(buffer, offset, if (remaining < 0) length else minOf(length.toLong(), remaining).toInt()) ?: -1
        if (count > 0 && remaining > 0) remaining -= count
        return count
    }
    override fun getUri(): android.net.Uri? = openedUri
    override fun getResponseHeaders(): Map<String, List<String>> = connection?.headerFields?.filterKeys { it != null }
        ?.mapKeys { it.key!! } ?: emptyMap()
    override fun close() {
        try { input?.close() } finally { connection?.disconnect(); connection = null; input = null; openedUri = null; remaining = -1L }
    }
}

private const val MAX_STREAM_BYTES = 512L * 1024 * 1024
