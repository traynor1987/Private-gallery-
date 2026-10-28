package uk.co.traynor.privategallery.core.browser.hidden

import android.app.*
import android.content.*
import android.net.*
import android.os.*
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/** Local deny-all VPN. It forwards no TUN packet. Only the authenticated relay bridge protects TCP. */
class HiddenTorIsolationService:VpnService() {
    internal inner class OwnerBinder:Binder(){fun service()=this@HiddenTorIsolationService}
    private val binder=OwnerBinder()
    private val closed=AtomicBoolean()
    private var tun:ParcelFileDescriptor?=null
    private var reader:Thread?=null
    private var sinkRead:ParcelFileDescriptor?=null
    private var ownedNetwork:Network?=null
    internal val droppedPackets=java.util.concurrent.atomic.AtomicLong()
    internal val ipv4Dropped=java.util.concurrent.atomic.AtomicLong()
    internal val ipv6Dropped=java.util.concurrent.atomic.AtomicLong()
    internal val udpDropped=java.util.concurrent.atomic.AtomicLong()
    private val networkEvents=object:ConnectivityManager.NetworkCallback(){override fun onLost(network:Network){if(network==ownedNetwork){revoked?.invoke();retire()}}}
    internal var relay:HiddenTorRelayBridge?=null;private set
    internal var unixPath:String?=null;private set
    private var revoked:(()->Unit)?=null
    override fun onBind(intent:Intent):IBinder?=if(intent.action==SERVICE_INTERFACE)super.onBind(intent) else binder
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int)=START_NOT_STICKY
    internal fun establish(authorized:()->Boolean,onRevoked:()->Unit,beginForeground:()->Unit={startForegroundConnection()}) {
        check(instance==null && !closed.get() && VpnService.prepare(this)==null && authorized())
        instance=this;revoked=onRevoked
        try {
            beginForeground()
            tun=checkNotNull(Builder().setSession("Private browser isolation").setMtu(1500)
                .addAddress("10.252.0.1",32).addAddress("fd42:252::1",128)
                .addRoute("0.0.0.0",0).addRoute("::",0)
                .addDnsServer("10.252.0.2").addDnsServer("fd42:252::2")
                .addAllowedApplication(packageName).setBlocking(true).establish())
            val dir=File(File(applicationInfo.dataDir).canonicalFile,"ts/"+java.util.UUID.randomUUID().toString().take(8))
            check(dir.mkdirs());android.system.Os.chmod(dir.absolutePath,448)
            unixPath=File(dir,"s").absolutePath.also {require(it.toByteArray().size<104)}
            relay=HiddenTorRelayBridge({socket->!closed.get() && authorized() && protect(socket)},{!closed.get() && authorized()})
            getSystemService(ConnectivityManager::class.java).registerNetworkCallback(NetworkRequest.Builder().removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN).addTransportType(NetworkCapabilities.TRANSPORT_VPN).build(),networkEvents)
            val duplicate=ParcelFileDescriptor.dup(checkNotNull(tun).fileDescriptor);sinkRead=duplicate
            reader=Thread({val buffer=ByteArray(65_535);try{ParcelFileDescriptor.AutoCloseInputStream(duplicate).use {input->while(true){val length=input.read(buffer);if(length<0)break;droppedPackets.incrementAndGet()
                if(length>0)when((buffer[0].toInt() ushr 4) and 15){4->{ipv4Dropped.incrementAndGet();if(length>=20 && buffer[9].toInt()==17)udpDropped.incrementAndGet()};6->{ipv6Dropped.incrementAndGet();if(length>=40 && buffer[6].toInt()==17)udpDropped.incrementAndGet()}}
                buffer.fill(0)}}}catch(_:Exception){}finally{buffer.fill(0)}},"private-dns-sink").also{it.start()}
        }catch(failure:Throwable){retire();throw failure}
    }
    private fun startForegroundConnection() {
        val channel="private-network-isolation"
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(channel,"Private connection",NotificationManager.IMPORTANCE_LOW))
        startForeground(176245,Notification.Builder(this,channel).setSmallIcon(applicationInfo.icon).setContentTitle("Private Gallery connection").setVisibility(Notification.VISIBILITY_SECRET).setOngoing(true).build())
        startService(Intent(this,HiddenTorIsolationService::class.java))
    }
    internal fun network():Network? {
        if(closed.get() || tun==null)return null
        val manager=getSystemService(ConnectivityManager::class.java)
        ownedNetwork?.let {return if(manager.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN)==true)it else null}
        return manager.allNetworks.singleOrNull {manager.getNetworkCapabilities(it)?.let {caps->caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) && caps.ownerUid==android.os.Process.myUid()}==true}.also {if(it!=null)ownedNetwork=it}
    }
    internal fun retire() {
        if(!closed.compareAndSet(false,true))return
        relay?.close();relay=null
        if(tun==null) {
            // No route or native child can have been admitted. Foreground setup failures must
            // not strand the singleton behind an unrelated process-inventory wait.
            if(instance===this)instance=null
            runCatching {stopForeground(STOP_FOREGROUND_REMOVE)};stopSelf();return
        }
        // Hold the sink while prior Tor/browser lifetimes stop. A revoked OS Network also
        // remains the child's dead process binding; it is never cleared or replaced.
        val handler=Handler(Looper.getMainLooper())
        val deadline=SystemClock.elapsedRealtime()+7_000
        val check=object:Runnable {override fun run(){
            val inventory=getSystemService(ActivityManager::class.java).runningAppProcesses
            if(inventory==null){handler.postDelayed(this,50);return} // Unknown is not proof of process death.
            val peers=inventory.filter {it.uid==android.os.Process.myUid() && it.processName in setOf(packageName+":hidden_tor",packageName+":hidden_vpn_browser")}
            if(peers.isNotEmpty()) {
                if(SystemClock.elapsedRealtime()>=deadline)peers.forEach {android.os.Process.killProcess(it.pid)}
                handler.postDelayed(this,50);return
            }
            sinkRead?.close();sinkRead=null;tun?.close();tun=null;reader?.interrupt();reader=null
            runCatching {getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(networkEvents)}
            unixPath?.let {path->val socket=File(path);socket.delete();socket.parentFile?.delete()};unixPath=null
            if(instance===this@HiddenTorIsolationService)instance=null
            stopForeground(STOP_FOREGROUND_REMOVE);stopSelf()
        }}
        handler.post(check)
    }
    override fun onRevoke(){revoked?.invoke();retire()}
    override fun onDestroy(){relay?.close();sinkRead?.close();sinkRead=null;tun?.close();tun=null;runCatching {getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(networkEvents)};if(instance===this)instance=null;super.onDestroy()}
    companion object {
        @Volatile private var instance:HiddenTorIsolationService?=null
        fun released()=instance==null
        fun hasTorProcess(context:Context)=context.getSystemService(ActivityManager::class.java).runningAppProcesses.orEmpty().any {it.uid==android.os.Process.myUid() && it.processName==context.packageName+":hidden_tor"}
    }
}

internal class HiddenTorIsolationConnection(private val context:Context,private val authorized:()->Boolean,private val onRevoked:()->Unit):java.io.Closeable {
    private var bound=false
    private var closed=false
    private var service:HiddenTorIsolationService?=null
    private val connection=object:ServiceConnection {
        override fun onServiceConnected(name:ComponentName,binder:IBinder){
            if(closed)return
            try {val active=(binder as HiddenTorIsolationService.OwnerBinder).service();service=active;active.establish(authorized,onRevoked)}catch(_:Exception){close();onRevoked()}
        }
        override fun onServiceDisconnected(name:ComponentName){service=null;onRevoked()}
    }
    fun start(){check(!closed);bound=context.bindService(Intent(context,HiddenTorIsolationService::class.java),connection,Context.BIND_AUTO_CREATE);check(bound)}
    fun network()=service?.network()
    fun droppedPackets()=service?.droppedPackets?.get() ?: 0L
    fun dropCounters():Triple<Long,Long,Long> = service?.let {Triple(it.ipv4Dropped.get(),it.ipv6Dropped.get(),it.udpDropped.get())} ?: Triple(0L,0L,0L)
    fun configuration(owner:IBinder):HiddenTorConfiguration {
        val active=checkNotNull(service);val relay=checkNotNull(active.relay)
        return HiddenTorConfiguration(checkNotNull(active.unixPath),relay.port,relay.username,relay.password,owner)
    }
    override fun close(){if(closed)return;closed=true;service?.retire();service=null;if(bound){context.unbindService(connection);bound=false}}
}

internal data class HiddenTorConfiguration(val unixPath:String,val relayPort:Int,val username:String,val password:String,val owner:IBinder)
