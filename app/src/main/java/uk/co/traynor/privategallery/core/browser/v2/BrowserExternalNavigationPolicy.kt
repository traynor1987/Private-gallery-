package uk.co.traynor.privategallery.core.browser.v2

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Converts a page-requested app link into a fresh, browsable Android intent. The original intent
 * is never forwarded: explicit components, packages, flags, extras and URI grants are discarded.
 */
data class BrowserExternalNavigationPlan(
    val openIntent: Intent?,
    val httpsFallback: String?,
)

object BrowserExternalNavigationPolicy {
    fun isCandidate(value: String): Boolean = runCatching {
        when (value.substringBefore(':', missingDelimiterValue = "").lowercase()) {
            "intent", "mailto", "tel" -> true
            else -> false
        }
    }.getOrDefault(false)

    fun plan(context: Context, value: String): BrowserExternalNavigationPlan {
        val parsed = runCatching { Uri.parse(value) }.getOrNull()
            ?: return BrowserExternalNavigationPlan(null, null)
        val parsedIntent = value.takeIf { parsed.scheme.equals("intent", ignoreCase = true) }
            ?.let { runCatching { Intent.parseUri(it, Intent.URI_INTENT_SCHEME) }.getOrNull() }
        val fallback = intentFallback(parsedIntent)
            ?: parsedIntent?.data?.toString()?.takeIf(BrowserSecurityPolicy::allowsNavigation)
        val externalUri = when (parsed.scheme?.lowercase()) {
            "mailto", "tel" -> parsed
            "intent" -> parsedIntent?.data?.takeUnless { BrowserSecurityPolicy.allowsNavigation(it.toString()) }
            else -> null
        }
        val cleanIntent = externalUri?.let { uri ->
            Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE)
                .takeIf { it.resolveActivity(context.packageManager) != null }
        }
        return BrowserExternalNavigationPlan(cleanIntent, fallback)
    }

    private fun intentFallback(intent: Intent?): String? = intent
        ?.getStringExtra("browser_fallback_url")
            ?.takeIf(BrowserSecurityPolicy::allowsNavigation)
}
