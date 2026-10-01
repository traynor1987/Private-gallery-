package uk.co.traynor.privategallery.phase0

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.net.http.SslError
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.ResultReceiver
import android.os.SystemClock
import android.view.WindowManager
import android.webkit.PermissionRequest
import android.webkit.ServiceWorkerClient
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.TextView
import androidx.webkit.Profile
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.File

/** NONPRODUCTION. Cross-process instrumentation collects synthetic markers only. */
class Phase0BrowserEvidenceActivity : Activity() {
  private val handler = Handler(Looper.getMainLooper())
  private val probes = mutableListOf<Probe>()
  private val report = JSONObject()
  private var finished = false
  private lateinit var host: LinearLayout
  private lateinit var app: Phase0EvidenceApplication
  private var runId = ""
  private var mode = ""
  private val markers get() = listOf("PHASE0_${runId}_NORMAL", "PHASE0_${runId}_HIDDEN")

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    host = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    host.addView(TextView(this).apply { text = "NONPRODUCTION synthetic Browser evidence" })
    setContentView(host)
    app = application as Phase0EvidenceApplication
    report.put("containment", "same_uid_dedicated_process").put("process", app.processName)
      .put("uid", Process.myUid()).put("pid", Process.myPid()).put("api", android.os.Build.VERSION.SDK_INT)
      .put("provider", "UNMEASURED_PROVIDER_NOT_LOADED").put("providerVersion", "UNMEASURED_PROVIDER_NOT_LOADED")
      .put("suffix", Phase0EvidenceApplication.SUFFIX).put("startupConfigurationNanos", app.startupNanos)
      .put("startupIncludesProcessSpawn", false).put("profilesPreviouslyTouched", app.profilesTouched)
      .put("physicalLockDeathReboot", "UNMEASURED")
    runId = intent.getStringExtra("runId") ?: ""
    mode = intent.getStringExtra("mode") ?: ""
    if (!runId.matches(Regex("[a-zA-Z0-9_]{1,48}")) || mode !in listOf("exercise", "read", "scan", "cold_delete")) {
      complete("CLOSED_INVALID_SYNTHETIC_REQUEST"); return
    }
    if (checkSelfPermission(Manifest.permission.INTERNET) != PackageManager.PERMISSION_DENIED) {
      complete("CLOSED_NETWORK_PERMISSION_PRESENT"); return
    }
    if (app.startupStatus != "SUFFIX_APPLIED") { complete(app.startupStatus); return }
    // Scan does not initialize provider profiles or WebViews, preserving the cold-delete precondition.
    if (mode == "scan") { complete("MEASURED_SCAN_PARTIAL"); return }
    try {
      val admission = Phase0BrowserPolicy.admit(
        app.processName == "$packageName:phase0_browser", true,
        WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE))
      if (admission != "READY") { complete(admission); return }
      val provider = WebViewCompat.getCurrentWebViewPackage(this)
      report.put("provider", provider?.packageName ?: "UNKNOWN")
        .put("providerVersion", provider?.versionName ?: "UNKNOWN")
      if (mode == "cold_delete") { coldDelete(); return }
      app.profilesTouched = true
      val before = SystemClock.elapsedRealtimeNanos()
      val normal = createProbe("NORMAL_PROFILE_TEST", markers[0])
      val hidden = createProbe("HIDDEN_PROFILE_TEST", markers[1])
      report.put("twoWebViewsCreationNanos", SystemClock.elapsedRealtimeNanos() - before)
      if (mode == "read") {
        snapshot(normal, false) { n -> snapshot(hidden, false) { h ->
          report.put("profiles", JSONArray().put(n).put(h))
          geolocationSnapshot(normal, hidden) { complete("MEASURED_READ_PARTIAL") }
        } }
      } else {
        seedCookie(normal) { snapshot(normal, true) { first ->
          snapshot(hidden, false) { hiddenBefore ->
            seedCookie(hidden) { snapshot(hidden, true) { hiddenAfter ->
              snapshot(normal, false) { normalAfter ->
                report.put("profiles", JSONArray().put(normalAfter).put(hiddenAfter))
                  .put("normalAfterWrite", first).put("hiddenBeforeWrite", hiddenBefore)
                checkStorage(normalAfter, hiddenAfter, hiddenBefore)
                geolocationExercise(normal, hidden) { complete("MEASURED_EXERCISE_PARTIAL") }
              }
            } }
          }
        } }
      }
    } catch (_: RuntimeException) { complete("CLOSED_PROVIDER_OR_FIXTURE_FAILURE") }
  }

  private data class Probe(val name: String, val marker: String, val view: WebView, val profile: Profile, val worker: Boolean)

  private fun createProbe(name: String, marker: String): Probe {
    val view = WebView(this)
    // First method call on this fresh view: never hot-switch a used/default view.
    try { WebViewCompat.setProfile(view, name) }
    catch (failure: RuntimeException) { view.destroy(); throw failure }
    val profile = WebViewCompat.getProfile(view)
    val workerFeatures = listOf(WebViewFeature.SERVICE_WORKER_BASIC_USAGE,
      WebViewFeature.SERVICE_WORKER_SHOULD_INTERCEPT_REQUEST, WebViewFeature.SERVICE_WORKER_BLOCK_NETWORK_LOADS)
    val worker = workerFeatures.all { WebViewFeature.isFeatureSupported(it) }
    val probe = Probe(name, marker, view, profile, worker)
    probes += probe
    if (worker) {
      val controller = profile.serviceWorkerController
      controller.serviceWorkerWebSettings.blockNetworkLoads = true
      controller.setServiceWorkerClient(object : ServiceWorkerClient() {
        override fun shouldInterceptRequest(request: WebResourceRequest): WebResourceResponse = SyntheticSite.response(request.url, marker)
      })
    }
    view.settings.apply {
      javaScriptEnabled = true
      domStorageEnabled = true
      allowFileAccess = false
      allowContentAccess = false
      mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
      blockNetworkLoads = true
      javaScriptCanOpenWindowsAutomatically = false
      setSupportMultipleWindows(false)
    }
    profile.cookieManager.setAcceptThirdPartyCookies(view, false)
    view.webViewClient = object : WebViewClient() {
      override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse = SyntheticSite.response(request.url, marker)
      override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = true
      override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) = handler.cancel()
    }
    view.webChromeClient = object : WebChromeClient() {
      override fun onPermissionRequest(request: PermissionRequest) = request.deny()
    }
    view.setDownloadListener { _, _, _, _, _ -> /* no download or external intent path */ }
    host.addView(view, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 180))
    return probe
  }

  private fun seedCookie(probe: Probe, done: () -> Unit) {
    probe.profile.cookieManager.setCookie(SyntheticSite.ORIGIN,
      "phase0_httpOnly=${probe.marker}_HTTPONLY; HttpOnly; Secure; SameSite=Strict; Path=/") { accepted ->
      if (!accepted) { complete("CLOSED_COOKIE_SEED_REJECTED"); return@setCookie }
      probe.profile.cookieManager.flush()
      done()
    }
  }

  private fun snapshot(probe: Probe, write: Boolean, done: (JSONObject) -> Unit) {
    val begin = SystemClock.elapsedRealtime()
    var started = false
    probe.view.webViewClient = object : WebViewClient() {
      override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse = SyntheticSite.response(request.url, probe.marker)
      override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = true
      override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) = handler.cancel()
      override fun onPageFinished(view: WebView, url: String) {
        if (started || finished || url != "${SyntheticSite.ORIGIN}/") return
        started = true
        view.evaluateJavascript(SyntheticSite.script(probe.marker, write, probe.worker), null)
        poll()
      }
      private fun poll() {
        if (finished) return
        probe.view.evaluateJavascript("JSON.stringify(window.phase0Snapshot || null)") { raw ->
          val value = runCatching { JSONTokener(raw).nextValue() as? String }.getOrNull()
          if (value != null && value != "null") {
            val result = runCatching { JSONObject(value) }.getOrNull()
            if (result == null) { complete("CLOSED_INVALID_FIXTURE_REPORT"); return@evaluateJavascript }
            result.put("profile", probe.name).put("expectedMarker", probe.marker)
              .put("nativeCookie", probe.profile.cookieManager.getCookie(SyntheticSite.ORIGIN) ?: "")
              .put("documentElapsedMillis", SystemClock.elapsedRealtime() - begin)
            done(result)
          } else if (SystemClock.elapsedRealtime() - begin > 25_000) complete("UNMEASURED_FIXTURE_TIMEOUT")
          else handler.postDelayed({ poll() }, 100)
        }
      }
    }
    // Page-finish itself may never arrive on a restrictive provider. Failure cannot count as isolation.
    handler.postDelayed({ if (!started && !finished) complete("UNMEASURED_INTERCEPTED_PAGE_UNAVAILABLE") }, 25_000)
    probe.view.loadUrl("${SyntheticSite.ORIGIN}/")
  }

  private fun checkStorage(normal: JSONObject, hidden: JSONObject, hiddenBefore: JSONObject) {
    val checks = JSONObject()
    val noCrossNormal = !normal.toString().contains(markers[1])
    val noCrossHidden = !hidden.toString().contains(markers[0]) && !hiddenBefore.toString().contains(markers[0])
    fun verify(condition: Boolean) = if (condition && noCrossNormal && noCrossHidden) "ISOLATED" else "FAILED_OR_UNMEASURED"
    checks.put("cookies", verify(normal.getString("nativeCookie").contains(markers[0]) && hidden.getString("nativeCookie").contains(markers[1])))
    checks.put("httpOnly", verify(normal.getString("nativeCookie").contains("${markers[0]}_HTTPONLY") &&
      hidden.getString("nativeCookie").contains("${markers[1]}_HTTPONLY") &&
      !normal.getJSONObject("domCookie").optString("value").contains("HTTPONLY") &&
      !hidden.getJSONObject("domCookie").optString("value").contains("HTTPONLY")))
    for (dimension in listOf("localStorage", "indexedDB", "cacheStorage", "serviceWorker")) {
      val n = normal.optJSONObject(dimension)
      val h = hidden.optJSONObject(dimension)
      checks.put(dimension, if (n?.optString("status") == "NOT_SUPPORTED" || h?.optString("status") == "NOT_SUPPORTED")
        "NOT_SUPPORTED" else verify(n?.optString("value") == markers[0] && h?.optString("value") == markers[1]))
    }
    // This proves two browsing contexts hold different values, not persistence/profile scope by itself.
    checks.put("sessionStorage", if (normal.optJSONObject("sessionStorage")?.optString("value") == markers[0] &&
      hidden.optJSONObject("sessionStorage")?.optString("value") == markers[1]) "ISOLATED_DOCUMENT_CONTEXTS" else "FAILED_OR_UNMEASURED")
      .put("httpCache", "UNMEASURED_SYNTHETIC_INTERCEPTION").put("history", "UNMEASURED_PROVIDER_HISTORY")
      .put("otherSitePermissions", "UNMEASURED_DENY_ONLY")
    report.put("checks", checks)
  }

  private fun geolocationSnapshot(normal: Probe, hidden: Probe, done: () -> Unit) {
    normal.profile.geolocationPermissions.getAllowed(SyntheticSite.ORIGIN) { n ->
      hidden.profile.geolocationPermissions.getAllowed(SyntheticSite.ORIGIN) { h ->
        report.put("geolocation", JSONObject().put("normalAllowed", n).put("hiddenAllowed", h)
          .put("actualCoordinates", "NOT_REQUESTED"))
        done()
      }
    }
  }

  private fun geolocationExercise(normal: Probe, hidden: Probe, done: () -> Unit) {
    normal.profile.geolocationPermissions.allow(SyntheticSite.ORIGIN)
    hidden.profile.geolocationPermissions.clear(SyntheticSite.ORIGIN)
    normal.profile.geolocationPermissions.getAllowed(SyntheticSite.ORIGIN) { n1 ->
      hidden.profile.geolocationPermissions.getAllowed(SyntheticSite.ORIGIN) { h1 ->
        normal.profile.geolocationPermissions.clear(SyntheticSite.ORIGIN)
        hidden.profile.geolocationPermissions.allow(SyntheticSite.ORIGIN)
        normal.profile.geolocationPermissions.getAllowed(SyntheticSite.ORIGIN) { n2 ->
          hidden.profile.geolocationPermissions.getAllowed(SyntheticSite.ORIGIN) { h2 ->
            report.put("geolocation", JSONArray().put(JSONObject().put("normal", n1).put("hidden", h1))
              .put(JSONObject().put("normal", n2).put("hidden", h2)))
            report.getJSONObject("checks").put("geolocation", if (n1 && !h1 && !n2 && h2) "ISOLATED" else "FAILED")
            done()
          }
        }
      }
    }
  }

  private fun coldDelete() {
    if (!Phase0BrowserPolicy.canColdDelete(app.profilesTouched)) { complete("CLOSED_COLD_DELETE_REQUIRES_FRESH_PROCESS"); return }
    // Do not getProfile/getOrCreateProfile/setProfile before deletion, including destroyed instances.
    val store = ProfileStore.getInstance()
    val deleted = JSONObject()
    for (name in listOf("NORMAL_PROFILE_TEST", "HIDDEN_PROFILE_TEST")) deleted.put(name, store.deleteProfile(name))
    report.put("deleteRequested", deleted).put("diskDeletionSynchronous", false)
    complete("MEASURED_COLD_DELETE_API_RESULT")
  }

  private fun destroyViews() {
    for (probe in probes) {
      host.removeView(probe.view)
      probe.view.stopLoading()
      probe.view.destroy()
    }
    probes.clear()
  }

  private fun complete(status: String) {
    if (finished) return
    finished = true
    handler.removeCallbacksAndMessages(null)
    destroyViews()
    report.put("status", status).put("mode", mode).put("runId", runId)
    // No immutable/forensic claim: renderer threads may still be live after destroy.
    if (runId.matches(Regex("[a-zA-Z0-9_]{1,48}"))) {
      report.put("afterViewDestructionScan", runCatching {
        EngineMarkerScan.scan(File(applicationInfo.dataDir), markers)
      }.getOrElse { JSONObject().put("status", "UNMEASURED_SCAN_IO") })
    }
    val json = report.toString()
    @Suppress("DEPRECATION")
    val receiver = intent.getParcelableExtra<ResultReceiver>("receiver")
    receiver?.send(0, Bundle().apply { putString("report", json) })
    finish()
  }

  override fun onDestroy() {
    handler.removeCallbacksAndMessages(null)
    destroyViews()
    super.onDestroy()
  }
}
