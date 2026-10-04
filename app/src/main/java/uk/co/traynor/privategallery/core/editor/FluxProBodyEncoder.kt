package uk.co.traynor.privategallery.core.editor

/** Typed text-only encoder. The caller supplies and owns the complete original
 * 64KiB array; no JSONObject, serialized String, substring or secondary array.
 * At most 4000 UTF16 units * 6 escape bytes + <128 fixed bytes fit below25KiB.
 * This encoder is not authority or Native ownership by itself. */
internal object FluxProBodyEncoder {
    const val CAPACITY=64*1024
    fun validate(request:GenerationRequest) {
        require(request.model==GenerationModel.FLUX_PRO)
        require(request.prompt.length in 1..4000 && request.prompt.isNotBlank())
        require(request.aspect in GenerationModel.FLUX_PRO.aspects)
        require(request.negativePrompt==null && request.steps==null && !request.relaxModeration)
        require(request.resolution=="2K" && request.references.isEmpty() && request.referenceHandles.isEmpty())
    }
    fun encode(request:GenerationRequest,destination:ByteArray):Int {
        validate(request);require(destination.size==CAPACITY)
        val writer=Encoder(destination)
        writer.literal("{\"input\":{\"prompt\":")
        writer.quoteTrimmed(request.prompt)
        writer.literal(",\"aspect_ratio\":\"")
        writer.literal(when(request.aspect){GenerationAspect.SQUARE->"1:1";GenerationAspect.PORTRAIT->"2:3";GenerationAspect.LANDSCAPE->"3:2"})
        writer.literal("\",\"output_format\":\"png\"")
        request.seed?.let{writer.literal(",\"seed\":");writer.number(it)}
        writer.literal("}}")
        return writer.count.also{check(it in 1..25000)}
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
        fun quoteTrimmed(value:String) {
            var start=0;var end=value.length
            while(start<end&&value[start].isWhitespace())start++
            while(end>start&&value[end-1].isWhitespace())end--
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
