package uk.co.traynor.privategallery.core.browser.hidden

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import uk.co.traynor.privategallery.core.home.HomeLayout
import uk.co.traynor.privategallery.core.home.NovaNavigation
import uk.co.traynor.privategallery.core.home.NovaReleaseFeatures

/** Disabled Tor must reject even explicit stale/service launch attempts. No Tor circuit is started. */
class TorDeferral17103Test {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private fun denied(action:()->Unit) {
        try {action();fail("Deferred Tor entry admitted")} catch(_:SecurityException) { }
    }
    @Test fun servicesCannotStartOrBindWhileDisabled() {
        for(type in listOf(HiddenTorService::class.java,HiddenTorIsolationService::class.java)) {
            val component=ComponentName(context,type)
            val info=context.packageManager.getServiceInfo(component,PackageManager.ComponentInfoFlags.of(PackageManager.MATCH_DISABLED_COMPONENTS.toLong()))
            assertFalse(info.enabled)
            val intent=Intent().setComponent(component)
            try {assertNull(context.startService(intent))}catch(_:SecurityException){}catch(_:IllegalStateException){}
            val connection=object:ServiceConnection {
                override fun onServiceConnected(name:ComponentName,service:IBinder) {fail("Disabled Tor service bound")}
                override fun onServiceDisconnected(name:ComponentName) { }
            }
            try {assertFalse(context.bindService(intent,connection,Context.BIND_AUTO_CREATE))}catch(_:SecurityException){}
        }
        assertFalse(context.getSystemService(ActivityManager::class.java).runningAppProcesses.orEmpty().any {it.processName==context.packageName+":hidden_tor"})
    }
    @Test fun launcherAndTransportFactoriesRejectTorBeforeNetworkCreation() {
        assertFalse(NovaNavigation.available(HomeLayout.Shortcut.TOR_BROWSER,true))
        assertFalse(NovaNavigation.available(HomeLayout.Shortcut.TOR_BROWSER,false))
        assertFalse(NovaReleaseFeatures.browserModeAllowed("TOR_ONLY"))
        assertTrue(NovaReleaseFeatures.browserModeAllowed("VPN_ONLY"))
        denied {HiddenTorConnection(context).start()}
        denied {HiddenTorRelayBridge({fail("Socket protection called");false},{true})}
        denied {HiddenHttpTransport.tor(1){true}}
        denied {HiddenVpnProxy.tor("/nonexistent",{true})}
        denied {uk.co.traynor.privategallery.core.evidence.EvidenceNetwork.tor(context,"/nonexistent",{true})}
    }
}
