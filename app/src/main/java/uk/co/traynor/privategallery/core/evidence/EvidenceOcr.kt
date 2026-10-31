package uk.co.traynor.privategallery.core.evidence

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.os.SystemClock
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import uk.co.traynor.privategallery.core.vault.VaultPdfPreview

/** Offline extraction only. Original bytes remain caller owned; public model is the only disk output. */
class EvidenceOcr internal constructor(
    context: Context,
    private val createEngine: (onProgress: () -> Unit) -> Engine,
    private val onNativeProgress: () -> Unit
) {
    constructor(context: Context) : this(context, { progress -> NativeEngine(progress) }, {})
    internal constructor(context: Context, onNativeProgress: () -> Unit) : this(context, { progress -> NativeEngine(progress) }, onNativeProgress)

    /** Narrow native ownership boundary: results expose only cached iterator operations. */
    internal interface Engine : AutoCloseable {
        fun init(model: File): Boolean
        fun setImage(image: Bitmap)
        fun recognize(page: Int): String?
        fun cursor(): Cursor?
        fun stop()
    }
    internal interface Cursor : AutoCloseable {
        fun begin()
        fun text(level: Int): String?
        fun confidence(level: Int): Float
        fun bounds(level: Int): IntArray?
        fun next(level: Int): Boolean
    }
    private class NativeEngine(onProgress: () -> Unit) : Engine {
        private val api = TessBaseAPI(TessBaseAPI.ProgressNotifier { onProgress() })
        override fun init(model: File): Boolean {
            val initialized = api.init(model.absolutePath, "eng", TessBaseAPI.OEM_LSTM_ONLY)
            if (initialized) api.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO)
            return initialized
        }
        override fun setImage(image: Bitmap) = api.setImage(image)
        override fun recognize(page: Int): String? = api.getHOCRText(page)
        override fun stop() = api.stop()
        override fun close() = api.recycle()
        override fun cursor(): Cursor? {
            // GetIterator checks page_res_ only; it never calls Recognize. Each accessor below
            // targets that existing ResultIterator, never a TessBaseAPI convenience getter.
            val iterator = api.resultIterator ?: return null
            return object : Cursor {
                override fun begin() = iterator.begin()
                override fun text(level: Int): String? = iterator.getUTF8Text(level)
                override fun confidence(level: Int): Float = iterator.confidence(level)
                override fun bounds(level: Int): IntArray? = iterator.getBoundingBox(level)
                override fun next(level: Int): Boolean = iterator.next(level)
                override fun close() = iterator.delete()
            }
        }
    }
    private val app = context.applicationContext

    suspend fun recognize(bytes: ByteArray, mime: String, rotation: Int, current: () -> Boolean): EvidenceCatalog.Ocr = withContext(Dispatchers.IO) {
        require(rotation in setOf(0, 90, 180, 270)) { "OCR rotation must be 0, 90, 180 or 270 degrees" }
        require(bytes.isNotEmpty() && bytes.size <= MAX_BYTES) { "Local OCR input must be between 1 byte and 32 MiB" }
        val job = currentCoroutineContext()[Job]!!
        fun authority() {
            job.ensureActive()
            if (!current()) throw CancellationException("OCR access revoked")
        }
        authority()
        // Serializes native engines, their large working images, and public-model initialization.
        val extraction = engines.withLock {
            authority()
            val deadline = SystemClock.elapsedRealtime() + DOCUMENT_MS
            val kind = mime.substringBefore(';').trim().lowercase(java.util.Locale.ROOT)
            if (kind == "text/plain" || kind == "text/csv") {
                val (text, partial) = utf8(bytes, ::authority)
                authority()
                return@withLock result("UTF8_DOCUMENT" + if (partial) ";PARTIAL_TEXT_LIMIT" else "", rotation, 0, 0, text, -1f, emptyList(), hints(text, 0), 1, partial)
            }
            require(kind == "application/pdf" || kind.startsWith("image/")) { "Local OCR supports images, PDF, UTF8 text and CSV" }
            val api = createEngine(onNativeProgress)
            try {
                authority()
                val model = prepareModel(::authority)
                check(api.init(model)) { "Bundled offline OCR model could not be initialized" }
                authority()
                val texts = StringBuilder()
                val words = ArrayList<EvidenceCatalog.Word>()
                val times = ArrayList<EvidenceCatalog.ExtractedTime>()
                val reasons = linkedSetOf<String>()
                var processed = 0
                var pages = 1
                var width = 0
                var height = 0
                var confidenceTotal = 0f
                var confidencePages = 0
                while (processed < minOf(pages, MAX_PAGES)) {
                    authority()
                    if (SystemClock.elapsedRealtime() >= deadline) {
                        if (processed == 0) throw LocalDeadline("Local OCR document time limit reached")
                        reasons += "DOCUMENT_TIME_LIMIT"
                        break
                    }
                    val pageIndex = processed
                    var bitmap: Bitmap? = null
                    try {
                        val pageDeadline = minOf(deadline, SystemClock.elapsedRealtime() + PAGE_MS)
                        val original: Bitmap
                        if (kind == "application/pdf") {
                            val rendered = try {
                                VaultPdfPreview.page(app, bytes, pageIndex) {
                                    job.isActive && current() && SystemClock.elapsedRealtime() < pageDeadline
                                }
                            } catch (failure: Exception) {
                                authority()
                                if (SystemClock.elapsedRealtime() >= pageDeadline) throw LocalDeadline("Local OCR PDF page time limit reached")
                                throw failure
                            }
                            original = rendered.first
                            pages = rendered.second
                            if (pages > MAX_PAGES) reasons += "PAGE_LIMIT"
                        } else {
                            original = image(bytes)
                        }
                        bitmap = original
                        authority()
                        if (rotation != 0) {
                            val rotated = Bitmap.createBitmap(original, 0, 0, original.width, original.height, Matrix().apply { postRotate(rotation.toFloat()) }, true)
                            bitmap = rotated
                            if (rotated !== original) wipe(original)
                        }
                        checkNotNull(bitmap).let {
                            require(it.width.toLong() * it.height <= MAX_WORK_PIXELS && maxOf(it.width, it.height) <= LONG_EDGE) { "Local OCR image exceeds working bounds" }
                            if (processed == 0) { width = it.width; height = it.height }
                            api.setImage(it)
                        }
                        val page = nativePage(api, pageIndex, pageDeadline, job, current)
                        authority()
                        val separator = if (processed == 0) "" else "\n\n"
                        val remaining = (MAX_TEXT - texts.length - separator.length).coerceAtLeast(0)
                        if (separator.isNotEmpty() && texts.length + separator.length <= MAX_TEXT) texts.append(separator)
                        val retained = prefix(page.text, remaining)
                        texts.append(retained)
                        if (retained.length < page.text.length || page.textLimit) reasons += "TEXT_LIMIT"
                        if (page.wordLimit) reasons += "WORD_LIMIT"
                        val remainingWords = MAX_WORDS - words.size
                        words.addAll(page.words.take(remainingWords))
                        if (page.words.size > remainingWords) reasons += "WORD_LIMIT"
                        val pageHints = hints(retained, pageIndex)
                        val remainingHints = MAX_TIMES - times.size
                        times.addAll(pageHints.take(remainingHints))
                        if (pageHints.size > remainingHints) reasons += "TIME_HINT_LIMIT"
                        if (page.confidence >= 0) { confidenceTotal += page.confidence; confidencePages++ }
                        processed++
                        if (texts.length >= MAX_TEXT && processed < pages) { reasons += "TEXT_LIMIT"; break }
                    } catch (failure: LocalDeadline) {
                        authority()
                        if (processed == 0) throw failure
                        reasons += if (SystemClock.elapsedRealtime() >= deadline) "DOCUMENT_TIME_LIMIT" else "PAGE_TIME_LIMIT"
                        break
                    } finally {
                        bitmap?.let(::wipe)
                    }
                }
                authority()
                check(processed > 0) { "Local OCR did not process a page" }
                val engine = "TESSERACT4ANDROID_4.9.0/tessdata_fast_4.1.0_eng" + if (reasons.isEmpty()) "" else ";PARTIAL_" + reasons.joinToString("+")
                result(engine, rotation, width, height, texts.toString(), if (confidencePages == 0) -1f else confidenceTotal / confidencePages, words, times, processed, reasons.isNotEmpty())
            } finally {
                // nativePage has waited for recognition and its stop monitor to exit before reaching here.
                api.close()
            }
        }
        authority()
        extraction
    }

    private data class Page(val text: String, val confidence: Float, val words: List<EvidenceCatalog.Word>, val wordLimit: Boolean, val textLimit: Boolean)

    private suspend fun nativePage(api: Engine, index: Int, deadline: Long, job: Job, current: () -> Boolean): Page {
        val gate = Any()
        var recognizing = true
        var stopped = false
        var timedOut = false
        // Independent monitor survives parent cancellation long enough to stop JNI. No recycle while JNI runs.
        val monitor = CoroutineScope(Dispatchers.IO).launch {
            while (true) {
                val authorized = job.isActive && runCatching(current).getOrDefault(false)
                val expired = SystemClock.elapsedRealtime() >= deadline
                synchronized(gate) {
                    if (!recognizing) return@launch
                    if (!authorized || expired) {
                        stopped = true
                        timedOut = expired && authorized
                        api.stop()
                    }
                }
                delay(25)
            }
        }
        val hocr = try {
            // This is the only recognition entry point. All later reads use the cached cursor.
            api.recognize(index)
        } finally {
            synchronized(gate) { recognizing = false }
            withContext(NonCancellable) { monitor.cancelAndJoin() }
        }
        job.ensureActive()
        if (!current()) throw CancellationException("OCR access revoked")
        if (stopped || SystemClock.elapsedRealtime() >= deadline) {
            if (timedOut || SystemClock.elapsedRealtime() >= deadline) throw LocalDeadline("Local OCR page time limit reached")
            throw CancellationException("OCR stopped")
        }
        check(!hocr.isNullOrBlank()) { "Local OCR hOCR recognition failed" }
        val iterator = api.cursor() ?: return Page("", -1f, emptyList(), false, false)
        val words = ArrayList<EvidenceCatalog.Word>()
        val text = StringBuilder()
        var wordLimit = false
        var textLimit = false
        var confidenceSum = 0.0
        var confidenceCount = 0
        fun checkPage() {
            job.ensureActive()
            if (!current()) throw CancellationException("OCR access revoked")
            if (SystemClock.elapsedRealtime() >= deadline) throw LocalDeadline("Local OCR page time limit reached")
        }
        try {
            iterator.begin()
            do {
                checkPage()
                val word = iterator.text(TessBaseAPI.PageIteratorLevel.RIL_WORD).orEmpty()
                if (word.isNotEmpty()) {
                    if (words.size == MAX_WORDS) { wordLimit = true; break }
                    val box = iterator.bounds(TessBaseAPI.PageIteratorLevel.RIL_WORD)
                    check(box != null && box.size == 4 && box.all { it >= 0 } && box[2] >= box[0] && box[3] >= box[1]) { "Local OCR returned invalid word geometry" }
                    if (word.length > MAX_TEXT) wordLimit = true
                    val score = confidence(iterator.confidence(TessBaseAPI.PageIteratorLevel.RIL_WORD))
                    if (score >= 0) { confidenceSum += score; confidenceCount++ }
                    words += EvidenceCatalog.Word(index, prefix(word, MAX_TEXT), score, box[0], box[1], box[2], box[3])
                }
            } while (iterator.next(TessBaseAPI.PageIteratorLevel.RIL_WORD))
            // Match Tesseract's paragraph text order without GetUTF8Text/MeanTextConf,
            // both of which may silently Recognize(nullptr) again on an empty/failed page.
            iterator.begin()
            do {
                checkPage()
                val paragraph = iterator.text(TessBaseAPI.PageIteratorLevel.RIL_PARA).orEmpty()
                val retained = prefix(paragraph, MAX_TEXT - text.length)
                text.append(retained)
                if (retained.length < paragraph.length) { textLimit = true; break }
            } while (iterator.next(TessBaseAPI.PageIteratorLevel.RIL_PARA))
        } finally { iterator.close() }
        val mean = if (confidenceCount == 0) -1f else (confidenceSum / confidenceCount).toFloat()
        return Page(text.toString(), mean, words, wordLimit, textLimit)
    }

    private fun image(bytes: ByteArray): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val width = bounds.outWidth
        val height = bounds.outHeight
        require(width > 0 && height > 0 && width <= 100_000 && height <= 100_000 && width.toLong() * height <= 512_000_000L) { "Local OCR image dimensions are unsupported or pathological" }
        var sample = 1
        while ((width.toLong() + sample - 1) / sample > LONG_EDGE || (height.toLong() + sample - 1) / sample > LONG_EDGE) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888; inScaled = false; inMutable = true }
        val bitmap = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)) { "Local OCR image could not be decoded" }
        if (bitmap.width.toLong() * bitmap.height > MAX_WORK_PIXELS || maxOf(bitmap.width, bitmap.height) > LONG_EDGE) {
            wipe(bitmap)
            throw IllegalArgumentException("Local OCR decoder exceeded working bounds")
        }
        return bitmap
    }

    private fun utf8(bytes: ByteArray, authority: () -> Unit): Pair<String, Boolean> {
        val decoder = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
        val input = ByteBuffer.wrap(bytes).asReadOnlyBuffer()
        val chars = CharBuffer.allocate(8192)
        val text = StringBuilder()
        var truncated = false
        try {
            while (true) {
                authority()
                val status = decoder.decode(input, chars, true)
                chars.flip()
                val count = minOf(chars.remaining(), MAX_TEXT - text.length)
                repeat(count) { text.append(chars.get()) }
                if (chars.hasRemaining()) truncated = true
                chars.clear()
                if (status.isError) status.throwException()
                if (status.isUnderflow) break
            }
            // A retained prefix cannot end halfway through a supplementary Unicode character.
            if (truncated && text.isNotEmpty() && text.last().isHighSurrogate()) text.setLength(text.length - 1)
            return text.toString() to truncated
        } finally { chars.array().fill('\u0000') }
    }

    private fun hints(text: String, page: Int): List<EvidenceCatalog.ExtractedTime> = EvidenceTextHints.scan(text).filter { it.kind() != "PARTICIPANT_CANDIDATE" }.map {
        EvidenceCatalog.ExtractedTime(page, it.text(), it.epochMillis(), it.provenance(), -1f)
    }

    private fun prepareModel(authority: () -> Unit): File {
        val base = File(app.noBackupFilesDir, "evidence179-ocr")
        val directory = File(base, "tessdata")
        check(directory.isDirectory || directory.mkdirs()) { "Offline OCR private model directory unavailable" }
        val model = File(directory, "eng.traineddata")
        if (model.isFile && model.length() == MODEL_LENGTH && digest(model) == MODEL_SHA) { authority(); return base }
        val temporary = File.createTempFile("eng-", ".model-partial", directory)
        try {
            val hash = MessageDigest.getInstance("SHA-256")
            var size = 0L
            val buffer = ByteArray(64 * 1024)
            try {
                app.assets.open("tessdata/eng.traineddata").use { input ->
                    FileOutputStream(temporary).use { output ->
                        while (true) {
                            authority()
                            val count = input.read(buffer)
                            if (count < 0) break
                            size += count
                            check(size <= MODEL_LENGTH) { "Bundled OCR model length mismatch" }
                            hash.update(buffer, 0, count)
                            output.write(buffer, 0, count)
                        }
                        output.fd.sync()
                    }
                }
            } finally { buffer.fill(0) }
            check(size == MODEL_LENGTH && hex(hash.digest()) == MODEL_SHA) { "Bundled OCR model checksum mismatch" }
            authority()
            check(temporary.renameTo(model)) { "Offline OCR model atomic installation failed" }
            return base
        } finally { temporary.delete() }
    }

    private fun digest(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        try { file.inputStream().use { input -> while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) } } }
        finally { buffer.fill(0) }
        return hex(digest.digest())
    }
    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it.toInt() and 255) }
    private fun prefix(text: String, limit: Int): String {
        var end = minOf(text.length, limit)
        if (end < text.length && end > 0 && text[end - 1].isHighSurrogate()) end--
        return text.substring(0, end)
    }
    private fun confidence(value: Float) = if (value.isFinite() && value in 0f..100f) value else -1f
    private fun wipe(bitmap: Bitmap) {
        if (!bitmap.isRecycled) { try { if (bitmap.isMutable) bitmap.eraseColor(Color.TRANSPARENT) } finally { bitmap.recycle() } }
    }
    private fun result(engine: String, rotation: Int, width: Int, height: Int, text: String, confidence: Float, words: List<EvidenceCatalog.Word>, times: List<EvidenceCatalog.ExtractedTime>, pages: Int, partial: Boolean) =
        EvidenceCatalog.Ocr(UUID.randomUUID().toString(), System.currentTimeMillis(), engine, rotation, width, height, text, confidence, words, times, pages, partial)
    private class LocalDeadline(message: String) : IllegalStateException(message)

    private companion object {
        val engines = Mutex()
        const val MAX_BYTES = 32 * 1024 * 1024
        const val MAX_TEXT = 100_000
        const val MAX_WORDS = 100_000
        const val MAX_TIMES = 4096
        const val MAX_PAGES = 32
        const val LONG_EDGE = 2048
        const val MAX_WORK_PIXELS = 16_000_000L
        const val PAGE_MS = 30_000L
        const val DOCUMENT_MS = 120_000L
        const val MODEL_LENGTH = 4_113_088L
        const val MODEL_SHA = "7d4322bd2a7749724879683fc3912cb542f19906c83bcc1a52132556427170b2"
    }
}
