package uk.co.traynor.privategallery.home2

import java.net.URI

internal data class NovaStoreRelease(
    val packageName: String,
    val versionCode: Long,
    val versionName: String,
    val certificate: String,
    val sha256: String,
    val url: String,
) {
    init {
        require(packageName.matches(Regex("[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+")))
        require(versionName.isNotBlank())
        require(versionCode > 0 && certificate.matches(sha256Format) && sha256.matches(sha256Format))
        require(trustedNovaReleaseUrl(url, initial = true))
    }

    fun accepts(
        packageName: String,
        versionCode: Long,
        versionName: String?,
        signers: List<String>,
        installedCode: Long?,
    ): Boolean =
        this.packageName == packageName && this.versionCode == versionCode &&
            this.versionName == versionName && signers == listOf(certificate) &&
            (installedCode == null || versionCode > installedCode)

    private companion object {
        val sha256Format = Regex("[0-9a-f]{64}")
    }
}

internal fun trustedNovaReleaseUrl(url: String, initial: Boolean): Boolean {
    val uri = runCatching { URI(url) }.getOrNull() ?: return false
    if (uri.scheme != "https" || uri.userInfo != null || uri.fragment != null || uri.port != -1) return false
    return when (uri.host) {
        "github.com" -> uri.path.startsWith("/traynor1987/") && "/releases/download/" in uri.path &&
            uri.normalize().path == uri.path && uri.query == null
        "release-assets.githubusercontent.com" -> !initial
        else -> false
    }
}
