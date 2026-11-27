package uk.co.traynor.privategallery.novaos2

import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.json.JSONObject

internal enum class NovaLockWallpaper(val title: String, val shades: List<Long>) {
    INDIGO_HOUR("Indigo Hour", listOf(0xFF071023, 0xFF24386C, 0xFF075468)),
    EMBER_DUSK("Ember Dusk", listOf(0xFF160E25, 0xFF442842, 0xFF6F3B3C)),
    OCEAN_GLASS("Ocean Glass", listOf(0xFF061E24, 0xFF1B454B, 0xFF28425D)),
}

internal enum class NovaLockClock(val title: String) {
    STUDIO("Studio"),
    EDITORIAL("Editorial"),
    DIGITAL("Digital"),
}

internal enum class NovaLockColour(val title: String, val argb: Long) {
    FROST("Frost", 0xFFF2F7FF),
    MINT("Mint", 0xFFB5F8E6),
    AMBER("Amber", 0xFFFFDDB1),
}

internal data class NovaLockStyle(
    val wallpaper: NovaLockWallpaper = NovaLockWallpaper.INDIGO_HOUR,
    val clock: NovaLockClock = NovaLockClock.STUDIO,
    val colour: NovaLockColour = NovaLockColour.FROST,
    val showNotificationExample: Boolean = false,
    val showBattery: Boolean = true,
    val ambientPreview: Boolean = false,
    val showCalendarDate: Boolean = false,
    val showMediaStatus: Boolean = false,
) {
    fun encode() = JSONObject().put("version", 1).put("wallpaper", wallpaper.name).put("clock", clock.name)
        .put("colour", colour.name).put("notifications", showNotificationExample).put("battery", showBattery)
        .put("ambient", ambientPreview).put("date", showCalendarDate).put("media", showMediaStatus).toString()

    companion object {
        fun decode(encoded: String): NovaLockStyle {
            val json = JSONObject(encoded)
            require(json.get("version") == 1)
            return NovaLockStyle(
                NovaLockWallpaper.valueOf(json.getString("wallpaper")),
                NovaLockClock.valueOf(json.getString("clock")),
                NovaLockColour.valueOf(json.getString("colour")),
                json.getBoolean("notifications"),
                json.getBoolean("battery"),
                json.getBoolean("ambient"),
                if (json.has("date")) json.getBoolean("date") else false,
                if (json.has("media")) json.getBoolean("media") else false,
            )
        }
    }
}

internal fun lockPreviewTime(time: LocalTime, uses24Hour: Boolean, locale: Locale): String =
    time.format(DateTimeFormatter.ofPattern(if (uses24Hour) "HH:mm" else "h:mm a", locale))
