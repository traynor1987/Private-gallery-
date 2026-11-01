package uk.co.traynor.privategallery.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect

/** Secret entry stays secure even when ordinary app screenshots are enabled. */
@Composable internal fun CredentialCaptureProtection() {
 val activity=androidx.activity.compose.LocalActivity.current as? uk.co.traynor.privategallery.MainActivity
 DisposableEffect(activity){val token=activity?.beginProtectedPage();onDispose{token?.let{activity?.endProtectedPage(it)}}}
}
