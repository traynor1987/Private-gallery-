package uk.co.traynor.privategallery

import android.app.Activity
import android.os.Bundle

class LauncherActivity : Activity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    startActivity(android.content.Intent(this, MainActivity::class.java))
    finish()
  }
}
