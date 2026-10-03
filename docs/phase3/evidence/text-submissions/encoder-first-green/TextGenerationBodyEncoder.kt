package uk.co.traynor.privategallery.core.editor

/** Separate fixed text-model admissions. <=6000 UTF16 units *6 escaped bytes
 * plus <512 fixed/version/number bytes fit below37KiB in the owned64KiB array.
 * This encoder is not authority and never accepts an arbitrary version/body. */
internal object TextGenerationBodyEncoder {
    const val CAPACITY=64*1024
    private const val WHISKII_VERSION="alicewuv/whiskii-gen:e90d5fa37f8c42812753afd6bc05409d67a970bc87ef57454892c0fab98a7b03"
    private fun validateCommon(r:GenerationRequest,model:GenerationModel) {
        require(r.model==model && r.prompt.length in 1..4000 && r.prompt.isNotBlank())
        require(r.aspect in model.aspects && r.resolution=="2K")
        require(r.references.isEmpty() && r.referenceHandles.isEmpty())
    }
    fun validateSeedream(r:GenerationRequest) {
        validateCommon(r,GenerationModel.SEEDREAM)
        require(r.negativePrompt==null && r.seed==null && r.steps==null)
    }
    fun validateWhiskii(r:GenerationRequest) {
        validateCommon(r,GenerationModel.WHISKII)
        require(!r.relaxModeration && (r.steps==null || r.steps in 1..100))
        require(r.negativePrompt==null || r.negativePrompt.length<=2000)
    }
    fun encodeSeedream(r:GenerationRequest,destination:ByteArray):Int {
        validateSeedream(r);require(destination.size==CAPACITY)
        val w=Encoder(destination)
        w.literal("{\"input\":{\"prompt\":");w.quote(r.prompt,trim=true)
        w.literal(",\"size\":\"2K\",\"aspect_ratio\":\"1:1\",\"sequential_image_generation\":\"disabled\",\"max_images\":1")
        if(r.relaxModeration)w.literal(",\"disable_safety_checker\":true")
        w.literal("}}")
        return w.count.also{check(it in 1..25000)}
    }
    fun encodeWhiskii(r:GenerationRequest,destination:ByteArray):Int {
        validateWhiskii(r);require(destination.size==CAPACITY)
        val w=Encoder(destination)
        w.literal("{\"version\":\"");w.literal(WHISKII_VERSION);w.literal("\",\"input\":{\"prompt\":")
        w.quote(r.prompt,trim=true)
        w.literal(",\"width\":");w.number(when(r.aspect){GenerationAspect.SQUARE->1024;GenerationAspect.PORTRAIT->832;GenerationAspect.LANDSCAPE->1216})
        w.literal(",\"height\":");w.number(when(r.aspect){GenerationAspect.SQUARE->1024;GenerationAspect.PORTRAIT->1216;GenerationAspect.LANDSCAPE->832})
        w.literal(",\"steps\":");w.number(r.steps?:30)
        w.literal(",\"guidance\":7,\"scheduler\":\"dpmpp_2m\"")
        r.negativePrompt?.takeIf{it.isNotBlank()}?.let{w.literal(",\"negative_prompt\":");w.quote(it)}
        r.seed?.let{w.literal(",\"seed\":");w.number(it)}
        w.literal("}}")
        return w.count.also{check(it in 1..37000)}
    }
    private class Encoder(private val bytes:ByteArray) {
        var count=0;private set
        private fun byte(value:Int){check(count<bytes.size);bytes[count++]=value.toByte()}
        fun literal(value:String){for(c in value){check(c.code<128);byte(c.code)}}
        fun number(value:Int) {
            var remaining=value.toLong()
            if(remaining<0){byte('-'.code);remaining=-remaining}
            var divisor=1L
            while(remaining/divisor>=10)divisor*=10
            do{byte('0'.code+(remaining/divisor).toInt());remaining%=divisor;divisor/=10}while(divisor>0)
        }
        private fun escaped(value:Int) {
            literal("\\u")
            for(shift in 12 downTo 0 step 4)byte("0123456789abcdef"[(value ushr shift) and 15].code)
        }
        fun quote(value:String,trim:Boolean=false) {
            var start=0;var end=value.length
            if(trim)while(start<end&&value[start].isWhitespace())start++
            if(trim)while(end>start&&value[end-1].isWhitespace())end--
            byte('"'.code);var i=start
            while(i<end) {
                val c=value[i++].code
                when {
                    c=='"'.code||c=='\\'.code->{byte('\\'.code);byte(c)}
                    c<32||c in 0x80..0x9f||c in 0x2000..0x20ff->escaped(c)
                    c in 0xd800..0xdbff && i<end && value[i].code in 0xdc00..0xdfff->{
                        val scalar=0x10000+((c-0xd800) shl 10)+(value[i++].code-0xdc00)
                        byte(0xf0 or (scalar ushr 18));byte(0x80 or ((scalar ushr 12) and 63))
                        byte(0x80 or ((scalar ushr 6) and 63));byte(0x80 or (scalar and 63))
                    }
                    // The legacy JSONObject String -> UTF8 encoding replaces each
                    // unpaired surrogate with '?', rather than preserving it in JSON.
                    c in 0xd800..0xdfff->byte('?'.code)
                    c<128->byte(c)
                    c<0x800->{byte(0xc0 or (c ushr 6));byte(0x80 or (c and 63))}
                    else->{byte(0xe0 or (c ushr 12));byte(0x80 or ((c ushr 6) and 63));byte(0x80 or (c and 63))}
                }
            }
            byte('"'.code)
        }
    }
}
