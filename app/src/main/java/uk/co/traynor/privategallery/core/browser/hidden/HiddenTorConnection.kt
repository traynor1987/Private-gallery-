package uk.co.traynor.privategallery.core.browser.hidden

import android.content.*
import android.os.*
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean

internal class HiddenTorConnection(private val context: Context) : Closeable {
    private val closed = AtomicBoolean(false)
    @Volatile private var remote: IBinder? = null
    private var bound = false
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            if (closed.get()) { stop(service); return }
            remote = service
        }
        override fun onServiceDisconnected(name: ComponentName) { remote = null }
        override fun onBindingDied(name: ComponentName) { remote = null }
    }
    fun start() {
        check(!closed.get())
        bound = context.bindService(Intent(context, HiddenTorService::class.java), connection, Context.BIND_AUTO_CREATE)
        check(bound)
    }
    fun port(): Int {
        if (closed.get()) return 0
        val binder = remote ?: return 0
        return runCatching { transaction(binder, HiddenTorService.STATUS) { it.readInt() } }.getOrDefault(0)
    }
    fun renew() { check(!closed.get()); transaction(checkNotNull(remote), HiddenTorService.RENEW) {} }
    private fun stop(binder: IBinder) { runCatching { transaction(binder, HiddenTorService.STOP) {} } }
    private fun <T> transaction(binder: IBinder, code: Int, result: (Parcel) -> T): T {
        val data = Parcel.obtain(); val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(HiddenTorService.DESCRIPTOR)
            check(binder.transact(code, data, reply, 0))
            reply.readException(); return result(reply)
        } finally { data.recycle(); reply.recycle() }
    }
    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        remote?.let(::stop); remote = null
        if (bound) { runCatching { context.unbindService(connection) }; bound = false }
    }
}
