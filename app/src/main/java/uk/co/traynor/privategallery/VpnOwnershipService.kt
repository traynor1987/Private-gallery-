package uk.co.traynor.privategallery

import android.app.Service
import android.content.Intent
import android.os.IBinder
import uk.co.traynor.privategallery.core.vpn.OwnedVpnTunnelRegistry

/**
 * Android delivers onTaskRemoved to a running Service when a known task is swiped away.
 * This is best-effort by platform design; it never claims a callback for arbitrary process death.
 */
class VpnOwnershipService : Service() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        OwnedVpnTunnelRegistry.disconnectOwnedTunnel()
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }
}
