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
        // Android boot class path excludes java.management; resolve the required host API
        // reflectively rather than removing or skipping its allocation assertion.
        val factory=Class.forName("java.lang.management.ManagementFactory")
        val bean=factory.getMethod("getThreadMXBean").invoke(null)
        val allocations=Class.forName("com.sun.management.ThreadMXBean")
        assertTrue("Required JVM allocation measurement unavailable",allocations.isInstance(bean))
        assertEquals(true,allocations.getMethod("isThreadAllocatedMemorySupported").invoke(bean))
        allocations.getMethod("setThreadAllocatedMemoryEnabled",java.lang.Boolean.TYPE).invoke(bean,true)
        val measure=allocations.getMethod("getThreadAllocatedBytes",java.lang.Long.TYPE)
        val holds=List(15){owner.createOwned(OwnedResourceManifest.io("hold")){attach("hold",AutoCloseable{})}}
        try{val id=Thread.currentThread().id;val before=(measure.invoke(bean,id) as Long)
            assertThrows(IllegalStateException::class.java){launchPhotoGenerationWork(owner,scope,source){error("No body")}}
            val after=measure.invoke(bean,id) as Long
            assertTrue("Required allocation counters unavailable",before>=0 && after>=before)
            val allocated=after-before
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
