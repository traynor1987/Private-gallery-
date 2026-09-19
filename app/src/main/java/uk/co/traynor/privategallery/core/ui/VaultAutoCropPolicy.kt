package uk.co.traynor.privategallery.core.ui

import kotlin.math.abs
import kotlin.math.max

/** Conservative, testable confidence gate for local edge-band detection. */
object VaultAutoCropPolicy {
    fun isConfidentEdgeBand(meanLuma: Int, minLuma: Int, maxLuma: Int, interiorLuma: Int): Boolean {
        val uniform = maxLuma - minLuma <= 18
        val extreme = meanLuma <= 24 || meanLuma >= 231
        val contrastingNeutral = (meanLuma <= 64 || meanLuma >= 200) && abs(meanLuma - interiorLuma) >= 55
        return uniform && (extreme || contrastingNeutral) && abs(meanLuma - interiorLuma) >= 24
    }

    fun minimumMeaningfulBand(axisPixels: Int): Int = max(2, (axisPixels * .025f).toInt())
}
