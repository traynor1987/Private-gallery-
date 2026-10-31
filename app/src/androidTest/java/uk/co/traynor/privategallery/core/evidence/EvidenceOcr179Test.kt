package uk.co.traynor.privategallery.core.evidence

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Matrix
import android.graphics.pdf.PdfDocument
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.io.File
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real native OCR; requires packaged model and API37 device, never a provider or online fallback. */
@RunWith(AndroidJUnit4::class)
class EvidenceOcr179Test {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val message = "Alice Smith\n2026-10-31 14:35\nPlease save this message."
    private fun image(rotation: Int = 0, width: Int = 1600, height: Int = 900): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 64f }
        message.lines().forEachIndexed { index, line -> canvas.drawText(line, 60f, 140f + index * 120f, paint) }
        val rotated = if (rotation == 0) bitmap else Bitmap.createBitmap(bitmap, 0, 0, width, height, Matrix().apply { postRotate(rotation.toFloat()) }, true)
        return try { ByteArrayOutputStream().use { rotated.compress(Bitmap.CompressFormat.PNG, 100, it); it.toByteArray() } }
        finally { if (rotated !== bitmap) { rotated.eraseColor(0); rotated.recycle() }; bitmap.eraseColor(0); bitmap.recycle() }
    }
    @Test fun renderedMessagePreservesNamesDatesWordsConfidenceAndRawCorrection() = runBlocking {
        val input = image()
        val copy = input.copyOf()
        val value = EvidenceOcr(context).recognize(input, "image/png", 0) { true }
        assertTrue(value.text(), value.text().contains("Alice"))
        assertTrue(value.text(), value.text().contains("14:35"))
        assertTrue(value.words().any { it.text().contains("Alice") })
        assertTrue(value.confidence() in 0f..100f)
        assertTrue(value.words().all { it.confidence() == -1f || it.confidence() in 0f..100f })
        assertTrue(value.times().any { it.text().contains("14:35") && it.epochMillis() == 0L && it.provenance().contains("UNVERIFIED") })
        assertEquals(1, value.pageCount())
        val evidenceId = java.util.UUID.randomUUID().toString()
        val record = EvidenceCatalog.Evidence(evidenceId, java.util.UUID.randomUUID().toString(), "Message", "image/png", "0".repeat(64), input.size.toLong(), 1L,
            EvidenceCatalog.Metadata("Message", 0L, 0L, 0L, 0L, 1600, 900, emptyMap(), false), "", "", emptyList(), emptyList(), emptyList(), emptyList())
        val catalog = EvidenceCatalog.empty(EvidenceCatalog.Owner.PRIMARY).upsertEvidence(record, 1L, "IMPORT_EVIDENCE").saveOcr(evidenceId, value, 2L)
        val corrected = catalog.reviseOcr(evidenceId, value.id(), "Owner correction", 3L).evidence().single()
        assertEquals("Owner correction", corrected.transcript())
        assertEquals(value.text(), corrected.ocr().single().text())
        assertEquals(value.words(), corrected.ocr().single().words())
        assertTrue(value.text().contains("Alice"))
        assertArrayEquals(copy, input)
    }
    @Test fun ordinary48MegapixelImageIsSampledBeforeWorkingAllocation() = runBlocking {
        val value = EvidenceOcr(context).recognize(image(width = 8000, height = 6000), "image/png", 0) { true }
        assertEquals(2000, value.width())
        assertEquals(1500, value.height())
        assertTrue(value.words().all { it.right() <= 2048 && it.bottom() <= 2048 })
    }
    @Test fun explicitRotationIsAppliedAndRecorded() = runBlocking {
        val value = EvidenceOcr(context).recognize(image(270), "image/png", 90) { true }
        assertEquals(90, value.rotation())
        assertTrue(value.text(), value.text().contains("Alice"))
    }
    @Test fun utf8DocumentIsStrictUnknownConfidenceAndBounded() = runBlocking {
        val value = EvidenceOcr(context).recognize(message.toByteArray(), "text/csv", 0) { true }
        assertEquals(message, value.text())
        assertEquals("UTF8_DOCUMENT", value.engine())
        assertEquals(-1f, value.confidence(), 0f)
        assertTrue(value.words().isEmpty())
        assertTrue(value.times().all { it.confidence() == -1f })
        try { EvidenceOcr(context).recognize(byteArrayOf(0xc3.toByte(), 0x28), "text/plain", 0) { true }; fail("malformed UTF8 accepted") } catch (_: java.nio.charset.CharacterCodingException) { }
        val malformedTail = "x".repeat(100001).toByteArray() + byteArrayOf(0xc3.toByte(), 0x28)
        try { EvidenceOcr(context).recognize(malformedTail, "text/plain", 0) { true }; fail("malformed tail accepted after truncation") } catch (_: java.nio.charset.CharacterCodingException) { }
        val limited = EvidenceOcr(context).recognize("x".repeat(100001).toByteArray(), "text/plain", 0) { true }
        assertTrue(limited.partial())
        assertEquals(100000, limited.text().length)
        assertTrue(limited.engine().contains("TEXT_LIMIT"))
    }
    @Test fun pdfPagesCarryIndependentWordAndTimeIndicesAndLimitExplanation() = runBlocking {
        val pdf = PdfDocument()
        val bytes = try {
            repeat(33) { index ->
                val page = pdf.startPage(PdfDocument.PageInfo.Builder(800, 500, index + 1).create())
                page.canvas.drawColor(Color.WHITE)
                page.canvas.drawText("Alice 14:35", 40f, 100f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 48f })
                pdf.finishPage(page)
            }
            ByteArrayOutputStream().use { pdf.writeTo(it); it.toByteArray() }
        } finally { pdf.close() }
        val value = EvidenceOcr(context).recognize(bytes, "application/pdf", 0) { true }
        assertTrue(value.pageCount() in 1..32)
        assertTrue(value.partial())
        assertTrue(value.engine().contains("LIMIT"))
        assertTrue(value.words().all { it.pageIndex() in 0 until value.pageCount() })
        assertTrue(value.words().any { it.pageIndex() > 0 })
    }
    private class ControlledEngine(
        private val hocr: String? = "<div class='ocr_page'></div>",
        private val block: Boolean = false,
        private val hasWord: Boolean = false
    ) : EvidenceOcr.Engine {
        val entered = CountDownLatch(1)
        val stopped = CountDownLatch(1)
        val release = CountDownLatch(if (block) 1 else 0)
        val events = CopyOnWriteArrayList<String>()
        lateinit var bitmap: Bitmap
        var cursorCalls = 0
        var closed = false
        var iteratorClosedWithLiveBitmap = false
        override fun init(model: File) = true
        override fun setImage(image: Bitmap) { bitmap = image }
        override fun recognize(page: Int): String? {
            events += "native-entry"
            entered.countDown()
            check(release.await(10, TimeUnit.SECONDS)) { "controlled native call not released" }
            events += "native-exit"
            return hocr
        }
        override fun stop() { events += "stop"; stopped.countDown() }
        override fun cursor(): EvidenceOcr.Cursor? {
            cursorCalls++
            if (!hasWord) return null
            return object : EvidenceOcr.Cursor {
                override fun begin() {}
                override fun text(level: Int) = if (level == 3) "Alice" else "Alice\n"
                override fun confidence(level: Int) = 0.5f
                override fun bounds(level: Int) = intArrayOf(1, 2, 20, 30)
                override fun next(level: Int) = false
                override fun close() {
                    iteratorClosedWithLiveBitmap = !bitmap.isRecycled
                    events += "iterator-close"
                }
            }
        }
        override fun close() {
            if (::bitmap.isInitialized && bitmap.isRecycled) events += "bitmap-clean"
            closed = true
            events += "engine-close"
        }
    }
    private suspend fun await(latch: CountDownLatch, message: String) {
        assertTrue(message, withContext(Dispatchers.IO) { latch.await(5, TimeUnit.SECONDS) })
    }
    @Test fun failedHocrNeverReadsCachedResultsAndCleansResources() = runBlocking {
        val input = image()
        for (failed in listOf(null, "")) {
            val engine = ControlledEngine(hocr = failed)
            val ocr = EvidenceOcr(context, { _ -> engine }, {})
            try { ocr.recognize(input, "image/png", 0) { true }; fail("failed hOCR accepted") }
            catch (failure: IllegalStateException) { assertTrue(failure.message.orEmpty(), failure.message.orEmpty().contains("hOCR")) }
            assertEquals(0, engine.cursorCalls)
            assertTrue(engine.closed)
            assertTrue(engine.bitmap.isRecycled)
            assertTrue(engine.events.indexOf("native-exit") < engine.events.indexOf("bitmap-clean"))
        }
    }
    @Test fun emptyCachedPageDoesNotStartAnotherRecognition() = runBlocking {
        val input = image()
        val engine = ControlledEngine()
        val value = EvidenceOcr(context, { _ -> engine }, {}).recognize(input, "image/png", 0) { true }
        assertEquals("", value.text())
        assertEquals(-1f, value.confidence(), 0f)
        assertTrue(value.words().isEmpty())
        assertEquals(1, engine.cursorCalls)
        assertEquals(1, engine.events.count { it == "native-entry" })
        assertTrue(engine.closed)
    }
    @Test fun cachedIteratorRetainsLowConfidenceAndClosesBeforeBitmapAndEngine() = runBlocking {
        val input = image()
        val engine = ControlledEngine(hasWord = true)
        val value = EvidenceOcr(context, { _ -> engine }, {}).recognize(input, "image/png", 0) { true }
        assertEquals("Alice\n", value.text())
        assertEquals(0.5f, value.confidence(), 0f)
        assertEquals("Alice", value.words().single().text())
        assertEquals(0.5f, value.words().single().confidence(), 0f)
        assertEquals(1, engine.events.count { it == "native-entry" })
        assertTrue(engine.iteratorClosedWithLiveBitmap)
        assertTrue(engine.events.indexOf("native-exit") < engine.events.indexOf("iterator-close"))
        assertTrue(engine.events.indexOf("iterator-close") < engine.events.indexOf("bitmap-clean"))
        assertTrue(engine.events.indexOf("bitmap-clean") < engine.events.indexOf("engine-close"))
    }
    private suspend fun controlledStop(cancel: Boolean) {
        val input = image() // Prepare before launching the operation under test.
        val engine = ControlledEngine(block = true)
        val active = AtomicBoolean(true)
        val ocr = EvidenceOcr(context, { _ -> engine }, {})
        kotlinx.coroutines.coroutineScope {
            val pending = async(Dispatchers.Default) { ocr.recognize(input, "image/png", 0) { active.get() } }
            try {
                await(engine.entered, "native recognition did not enter")
                if (cancel) pending.cancel() else active.set(false)
                await(engine.stopped, "active native recognition was not stopped")
                assertFalse("bitmap freed before native exit", engine.bitmap.isRecycled)
                assertFalse("engine freed before native exit", engine.closed)
                assertFalse("operation completed before controlled native exit", pending.isCompleted)
                engine.release.countDown()
                withTimeout(5_000) { pending.join() }
                try { pending.await(); fail("stopped extraction returned a result") } catch (_: java.util.concurrent.CancellationException) {}
                assertEquals(0, engine.cursorCalls)
                assertTrue(engine.bitmap.isRecycled)
                assertTrue(engine.closed)
                assertTrue(engine.events.indexOf("stop") < engine.events.indexOf("native-exit"))
                assertTrue(engine.events.indexOf("native-exit") < engine.events.indexOf("bitmap-clean"))
                assertTrue(engine.events.indexOf("bitmap-clean") < engine.events.indexOf("engine-close"))
            } finally {
                engine.release.countDown()
                withContext(NonCancellable) { withTimeout(5_000) { pending.cancelAndJoin() } }
            }
        }
        assertTrue(EvidenceOcr(context).recognize(input, "image/png", 0) { true }.text().contains("Alice"))
    }
    @Test fun activeNativeCancellationWaitsForExitBeforeCleanup() = runBlocking { controlledStop(true) }
    @Test fun activeNativeRevocationWaitsForExitBeforeCleanup() = runBlocking { controlledStop(false) }

    private fun denseImage(): ByteArray {
        val bitmap = Bitmap.createBitmap(2048, 2048, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(Color.WHITE)
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 26f }
            repeat(58) { row -> canvas.drawText("Alice Smith 2026-10-31 14:35 message record $row save this text for review", 12f, 35f + row * 34f, paint) }
            return ByteArrayOutputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it); it.toByteArray() }
        } finally { bitmap.eraseColor(0); bitmap.recycle() }
    }
    private suspend fun realJniStop(cancel: Boolean) {
        val ordinary = image()
        val input = denseImage() // No bitmap generation/compression occurs inside the async operation.
        EvidenceOcr(context).recognize(ordinary, "image/png", 0) { true }
        fun files() = listOf(context.filesDir, context.cacheDir, context.noBackupFilesDir).flatMap { root -> root.walkTopDown().filter { it.isFile }.map { it.absolutePath }.toList() }.toSet()
        val before = files()
        val entered = CountDownLatch(1)
        val releaseProgress = CountDownLatch(1)
        val firstProgress = AtomicBoolean(true)
        val progressTimedOut = AtomicBoolean(false)
        val active = AtomicBoolean(true)
        val ocr = EvidenceOcr(context) {
            if (firstProgress.getAndSet(false)) {
                entered.countDown() // Actual JNI hOCR progress callback, not the launch time.
                if (!releaseProgress.await(5, TimeUnit.SECONDS)) progressTimedOut.set(true)
            }
        }
        kotlinx.coroutines.coroutineScope {
            val pending = async(Dispatchers.Default) { ocr.recognize(input, "image/png", 0) { active.get() } }
            try {
                await(entered, "real JNI hOCR progress not observed")
                if (cancel) pending.cancel() else active.set(false)
                releaseProgress.countDown()
                withTimeout(5_000) { pending.join() }
                try { pending.await(); fail("active JNI stop returned an extraction") } catch (_: java.util.concurrent.CancellationException) {}
                assertFalse("native progress handshake timed out", progressTimedOut.get())
                assertEquals(before, files())
            } finally {
                releaseProgress.countDown()
                withContext(NonCancellable) { withTimeout(5_000) { pending.cancelAndJoin() } }
            }
        }
        assertTrue(ocr.recognize(ordinary, "image/png", 0) { true }.text().contains("Alice"))
    }
    @Test fun realJniProgressCancellationCompletesWithinBoundAndCanRecognizeAgain() = runBlocking { realJniStop(true) }
    @Test fun realJniProgressRevocationCompletesWithinBoundAndCreatesNoEvidenceFiles() = runBlocking { realJniStop(false) }
    @Test fun blankRealJniPageHasEmptyTextAndUnknownConfidence() = runBlocking {
        val bitmap = Bitmap.createBitmap(600, 400, Bitmap.Config.ARGB_8888)
        val input = try { bitmap.eraseColor(Color.WHITE); ByteArrayOutputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it); it.toByteArray() } }
        finally { bitmap.eraseColor(0); bitmap.recycle() }
        val value = EvidenceOcr(context).recognize(input, "image/png", 0) { true }
        assertEquals("", value.text())
        assertTrue(value.words().isEmpty())
        assertEquals(-1f, value.confidence(), 0f)
    }
    @Test fun rejectsOversizedInputAndInvalidRotation() = runBlocking {
        val input = image()
        val ocr = EvidenceOcr(context)
        try { ocr.recognize(input, "image/png", 45) { true }; fail("rotation accepted") } catch (_: IllegalArgumentException) { }
        try { ocr.recognize(ByteArray(32 * 1024 * 1024 + 1), "image/png", 0) { true }; fail("oversize accepted") } catch (_: IllegalArgumentException) { }
    }
}
