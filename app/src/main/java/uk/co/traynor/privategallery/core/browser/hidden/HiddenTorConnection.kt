package uk.co.traynor.privategallery.core.browser.hidden

import android.content.*
import android.os.*
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean

internal class HiddenTorConnection(private val context: Context,private val isolated:HiddenTorConfiguration?=null) : Closeable {
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
        val intent=Intent(context,HiddenTorService::class.java)
        isolated?.let {value->intent.putExtras(Bundle().apply {putBoolean("isolated",true);putBinder("route-owner",value.owner);putString("unix-path",value.unixPath);putInt("relay-port",value.relayPort);putString("relay-user",value.username);putString("relay-password",value.password)})}
        bound = context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        check(bound)
    }
    fun alive(): Boolean = !closed.get() && remote?.isBinderAlive == true
    fun unixReady():Boolean = !closed.get() && remote?.let {runCatching {transaction(it,HiddenTorService.UNIX_READY){p->p.readInt()==1}}.getOrDefault(false)}==true
    fun port(): Int {
        if (closed.get()) return 0
        val binder = remote ?: return 0
        return runCatching { transaction(binder, HiddenTorService.STATUS) { it.readInt() } }.getOrDefault(0)
    }
    fun bootstrapProgress(): Int {
        if(closed.get())return 0
        val binder=remote ?: return 0
        return runCatching {transaction(binder,HiddenTorService.BOOTSTRAP){it.readInt()}}.getOrDefault(0)
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
