package uk.co.traynor.privategallery.core.browser.v2

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class ManifestProtectionBytePolicyTest {
    @Test fun quotedUriNoneBaitDoesNotHideActualMethod() = assertTrue(protected("#EXT-X-KEY:METHOD=AES-128,URI=\"https://invalid.example/METHOD=NONE,foo\""))
    @Test fun conflictingMethodDoesNotHideActualMethod() = assertTrue(protected("#EXT-X-KEY:METHOD=AES-128,METHOD=NONE"))
    @Test fun unrelatedSuffixDoesNotReplaceRequiredMethod() = assertTrue(protected("#EXT-X-KEY:X-METHOD=NONE"))
    @Test fun extraAttributesAfterNoneDoNotGrantEligibility() = assertTrue(protected("#EXT-X-KEY:METHOD=NONE,URI=\"key\""))
    @Test fun ordinaryAndSoleNoneStayEligible() {
        assertFalse(protected("#EXTM3U\n#EXTINF:4,\npart001.ts\n#EXT-X-ENDLIST"))
        assertFalse(protected("#EXTM3U\n#EXT-X-KEY:METHOD=NONE\n"))
    }
    @Test fun existingCaseAndWhitespaceConservatismIsPreserved() {
        assertTrue(protected(" \t#ext-x-key:method=aes-128,uri=\"key\" \t"))
        assertFalse(protected(" \t#ext-x-key:method = none \t"))
    }
    @Test fun sessionKeyNeverGrantsNoneException() = assertTrue(protected("#EXT-X-SESSION-KEY:METHOD=NONE"))
    @Test fun namespacedAndLegacyDashProtectionAreRejected() {
        assertTrue(protected("<MPD><cenc:ContentProtection/></MPD>"))
        assertTrue(protected("<mpd><CONTENTPROTECTION schemeIdUri=\"synthetic\"/></mpd>"))
        assertTrue(protected("<ContentProtectionUnexpectedSuffix/>"))
    }
    @Test fun nonAsciiNamespaceAndAmbiguousNameCannotHideProtection() {
        assertTrue(protected("<MPD><世界:ContentProtection xmlns:世界=\"synthetic\"/></MPD>"))
        assertTrue(protected("<cenc:ContentProtection:Unexpected/>"))
    }
    @Test fun malformedUtf8CannotEstablishAbsenceOfProtection() {
        for (data in listOf(intArrayOf(0xc0,0xaf),intArrayOf(0x80),intArrayOf(0xe0,0x80,0x80),intArrayOf(0xed,0xa0,0x80),
            intArrayOf(0xf0,0x80,0x80,0xaf),intArrayOf(0xf4,0x90,0x80,0x80),intArrayOf(0xff),intArrayOf(0xe2,0x82),intArrayOf(0xe2,0x28,0xa1))) {
            val bytes=ByteArray(data.size) { data[it].toByte() }
            assertThrows(IOException::class.java) { ManifestProtectionPolicy.isProtected(bytes,bytes.size) }
        }
    }
    @Test fun utf16AndControlTextDenyBeforeEligibility() {
        for (bytes in listOf("<ContentProtection/>".toByteArray(Charsets.UTF_16LE),"<ContentProtection/>".toByteArray(Charsets.UTF_16BE),byteArrayOf(0),byteArrayOf(0x7f)))
            assertThrows(IOException::class.java) { ManifestProtectionPolicy.isProtected(bytes,bytes.size) }
    }
    @Test fun validUtf8NonAsciiUrisRemainEligible() = assertFalse(protected("#EXTM3U\n#EXTINF:4,\né世界😀.ts\n"))
    @Test fun unicodeOuterWhitespaceDoesNotHideKey() {
        assertTrue(protected("\u2003\u00a0#EXT-X-KEY:METHOD=AES-128\u2003"))
        assertFalse(protected("\u2003#EXT-X-KEY:METHOD=NONE\u2003"))
    }
    @Test fun parserConsumesOnlyDeclaredOriginalSlice() {
        val prefix="#EXTM3U\n".toByteArray();val bytes=prefix+"#EXT-X-KEY:METHOD=AES-128".toByteArray()
        assertFalse(ManifestProtectionPolicy.isProtected(bytes,prefix.size));assertTrue(ManifestProtectionPolicy.isProtected(bytes,bytes.size))
        assertThrows(IllegalArgumentException::class.java) { ManifestProtectionPolicy.isProtected(bytes,-1) }
        assertThrows(IllegalArgumentException::class.java) { ManifestProtectionPolicy.isProtected(bytes,bytes.size+1) }
    }
    @Test fun utf8BomDoesNotHideFirstKeyAndOrdinaryXmlRemainsEligible() {
        assertTrue(protected("\ufeff#EXT-X-KEY:METHOD=AES-128"))
        assertFalse(protected("\ufeff<MPD><Period/></MPD>"))
    }
    @Test fun declaredSliceTruncationDoesNotReadUtf8ContinuationOutsideSlice() {
        val bytes="é".toByteArray();assertThrows(IOException::class.java) { ManifestProtectionPolicy.isProtected(bytes,1) }
    }
    private fun protected(text: String): Boolean {
        val bytes=text.toByteArray();return ManifestProtectionPolicy.isProtected(bytes,bytes.size)
    }
}
