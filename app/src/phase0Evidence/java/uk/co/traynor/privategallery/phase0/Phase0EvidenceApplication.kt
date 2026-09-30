package uk.co.traynor.privategallery.phase0

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Process
import android.os.SystemClock
import androidx.webkit.ProcessGlobalConfig
import androidx.webkit.WebViewFeature

/** No production initialization, repositories, keys or UI in this evidence host. */
class Phase0EvidenceApplication : Application() {
  internal var startupStatus = "CLOSED_WRONG_PROCESS"
    private set
  internal var startupNanos = 0L
    private set
  internal var profilesTouched = false
  internal var processName = "UNKNOWN"
    private set

  override fun attachBaseContext(base: Context) {
    super.attachBaseContext(base)
    val start = SystemClock.elapsedRealtimeNanos()
    processName = if (Build.VERSION.SDK_INT >= 28) getProcessName() else {
      val manager = base.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
      manager.runningAppProcesses?.firstOrNull { it.pid == Process.myPid() }?.processName ?: "UNKNOWN"
    }
    if (processName != "${base.packageName}:phase0_browser") return
    // Startup feature query is specifically safe before WebView loading. No other webkit call precedes apply.
    startupStatus = try {
      if (!WebViewFeature.isStartupFeatureSupported(base, WebViewFeature.STARTUP_FEATURE_SET_DATA_DIRECTORY_SUFFIX)) {
        "NOT_SUPPORTED_STARTUP_SUFFIX"
      } else {
        ProcessGlobalConfig.apply(ProcessGlobalConfig().setDataDirectorySuffix(base, SUFFIX))
        "SUFFIX_APPLIED"
      }
    } catch (_: RuntimeException) { "CLOSED_STARTUP_CONFIGURATION" }
    startupNanos = SystemClock.elapsedRealtimeNanos() - start
  }

  companion object { internal const val SUFFIX = "phase0_browser_evidence" }
}
