package uk.co.traynor.privategallery.core.security

enum class AutoLockTimeout(val milliseconds: Long) {
  IMMEDIATELY(0), SECONDS_30(30_000), MINUTE_1(60_000), MINUTES_5(300_000)
}

class LockSession(private var timeout: AutoLockTimeout) {
  var isUnlocked: Boolean = false
    private set
  private var backgroundedAt: Long? = null
  private var authenticationEpoch=0L
  fun captureAuthenticationEpoch():Long=authenticationEpoch
  fun isAuthenticationCurrent(epoch:Long):Boolean=isUnlocked && epoch==authenticationEpoch
  fun expireForegroundGap(epoch:Long,stoppedAt:Long,now:Long,screenOff:Boolean):Boolean {
    require(stoppedAt>=0 && stoppedAt<=now)
    if(!isAuthenticationCurrent(epoch))return false
    if(screenOff || now-stoppedAt>=timeout.milliseconds) {lock();return true}
    return false
  }
  fun unlock() { authenticationEpoch++;isUnlocked = true; backgroundedAt = null }
  fun lock() { authenticationEpoch++;isUnlocked = false; backgroundedAt = null }
  fun setTimeout(value: AutoLockTimeout) { timeout = value }
  fun onActivityStopped(now: Long, changingConfigurations: Boolean, screenInteractive: Boolean) {
    if (!screenInteractive) lock()
    else if (!changingConfigurations) onAppBackgrounded(now)
  }
  fun onAppBackgrounded(now: Long) {
    if (timeout == AutoLockTimeout.IMMEDIATELY) lock() else backgroundedAt = minOf(backgroundedAt ?: now,now)
  }
  fun onForegrounded(now: Long) {
    val leftAt = backgroundedAt ?: return
    if (now - leftAt >= timeout.milliseconds) lock() else backgroundedAt = null
  }
}
