package uk.co.traynor.privategallery.core.security

import android.os.Handler
import android.os.Looper
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Test

/** Real Android Main dispatch, synthetic one-leaf value; no WebView/provider completion claim. */
@org.junit.runner.RunWith(androidx.test.ext.junit.runners.AndroidJUnit4::class)
class Phase3MainNativeAffinityAndroidTest {
    @Test fun actualMainFactoryUseAndQueuedDisposalKeepTheirOriginal() = isolated { _,guard ->
        val handler=Handler(Looper.getMainLooper());val calls=AtomicInteger();val actual=Any()
        lateinit var source:OwnedMainThreadValue<Any>
        main {
            source=guard.createOwnedMainValue(post=handler::post,isMain=::isMain,dispose={value:Any->assertTrue(isMain());assertSame(actual,value);calls.incrementAndGet()}) {assertTrue(isMain());actual}
            source.useValue{assertTrue(isMain());assertSame(actual,it)}
            source.close();assertFalse(source.retirement.isComplete);assertEquals(0,calls.get())
        }
        assertTrue(source.retirement.await(5,TimeUnit.SECONDS));assertEquals(1,calls.get());source.close()
    }
    @Test fun actualHeldMainNativeReturnKeepsRevokedOriginalCharged() = isolated { authority,guard ->
        val handler=Handler(Looper.getMainLooper());val entered=CountDownLatch(1);val allow=CountDownLatch(1)
        lateinit var source:OwnedMainThreadValue<Any>
        main { source=guard.createOwnedMainValue(post=handler::post,isMain=::isMain,dispose={_:Any->assertTrue(isMain());entered.countDown();allow.await()}){Any()} }
        try {
            source.close();assertTrue(entered.await(5,TimeUnit.SECONDS));authority.revoke()
            assertFalse(source.retirement.isComplete);assertFalse(authority.cleanupComplete)
            val key=ByteArray(32){7};assertThrows(IllegalStateException::class.java){authority.open(key)};assertArrayEquals(ByteArray(32),key)
        } finally {allow.countDown()}
        assertTrue(source.retirement.await(5,TimeUnit.SECONDS))
    }
    @Test fun actualWrongMainConsumerDeniesBeforeCallback() = isolated { _,guard ->
        assertFalse(isMain());val handler=Handler(Looper.getMainLooper());var calls=0
        lateinit var source:OwnedMainThreadValue<Any>
        main {source=guard.createOwnedMainValue(post=handler::post,isMain=::isMain,dispose={_:Any->assertTrue(isMain())}){Any()}}
        try {assertThrows(IllegalStateException::class.java){source.useValue{calls++}};assertEquals(0,calls)}
        finally {source.close();assertTrue(source.retirement.await(5,TimeUnit.SECONDS))}
    }
    private fun isMain():Boolean=Looper.myLooper()===Looper.getMainLooper()
    private fun main(action:()->Unit) {
        var failure:Throwable?=null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {try{action()}catch(t:Throwable){failure=t}}
        failure?.let{throw it}
    }
    private fun isolated(body:(PrimarySessionAuthority,ScopedIoGuard)->Unit) {
        val authority=PrimarySessionAuthority().also {owner->
            for(name in listOf("ioReleasePool","presentationReleasePool")) owner.javaClass.getDeclaredField(name).apply{isAccessible=true}.set(owner,ReleasePool(16))
        }
        authority.open(ByteArray(32));val op=checkNotNull(authority.operationOrNull(setOf(PrimaryScope.READ)))
        try{body(authority,ScopedIoGuard(op,PrimaryScope.READ))}finally{op.close();authority.revoke()}
    }
}
