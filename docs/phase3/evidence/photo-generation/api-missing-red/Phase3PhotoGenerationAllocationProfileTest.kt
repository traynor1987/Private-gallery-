package uk.co.traynor.privategallery.core.editor

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import uk.co.traynor.privategallery.core.security.*

/** JVM infrastructure allocation measurement only; not an Android/Native acceptance case. */
class Phase3PhotoGenerationAllocationProfileTest {
    private val scope=CoroutineScope(Dispatchers.IO)
    @Test fun quotaRejectionAllocatesLessThanOneEighthOfAlreadyAllocatedEightMiBSource()=isolated {a,owner->
        // Warm the fixed helper before measuring only the denied constructor thread.
        val warm=launchPhotoGenerationWork(owner,scope,byteArrayOf(7)){}
        runBlocking{withTimeout(5000){warm.job.join()}};await{a.cleanupComplete}
        val source=ByteArray(8*1024*1024){7}
        val bean=java.lang.management.ManagementFactory.getThreadMXBean()
        assertTrue("Required JVM allocation measurement unavailable",bean is com.sun.management.ThreadMXBean)
        val allocations=bean as com.sun.management.ThreadMXBean
        assertTrue(allocations.isThreadAllocatedMemorySupported);allocations.isThreadAllocatedMemoryEnabled=true
        val holds=List(15){owner.createOwned(OwnedResourceManifest.io("hold")){attach("hold",AutoCloseable{})}}
        try{val id=Thread.currentThread().id;val before=allocations.getThreadAllocatedBytes(id)
            assertThrows(IllegalStateException::class.java){launchPhotoGenerationWork(owner,scope,source){error("No body")}}
            val allocated=allocations.getThreadAllocatedBytes(id)-before
            assertTrue("Denied helper allocated a source-sized copy: $allocated",allocated in 0 until (source.size/8).toLong())
            assertTrue(source.all{it==7.toByte()})
        }finally{holds.forEach{it.close()}}
    }
    private fun isolated(body:(PrimarySessionAuthority,PrimaryOperation)->Unit) {
        val a=testPrimaryAuthority();a.open(ByteArray(32));val owner=checkNotNull(a.operationOrNull(setOf(PrimaryScope.READ,PrimaryScope.LOCAL_EDIT)))
        try{body(a,owner)}finally{owner.close();a.revoke()}
    }
    private fun await(predicate:()->Boolean) {
        val end=System.nanoTime()+5_000_000_000
        while(!predicate()){check(System.nanoTime()<end){"Actual original acknowledgement missing"};Thread.yield()}
    }
}
