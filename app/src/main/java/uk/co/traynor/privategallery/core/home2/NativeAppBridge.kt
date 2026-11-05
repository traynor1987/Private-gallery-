package uk.co.traynor.privategallery.core.home2

import java.net.URI
import java.util.Locale
import java.util.UUID
import uk.co.traynor.privategallery.core.privateapps.PrivateAppCatalog
import uk.co.traynor.privategallery.core.privateapps.PrivateAppPolicy
import uk.co.traynor.privategallery.core.privateapps.PrivateAppStore

/** Home holds catalogue authority only. Launching still belongs to the production browser controller. */
interface NativeAppsBridge {
    fun list(): List<PrivateAppCatalog.App>
    fun install(name: String, url: String): PrivateAppCatalog.App
    fun uninstall(id: String)
    fun launch(id: String)
    fun close(id: String)
    fun browse(url: String)
}

internal class NativeAppBridge(
    private val store: PrivateAppStore,
    private val current: () -> Boolean,
    private val launchApp: (String) -> Unit,
    private val closeApp: (String) -> Unit,
    private val browseUrl: (String) -> Unit,
) : NativeAppsBridge {
    init { require(store.owner() == PrivateAppStore.Owner.PRIMARY) }
    private fun checkCurrent() = check(current()) { "Unlock Primary Space to use private apps." }
    override fun list(): List<PrivateAppCatalog.App> {
        checkCurrent()
        return store.read().apps.also { checkCurrent() }
    }
    override fun install(name: String, url: String): PrivateAppCatalog.App {
        checkCurrent()
        require(PrivateAppPolicy.validName(name)) { "Enter an app name." }
        val canonical = canonicalUrl(url)
        var installed: PrivateAppCatalog.App? = null
        store.update { catalog ->
            checkCurrent()
            val existing = catalog.apps.firstOrNull { canonicalUrl(it.startUrl()) == canonical }
            // Reinstall is idempotent: preserve UUID, authentication, icon, order and all app settings.
            installed = existing ?: PrivateAppCatalog.App(UUID.randomUUID().toString(), name,
                canonical, "", "browser", "", false, false, false)
            if (existing != null) catalog else catalog.upsert(checkNotNull(installed))
        }
        checkCurrent()
        return checkNotNull(installed)
    }
    override fun uninstall(id: String) {
        checkCurrent()
        requireId(id)
        store.update { checkCurrent(); it.remove(id) }
        checkCurrent()
        closeApp(id)
    }
    override fun launch(id: String) {
        checkCurrent()
        requireId(id)
        check(list().any { it.id() == id }) { "This private app is no longer installed." }
        launchApp(id)
    }
    override fun close(id: String) {
        checkCurrent()
        requireId(id)
        closeApp(id)
    }
    override fun browse(url: String) {
        checkCurrent()
        browseUrl(canonicalUrl(url))
    }
    private fun requireId(id: String) = require(id.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")))
    companion object {
        internal fun canonicalUrl(url: String): String {
            require(PrivateAppPolicy.safeUrl(url, false)) { "Enter a valid HTTPS app address." }
            val uri = URI(url).normalize()
            val port = if (uri.port == 443) -1 else uri.port
            // Retain raw path/query encoding; decoding and re-encoding changes app addresses.
            val authority = uri.host.lowercase(Locale.ROOT).let { if (':' in it && !it.startsWith("[")) "[$it]" else it } +
                if (port == -1) "" else ":$port"
            return "https://$authority${uri.rawPath.ifEmpty { "/" }}" +
                (uri.rawQuery?.let { "?$it" } ?: "") + (uri.rawFragment?.let { "#$it" } ?: "")
        }
    }
}
