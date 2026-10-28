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
        const val BOOTSTRAP = STATUS + 3
        const val UNIX_READY = STATUS + 4
    }
    private var initialized=false
    private var isolated=false
    private var isolatedSocketPath:String?=null
    private var owner:IBinder?=null
    private val ownerDeath=IBinder.DeathRecipient {Process.killProcess(Process.myPid())}
    // Native initialization must wait until onBind supplies the authenticated route configuration.
    override fun onCreate() {}
    private fun initialize(intent:Intent) {
        check(!initialized);initialized=true
        val extras=intent.extras
        isolated=extras?.getBoolean("isolated",false)==true
        var config="SocksPort 127.0.0.1:auto\nHTTPTunnelPort 0\nSafeSocks 1\nSafeLogging 1\nClientOnly 1\nAvoidDiskWrites 1\nLog err stdout\n"
        if(isolated) {
            val parent=checkNotNull(extras?.getBinder("route-owner"));check(parent.isBinderAlive);parent.linkToDeath(ownerDeath,0);owner=parent
            val path=checkNotNull(extras.getString("unix-path"));require(path.toByteArray().size<104 && !path.contains('\n') && !path.contains(' '))
            val dir=java.io.File(path).parentFile!!;require(dir.canonicalFile==dir.absoluteFile && dir.isDirectory && dir.absolutePath.startsWith(java.io.File(applicationInfo.dataDir,"ts").canonicalPath+"/"))
            val port=extras.getInt("relay-port");require(port in 1..65535)
            val user=checkNotNull(extras.getString("relay-user"));val password=checkNotNull(extras.getString("relay-password"))
            require(user.matches(Regex("[a-zA-Z0-9-]{1,64}")) && password.matches(Regex("[a-zA-Z0-9-]{16,128}")))
            isolatedSocketPath=path
            config="SocksPort unix:$path\nHTTPTunnelPort 0\nSafeSocks 1\nSafeLogging 1\nClientOnly 1\nAvoidDiskWrites 1\nLog err stdout\nSocks5Proxy 127.0.0.1:$port\nSocks5ProxyUsername $user\nSocks5ProxyPassword $password\n"
        }
        getTorrc(this).writeText(config)
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
                UNIX_READY -> {reply?.writeNoException();reply?.writeInt(if(isolated && getInfo("status/circuit-established")=="1" && getInfo("net/listeners/socks").let {listeners ->listeners.contains(checkNotNull(isolatedSocketPath)) && listeners.trim().split(' ').all {it.trim('"').startsWith("unix:")}})1 else 0)}
                BOOTSTRAP -> {
                    val progress = Regex("PROGRESS=([0-9]{1,3})").find(getInfo("status/bootstrap-phase").orEmpty())
                        ?.groupValues?.get(1)?.toIntOrNull()?.coerceIn(0,100) ?: 0
                    reply?.writeNoException(); reply?.writeInt(progress)
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
    override fun onBind(intent: Intent): IBinder {if(!initialized)initialize(intent);return ownerBinder}
    override fun onUnbind(intent: Intent): Boolean { Process.killProcess(Process.myPid()); return false }
    override fun onDestroy() { Process.killProcess(Process.myPid()) }
}
