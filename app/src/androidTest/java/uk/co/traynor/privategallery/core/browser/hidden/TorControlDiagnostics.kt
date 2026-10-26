package uk.co.traynor.privategallery.core.browser.hidden

import android.content.Context
import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.os.SystemClock
import java.io.Closeable
import java.io.InputStream
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Opt-in TEST ONLY observer of the existing instance; never reconfigures or controls circuits. */
internal class TorControlDiagnostics(context:Context, private val fixtureHost:String) : Closeable {
    private val socket=LocalSocket()
    private val started=SystemClock.elapsedRealtime()
    private val records=mutableListOf<String>()
    private var dropped=0
    private var ignored=0
    private var ended=false
    private val watchdog=Executors.newSingleThreadScheduledExecutor {task -> Thread(task,"tor-control-deadline").apply {isDaemon=true}}
    private var reader:Thread?=null
    private var expiry:java.util.concurrent.ScheduledFuture<*>?=null
    init {
        require(uk.co.traynor.privategallery.BuildConfig.DEBUG)
        require(fixtureHost.matches(Regex("[a-z2-7]{56}\\.onion")))
        expiry=watchdog.schedule({mark("deadline");runCatching {socket.close()}},240,TimeUnit.SECONDS)
        try {
            // Pinned tor-android TorService uses getDir("TorService",MODE_PRIVATE)/data/ControlSocket.
            // Its own controller uses empty AUTHENTICATE; no TCP control listener is added.
            socket.connect(LocalSocketAddress(java.io.File(context.getDir("TorService",0),"data/ControlSocket").path,LocalSocketAddress.Namespace.FILESYSTEM))
            socket.soTimeout=5000
            val input=socket.inputStream
            command(input,"AUTHENTICATE")
            command(input,"SETEVENTS STREAM CIRC HS_DESC")
            mark("subscribed")
            socket.soTimeout=0
            reader=Thread({
                try {
                    while(true) {
                        val raw=line(input) ?: break
                        observe(raw)
                    }
                } catch(_:Exception) {mark("reader_ended")}
            },"tor-control-observer").apply {isDaemon=true;start()}
        } catch(failure:Exception) {
            expiry?.cancel(false);runCatching {socket.close()};watchdog.shutdownNow()
            // Fixed message only: never include Unix path or raw reply in exceptions.
            throw AssertionError("Test-only Tor control subscription failed",null)
        }
    }
    private fun command(input:InputStream,value:String) {
        require(value=="AUTHENTICATE" || value=="SETEVENTS STREAM CIRC HS_DESC")
        socket.outputStream.write((value+"\r\n").toByteArray(Charsets.US_ASCII));socket.outputStream.flush()
        repeat(64) {
            val reply=line(input) ?: error("control closed")
            if(reply=="250 OK")return
            if(reply.startsWith("650 ")||reply.startsWith("650-"))observe(reply)
            else error("control rejected")
        }
        error("control acknowledgment missing")
    }
    private fun line(input:InputStream):String? {
        val bytes=java.io.ByteArrayOutputStream()
        while(true) {
            val next=input.read()
            if(next<0)return if(bytes.size()==0)null else error("incomplete control line")
            if(next==10) return bytes.toString("US-ASCII").removeSuffix("\r")
            if(bytes.size()>=8192)error("control line too long")
            bytes.write(next)
        }
    }
    @Synchronized private fun observe(raw:String) {
        val summary=TorControlEventSummary.summarize(raw)
        if(summary.isEmpty()) {ignored++;return}
        // Match only in memory, without saving target, fingerprints or circuit paths.
        val parts=raw.replaceFirst("650-","650 ").split(' ')
        val fixture=when(parts.getOrNull(1)) {
            "STREAM" -> parts.getOrNull(5)=="$fixtureHost:80"
            "HS_DESC" -> parts.getOrNull(3)==fixtureHost.removeSuffix(".onion") || parts.getOrNull(3)==fixtureHost
            "CIRC" -> parts.contains("REND_QUERY="+fixtureHost.removeSuffix(".onion"))
            else -> false
        }
        record("$summary fixture=$fixture")
    }
    @Synchronized fun mark(phase:String) {
        require(phase.matches(Regex("[a-z_]{1,40}")))
        record("phase=$phase")
    }
    @Synchronized private fun record(value:String) {
        if(ended)return
        if(records.size<4096)records.add("elapsedMs=${SystemClock.elapsedRealtime()-started} $value")else dropped++
    }
    override fun close() {
        expiry?.cancel(false);watchdog.shutdownNow()
        runCatching {socket.close()}
        reader?.join(1500)
        synchronized(this) {
            ended=true
            records.forEach {println("Tor control observation: $it")}
            println("Tor control observation: events=${records.size} dropped=$dropped ignored=$ignored readerAlive=${reader?.isAlive==true}")
        }
    }
}
