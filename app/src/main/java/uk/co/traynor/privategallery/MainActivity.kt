package uk.co.traynor.privategallery

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
    setContent { PrivateGalleryTheme { Text("Private Gallery") } }
  }
}

@Composable private fun PrivateGalleryTheme(content: @Composable () -> Unit) {
  MaterialTheme(content = content)
}
