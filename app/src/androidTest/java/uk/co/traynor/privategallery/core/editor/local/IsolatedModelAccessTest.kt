package uk.co.traynor.privategallery.core.editor.local

import android.content.*
import android.content.pm.PackageManager
import android.os.*
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class IsolatedModelAccessTest {
    @Test fun localPromptLimitUsesActualTokenizerWithoutLoadingWeights() {
        assertTrue(LocalNative.available)
        assertTrue(LocalNative.promptFits("a blue flower"))
        assertFalse(LocalNative.promptFits("flower ".repeat(100)))
    }
    @Test fun privateModelDescriptorWorksInIsolatedUidWithoutNetworkPermission() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // Tiny valid safetensors DATA fixture: no production weights, no cloud request.
        val header = "{\"fixture\":{\"dtype\":\"F32\",\"shape\":[2],\"data_offsets\":[0,8]}}".toByteArray()
        val fixture = File(context.noBackupFilesDir, "model-fd-test.safetensors")
        fixture.outputStream().use { it.write(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(header.size.toLong()).array()); it.write(header); it.write(ByteArray(8) { index -> index.toByte() }) }
        fixture.setReadable(false, false); fixture.setReadable(true, true)
        val memory = SharedMemory.create("fixture-pixels", 64 * 64 * 7)
        val opened = CountDownLatch(1)
        val failed = CountDownLatch(1)
        val workerUid = AtomicInteger(-1)
        val permission = AtomicInteger(PackageManager.PERMISSION_GRANTED)
        var remote: Messenger? = null
        val descriptor = ParcelFileDescriptor.open(fixture, ParcelFileDescriptor.MODE_READ_ONLY)
        val response = Messenger(Handler(Looper.getMainLooper()) { message ->
            when (message.what) {
                LocalInferenceService.MODEL_OPENED -> { workerUid.set(message.arg1); permission.set(message.arg2); opened.countDown() }
                LocalInferenceService.FAILED -> failed.countDown()
            }
            true
        })
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                remote = Messenger(binder)
                remote!!.send(Message.obtain(null, LocalInferenceService.GENERATE).apply {
                    replyTo = response
                    data = Bundle().apply { putParcelable("pixels", memory); putParcelable("model", descriptor); putInt("width", 64); putInt("height", 64); putString("prompt", "fixture") }
                })
            }
            override fun onServiceDisconnected(name: ComponentName) {}
        }
        try {
            assertTrue(context.bindIsolatedService(Intent(context, LocalInferenceService::class.java), Context.BIND_AUTO_CREATE, "fixture-" + java.util.UUID.randomUUID(), context.mainExecutor, connection))
            assertTrue("FD-native safetensors and mmap preflight did not complete", opened.await(20, TimeUnit.SECONDS))
            assertNotEquals(android.os.Process.myUid(), workerUid.get())
            assertEquals(PackageManager.PERMISSION_DENIED, permission.get())
            assertTrue("Invalid tiny fixture must fail generation gracefully", failed.await(20, TimeUnit.SECONDS))
            assertEquals(fixture.length(), descriptor.statSize)
        } finally {
            runCatching { remote?.send(Message.obtain(null, LocalInferenceService.CANCEL)) }
            runCatching { context.unbindService(connection) }
            descriptor.close(); memory.close(); fixture.delete()
        }
    }
}
