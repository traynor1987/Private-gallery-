package uk.co.traynor.privategallery.intelligence

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class IntelligenceConversationTest {
    @Test fun cancelAndRetryKeepTheSameApprovalAndNeverAppendALateReply() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val waiting = CompletableDeferred<Unit>()
        val sent = mutableListOf<String>()
        val assistant = assistant { request, allowed ->
            assertTrue(allowed())
            sent.add(request.requestId)
            if (sent.size == 1) {
                started.complete(Unit)
                waiting.await()
                IntelligenceCloudAnswer("Late reply", cloud = true)
            } else IntelligenceCloudAnswer("Approved reply", cloud = true)
        }
        val chat = IntelligenceConversation(this)
        val request = IntelligenceCloudRequest.prepare("Give me an example", emptyList(), IntelligenceCloudMode.CONFIGURED_BRAIN)
        chat.sendCloud(request, this, assistant) { true }
        chat.sendCloud(request, this, assistant) { true }
        started.await()
        chat.cancelCloud()
        assertNull(chat.activeCloud.value)
        assertSame(request, chat.retryCloud.value)
        waiting.complete(Unit)
        yield()
        assertTrue(chat.history.value.isEmpty())

        chat.sendCloud(checkNotNull(chat.retryCloud.value), this, assistant) { true }
        yield()
        assertEquals(listOf(request.requestId, request.requestId), sent)
        assertEquals("Approved reply", chat.history.value.single().answer)
        assertNotNull(chat.history.value.single().timestamp)
        assertNull(chat.retryCloud.value)
        assertNull(chat.activeCloud.value)
    }

    @Test fun localFallbackNeverAppearsAsCloudHistory() = runBlocking {
        val chat = IntelligenceConversation(this)
        val request = IntelligenceCloudRequest.prepare("What is a haiku?", emptyList(), IntelligenceCloudMode.OWNER_PREVIEW)
        chat.sendCloud(request, this, assistant { _, _ -> IntelligenceCloudAnswer("Preview unavailable") }) { true }
        yield()
        val fallback = chat.history.value.single()
        assertFalse(fallback.cloud)
        assertFalse(fallback.ownerPreview)
        assertEquals("Preview unavailable", fallback.answer)
    }

    private fun assistant(answer: suspend (IntelligenceCloudRequest, () -> Boolean) -> IntelligenceCloudAnswer) =
        object : IntelligenceAssistant {
            override fun previewEnabled() = false
            override suspend fun check(mayContinue: () -> Boolean) = "Unused"
            override suspend fun ask(request: IntelligenceCloudRequest, maySend: () -> Boolean) = answer(request, maySend)
        }
}
