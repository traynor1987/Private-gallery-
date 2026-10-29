package uk.co.traynor.privategallery.core.vault

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

class VaultDocumentPolicyTest {
    private fun validate(mime:String,data:ByteArray)=VaultDocumentPolicy.validatingStream(mime,data.inputStream()).use {it.readBytes()}
    @Test fun acceptsOnlyInertDocumentTypes() {
        assertTrue(VaultDocumentPolicy.isDocument("application/pdf"))
        assertTrue(VaultDocumentPolicy.isDocument("text/csv"))
        for(type in listOf("text/html","application/javascript","image/svg+xml","application/zip","application/octet-stream"))assertFalse(VaultDocumentPolicy.isDocument(type))
    }
    @Test fun completePdfMustHaveMatchingHeaderAndTrailer() {
        val pdf="%PDF-1.7\n1 0 obj\n<<>>\nendobj\n%%EOF\n".toByteArray()
        assertArrayEquals(pdf,validate("application/pdf",pdf))
        assertTrue(runCatching {validate("application/pdf",pdf.copyOf(pdf.size-8))}.isFailure)
        assertTrue(runCatching {validate("application/pdf","<html>error</html>%%EOF".toByteArray())}.isFailure)
    }
    @Test fun utf8DocumentsAreLiteralAndRejectBinaryAndMalformedUtf8() {
        val csv="name,value\nformula,=1+2\n脚本,<script>literal</script>".toByteArray()
        assertArrayEquals(csv,validate("text/csv",csv))
        assertTrue(runCatching {validate("text/plain",byteArrayOf(0,1,2))}.isFailure)
        assertTrue(runCatching {validate("text/plain",byteArrayOf(0xc3.toByte(),0x28))}.isFailure)
        assertTrue(runCatching {validate("text/plain",byteArrayOf())}.isFailure)
    }
    @Test fun cancellationAndBoundedSizesFailBeforeEof() {
        assertTrue(runCatching {VaultDocumentPolicy.validatingStream("text/plain",ByteArrayInputStream(byteArrayOf(65))){true}.use {it.read()}}.isFailure)
        assertTrue(runCatching {validate("text/plain",ByteArray(VaultDocumentPolicy.MAX_TEXT_BYTES.toInt()+1){65})}.isFailure)
    }
    @Test fun maliciousFilenameCannotChangeVerifiedDocumentType() {
        assertEquals("application/pdf",VaultMimePolicy.effectiveType("application/pdf","photo.jpg","%PDF-1.7".toByteArray()))
    }
}
