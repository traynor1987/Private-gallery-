package uk.co.traynor.privategallery.core.browser.v2

import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import uk.co.traynor.privategallery.core.vault.VaultImportSource
import java.io.File
import java.io.FileInputStream
import java.io.FilterInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

data class VideoValidationFacts(val bytes: Long, val durationMs: Long, val videoTracks: Int, val audioTracks: Int)

internal object VideoValidationPolicy {
    fun headerReason(mime: String, bytes: ByteArray): MediaSaveReason? {
        val leading = bytes.toString(Charsets.UTF_8).trimStart()
        if (leading.startsWith("#EXTM3U", true) || leading.startsWith("<MPD", true)) return MediaSaveReason.MANIFEST_DETECTED
        if (leading.startsWith("<html", true) || leading.startsWith("<!doctype", true) || leading.startsWith("{", true)) return MediaSaveReason.NON_MEDIA_RESPONSE
        return if (validHeader(mime, bytes, bytes.size)) null else MediaSaveReason.NON_MEDIA_RESPONSE
    }
    fun factsReason(bytes: Long, durationMs: Long, videoTracks: Int, samplesReachEnd: Boolean): MediaSaveReason? = when {
        bytes < 1024 -> MediaSaveReason.MEDIA_VALIDATION_FAILED
        videoTracks < 1 -> MediaSaveReason.VIDEO_TRACK_MISSING
        durationMs <= 0 -> MediaSaveReason.ZERO_DURATION
        !samplesReachEnd -> MediaSaveReason.DIRECT_MEDIA_PARTIAL
        else -> null
    }
}

/** Checks the final local file, never a manifest, URL, header or encrypted staging fragment. */
internal object VideoFileValidator {
    fun inspect(file: File, mime: String, requireAudio: Boolean): VideoValidationFacts {
        var reason = MediaSaveReason.MEDIA_VALIDATION_FAILED
        try {
            if (!file.isFile || file.length() < 1024 || file.length() > MAX_VALIDATED_VIDEO_BYTES)
                throw BrowserVideoUnavailableException(reason)
            val header = FileInputStream(file).use { input -> ByteArray(64).also { if (input.read(it) < 12) throw BrowserVideoUnavailableException(reason) } }
            VideoValidationPolicy.headerReason(mime, header)?.let { throw BrowserVideoUnavailableException(it) }
            val retriever = MediaMetadataRetriever()
            val duration = try {
                retriever.setDataSource(file.absolutePath)
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            } finally { retriever.release() }
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(file.absolutePath)
                val formats = (0 until extractor.trackCount).map { extractor.getTrackFormat(it) }
                val video = formats.indices.filter { formats[it].getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true }
                val audio = formats.indices.filter { formats[it].getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
                if (requireAudio && audio.isEmpty()) throw BrowserVideoUnavailableException(MediaSaveReason.AUDIO_TRACK_MISSING)
                val tracks = video + audio
                val samplesReachEnd = tracks.isNotEmpty() && tracks.all { index ->
                    extractor.selectTrack(index)
                    val first = extractor.readSampleData(ByteBuffer.allocate(256 * 1024), 0) > 0
                    extractor.seekTo((duration - 1500).coerceAtLeast(0) * 1000, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                    val nearEnd = extractor.readSampleData(ByteBuffer.allocate(256 * 1024), 0) > 0 &&
                        extractor.sampleTime >= (duration - 10_000).coerceAtLeast(0) * 1000
                    extractor.unselectTrack(index)
                    first && nearEnd
                }
                VideoValidationPolicy.factsReason(file.length(), duration, video.size, samplesReachEnd)?.let {
                    throw BrowserVideoUnavailableException(it)
                }
                return VideoValidationFacts(file.length(), duration, video.size, audio.size)
            } finally { extractor.release() }
        } catch (failure: BrowserVideoUnavailableException) { throw failure
        } catch (_: Exception) { throw BrowserVideoUnavailableException(reason) }
    }
}

/** Direct HTTP media is fully retrieved, parsed, then streamed to encrypted staging. */
internal fun validatedDirectVideoSource(cache: File, original: VaultImportSource,
    onFailure: (MediaSaveReason) -> Unit, onValidated: (VideoValidationFacts) -> Unit = {}): VaultImportSource {
    val staged = AtomicReference<File?>()
    return original.copy(openStream = {
        val directory = File(cache, "browser-video").apply { mkdirs() }
        val output = File(directory, "${UUID.randomUUID()}.partial")
        staged.set(output)
        try {
            original.openStream().use { input -> output.outputStream().use { sink ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    if (original.isCancelled()) throw IOException("Video save cancelled")
                    val read = input.read(buffer)
                    if (read < 0) break
                    if (output.length() + read > MAX_VALIDATED_VIDEO_BYTES) throw BrowserVideoUnavailableException(MediaSaveReason.MEDIA_VALIDATION_FAILED)
                    sink.write(buffer, 0, read)
                }
            } }
            if (original.isCancelled()) throw IOException("Video save cancelled")
            onValidated(VideoFileValidator.inspect(output, original.mimeType, false))
            object : FilterInputStream(FileInputStream(output)) {
                override fun close() { try { super.close() } finally { output.delete(); staged.compareAndSet(output, null) } }
            }
        } catch (failure: Throwable) {
            output.delete(); staged.compareAndSet(output, null)
            onFailure((failure as? BrowserVideoUnavailableException)?.reason ?: MediaSaveReason.MEDIA_REQUEST_FAILED)
            throw failure
        }
    }, onConsumed = { staged.getAndSet(null)?.delete(); original.onConsumed() })
}

private const val MAX_VALIDATED_VIDEO_BYTES = 512L * 1024 * 1024
