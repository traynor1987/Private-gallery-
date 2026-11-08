package uk.co.traynor.privategallery.core.sanctuary

/** Short owner authorisations never survive process death or an unrelated Sanctuary session. */
internal class SanctuaryTemporaryAccess70(private val clock: () -> Long) {
    class Request internal constructor(val session: String, val packageName: String?, val expiresAt: Long)

    private data class Permit(val session: String, val expiresAt: Long)

    private var pending: Request? = null
    private var pause: Permit? = null
    private val apps = mutableMapOf<String, Permit>()

    @Synchronized fun request(session: String, packageName: String?): Request =
        Request(session, packageName, clock() + REQUEST_DURATION).also { pending = it }

    @Synchronized fun complete(request: Request, activeSession: String?, confirmed: Boolean): Boolean {
        if (pending !== request) return false
        pending = null
        val now = clock()
        if (!confirmed || activeSession != request.session || now >= request.expiresAt) return false
        val permit = Permit(request.session, now + ACCESS_DURATION)
        if (request.packageName == null) pause = permit else {
            apps.entries.removeAll { (_, value) -> value.session != request.session || value.expiresAt <= now }
            apps[request.packageName] = permit
        }
        return true
    }

    @Synchronized fun paused(session: String): Boolean =
        pause?.let { it.session == session && clock() < it.expiresAt } == true

    @Synchronized fun packages(session: String): Set<String> {
        val now = clock()
        return apps.filterValues { it.session == session && now < it.expiresAt }.keys
    }

    companion object {
        const val ACCESS_DURATION = 5 * 60_000L
        private const val REQUEST_DURATION = 60_000L
    }
}
