package uk.co.traynor.privategallery.ui

import android.view.Window
import android.view.WindowManager

/**
 * Generic hosts have no authenticated screenshot-eligibility policy. Once sensitive Evidence
 * occupies their Activity window, leave it secure for that window's lifetime. Disposal cannot
 * restore eligibility or override protection acquired by another page/background transition.
 * Production MainActivity retains its separate authenticated protected-page token policy.
 * Main-thread only; overlapping and repeated releases remain fail closed.
 */
internal object EvidenceSensitiveWindowProtection {
 fun acquire(window:Window):AutoCloseable {
  check(android.os.Looper.myLooper()==android.os.Looper.getMainLooper())
  val flag=WindowManager.LayoutParams.FLAG_SECURE
  window.addFlags(flag)
  return AutoCloseable {
   check(android.os.Looper.myLooper()==android.os.Looper.getMainLooper())
   window.addFlags(flag)
  }
 }
}
