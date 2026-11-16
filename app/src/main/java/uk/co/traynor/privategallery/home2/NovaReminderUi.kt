package uk.co.traynor.privategallery.home2

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import uk.co.traynor.privategallery.core.home2.SpaceContext

@Composable
internal fun NovaReminderPermission(space: SpaceContext, enabled: Boolean) {
    var allowed by remember(space) { mutableStateOf(NovaReminders.canNotify(space)) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        allowed = NovaReminders.canNotify(space)
    }
    if (!allowed) {
        Text("Android notifications are off. Your reminder settings can still be saved.")
        TextButton(enabled = enabled, onClick = {
            if (space.current()) request.launch(Manifest.permission.POST_NOTIFICATIONS)
        }) { Text("Allow reminder notifications") }
    }
}
