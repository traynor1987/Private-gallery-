package uk.co.traynor.privategallery.core.sanctuary

import java.net.IDN
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale

/** Exact known app identities and domain suffixes. Labels and page content are never inspected. */
internal object SanctuaryWhatsApp {
    private val appIds = setOf(
        "chat", "chat-inspector", "chat-settings", "chat-lite", "nova-chat-lite",
        "whatsapp", "whatsapp-web", "com.whatsapp", "com.whatsapp.w4b",
    )
    private val domains = setOf("whatsapp.com", "whatsapp.net", "wa.me")
    private val routePrefixes = listOf("resume-internal:", "store:", "web:")
    private val schemePattern = Regex("^[A-Za-z][A-Za-z0-9+.-]*:")
    private val leadingSlashes = Regex("^/+")

    fun isRelatedApp(id: String): Boolean {
        var identity = id.lowercase(Locale.ROOT)
        while (true) {
            val prefix = routePrefixes.firstOrNull(identity::startsWith) ?: break
            identity = identity.removePrefix(prefix)
        }
        return identity in appIds
    }

    fun isRelatedUrl(raw: String): Boolean {
        val value = raw.trim { it <= ' ' }.replace("\t", "").replace("\r", "").replace("\n", "")
        val scheme = schemePattern.find(value)?.value?.dropLast(1)?.lowercase(Locale.ROOT)
        if (scheme == "whatsapp") return true
        if (scheme == "intent") {
            val intent = value.substringAfter("#Intent;", "")
            return intent.split(';').any {
                it.equals("scheme=whatsapp", true) || it.equals("package=com.whatsapp", true) ||
                    it.equals("package=com.whatsapp.w4b", true)
            }
        }
        if (scheme != null && scheme !in setOf("http", "https")) return false
        return runCatching {
            // Chromium accepts backslashes and redundant leading slashes in HTTP(S) URLs.
            val address = (if (scheme == null) value else value.substringAfter(':'))
                .replace('\\', '/').replace(leadingSlashes, "")
            val authority = URI("https://$address").rawAuthority ?: return false
            val rawHost = authority.substringAfterLast('@').substringBeforeLast(':')
            val decoded = URLDecoder.decode(rawHost, StandardCharsets.UTF_8.name())
            val host = IDN.toASCII(decoded, IDN.USE_STD3_ASCII_RULES).trimEnd('.').lowercase(Locale.ROOT)
            domains.any { host == it || host.endsWith(".$it") }
        }.getOrDefault(false)
    }
}
