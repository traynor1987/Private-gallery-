package uk.co.traynor.privategallery.novaos2

import android.content.pm.PackageInfo
import org.json.JSONArray
import org.json.JSONObject

internal data class NovaObservedVersion(val version: String, val build: Long, val installedAt: Long)

/** Records only the installed package metadata that Nova Utilities actually observes. */
internal object NovaUpdateHistory {
    private const val LIMIT = 12

    fun read(config: NovaSystemConfig): List<NovaObservedVersion> {
        val saved = config.read() ?: return emptyList()
        val value = JSONArray(saved)
        if (value.length() > LIMIT) return emptyList()
        return List(value.length()) { index ->
            val entry = value.getJSONObject(index)
            NovaObservedVersion(entry.getString("version"), entry.getLong("build"), entry.getLong("installedAt"))
        }.filter(::valid)
    }

    @Synchronized
    fun recordObserved(config: NovaSystemConfig, installed: PackageInfo): List<NovaObservedVersion> {
        val existing = read(config)
        val entry = NovaObservedVersion(installed.versionName.orEmpty(), installed.longVersionCode, installed.lastUpdateTime)
        if (!valid(entry) || entry in existing) return existing
        val history = (listOf(entry) + existing).take(LIMIT)
        val json = JSONArray()
        for (update in history) json.put(JSONObject().put("version", update.version).put("build", update.build)
            .put("installedAt", update.installedAt))
        config.write(json.toString())
        return history
    }

    private fun valid(entry: NovaObservedVersion) = entry.version.length in 1..80 &&
        entry.version.none(Char::isISOControl) && entry.build > 0 && entry.installedAt > 0
}
