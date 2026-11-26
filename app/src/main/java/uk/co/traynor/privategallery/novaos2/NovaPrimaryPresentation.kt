package uk.co.traynor.privategallery.novaos2

import android.content.Context
import android.os.SystemClock
import androidx.compose.runtime.Composable
import java.util.UUID
import uk.co.traynor.privategallery.core.home2.SpaceContext
import uk.co.traynor.privategallery.privateWorkspaceEntryIntent

internal typealias NovaPrimaryContent = @Composable (SpaceContext, () -> Unit, () -> Boolean) -> Unit

internal object NovaPrimaryPresentation {
    private const val EXPIRY_MS = 60_000L
    private const val PREFIX = "uk.co.traynor.privategallery.action.NOVA_PRIMARY."
    private class Ticket(val action: String, val created: Long, val content: NovaPrimaryContent)
    private var ticket: Ticket? = null

    fun identifies(action: String?) = action?.startsWith(PREFIX) == true

    @Synchronized
    fun offer(content: NovaPrimaryContent): String {
        val action = PREFIX + UUID.randomUUID()
        val next = Ticket(action, SystemClock.elapsedRealtime(), content)
        ticket = next
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ discard(action) }, EXPIRY_MS)
        return action
    }

    @Synchronized
    fun take(action: String?): NovaPrimaryContent? {
        val offered = ticket ?: return null
        if (action != offered.action) return null
        ticket = null
        return offered.content.takeIf { SystemClock.elapsedRealtime() - offered.created in 0 until EXPIRY_MS }
    }

    @Synchronized
    fun discard(action: String?) {
        if (ticket?.action == action) ticket = null
    }
}

internal fun openNovaPrimaryPresentation(context: Context, content: NovaPrimaryContent): Boolean {
    val action = NovaPrimaryPresentation.offer(content)
    return runCatching { context.startActivity(privateWorkspaceEntryIntent(context).setAction(action)) }.isSuccess
        .also { if (!it) NovaPrimaryPresentation.discard(action) }
}
