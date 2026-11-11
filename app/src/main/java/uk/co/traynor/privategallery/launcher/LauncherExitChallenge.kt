package uk.co.traynor.privategallery.launcher

import java.util.Locale

internal const val LAUNCHER_EXIT_HOLD_MILLIS = 5_000L

internal enum class LauncherExitStep { PIN, PHRASE, HOLD }

internal class LauncherExitChallenge private constructor(
    val sessionId: String?,
    val step: LauncherExitStep,
    val holdStartedMillis: Long? = null,
) {
    constructor(sessionId: String?) : this(sessionId, LauncherExitStep.PIN)

    fun verifyPin(matches: Boolean): LauncherExitChallenge? =
        if (step == LauncherExitStep.PIN && matches) LauncherExitChallenge(sessionId, LauncherExitStep.PHRASE) else null

    fun verifyPhrase(transcript: String, expected: String): LauncherExitChallenge? {
        if (step != LauncherExitStep.PHRASE) return null
        val expectedPhrase = normalizePhrase(expected)
        return if (expectedPhrase.isNotEmpty() && normalizePhrase(transcript) == expectedPhrase) {
            LauncherExitChallenge(sessionId, LauncherExitStep.HOLD)
        } else null
    }

    fun beginHold(nowMillis: Long): LauncherExitChallenge? =
        if (step == LauncherExitStep.HOLD && holdStartedMillis == null) {
            LauncherExitChallenge(sessionId, step, nowMillis)
        } else null

    fun cancelHold(): LauncherExitChallenge = if (holdStartedMillis == null) this else LauncherExitChallenge(sessionId, step)

    fun finishHold(nowMillis: Long, activeSessionId: String?): Boolean =
        sessionId == activeSessionId && step == LauncherExitStep.HOLD &&
            holdStartedMillis?.let { nowMillis - it >= LAUNCHER_EXIT_HOLD_MILLIS } == true
}

private val phraseWhitespace = Regex("\\s+")

private fun normalizePhrase(value: String): String =
    value.trim().trimEnd('.', '!', '?').trimEnd().replace(phraseWhitespace, " ").lowercase(Locale.ROOT)

internal data class LauncherExitTime(val elapsed: Long, val wall: Long, val boot: Int)

internal data class LauncherExitAttempts(
    val failures: Int = 0,
    val deadline: LauncherExitTime? = null,
) {
    fun remaining(now: LauncherExitTime, maxDuration: Long): Long {
        val until = deadline ?: return 0
        val remaining = if (now.boot >= 0 && now.boot == until.boot) until.elapsed - now.elapsed
            else until.wall - now.wall
        return remaining.coerceIn(0, maxDuration)
    }

    fun failed(now: LauncherExitTime, duration: Long): LauncherExitAttempts {
        if (remaining(now, duration) > 0) return this
        val count = if (deadline != null) 1 else failures.coerceIn(0, 2) + 1
        return if (count < 3) LauncherExitAttempts(count)
        else LauncherExitAttempts(count, LauncherExitTime(now.elapsed + duration, now.wall + duration, now.boot))
    }
}
