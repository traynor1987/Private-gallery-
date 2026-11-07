package uk.co.traynor.privategallery

import android.app.Activity
import android.os.Bundle

class LauncherActivity : Activity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    // A launcher restart can reconcile expired Sanctuary DND before unlocking a Space.
    uk.co.traynor.privategallery.core.sanctuary.SanctuaryDnd.reconcile(applicationContext)
    startActivity(android.content.Intent(this, MainActivity::class.java))
    finish()
  }
}
