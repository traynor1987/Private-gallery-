package uk.co.traynor.privategallery.core.editor

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Public synthetic text only; no provider, credential or live TLS use. */
class Phase3FluxBodyEncoderTest {
    private fun request(prompt:String,aspect:GenerationAspect=GenerationAspect.SQUARE,seed:Int?=null)=
        GenerationRequest(GenerationModel.FLUX_PRO,prompt,aspect,seed=seed)
    private fun actual(request:GenerationRequest):JSONObject {
        val bytes=ByteArray(64*1024)
        try {
            val count=FluxProBodyEncoder.encode(request,bytes)
            assertTrue(count in 1..25000)
            return JSONObject(String(bytes,0,count,Charsets.UTF_8))
        }finally{bytes.fill(0)}
    }
    private fun parity(prompt:String,seed:Int?=null) {
        val r=request(prompt,seed=seed)
        // Compare the ACTUAL old JSONObject -> UTF8 -> JSON pipeline, including
        // replacement of malformed UTF16 at UTF8 encoding, not just input DTOs.
        val old=JSONObject(JSONObject().put("input",r.model.input(r)).toString().toByteArray(Charsets.UTF_8).toString(Charsets.UTF_8))
        assertTrue(old.similar(actual(r)))
    }
    @Test fun literalOnlyDocumentedFieldsAndAllAspects() {
        for((aspect,ratio) in listOf(GenerationAspect.SQUARE to "1:1",GenerationAspect.PORTRAIT to "2:3",GenerationAspect.LANDSCAPE to "3:2")) {
            val root=actual(request("  public blue bird  ",aspect));assertEquals(1,root.length())
            val input=root.getJSONObject("input");assertEquals(3,input.length())
            assertEquals("public blue bird",input.getString("prompt"));assertEquals(ratio,input.getString("aspect_ratio"))
            assertEquals("png",input.getString("output_format"));assertFalse(input.has("negative_prompt"));assertFalse(input.has("image_input"))
        }
    }
    @Test fun signedSeedLimitsAndZeroRemainNumbers() {
        for(seed in listOf(Int.MIN_VALUE,-1,0,1,Int.MAX_VALUE)) {
            val input=actual(request("public",seed=seed)).getJSONObject("input")
            assertEquals(seed,input.getInt("seed"));assertTrue(input.get("seed") is Number);parity("public",seed)
        }
    }
    @Test fun escapeControlsQuoteBackslashSlashAndUnicode() {
        parity("public \" \\ </ \b \t \n \r \u0000 \u001f \u0080 \u009f \u2000 \u2028 \u20ff café 日本語 \ud83d\ude03 end")
    }
    @Test fun whitespaceTrimMatchesExistingKotlinIncludingUnicode() {
        parity("\u2000\t public \u2000 middle \u2000\n")
    }
    @Test fun malformedSurrogatesPreserveActualLegacyUtf8Replacement() {
        for(text in listOf("public \ud800 x","public \udc00 x","public \ud800\ud800\udc00 x","public \ud83d\ude03 x"))parity(text)
    }
    @Test fun worstCaseFourThousandUtf16UnitsFitsWithoutSecondaryArray() {
        val r=request("x"+"\u0000".repeat(3999),seed=Int.MIN_VALUE)
        val bytes=ByteArray(64*1024){0x55.toByte()}
        val count=FluxProBodyEncoder.encode(r,bytes)
        assertTrue(count<25000);assertTrue(bytes.drop(count).all{it==0x55.toByte()})
        assertEquals(r.prompt,JSONObject(String(bytes,0,count,Charsets.UTF_8)).getJSONObject("input").getString("prompt"))
    }
    @Test fun nonFluxRequestDeniedBeforeDestinationMutation() {
        val bytes=ByteArray(64*1024){0x55.toByte()}
        val bad=GenerationRequest(GenerationModel.SEEDREAM,"public",GenerationAspect.SQUARE)
        assertTrue(runCatching{FluxProBodyEncoder.encode(bad,bytes)}.exceptionOrNull() is IllegalArgumentException)
        assertTrue(bytes.all{it==0x55.toByte()})
    }
    @Test fun wrongDestinationCapacityDeniedBeforeMutation() {
        for(size in listOf(1,65535,65537)) {
            val bytes=ByteArray(size){0x55.toByte()}
            assertTrue(runCatching{FluxProBodyEncoder.encode(request("public"),bytes)}.exceptionOrNull() is IllegalArgumentException)
            assertTrue(bytes.all{it==0x55.toByte()})
        }
    }
}
