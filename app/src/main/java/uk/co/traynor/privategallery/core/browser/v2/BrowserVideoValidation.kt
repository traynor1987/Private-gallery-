package uk.co.traynor.privategallery.core.browser.v2

import android.media.MediaDataSource
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import uk.co.traynor.privategallery.core.vault.VaultImportSource
import java.io.File
import java.io.FileInputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

data class VideoValidationFacts(val bytes: Long, val durationMs: Long, val videoTracks: Int, val audioTracks: Int)

internal object VideoValidationPolicy {
    fun expectedDurationReason(actualMs: Long, expectedMs: Long?): MediaSaveReason? =
        if (expectedMs != null && actualMs + maxOf(2_000L, expectedMs / 50) < expectedMs)
            MediaSaveReason.DIRECT_MEDIA_PARTIAL else null

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

/** Container/sample completeness checks; this does not decode every frame. */
internal object VideoFileValidator {
    fun inspect(file: File, mime: String, requireAudio: Boolean, expectedDurationMs: Long? = null,
        cancelled: () -> Boolean = { false }): VideoValidationFacts = RandomAccessFile(file, "r").use { input ->
        val source = object : MediaDataSource() {
            override fun getSize() = input.length()
            override fun close() = Unit
            override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
                if (cancelled()) throw IOException("Video validation cancelled")
                if (size == 0) return 0
                if (position < 0) throw IOException("Invalid media position")
                if (position >= input.length()) return -1
                input.seek(position)
                return input.read(buffer, offset, size)
            }
        }
        inspect(source, input.length(), mime, requireAudio, expectedDurationMs, cancelled)
    }

    /** Caller owns the source. Hidden passes an authenticated reader over encrypted staging. */
    fun inspect(source: MediaDataSource, size: Long, mime: String, requireAudio: Boolean,
        expectedDurationMs: Long? = null, cancelled: () -> Boolean = { false }): VideoValidationFacts {
        fun allowed() { if (cancelled()) throw IOException("Video validation cancelled") }
        try {
            allowed()
            if (size < 1024 || size > MAX_VALIDATED_VIDEO_BYTES)
                throw BrowserVideoUnavailableException(MediaSaveReason.MEDIA_VALIDATION_FAILED)
            val header = ByteArray(64)
            try {
                var received = 0
                while (received < header.size) {
                    allowed()
                    val count = source.readAt(received.toLong(), header, received, header.size - received)
                    if (count <= 0) throw BrowserVideoUnavailableException(MediaSaveReason.DIRECT_MEDIA_PARTIAL)
                    received += count
                }
                VideoValidationPolicy.headerReason(mime, header)?.let { throw BrowserVideoUnavailableException(it) }
            } finally { header.fill(0) }
            if (mime in setOf("video/mp4", "video/quicktime")) {
                try {
                    IsoBmffCompleteness.validate(size,
                        { position, bytes, offset, length -> source.readAt(position, bytes, offset, length) },
                        { allowed() })
                } catch (_: IOException) {
                    throw BrowserVideoUnavailableException(MediaSaveReason.DIRECT_MEDIA_PARTIAL)
                }
            }
            val retriever = MediaMetadataRetriever()
            val duration = try {
                retriever.setDataSource(source)
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            } finally { retriever.release() }
            allowed()
            VideoValidationPolicy.expectedDurationReason(duration, expectedDurationMs)?.let { throw BrowserVideoUnavailableException(it) }
            val extractor = MediaExtractor()
            val sampleBuffer = ByteBuffer.allocate(MAX_SAMPLE_BYTES)
            try {
                extractor.setDataSource(source)
                val formats = (0 until extractor.trackCount).map { extractor.getTrackFormat(it) }
                val video = formats.indices.filter { formats[it].getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true }
                val audio = formats.indices.filter { formats[it].getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
                if (requireAudio && audio.isEmpty()) throw BrowserVideoUnavailableException(MediaSaveReason.AUDIO_TRACK_MISSING)
                val tracks = video + audio
                val samplesReachEnd = tracks.isNotEmpty() && tracks.all { index ->
                    extractor.selectTrack(index)
                    extractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                    var samples = 0L
                    var lastTimeUs = -1L
                    try {
                        while (true) {
                            allowed()
                            if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_ENCRYPTED != 0)
                                throw BrowserVideoUnavailableException(MediaSaveReason.DRM_DETECTED)
                            sampleBuffer.clear()
                            val count = extractor.readSampleData(sampleBuffer, 0)
                            if (count < 0) break
                            if (count == 0 || count > MAX_SAMPLE_BYTES || ++samples > MAX_TRACK_SAMPLES)
                                throw BrowserVideoUnavailableException(MediaSaveReason.MEDIA_VALIDATION_FAILED)
                            val time = extractor.sampleTime
                            if (time < 0) throw BrowserVideoUnavailableException(MediaSaveReason.DIRECT_MEDIA_PARTIAL)
                            lastTimeUs = maxOf(lastTimeUs, time)
                            if (!extractor.advance()) break
                        }
                    } finally { extractor.unselectTrack(index) }
                    val trackDuration = if (formats[index].containsKey(MediaFormat.KEY_DURATION))
                        formats[index].getLong(MediaFormat.KEY_DURATION) / 1000 else duration
                    val expected = minOf(duration, trackDuration.takeIf { it > 0 } ?: duration)
                    samples > 0 && VideoValidationPolicy.expectedDurationReason(lastTimeUs / 1000, expected) == null
                }
                allowed()
                VideoValidationPolicy.factsReason(size, duration, video.size, samplesReachEnd)?.let {
                    throw BrowserVideoUnavailableException(it)
                }
                return VideoValidationFacts(size, duration, video.size, audio.size)
            } finally { sampleBuffer.array().fill(0); extractor.release() }
        } catch (failure: BrowserVideoUnavailableException) { throw failure
        } catch (_: Exception) { throw BrowserVideoUnavailableException(MediaSaveReason.MEDIA_VALIDATION_FAILED) }
    }
    private const val MAX_SAMPLE_BYTES = 8 * 1024 * 1024
    private const val MAX_TRACK_SAMPLES = 10_000_000L
}

/** Direct HTTP media is fully retrieved, parsed, then streamed to encrypted staging. */
internal fun validatedDirectVideoSource(cache: File, original: VaultImportSource,
    onFailure: (MediaSaveReason) -> Unit, onValidated: (VideoValidationFacts) -> Unit = {}, expectedDurationMs: Long? = null): VaultImportSource {
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
            onValidated(VideoFileValidator.inspect(output, original.mimeType, false, expectedDurationMs, original.isCancelled))
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
