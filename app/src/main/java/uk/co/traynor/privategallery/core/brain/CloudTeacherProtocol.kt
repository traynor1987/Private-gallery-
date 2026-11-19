package uk.co.traynor.privategallery.core.brain

import org.json.JSONObject
import java.math.BigDecimal
import java.util.UUID

private fun JSONObject.amount(key: String): Long? {
    if (!has(key) || isNull(key)) return null
    val value = get(key)
    require(value is Int || value is Long) { "Invalid budget amount" }
    return (value as Number).toLong().also { require(it >= 0) }
}
private fun money(value: Long?) = value?.let { "\$" + BigDecimal.valueOf(it, 6).toPlainString() } ?: "Unavailable"

/** Authenticated server values, never a provider invoice or a guessed account balance. */
internal data class CloudTeacherStatus(
    val model: String,
    val staging: Boolean,
    val analysisEnabled: Boolean,
    val fundedAllowance: Long?,
    val cycleLimit: Long?,
    val cycleCeiling: Long?,
    val remaining: Long?,
    val reservation: Long?,
    val monthlyLimit: Long?,
    val monthlyExposure: Long?,
    val monthlyRemaining: Long?,
    val usageReportedCost: Long?,
    val approvedRequestsRemaining: Long?,
    val expired: Boolean,
    val halted: Boolean,
    val hasBudget: Boolean,
    val dailyExposure: Long? = null,
    val dailyRequests: Long? = null,
    val monthlyRequests: Long? = null,
    val expiresAt: Long? = null,
    val automaticAllowed: Boolean = false,
) {
    val canAnalyze: Boolean get() = analysisEnabled && !staging && !expired && !halted &&
        model in setOf("gpt-5-mini", "gpt-5-mini-2025-08-07") &&
        fundedAllowance != null && cycleLimit != null && cycleCeiling != null &&
        cycleCeiling <= minOf(fundedAllowance, cycleLimit) &&
        monthlyLimit != null && monthlyLimit in 1L..100_000_000L && monthlyExposure != null &&
        reservation != null && reservation > 0 && remaining != null && remaining >= reservation &&
        remaining <= cycleCeiling && monthlyRemaining != null && monthlyRemaining >= reservation &&
        monthlyRemaining <= (monthlyLimit-monthlyExposure).coerceAtLeast(0) &&
        approvedRequestsRemaining != null && approvedRequestsRemaining > 0

    fun lines(): List<String> = buildList {
        add("Model: " + if (model in setOf("staging", "gpt-5-mini", "gpt-5-mini-2025-08-07", "unconfigured")) model else "Unrecognized configured model")
        if (!hasBudget || staging) {
            add("Spending and remaining allowance: Unavailable · funded ledger not loaded")
            add("Connecting does not enable paid analysis")
            return@buildList
        }
        add("Monthly maximum (UTC): ${money(monthlyLimit)}")
        add("Token-reported cost estimate this month: ${money(usageReportedCost)}")
        add("Monthly reserved/uncertain exposure: ${money(monthlyExposure)}")
        add("Remaining local allowance: ${money(remaining)}")
        add("Remaining monthly room: ${money(monthlyRemaining)}")
        add("Maximum reservation for one request: ${money(reservation)}")
        add("Approved requests remaining: ${approvedRequestsRemaining ?: "Unavailable"}")
        add("Configured funded allowance: ${money(fundedAllowance)}")
        add("Configured maximum spending: ${money(cycleLimit)}")
        add("Effective funded-cycle limit: ${money(cycleCeiling)}")
        add("Today (UTC): ${money(dailyExposure)} · ${dailyRequests ?: "Unavailable"} requests")
        add("Month to date (UTC): ${money(monthlyExposure)} · ${monthlyRequests ?: "Unavailable"} requests")
        if (expired) add("Funded allowance expired · no paid requests")
        if (halted) add("Paid requests halted · server budget/pricing review required")
        add("Budget permits another request: ${if (canAnalyze) "Yes, subject to server checks" else "No"}")
        add("Confirmed OpenAI charges are unavailable here")
        add("These are server ledger values and token-price estimates, not verified OpenAI billing or a provider credit balance")
    }
    companion object {
        fun parse(value: JSONObject): CloudTeacherStatus {
            require(value.getString("status") == "authenticated")
            val model = value.getString("model").also { require(it.length in 1..100) }
            val staging = model == "staging"
            require(value.get("analysis_enabled") is Boolean)
            val budget = value.optJSONObject("budget")
            val activity = budget?.optJSONObject("reservation_activity_utc")
            if (budget != null) {
                require(budget.get("expired") is Boolean)
                if (budget.has("paid_requests_halted")) require(budget.get("paid_requests_halted") is Boolean)
            }
            return CloudTeacherStatus(model, staging, value.getBoolean("analysis_enabled") && !staging,
                budget?.amount("funded_allowance_micro_usd"), budget?.amount("configured_spending_limit_micro_usd"),
                budget?.amount("configured_ceiling_micro_usd"), budget?.amount("remaining_micro_usd"),
                budget?.amount("request_reservation_micro_usd"), budget?.amount("monthly_spending_limit_micro_usd"),
                budget?.amount("monthly_exposure_micro_usd"), budget?.amount("monthly_remaining_micro_usd"),
                budget?.amount("usage_reported_cost_micro_usd"), budget?.amount("approved_requests_remaining"),
                budget?.getBoolean("expired") ?: true, budget?.optBoolean("paid_requests_halted", false) ?: false,
                budget != null, activity?.amount("daily_reserved_micro_usd"), activity?.amount("daily_requests"),
                activity?.amount("monthly_requests"), budget?.amount("expires_at"),
                value.optJSONObject("cloud_teacher")?.opt("automatic_analysis_enabled") == true)
        }
    }
}

/** A model prediction, separate from owner labels and held-out validation. */
internal data class CloudLesson(val assessment: String, val patterns: List<String>, val uncertainty: String,
    val summary: String, val usageCost: Long?) {
    override fun toString() = "CloudLesson(redacted)"
    fun json(): JSONObject = JSONObject().put("assessment", assessment)
        .put("patterns", org.json.JSONArray(patterns)).put("uncertainty", uncertainty).put("summary", summary)
        .put("usage_cost_micro_usd", usageCost ?: JSONObject.NULL)
    companion object {
        val PATTERNS = setOf("GUILT","GASLIGHTING","EMOTIONAL_PRESSURE","REPEATED_DEMAND","BLAME_SHIFTING",
            "PASSIVE_AGGRESSION","COERCION","BOUNDARY_PRESSURE","THREAT","INSULT","REPEATED_CONTACT")
        fun parseLesson(value: JSONObject, usage: Long?): CloudLesson {
            require(value.keys().asSequence().toSet() == setOf("assessment","patterns","uncertainty","summary"))
            val assessment = value.getString("assessment").also { require(it in setOf("LOW_RISK","UPSETTING","UNCERTAIN")) }
            val uncertainty = value.getString("uncertainty").also { require(it in setOf("LOW","MEDIUM","HIGH")) }
            val array = value.getJSONArray("patterns")
            require(array.length() <= PATTERNS.size)
            val patterns = List(array.length()) { array.getString(it).also { p -> require(p in PATTERNS) } }
            require(patterns.distinct().size == patterns.size)
            val summary = value.getString("summary").trim().also {
                require(it.length in 1..1000 && it.none { c -> c.code < 32 && c !in "\n\t" })
            }
            return CloudLesson(assessment, patterns, uncertainty, summary, usage)
        }
        fun parseResponse(value: JSONObject, requestId: String): CloudLesson {
            require(value.getString("source") == "model_prediction")
            require(value.getString("request_id") == UUID.fromString(requestId).toString())
            require(value.get("provider_billing_verified") == false)
            return parseLesson(value.getJSONObject("lesson"), value.amount("usage_cost_micro_usd"))
        }
        fun fromStored(value: JSONObject): CloudLesson {
            val cost = value.amount("usage_cost_micro_usd")
            val lesson = JSONObject(value.toString()).apply { remove("usage_cost_micro_usd") }
            return parseLesson(lesson, cost)
        }
    }
}

internal fun approvedCloudPayload(message: String, approved: Boolean, requestId: String, automatic: Boolean = false): ByteArray {
    require(approved && message.length in 1..4096 && message.isNotBlank())
    val identity = UUID.fromString(requestId).toString()
    return JSONObject().put("message", message).put("consent", true).put("request_id", identity)
        .put("protocol_version", 2).apply { if (automatic) put("mode", "automatic") }
        .toString().toByteArray(Charsets.UTF_8)
}
