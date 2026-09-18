package uk.co.traynor.privategallery.core.update

import java.security.MessageDigest
import java.net.HttpURLConnection
import java.net.URL
import java.net.URI
import org.json.JSONObject

data class ReleaseVersion(val raw: String, val parts: List<Int>) : Comparable<ReleaseVersion> {
    override fun compareTo(other: ReleaseVersion): Int {
        val length = maxOf(parts.size, other.parts.size)
        for (index in 0 until length) {
            val comparison = (parts.getOrElse(index) { 0 }).compareTo(other.parts.getOrElse(index) { 0 })
            if (comparison != 0) return comparison
        }
        return 0
    }

    companion object {
        fun parse(value: String): ReleaseVersion {
            val clean = value.removePrefix("v")
            require(clean.matches(Regex("\\d+(\\.\\d+){0,2}"))) { "Invalid release version" }
            return ReleaseVersion(clean, clean.split('.').map(String::toInt))
        }
    }
}

data class ReleaseMetadata(
    val version: ReleaseVersion,
    val apkUrl: String,
    val sha256Url: String,
) {
    companion object {
        fun parse(json: String): ReleaseMetadata {
            val release = JSONObject(json)
            require(!release.optBoolean("draft") && !release.optBoolean("prerelease")) { "Release is not installable" }
            val assets = release.optJSONArray("assets") ?: error("Release assets are missing")
            var apkUrl: String? = null
            var shaUrl: String? = null
            for (index in 0 until assets.length()) {
                val asset = assets.getJSONObject(index)
                when (asset.optString("name")) {
                    "private-gallery-release.apk" -> apkUrl = asset.optString("browser_download_url").takeIf(String::isNotBlank)
                    "private-gallery-release.apk.sha256" -> shaUrl = asset.optString("browser_download_url").takeIf(String::isNotBlank)
                }
            }
            return ReleaseMetadata(
                version = ReleaseVersion.parse(release.getString("tag_name")),
                apkUrl = requireNotNull(apkUrl) { "Release APK is missing" }.also(::requireOfficialAsset),
                sha256Url = requireNotNull(shaUrl) { "Release SHA-256 is missing" }.also(::requireOfficialAsset),
            )
        }

        private fun requireOfficialAsset(url: String) {
            val uri = URI(url)
            require(uri.scheme == "https" && uri.host == "github.com") { "Untrusted release asset" }
            require(uri.path.startsWith("/traynor1987/Private-gallery-/releases/download/")) { "Untrusted release asset" }
        }
    }
}

sealed interface UpdateCheck {
    data class UpToDate(val release: ReleaseMetadata) : UpdateCheck
    data class Available(val release: ReleaseMetadata) : UpdateCheck
    data class Failed(val reason: UpdateFailure) : UpdateCheck
}

enum class UpdateFailure(val userMessage: String) {
    NETWORK_ERROR("Network error"),
    GITHUB_API_ERROR("GitHub/API error"),
    NO_VALID_RELEASE("No valid release"),
    INVALID_RELEASE_METADATA("Invalid release metadata"),
    MISSING_ASSET("Missing release asset"),
    PARSE_ERROR("Release parse error"),
}

class GithubHttpException(val statusCode: Int) : java.io.IOException("GitHub returned HTTP $statusCode")

fun interface ReleaseTransport { fun fetch(url: String): ByteArray }

class GithubReleaseUpdateService(
    private val transport: ReleaseTransport = ReleaseTransport { url ->
        (URL(url).openConnection() as HttpURLConnection).run {
            connectTimeout = 15_000
            readTimeout = 30_000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/vnd.github+json")
            if (responseCode !in 200..299) throw GithubHttpException(responseCode)
            inputStream.use { it.readBytes() }
        }
    },
) {
    fun check(installedVersion: String): UpdateCheck {
        val response = try {
            transport.fetch(LATEST_RELEASE_URL).decodeToString()
        } catch (error: Throwable) {
            return UpdateCheck.Failed(error.asUpdateFailure())
        }
        val release = try {
            ReleaseMetadata.parse(response)
        } catch (error: Throwable) {
            return UpdateCheck.Failed(error.asUpdateFailure())
        }
        return try {
            if (release.version > ReleaseVersion.parse(installedVersion)) UpdateCheck.Available(release) else UpdateCheck.UpToDate(release)
        } catch (_: Throwable) {
            UpdateCheck.Failed(UpdateFailure.PARSE_ERROR)
        }
    }

    fun downloadVerified(release: ReleaseMetadata): ByteArray? = runCatching {
        val apk = transport.fetch(release.apkUrl)
        val expected = transport.fetch(release.sha256Url).decodeToString()
        apk.takeIf { UpdateVerifier.matchesSha256(it, expected) }
    }.getOrNull()

    private companion object {
        const val LATEST_RELEASE_URL = "https://api.github.com/repos/traynor1987/Private-gallery-/releases/latest"
    }
}

private fun Throwable.asUpdateFailure(): UpdateFailure = when (this) {
    is GithubHttpException -> if (statusCode == 404) UpdateFailure.NO_VALID_RELEASE else UpdateFailure.GITHUB_API_ERROR
    is java.io.IOException -> UpdateFailure.NETWORK_ERROR
    is org.json.JSONException -> UpdateFailure.PARSE_ERROR
    is IllegalArgumentException -> when {
        message?.contains("missing", ignoreCase = true) == true -> UpdateFailure.MISSING_ASSET
        message?.contains("installable", ignoreCase = true) == true -> UpdateFailure.NO_VALID_RELEASE
        else -> UpdateFailure.INVALID_RELEASE_METADATA
    }
    else -> UpdateFailure.INVALID_RELEASE_METADATA
}

object UpdateVerifier {
    fun matchesSha256(bytes: ByteArray, expected: String): Boolean =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            .equals(expected.trim().lowercase().substringBefore(' '), ignoreCase = true)
}
