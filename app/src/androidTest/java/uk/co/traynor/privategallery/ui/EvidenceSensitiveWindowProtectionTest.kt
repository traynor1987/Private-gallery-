package uk.co.traynor.privategallery.ui

import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class EvidenceSensitiveWindowProtectionTest {
 @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
 @Test fun overlappingSensitivePagesAndFinalReleaseKeepGenericWindowSecure() {
  compose.runOnUiThread {
   val window=compose.activity.window;val flag=WindowManager.LayoutParams.FLAG_SECURE
   val initial=window.attributes.flags and flag!=0
   try {
    window.clearFlags(flag)
    val first=EvidenceSensitiveWindowProtection.acquire(window)
    val replacement=EvidenceSensitiveWindowProtection.acquire(window)
    assertTrue(window.attributes.flags and flag!=0)
    first.close();first.close()
    assertTrue("Retiring one page must not expose its replacement",window.attributes.flags and flag!=0)
    replacement.close()
    assertTrue("Generic hosts have no authority to restore screenshot eligibility",window.attributes.flags and flag!=0)
    replacement.close()
    assertTrue(window.attributes.flags and flag!=0)
   }finally{if(initial)window.addFlags(flag)else window.clearFlags(flag)}
  }
 }
 @Test fun initiallySecureWindowRemainsSecureAfterSensitivePageCloses() {
  compose.runOnUiThread {
   val window=compose.activity.window;val flag=WindowManager.LayoutParams.FLAG_SECURE
   val initial=window.attributes.flags and flag!=0
   try {
    window.addFlags(flag)
    EvidenceSensitiveWindowProtection.acquire(window).close()
    assertTrue(window.attributes.flags and flag!=0)
   }finally{if(initial)window.addFlags(flag)else window.clearFlags(flag)}
  }
 }
}
