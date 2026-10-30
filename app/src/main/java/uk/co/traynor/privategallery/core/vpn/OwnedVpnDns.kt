package uk.co.traynor.privategallery.core.vpn

import android.content.Context
import android.net.ConnectivityManager
import android.net.InetAddresses
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Process
import android.os.SystemClock
import java.io.Closeable
import java.io.DataOutputStream
import java.net.*
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** DNS to the captured VPN's configured numeric server, never Android's fallback resolver. */
internal object OwnedVpnDns {
    private val random=SecureRandom()
    fun lookup(context:Context,network:Network,hostname:String,current:()->Boolean):List<InetAddress> {
        val manager=context.applicationContext.getSystemService(ConnectivityManager::class.java)
        fun properties():android.net.LinkProperties {
            check(current())
            val caps=checkNotNull(manager.getNetworkCapabilities(network))
            check(caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) && caps.ownerUid==Process.myUid())
            return checkNotNull(manager.getLinkProperties(network)).also {check(!it.interfaceName.isNullOrBlank())}
        }
        try {
            val original=properties()
            if(InetAddresses.isNumericAddress(hostname))return listOf(InetAddresses.parseNumericAddress(hostname)).also {properties()}
            val name=VpnDnsMessage.hostname(hostname)
            val servers=original.dnsServers.toList()
            require(servers.isNotEmpty() && servers.size<=8 && servers.all {
                !it.isAnyLocalAddress && !it.isLoopbackAddress && !it.isLinkLocalAddress && !it.isMulticastAddress
            })
            val deadline=SystemClock.elapsedRealtime()+8_000
            val stopped=AtomicBoolean()
            val gate=Any()
            val resources=ConcurrentHashMap.newKeySet<Closeable>()
            fun active():Boolean=runCatching {
                !stopped.get() && SystemClock.elapsedRealtime()<deadline && properties().let {
                    it.interfaceName==original.interfaceName && it.dnsServers==servers
                }
            }.getOrDefault(false)
            fun checkActive(){check(active())}
            fun remaining():Int {checkActive();return (deadline-SystemClock.elapsedRealtime()).coerceIn(1,3_000).toInt()}
            fun register(value:Closeable)=synchronized(gate){checkActive();resources.add(value)}
            val watch=Executors.newSingleThreadScheduledExecutor()
            watch.scheduleWithFixedDelay({if(!active())synchronized(gate){stopped.set(true);resources.forEach {runCatching {it.close()}}}},0,50,TimeUnit.MILLISECONDS)
            fun exchange(query:ByteArray,type:Int,id:Int):List<InetAddress> {
                val reply=DatagramSocket().use {socket ->
                    register(socket)
                    try {
                        checkActive();network.bindSocket(socket)
                        socket.connect(InetSocketAddress(servers.first(),53));socket.soTimeout=100
                        checkActive();socket.send(DatagramPacket(query,query.size))
                        val buffer=ByteArray(4097)
                        try {
                            val receiveDeadline=minOf(deadline,SystemClock.elapsedRealtime()+3_000)
                            fun receive():ByteArray {
                                while(true) {
                                    checkActive();if(SystemClock.elapsedRealtime()>=receiveDeadline)throw SocketTimeoutException()
                                    val packet=DatagramPacket(buffer,buffer.size)
                                    try {socket.receive(packet)}catch(_:SocketTimeoutException){continue}
                                    checkActive();if(SystemClock.elapsedRealtime()>=receiveDeadline)throw SocketTimeoutException()
                                    require(packet.length in 12..4096);return buffer.copyOf(packet.length)
                                }
                            }
                            receive()
                        } finally {buffer.fill(0)}
                    } finally {resources.remove(socket)}
                }
                try {
                    if(!VpnDnsMessage.truncated(reply,name,type,id))return VpnDnsMessage.answers(reply,name,type,id).also {checkActive()}
                    // Only a verified truncated reply permits TCP, to the same server on the same Network.
                    return Socket().use {socket ->
                        register(socket)
                        try {
                            checkActive();network.bindSocket(socket);socket.connect(InetSocketAddress(servers.first(),53),remaining());socket.soTimeout=100
                            checkActive();val output=DataOutputStream(socket.getOutputStream());output.writeShort(query.size);output.write(query);output.flush()
                            val input=socket.getInputStream()
                            val receiveDeadline=minOf(deadline,SystemClock.elapsedRealtime()+3_000)
                            fun exact(count:Int):ByteArray {
                                val bytes=ByteArray(count);var offset=0
                                try {
                                    while(offset<count) {
                                        checkActive();if(SystemClock.elapsedRealtime()>=receiveDeadline)throw SocketTimeoutException()
                                        val n=try {input.read(bytes,offset,count-offset)}catch(_:SocketTimeoutException){continue}
                                        checkActive();if(SystemClock.elapsedRealtime()>=receiveDeadline)throw SocketTimeoutException()
                                        require(n>0);offset+=n
                                    }
                                    checkActive();if(SystemClock.elapsedRealtime()>=receiveDeadline)throw SocketTimeoutException()
                                    return bytes
                                } catch(failure:Throwable){bytes.fill(0);throw failure}
                            }
                            val prefix=exact(2)
                            val count=try {((prefix[0].toInt() and 255) shl 8) or (prefix[1].toInt() and 255)}finally{prefix.fill(0)}
                            require(count in 12..4096);val buffer=exact(count)
                            try {VpnDnsMessage.answers(buffer,name,type,id).also {checkActive()}}finally{buffer.fill(0)}
                        } finally {resources.remove(socket)}
                    }
                } finally {reply.fill(0)}
            }
            try {
                val result=ArrayList<InetAddress>()
                for(type in intArrayOf(1,28)) {
                    checkActive();val id=random.nextInt(65536);val query=VpnDnsMessage.query(name,type,id)
                    try {result.addAll(exchange(query,type,id))}finally{query.fill(0)}
                }
                checkActive();if(result.isEmpty())throw UnknownHostException("No VPN DNS address")
                return result.distinct()
            } finally {synchronized(gate){stopped.set(true);resources.forEach {runCatching {it.close()}};resources.clear()};watch.shutdownNow()}
        } catch(_:Exception) {throw UnknownHostException("Private VPN DNS unavailable")}
    }
}
