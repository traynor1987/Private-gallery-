package uk.co.traynor.privategallery.core.browser.hidden

import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.os.Process
import org.torproject.jni.TorService

/** Bound-only Tor in a killable private process. No Activity, credentials or browser data here. */
class HiddenTorService : TorService() {
    companion object {
        const val DESCRIPTOR = "uk.co.traynor.privategallery.HiddenTor"
        const val STATUS = IBinder.FIRST_CALL_TRANSACTION
        const val STOP = STATUS + 1
        const val RENEW = STATUS + 2
    }
    override fun onCreate() {
        // Random loopback listener; SafeSocks refuses requests which already resolved a hostname.
        getTorrc(this).writeText("SocksPort 127.0.0.1:auto\nHTTPTunnelPort 0\nSafeSocks 1\nClientOnly 1\nAvoidDiskWrites 1\nLog err stdout\n")
        super.onCreate()
    }
    private val ownerBinder = object : Binder() {
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code == INTERFACE_TRANSACTION) { reply?.writeString(DESCRIPTOR); return true }
            if (Binder.getCallingUid() != Process.myUid()) throw SecurityException("Private service")
            data.enforceInterface(DESCRIPTOR)
            when (code) {
                STATUS -> {
                    val ready = getInfo("status/circuit-established") == "1"
                    reply?.writeNoException(); reply?.writeInt(if (ready) socksPort else 0)
                }
                RENEW -> {
                    torControlConnection?.signal("NEWNYM")
                    reply?.writeNoException()
                }
                STOP -> { Process.killProcess(Process.myPid()) }
                else -> return super.onTransact(code, data, reply, flags)
            }
            return true
        }
    }
    override fun onBind(intent: Intent): IBinder = ownerBinder
    override fun onUnbind(intent: Intent): Boolean { Process.killProcess(Process.myPid()); return false }
    override fun onDestroy() { Process.killProcess(Process.myPid()) }
}
