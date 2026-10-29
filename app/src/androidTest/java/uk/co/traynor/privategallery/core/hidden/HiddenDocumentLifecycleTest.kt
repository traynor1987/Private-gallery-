package uk.co.traynor.privategallery.core.hidden

import android.content.ContextWrapper
import android.graphics.pdf.PdfDocument
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test
import uk.co.traynor.privategallery.core.vault.VaultPdfPreview

class HiddenDocumentLifecycleTest {
    @Test fun pdfAndCsvRemainEncryptedPreviewReopenTrashRestoreAndRevoke() {
        val base=InstrumentationRegistry.getInstrumentation().targetContext
        val root=File(base.noBackupFilesDir,"documents-${UUID.randomUUID()}")
        val context=object:ContextWrapper(base){override fun getNoBackupFilesDir()=root}
        val session=HiddenSession(android.os.SystemClock::elapsedRealtime)
        assertTrue(session.complete(session.beginAuthentication(),ByteArray(32){23}))
        val pdf=PdfDocument().let {document ->try {
            val page=document.startPage(PdfDocument.PageInfo.Builder(300,400,1).create())
            page.canvas.drawText("private-pdf-marker",20f,30f,android.graphics.Paint().apply {textSize=14f})
            document.finishPage(page)
            ByteArrayOutputStream().use {document.writeTo(it);it.toByteArray()}
        }finally {document.close()}}
        try {
            val repo=HiddenRepository(context,session)
            val item=repo.importMedia("private-document.pdf","application/pdf",pdf.inputStream())
            val csv="private-csv-marker,value\nformula,=1+2".toByteArray()
            val text=repo.importMedia("private-sheet.csv","text/csv",csv.inputStream())
            val reopened=HiddenRepository(context,session)
            assertArrayEquals(pdf,reopened.readForDisplay(item.id));assertArrayEquals(csv,reopened.readForDisplay(text.id))
            reopened.preview(item.id).recycle();reopened.preview(text.id).recycle()
            VaultPdfPreview.page(context,pdf,0){true}.let {assertEquals(1,it.second);assertTrue(it.first.width<=1400);it.first.recycle()}
            assertTrue(runCatching {VaultPdfPreview.page(context,pdf,0){false}}.isFailure)
            reopened.trash(listOf(item.id));assertTrue(reopened.items().none {it.id==item.id});reopened.preview(item.id,true).recycle()
            reopened.restore(item.id);reopened.verify(item.id)
            root.walkTopDown().filter {it.isFile}.forEach {file ->
                val disk=file.readBytes().toString(Charsets.ISO_8859_1)
                assertFalse(disk.contains("private-document"));assertFalse(disk.contains("private-csv-marker"));assertFalse(disk.startsWith("%PDF-"))
            }
            session.lock();assertTrue(runCatching {reopened.read(item.id)}.isFailure)
        }finally {pdf.fill(0);session.lock();root.deleteRecursively()}
    }
    @Test fun truncatedDocumentNeverPublishesAndFreshRetrySucceeds() {
        val base=InstrumentationRegistry.getInstrumentation().targetContext
        val root=File(base.noBackupFilesDir,"documents-${UUID.randomUUID()}")
        val context=object:ContextWrapper(base){override fun getNoBackupFilesDir()=root}
        val session=HiddenSession(android.os.SystemClock::elapsedRealtime)
        assertTrue(session.complete(session.beginAuthentication(),ByteArray(32){24}))
        try {
            val repo=HiddenRepository(context,session)
            assertTrue(runCatching {repo.importMedia("broken.pdf","application/pdf","%PDF-1.7\npartial".byteInputStream())}.isFailure)
            assertTrue(repo.items().isEmpty());repo.reconcile()
            val item=repo.importMedia("retry.txt","text/plain","complete retry".byteInputStream())
            assertEquals("complete retry",String(repo.read(item.id)))
            assertTrue(root.walkTopDown().none {it.name.endsWith(".part")})
        }finally {session.lock();root.deleteRecursively()}
    }
}
