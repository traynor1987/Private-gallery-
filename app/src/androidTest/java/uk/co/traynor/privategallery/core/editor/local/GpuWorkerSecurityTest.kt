package uk.co.traynor.privategallery.core.editor.local

import android.content.*
import android.os.*
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class GpuWorkerSecurityTest {
    @Test fun gpuProbeRequiresNetworkGuardAndCancellationTerminatesWorker() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val probed = CountDownLatch(1)
        val died = CountDownLatch(1)
        val guard = AtomicInteger(-1)
        val uid = AtomicInteger(-1)
        var remote: Messenger? = null
        val reply = Messenger(Handler(Looper.getMainLooper()) { message ->
            if (message.what == LocalInferenceService.PROBED) {
                guard.set(message.arg2)
                uid.set(message.data.getInt("workerUid"))
                probed.countDown()
            }
            true
        })
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                binder.linkToDeath({ died.countDown() }, 0)
                remote = Messenger(binder)
                remote!!.send(Message.obtain(null, LocalInferenceService.PROBE).apply { replyTo = reply })
            }
            override fun onServiceDisconnected(name: ComponentName) {}
        }
        try {
            assertTrue(context.bindService(Intent(context, LocalGpuInferenceService::class.java), connection, Context.BIND_AUTO_CREATE))
            // No weights, images or API requests. A software-only emulator may reject Vulkan.
            assertTrue("GPU probe did not return", probed.await(60, TimeUnit.SECONDS))
            assertEquals("App-UID compute must install its fail-closed network guard", 1, guard.get())
            assertEquals(android.os.Process.myUid(), uid.get())
            remote!!.send(Message.obtain(null, LocalInferenceService.CANCEL))
            assertTrue("GPU worker must die on cancellation", died.await(10, TimeUnit.SECONDS))
        } finally {
            runCatching { remote?.send(Message.obtain(null, LocalInferenceService.CANCEL)) }
            runCatching { context.unbindService(connection) }
        }
    }
}
