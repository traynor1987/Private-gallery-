package uk.co.traynor.privategallery.core.editor

import java.io.ByteArrayOutputStream
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ReplicateSeedreamTest {
    private val token = "synthetic-token".toByteArray()
    private class FakeTransport(val respond: suspend (AiHttpRequest) -> AiHttpResponse) : AiHttpTransport {
        val requests = mutableListOf<AiHttpRequest>()
        override suspend fun execute(request: AiHttpRequest): AiHttpResponse { requests += request; return respond(request) }
    }
    private fun json(value: String, status: Int = 200) = AiHttpResponse(status, "application/json", value.toByteArray())
    @Test fun connectionCheckOnlyReadsAccountAndChosenModel() = runBlocking {
        val transport = FakeTransport { if (it.url.endsWith("/account")) json("""{"type":"user","username":"synthetic"}""") else json("""{"owner":"bytedance","name":"seedream-4.5"}""") }
        ReplicateSeedreamApi(transport).testConnection(token)
        assertEquals(listOf("https://api.replicate.com/v1/account", "https://api.replicate.com/v1/models/bytedance/seedream-4.5"), transport.requests.map { it.url })
        assertTrue(transport.requests.all { it.method == "GET" && it.body == null })
    }
    @Test fun editSendsOnlyOneInlineImageAndPromptThenDownloadsResult() = runBlocking {
        var body: JSONObject? = null
        val image = byteArrayOf(1,2,3)
        val transport = FakeTransport { request ->
            if (request.method == "POST") {
                val stream = ByteArrayOutputStream(); request.body!!.writeTo(stream); body = JSONObject(stream.toString("UTF-8"))
                assertEquals("90s", request.headers["Cancel-After"])
                json("""{"id":"abc123","status":"succeeded","output":["https://replicate.delivery/a/result.png"]}""")
            } else AiHttpResponse(200, "image/png", byteArrayOf(7,8))
        }
        val result = ReplicateSeedreamApi(transport).edit(token, image, "Remove the text")
        assertArrayEquals(byteArrayOf(7,8), result)
        val input = body!!.getJSONObject("input")
        assertEquals(setOf("input"), body!!.keySet())
        assertEquals(setOf("prompt","image_input","size","aspect_ratio","sequential_image_generation","max_images"), input.keySet())
        assertEquals("Remove the text", input.getString("prompt"))
        assertEquals(1, input.getJSONArray("image_input").length())
        assertEquals("data:image/jpeg;base64,AQID", input.getJSONArray("image_input").getString(0))
        assertEquals("match_input_image", input.getString("aspect_ratio"))
        assertEquals("https://api.replicate.com/v1/models/bytedance/seedream-4.5/predictions", transport.requests.first().url)
    }
    @Test fun pollingUsesValidatedIdNeverProviderSuppliedApiUrl() = runBlocking {
        var calls = 0
        val transport = FakeTransport {
            calls++
            when(calls) {
                1 -> json("""{"id":"abc123","status":"starting","urls":{"get":"https://attacker.invalid"}}""")
                2 -> json("""{"id":"abc123","status":"succeeded","output":["https://x.replicate.delivery/result.jpg"]}""")
                else -> AiHttpResponse(200,"image/jpeg",byteArrayOf(1))
            }
        }
        ReplicateSeedreamApi(transport, pollMillis = 1).edit(token,byteArrayOf(1),"edit")
        assertEquals("https://api.replicate.com/v1/predictions/abc123", transport.requests[1].url)
    }
    @Test fun untrustedOutputUrlsAreRejectedBeforeCredentialTransmission() = runBlocking {
        for (url in listOf("http://replicate.delivery/a", "https://replicate.delivery.evil.test/a", "https://evil.test/a", "https://user@replicate.delivery/a", "https://replicate.delivery:444/a")) {
            val transport = FakeTransport { json("""{"id":"abc123","status":"succeeded","output":["$url"]}""") }
            try { ReplicateSeedreamApi(transport).edit(token, byteArrayOf(1), "edit"); fail(url) } catch (_: AiEditFailure) { }
            assertEquals(1,transport.requests.size)
        }
    }
    @Test fun httpFailuresAreUsefulAndNeverEchoProviderBodies() = runBlocking {
        for (status in listOf(401,403,402,429,500,302)) {
            val transport = FakeTransport { json("""{"error":"private token and prompt"}""", status) }
            try { ReplicateSeedreamApi(transport).testConnection(token); fail() }
            catch (e: AiEditFailure) { assertFalse(e.message!!.contains("private token")); assertFalse(e.message!!.contains("prompt")) }
        }
    }
    @Test fun malformedOrMultipleResultsFailClosed() = runBlocking {
        for (response in listOf("{}", "not json", """{"id":"../x","status":"processing"}""", """{"id":"abc","status":"succeeded","output":[]}""", """{"id":"abc","status":"succeeded","output":["https://replicate.delivery/a","https://replicate.delivery/b"]}""")) {
            val transport = FakeTransport { json(response) }
            try { ReplicateSeedreamApi(transport).edit(token, byteArrayOf(1), "edit"); fail() } catch (_: AiEditFailure) { }
        }
    }
    @Test fun cancellationAttemptsRemoteCancelWithoutRetryingGeneration() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val transport = FakeTransport {
            if (it.url.endsWith("/cancel")) json("{}")
            else if(it.method == "POST") json("""{"id":"abc","status":"processing"}""")
            else { started.complete(Unit); awaitCancellation() }
        }
        val job = launch { ReplicateSeedreamApi(transport, 1).edit(token, byteArrayOf(1), "edit") }
        started.await(); job.cancelAndJoin()
        assertEquals(1, transport.requests.count { it.url.endsWith("/predictions") })
        assertTrue(transport.requests.any { it.url.endsWith("/abc/cancel") })
    }
    @Test fun emptyPromptAndOversizedInputNeverContactProvider() = runBlocking {
        val transport = FakeTransport { error("must not call") }
        for ((bytes, prompt) in listOf(byteArrayOf(1) to " ", ByteArray(ReplicateSeedreamApi.MAX_INLINE_BYTES + 1) to "edit")) {
            try { ReplicateSeedreamApi(transport).edit(token,bytes,prompt); fail() } catch (_: AiEditFailure) { }
        }
        assertTrue(transport.requests.isEmpty())
    }
    @Test fun cancellationAtResultHandoffWipesDownloadedBytes() = runBlocking {
        val downloaded = byteArrayOf(8,9,10)
        val transport = FakeTransport {
            if (it.method == "POST") json("""{"id":"abc","status":"succeeded","output":["https://replicate.delivery/a.png"]}""")
            else { currentCoroutineContext().cancel(); AiHttpResponse(200,"image/png",downloaded) }
        }
        val provider = ReplicateSeedreamProvider({ token.copyOf() },ReplicateSeedreamApi(transport),{ it.copyOf() })
        val job = launch { provider.edit(AiEditRequest(byteArrayOf(1),AiParameters(prompt="edit"))) }
        job.join()
        assertTrue(job.isCancelled)
        assertTrue(downloaded.all { it == 0.toByte() })
    }
    @Test fun boundedResponseReaderRejectsOversizeAndHonoursCancellation() {
        try { PrivateAiHttpTransport.readBounded(java.io.ByteArrayInputStream(ByteArray(33)),32) {}; fail() } catch (_: AiEditFailure) { }
        assertArrayEquals(byteArrayOf(1,2),PrivateAiHttpTransport.readBounded(java.io.ByteArrayInputStream(byteArrayOf(1,2)),32) {})
        try { PrivateAiHttpTransport.readBounded(java.io.ByteArrayInputStream(ByteArray(33)),32) { throw CancellationException() }; fail() } catch (_: CancellationException) { }
    }

}
