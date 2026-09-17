package uk.co.traynor.privategallery.core.security

enum class AutoLockTimeout(val milliseconds: Long) {
  IMMEDIATELY(0), SECONDS_30(30_000), MINUTE_1(60_000), MINUTES_5(300_000)
}

class LockSession(private var timeout: AutoLockTimeout) {
  var isUnlocked: Boolean = false
    private set
  private var backgroundedAt: Long? = null

  fun unlock() { isUnlocked = true; backgroundedAt = null }
  fun lock() { isUnlocked = false; backgroundedAt = null }
  fun setTimeout(value: AutoLockTimeout) { timeout = value }
  fun onAppBackgrounded(now: Long) {
    if (timeout == AutoLockTimeout.IMMEDIATELY) lock() else backgroundedAt = now
  }
  fun onForegrounded(now: Long) {
    val leftAt = backgroundedAt ?: return
    if (now - leftAt >= timeout.milliseconds) lock() else backgroundedAt = null
  }
}
