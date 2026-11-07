package uk.co.traynor.privategallery.home2

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

/** Decimal storage units. The formatter never changes the byte count held by the archive. */
internal fun backupSizeDisplay(bytes: Long, locale: Locale = Locale.getDefault()): String {
    require(bytes >= 0)
    if (bytes == 0L) return "0 KB"
    if (bytes < 1_000L) return "<1 KB"

    val units = listOf("KB", "MB", "GB")
    var size = BigDecimal.valueOf(bytes).movePointLeft(3)
    var unit = 0
    while (unit < units.lastIndex && size.setScale(1, RoundingMode.HALF_UP) >= BigDecimal(1_000)) {
        size = size.movePointLeft(3)
        unit++
    }
    val display = NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 1 }
    return "${display.format(size.setScale(1, RoundingMode.HALF_UP))} ${units[unit]}"
}
