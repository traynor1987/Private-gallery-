package uk.co.traynor.privategallery.core.security

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import uk.co.traynor.privategallery.core.editor.launchPhotoGenerationWork

class PlaintextPublicationTest {
    @Test fun sourcePublicationDeniesRevocationEvenAfterItsProducerClosed() {
        val a=testPrimaryAuthority();a.open(ByteArray(32));val op=checkNotNull(a.operationOrNull(setOf(PrimaryScope.READ)))
        val source=op.createOwnedPlaintext { byteArrayOf(3) };op.close();var published=0
        source.publish { published++ };assertEquals(1,published)
        a.revoke();assertThrows(IllegalStateException::class.java) { source.publish { published++ } };assertEquals(1,published)
        source.close();assertTrue(source.retirement.await(5,TimeUnit.SECONDS))
    }
    @Test fun sourceRetiringAfterProducerInputCopyCancelsTheQueuedProducer() = runBlocking {
        val a=testPrimaryAuthority();a.open(ByteArray(32));val op=checkNotNull(a.operationOrNull(PrimaryScope.entries.toSet()))
        val source=op.createOwnedPlaintext { byteArrayOf(1,2,3) };val queue=ConcurrentLinkedQueue<Runnable>()
        val dispatcher=object:CoroutineDispatcher() { override fun dispatch(context:CoroutineContext,block:Runnable) { source.close();queue.add(block) } }
        val parent=SupervisorJob();var bodies=0
        try {
            assertThrows(IllegalStateException::class.java) { launchPhotoGenerationWork(op,CoroutineScope(parent+dispatcher),source) { bodies++ } }
            while(true) (queue.poll()?:break).run()
            assertEquals(0,bodies)
        } finally { parent.cancel();while(true)(queue.poll()?:break).run();source.close();op.close();a.revoke() }
        assertTrue(source.retirement.await(5,TimeUnit.SECONDS))
    }
}
