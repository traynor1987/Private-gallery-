package uk.co.traynor.privategallery.core.browser.hidden

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle

/** Debug-only observer. A real browser Activity starts the nonsticky observer in its own process. */
class HiddenContinuityFixtureApplication:Application(),Application.ActivityLifecycleCallbacks {
    private var started=false
    var current:Activity?=null;private set
    private val probing=java.util.concurrent.atomic.AtomicBoolean()
    fun startProbe() {
        check(current?.getSystemService(android.net.ConnectivityManager::class.java)?.boundNetworkForProcess!=null)
        if(!probing.compareAndSet(false,true))return
        Thread({
            val packet=byteArrayOf(0,1,0,0,0x21,0x12,0xa4.toByte(),0x42)+"nv17103probe".toByteArray(Charsets.US_ASCII)
            val destination=java.net.InetAddress.getByAddress(byteArrayOf(203.toByte(),0,113,10))
            val until=android.os.SystemClock.elapsedRealtime()+180_000
            while(probing.get() && android.os.SystemClock.elapsedRealtime()<until) {
                // Intentionally no authority/capability pre-check: prove the process pin blocks attempted native egress.
                runCatching {java.net.DatagramSocket().use {it.send(java.net.DatagramPacket(packet,packet.size,destination,3478))}}
                try {Thread.sleep(100)}catch(_:InterruptedException){break}
            }
        },"debug-continuity-native-probe").apply {isDaemon=true;start()}
    }
    override fun onCreate(){super.onCreate();if(getProcessName().endsWith(":hidden_vpn_browser"))registerActivityLifecycleCallbacks(this)}
    override fun onActivityResumed(activity:Activity) {
        if(activity is HiddenVpnBrowserActivity)current=activity
        if(!started && activity is HiddenVpnBrowserActivity) {
            started=true
            activity.startService(Intent(activity,HiddenContinuityFixtureService::class.java))
        }
    }
    override fun onActivityCreated(a:Activity,b:Bundle?){}
    override fun onActivityStarted(a:Activity){}
    override fun onActivityPaused(a:Activity){}
    override fun onActivityStopped(a:Activity){}
    override fun onActivitySaveInstanceState(a:Activity,b:Bundle){}
    override fun onActivityDestroyed(a:Activity){if(current===a){current=null;probing.set(false)}}
}
