package uk.co.traynor.privategallery.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.ExifInterface
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import uk.co.traynor.privategallery.core.editor.*

class ReplicatePrivacyTest {
    @Test fun remotePreparationHasBoundedDimensionsNoGpsAndNoFiles() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val before = context.cacheDir.walkTopDown().filter { it.isFile }.map { it.absolutePath }.toSet()
        val bitmap = Bitmap.createBitmap(3000,2000,Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.BLUE)
        val source = try { PhotoRenderer.encode(bitmap) } finally { bitmap.recycle() }
        val prepared = ReplicateImagePreparation.prepare(source)
        try {
            assertTrue(prepared.size <= ReplicateSeedreamApi.MAX_INLINE_BYTES)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(prepared,0,prepared.size,bounds)
            assertTrue(bounds.outWidth.toLong() * bounds.outHeight <= PhotoRenderer.PREVIEW_PIXELS)
            assertNull(ExifInterface(ByteArrayInputStream(prepared)).getAttribute(ExifInterface.TAG_GPS_LATITUDE))
            assertEquals(before, context.cacheDir.walkTopDown().filter { it.isFile }.map { it.absolutePath }.toSet())
        } finally { source.fill(0); prepared.fill(0) }
    }
    @Test fun successfulSetupSurvivesRecreationOnlyAsEncryptedCredential() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = AiCredentialStore(context)
        val id = "replicate-setup-test"
        val credentials = object : AiCredentials {
            override fun isConfigured() = store.isConfigured(id)
            override fun read() = store.read(id)
            override fun save(credential: ByteArray) = store.save(id,credential)
            override fun clear() = store.clear(id)
        }
        fun recreate() = AiProviderConfiguration(credentials,{}) { read -> ReplicateSeedreamProvider(read,ReplicateSeedreamApi(AiHttpTransport { error("No live API") }),{ it.copyOf() }) }
        try {
            val config = recreate(); config.connect("synthetic-token".toByteArray())
            val restored = recreate()
            assertNotNull(restored.provider)
            assertEquals(AiConnectionStatus.CONFIGURED,restored.status.value)
            val encrypted = java.io.File(context.noBackupFilesDir,"ai-provider-$id.enc").readBytes()
            assertFalse(encrypted.toString(Charsets.UTF_8).contains("synthetic-token"))
            restored.remove {}
            assertNull(recreate().provider)
        } finally { store.clear(id) }
    }
    @Test fun realAdapterUsesOnlyPreparedSelectedImageAndSanitizesResult() = runBlocking {
        fun image(colour: Int): ByteArray {
            val bitmap = Bitmap.createBitmap(40,30,Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(colour)
            return try { PhotoRenderer.encode(bitmap) } finally { bitmap.recycle() }
        }
        val source = image(Color.BLUE)
        val returned = image(Color.RED)
        var generationRequests = 0
        val transport = AiHttpTransport { request ->
            if (request.method == "POST") {
                generationRequests++
                val stream = java.io.ByteArrayOutputStream()
                request.body!!.writeTo(stream)
                val input = org.json.JSONObject(stream.toString("UTF-8")).getJSONObject("input")
                val inputs = input.getJSONArray("image_input")
                assertEquals(1,inputs.length())
                val selected = java.util.Base64.getDecoder().decode(inputs.getString(0).substringAfter(","))
                val bitmap = BitmapFactory.decodeByteArray(selected,0,selected.size)
                try { assertEquals(40,bitmap.width); assertTrue(Color.blue(bitmap.getPixel(0,0)) > 240) }
                finally { bitmap.recycle(); selected.fill(0) }
                AiHttpResponse(201,"application/json","""{"id":"synthetic1","status":"succeeded","output":["https://replicate.delivery/result.png"]}""".toByteArray())
            } else AiHttpResponse(200,"image/png",returned)
        }
        val provider = ReplicateSeedreamProvider({ "synthetic-token".toByteArray() },ReplicateSeedreamApi(transport),ReplicateImagePreparation::prepare)
        val output = AiEditPipeline(PhotoRenderer::sanitize).generate(provider,true,source,AiParameters(prompt="Make it red"))
        try {
            assertEquals(1,generationRequests)
            val bitmap = BitmapFactory.decodeByteArray(output,0,output.size)
            try { assertEquals(Color.RED,bitmap.getPixel(0,0)) } finally { bitmap.recycle() }
            assertTrue(returned.all { it == 0.toByte() })
            val original = BitmapFactory.decodeByteArray(source,0,source.size)
            try { assertEquals(Color.BLUE,original.getPixel(0,0)) } finally { original.recycle() }
        } finally { source.fill(0); output.fill(0) }
    }

}
