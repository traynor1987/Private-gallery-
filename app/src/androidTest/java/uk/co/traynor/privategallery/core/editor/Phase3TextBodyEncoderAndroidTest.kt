package uk.co.traynor.privategallery.core.editor

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

/** Fixed registered models and public synthetic text; no provider request. */
@RunWith(AndroidJUnit4::class)
class Phase3TextBodyEncoderAndroidTest {
    private fun request(model:GenerationModel,prompt:String="  public blue bird  ",aspect:GenerationAspect=GenerationAspect.SQUARE,
        negative:String?=null,seed:Int?=null,steps:Int?=null,relax:Boolean=false)=
        GenerationRequest(model,prompt,aspect,negative,seed,steps,relax)
    private fun encode(r:GenerationRequest,bytes:ByteArray)=when(r.model) {
        GenerationModel.SEEDREAM->TextGenerationBodyEncoder.encodeSeedream(r,bytes)
        GenerationModel.WHISKII->TextGenerationBodyEncoder.encodeWhiskii(r,bytes)
        else->error("Fixture model")
    }
    private fun actual(r:GenerationRequest):JSONObject {
        val bytes=ByteArray(65536)
        try{val count=encode(r,bytes);assertTrue(count in 1..37000);return JSONObject(String(bytes,0,count,Charsets.UTF_8))}
        finally{bytes.fill(0)}
    }
    private fun parity(r:GenerationRequest) {
        val old=JSONObject().put("input",r.model.input(r))
        if(r.model==GenerationModel.WHISKII)old.put("version",r.model.modelId)
        val decoded=JSONObject(old.toString().toByteArray(Charsets.UTF_8).toString(Charsets.UTF_8))
        val encoded=actual(r)
        assertEquals(decoded.length(),encoded.length())
        if(decoded.has("version"))assertEquals(decoded.getString("version"),encoded.getString("version"))
        val input=decoded.getJSONObject("input");val result=encoded.getJSONObject("input")
        assertEquals(input.length(),result.length())
        for(key in input.keys())assertEquals(key,input.get(key),result.get(key))
    }
    @Test fun seedreamExactSquareFieldsAndRelaxationOmission() {
        for(relax in listOf(false,true)) {
            val root=actual(request(GenerationModel.SEEDREAM,relax=relax));assertEquals(1,root.length())
            val i=root.getJSONObject("input");assertEquals(if(relax)6 else 5,i.length())
            assertEquals("public blue bird",i.getString("prompt"));assertEquals("2K",i.getString("size"));assertEquals("1:1",i.getString("aspect_ratio"))
            assertEquals("disabled",i.getString("sequential_image_generation"));assertEquals(1,i.getInt("max_images"))
            assertEquals(relax,i.has("disable_safety_checker"));if(relax)assertTrue(i.getBoolean("disable_safety_checker"))
            assertFalse(i.has("image_input"));assertFalse(i.has("seed"));parity(request(GenerationModel.SEEDREAM,relax=relax))
        }
    }
    @Test fun whiskiiExactVersionAllDimensionsAndDefaultSteps() {
        for((aspect,dims) in listOf(GenerationAspect.SQUARE to (1024 to 1024),GenerationAspect.PORTRAIT to (832 to 1216),GenerationAspect.LANDSCAPE to (1216 to 832))) {
            val r=request(GenerationModel.WHISKII,aspect=aspect);val root=actual(r);assertEquals(2,root.length())
            assertEquals("alicewuv/whiskii-gen:e90d5fa37f8c42812753afd6bc05409d67a970bc87ef57454892c0fab98a7b03",root.getString("version"))
            val i=root.getJSONObject("input");assertEquals(6,i.length());assertEquals("public blue bird",i.getString("prompt"))
            assertEquals(dims.first,i.getInt("width"));assertEquals(dims.second,i.getInt("height"));assertEquals(30,i.getInt("steps"))
            assertEquals(7,i.getInt("guidance"));assertEquals("dpmpp_2m",i.getString("scheduler"));parity(r)
        }
    }
    @Test fun whiskiiSignedSeedsAndStepsRemainNumbers() {
        for(seed in listOf(Int.MIN_VALUE,-1,0,1,Int.MAX_VALUE))for(steps in listOf(1,100)) {
            val r=request(GenerationModel.WHISKII,seed=seed,steps=steps);val i=actual(r).getJSONObject("input")
            assertEquals(seed,i.getInt("seed"));assertTrue(i.get("seed") is Number);assertEquals(steps,i.getInt("steps"));parity(r)
        }
    }
    @Test fun blankNegativeOmittedNonblankWhitespacePreserved() {
        for(negative in listOf(null,""," \t\n\u2000","  public negative \t ")) {
            val r=request(GenerationModel.WHISKII,negative=negative);val i=actual(r).getJSONObject("input")
            assertEquals(!negative.isNullOrBlank(),i.has("negative_prompt"));if(!negative.isNullOrBlank())assertEquals(negative,i.getString("negative_prompt"));parity(r)
        }
    }
    @Test fun controlsQuotedSlashUnicodeAndMalformedSurrogatesMatchLegacyUtf8() {
        for(text in listOf("public \" \\ </ \b \t \n \r \u0000 \u001f \u0080 \u009f \u2000 \u2028 \u20ff café 日本語 \ud83d\ude03 end",
            "public \ud800 x","public \udc00 x","public \ud800\ud800\udc00 x","\u2000\t public \u2000 middle \u2000\n")) {
            parity(request(GenerationModel.SEEDREAM,prompt=text));parity(request(GenerationModel.WHISKII,prompt=text,negative=" \t$text  "))
        }
    }
    @Test fun worstSixThousandUnitsFitAndLeaveTailUntouched() {
        for(model in listOf(GenerationModel.SEEDREAM,GenerationModel.WHISKII)) {
            val r=request(model,prompt="x"+"\u0000".repeat(3999),negative=if(model==GenerationModel.WHISKII)"x"+"\u0000".repeat(1999)else null,seed=if(model==GenerationModel.WHISKII)Int.MIN_VALUE else null)
            val bytes=ByteArray(65536){0x55.toByte()};val count=encode(r,bytes);assertTrue(count<37000)
            for(index in count until bytes.size)assertEquals(0x55.toByte(),bytes[index])
            assertEquals(r.prompt,JSONObject(String(bytes,0,count,Charsets.UTF_8)).getJSONObject("input").getString("prompt"));parity(r)
        }
    }
    @Test fun encodersRejectCrossModelBeforeDestinationMutation() {
        for(model in GenerationModel.entries) {
            val r=request(model);val bytes=ByteArray(65536){0x55.toByte()}
            if(model!=GenerationModel.SEEDREAM)assertTrue(runCatching{TextGenerationBodyEncoder.encodeSeedream(r,bytes)}.exceptionOrNull() is IllegalArgumentException)
            if(model!=GenerationModel.WHISKII)assertTrue(runCatching{TextGenerationBodyEncoder.encodeWhiskii(r,bytes)}.exceptionOrNull() is IllegalArgumentException)
            assertTrue(bytes.all{it==0x55.toByte()})
        }
    }
    @Test fun encodersRejectWrongArrayCapacityBeforeMutation() {
        for(model in listOf(GenerationModel.SEEDREAM,GenerationModel.WHISKII))for(size in listOf(1,65535,65537)) {
            val bytes=ByteArray(size){0x55.toByte()};assertTrue(runCatching{encode(request(model),bytes)}.exceptionOrNull() is IllegalArgumentException)
            assertTrue(bytes.all{it==0x55.toByte()})
        }
    }
}
