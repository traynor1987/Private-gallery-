package uk.co.traynor.privategallery.launcher

import org.json.JSONObject
import java.net.URI
import java.time.Instant
import java.util.Locale

internal fun cloudTeacherEndpoint(value: String): URI {
    val uri = URI(value.trim())
    require(uri.scheme == "https" && uri.host != null && uri.rawUserInfo == null &&
        uri.rawQuery == null && uri.rawFragment == null && uri.rawPath == "/guardian/analyze")
    return uri
}

internal fun cloudTeacherStatusEndpoint(endpoint: URI): URI =
    cloudTeacherEndpoint(endpoint.toString()).resolve("/guardian/status")

internal fun cloudTeacherStatusLines(status: JSONObject): List<String> = buildList {
    require(status.getString("status") == "authenticated")
    val model = status.getString("model")
    val staging = model == "staging"
    val enabled = status.getBoolean("analysis_enabled") && !staging
    add(if (staging) "Staging · analysis disabled" else if (enabled) "Cloud analysis enabled" else "Cloud analysis disabled")
    add("Model: ${model.take(100)}")
    val budget = status.optJSONObject("budget")
    if (budget == null || staging) {
        add("No funded-cycle report available")
        return@buildList
    }
    fun money(key: String, source: JSONObject = budget): String =
        if (source.has(key) && !source.isNull(key)) {
            String.format(Locale.US, "\$%.6f", source.getLong(key).coerceAtLeast(0) / 1_000_000.0)
        } else "Unavailable"
    add("Configured funded allowance: ${money("funded_allowance_micro_usd")}")
    add("Configured maximum spending: ${money("configured_spending_limit_micro_usd")}")
    add("Effective funded-cycle limit: ${money("configured_ceiling_micro_usd")}")
    add("Reserved this cycle: ${money("reserved_upper_bound_micro_usd")}")
    add("Remaining allowance: ${money("remaining_micro_usd")}")
    add("Reservation per request: ${money("request_reservation_micro_usd")}")
    val expiry = budget.optLong("expires_at", 0)
    if (expiry > 0) add("Expires: ${Instant.ofEpochSecond(expiry)}")
    val reserve = budget.optLong("request_reservation_micro_usd", -1)
    val remaining = budget.optLong("remaining_micro_usd", -1)
    val available = when {
        !enabled || budget.optBoolean("expired", true) -> "No"
        reserve <= 0 || remaining < 0 -> "Unknown"
        remaining >= reserve -> "Yes, subject to server checks"
        else -> "No"
    }
    add("Budget permits another request: $available")
    budget.optJSONObject("reservation_activity_utc")?.let { activity ->
        add("Today (UTC): ${money("daily_reserved_micro_usd", activity)} · ${activity.getLong("daily_requests")} requests")
        add("Month to date (UTC): ${money("monthly_reserved_micro_usd", activity)} · ${activity.getLong("monthly_requests")} requests")
    }
    add("Reservations are conservative estimates. Confirmed OpenAI charges are unavailable here.")
}

internal fun cloudTeacherApprovedPayload(message: String, approved: Boolean): ByteArray {
    require(approved && message.length in 1..4096 && message.isNotBlank())
    return JSONObject().put("message", message).put("consent", true).toString().toByteArray(Charsets.UTF_8)
}
