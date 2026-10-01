package uk.co.traynor.privategallery.phase0

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Deliberately fails unavailable capabilities; an ordinary CI provider is not physical admission. */
class Phase0BrowserEvidenceTest {
  @Test fun syntheticProfileEvidenceInDedicatedProcess() {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val context = instrumentation.targetContext
    assertTrue(context.packageName.endsWith(".phase0evidence"))
    assertEquals(PackageManager.PERMISSION_DENIED,
      context.packageManager.checkPermission(Manifest.permission.INTERNET, context.packageName))
    val args = InstrumentationRegistry.getArguments()
    val mode = args.getString("phase0Mode") ?: "exercise"
    val runId = args.getString("phase0RunId") ?: "synthetic_20260929"
    val latch = CountDownLatch(1)
    var report: JSONObject? = null
    val receiver = object : ResultReceiver(Handler(Looper.getMainLooper())) {
      override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
        report = JSONObject(requireNotNull(resultData?.getString("report")))
        latch.countDown()
      }
    }
    context.startActivity(Intent().setClassName(context.packageName,
      "uk.co.traynor.privategallery.phase0.Phase0BrowserEvidenceActivity")
      .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      .putExtra("receiver", receiver).putExtra("mode", mode).putExtra("runId", runId))
    assertTrue("UNMEASURED: host produced no report", latch.await(120, TimeUnit.SECONDS))
    val evidence = requireNotNull(report)
    println("PHASE0_SYNTHETIC_REPORT=$evidence")
    assertEquals("same_uid_dedicated_process", evidence.getString("containment"))
    assertEquals("${context.packageName}:phase0_browser", evidence.getString("process"))
    assertEquals(android.os.Process.myUid(), evidence.getInt("uid"))
    assertTrue("CAPABILITY UNAVAILABLE IS NOT PASS: $evidence", evidence.getString("status").startsWith("MEASURED_"))
    if (mode == "exercise") {
      val checks = evidence.getJSONObject("checks")
      for (dimension in listOf("cookies", "httpOnly", "localStorage", "indexedDB", "cacheStorage", "serviceWorker", "geolocation")) {
        assertEquals("Individual dimension unavailable/unmeasured: $dimension; $evidence", "ISOLATED", checks.getString(dimension))
      }
      assertEquals("UNMEASURED_SYNTHETIC_INTERCEPTION", checks.getString("httpCache"))
      assertEquals("ISOLATED_DOCUMENT_CONTEXTS", checks.getString("sessionStorage"))
    }
    if (mode == "read") {
      // Read-only mode collects death/reboot persistence evidence without recreating markers.
      val profiles = evidence.getJSONArray("profiles")
      assertEquals(2, profiles.length())
      for (index in 0..1) {
        for (dimension in listOf("domCookie", "localStorage", "indexedDB", "cacheStorage", "serviceWorker")) {
          assertEquals("Read dimension unavailable/unmeasured: $dimension; $evidence", "MEASURED",
            profiles.getJSONObject(index).getJSONObject(dimension).getString("status"))
        }
      }
    }
    if (mode == "scan") assertEquals("Missing/failed scan is UNMEASURED: $evidence",
      "BOUNDED_READ_ONLY_OBSERVATION", evidence.getJSONObject("afterViewDestructionScan").getString("status"))
    if (mode == "cold_delete") {
      assertEquals("MEASURED_COLD_DELETE_API_RESULT", evidence.getString("status"))
      assertFalse(evidence.getBoolean("profilesPreviouslyTouched"))
    }
  }
}
