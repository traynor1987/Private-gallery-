package uk.co.traynor.privategallery.core.evidence

import android.content.Context
import org.json.JSONArray
import uk.co.traynor.privategallery.core.home2.SpaceContext
import java.time.Instant
import java.security.MessageDigest

/** Device-local annotations are keyed to the exact OCR or correction revision that was reviewed. */
internal class EvidenceReview68(context: Context, access: EvidenceAccess) : AutoCloseable {
    private val space = SpaceContext(context, access)
    private val preferences = space.getSharedPreferences("evidence-chronology-68", Context.MODE_PRIVATE)

    fun confirmed(): Map<EvidenceChronology68.MessageLocation, Instant> = buildMap {
        for ((key, value) in preferences.all) {
            if (!key.startsWith("time-")) continue
            val row = JSONArray(value as String)
            put(EvidenceChronology68.MessageLocation(row.getString(0), row.getString(1), row.getInt(2), row.getInt(3)),
                Instant.ofEpochMilli(row.getLong(4)))
        }
    }

    fun skipped(): Set<String> = preferences.all.keys.filter { it.startsWith("skip-") }.toSet()

    fun confirm(location: EvidenceChronology68.MessageLocation, timestamp: Long) {
        require(timestamp > 0)
        val row = JSONArray(listOf(location.evidenceId, location.transcriptVersion, location.startLine, location.endLine))
        val key = "time-" + fingerprint(row.toString())
        row.put(timestamp)
        check(preferences.edit().putString(key, row.toString()).commit())
    }

    fun skip(review: EvidenceChronology68.Review) {
        check(preferences.edit().putBoolean(key(review), true).commit())
    }

    override fun close() = space.close()

    companion object {
        fun key(review: EvidenceChronology68.Review): String {
            val location = review.location
            return "skip-" + fingerprint(JSONArray(listOf(review.reason.name, review.evidenceIds.sorted().joinToString(","),
                location?.evidenceId, location?.transcriptVersion, location?.startLine, location?.endLine)).toString())
        }

        private fun fingerprint(value: String): String = MessageDigest.getInstance("SHA-256")
            .digest(value.encodeToByteArray()).joinToString("") { "%02x".format(it.toInt() and 255) }
    }
}
