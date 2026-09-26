package uk.co.traynor.privategallery.core.editor

import java.io.ByteArrayOutputStream
import java.util.Base64
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class OpenAiImageApiTest {
    private class FakeTransport(private val status: Int = 200) : AiHttpTransport {
        var request: AiHttpRequest? = null
        var body = byteArrayOf()
        override suspend fun execute(request: AiHttpRequest): AiHttpResponse {
            this.request = request
            body = ByteArrayOutputStream().also { request.body?.writeTo(it) }.toByteArray()
            val json = if (request.method == "GET") """{"id":"gpt-image-2.5-flare","object":"model"}"""
                else """{"data":[{"b64_json":"${Base64.getEncoder().encodeToString(byteArrayOf(1,2,3))}"}],"usage":{"input_tokens":12,"output_tokens":34,"total_tokens":46}}"""
            return AiHttpResponse(status, "application/json", json.toByteArray())
        }
    }

    @Test fun readOnlyModelCheckDoesNotGenerate() = runBlocking {
        val fake = FakeTransport()
        OpenAiImageApi(fake).testConnection("synthetic-key".toByteArray(), OpenAiImageModel.FLARE)
        assertEquals("GET", fake.request!!.method)
        assertEquals("https://api.openai.com/v1/models/gpt-image-2.5-flare", fake.request!!.url)
        assertNull(fake.request!!.body)
    }

    @Test fun multipartEditUsesDocumentedFieldsAndParsesActualUsage() = runBlocking {
        val fake = FakeTransport()
        val result = OpenAiImageApi(fake).edit("synthetic-key".toByteArray(), byteArrayOf(4,5,6), "change lighting",
            OpenAiImageModel.SUNBURST, OpenAiImageQuality.XHIGH, OpenAiImageModeration.LOWER)
        assertEquals("https://api.openai.com/v1/images/edits", fake.request!!.url)
        assertEquals("POST", fake.request!!.method)
        val body = fake.body.toString(Charsets.ISO_8859_1)
        assertTrue(body.contains("gpt-image-2.5-sunburst"))
        assertTrue(body.contains("name=\"quality\"\r\n\r\nxhigh"))
        assertTrue(body.contains("name=\"moderation\"\r\n\r\nlow"))
        assertTrue(body.contains("name=\"size\"\r\n\r\nauto"))
        assertTrue(body.contains("name=\"image[]\"; filename=\"source.png\""))
        assertArrayEquals(byteArrayOf(1,2,3), result.bytes)
        assertEquals(OpenAiImageUsage(12,34,46), result.usage)
    }

    @Test fun invalidKeyMappedWithoutResponseBody() = runBlocking {
        val fake = FakeTransport(401)
        try { OpenAiImageApi(fake).testConnection("synthetic-key".toByteArray(), OpenAiImageModel.FLARE); fail() }
        catch (failure: AiEditFailure) { assertTrue(failure.message!!.contains("API key")) }
    }

    @Test fun targetedEditAddsOnlyExplicitMaskPart() = runBlocking {
        val fake = FakeTransport()
        OpenAiImageApi(fake).edit("synthetic-key".toByteArray(), byteArrayOf(4,5,6), "remove object",
            OpenAiImageModel.FLARE, OpenAiImageQuality.AUTO, OpenAiImageModeration.STANDARD, byteArrayOf(7,8,9))
        val body = fake.body.toString(Charsets.ISO_8859_1)
        assertTrue(body.contains("name=\"mask\"; filename=\"mask.png\""))
        assertTrue(fake.body.toList().containsAll(byteArrayOf(7,8,9).toList()))
    }
}
