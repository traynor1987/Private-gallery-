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
    @Test fun pdfAboveLegacyMediaReadLimitDisplaysAuthenticatesAndRevokes() {
        val base=InstrumentationRegistry.getInstrumentation().targetContext
        val root=File(base.noBackupFilesDir,"large-documents-${UUID.randomUUID()}")
        val context=object:ContextWrapper(base){override fun getNoBackupFilesDir()=root}
        val session=HiddenSession(android.os.SystemClock::elapsedRealtime)
        assertTrue(session.complete(session.beginAuthentication(),ByteArray(32){25}))
        // Valid one-page PDF with an unreferenced large stream; xref offsets remain exact.
        // This exercises document bytes above the old 16 MiB media limit without huge page images.
        val pdf=ByteArrayOutputStream().use {output ->
            fun write(value:String) {output.write(value.toByteArray(Charsets.US_ASCII))}
            val offsets=mutableListOf(0)
            fun objectValue(id:Int,value:String) {offsets.add(output.size());write("$id 0 obj\n$value\nendobj\n")}
            write("%PDF-1.4\n")
            objectValue(1,"<< /Type /Catalog /Pages 2 0 R >>")
            objectValue(2,"<< /Type /Pages /Count 1 /Kids [3 0 R] >>")
            objectValue(3,"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 300 400] /Resources << >> /Contents 4 0 R >>")
            objectValue(4,"<< /Length 0 >>\nstream\nendstream")
            offsets.add(output.size())
            val paddingSize=17*1024*1024
            write("5 0 obj\n<< /Length $paddingSize >>\nstream\n")
            val padding=ByteArray(64*1024){32}
            repeat(paddingSize/padding.size) {output.write(padding)}
            write("\nendstream\nendobj\n")
            val xref=output.size()
            write("xref\n0 6\n0000000000 65535 f \n")
            offsets.drop(1).forEach {write(it.toString().padStart(10,'0')+" 00000 n \n")}
            write("trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n$xref\n%%EOF\n")
            output.toByteArray()
        }
        try {
            assertTrue(pdf.size>HiddenRecordStore.MAX_MEDIA_BYTES)
            val repo=HiddenRepository(context,session)
            val item=repo.importMedia("large.pdf","application/pdf",pdf.inputStream())
            val reopened=HiddenRepository(context,session)
            val display=reopened.readForDisplay(item.id)
            try {
                assertArrayEquals(pdf,display)
                VaultPdfPreview.page(context,display,0){session.isUnlocked}.let {assertEquals(1,it.second);it.first.recycle()}
            } finally {display.fill(0)}
            reopened.preview(item.id).recycle()
            session.lock()
            assertTrue(runCatching {reopened.readForDisplay(item.id)}.isFailure)
            assertTrue(session.complete(session.beginAuthentication(),ByteArray(32){25}))
            val encrypted=File(root,"hidden-media-v1/media-${item.id}.enc")
            java.io.RandomAccessFile(encrypted,"rw").use {file ->
                file.seek(file.length()-1);val last=file.read();file.seek(file.length()-1);file.write(last xor 1)
            }
            assertTrue(runCatching {HiddenRepository(context,session).readForDisplay(item.id)}.isFailure)
        }finally {pdf.fill(0);session.lock();root.deleteRecursively()}
    }

}
