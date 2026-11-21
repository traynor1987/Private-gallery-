package uk.co.traynor.privategallery.home2

import android.view.inputmethod.EditorInfo
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.PlatformTextInputMethodRequest
import androidx.compose.ui.platform.PlatformTextInputInterceptor
import androidx.compose.ui.text.AnnotatedString
import uk.co.traynor.privategallery.core.home2.SpaceContext

/** The Hidden text editor's selection clipboard lives only during this presentation. */
@OptIn(ExperimentalComposeUiApi::class)
@Suppress("DEPRECATION")
@Composable
internal fun HiddenWorkspacePrivacy(space: SpaceContext, content: @Composable () -> Unit) {
    val clipboard = remember(space) {
        object : ClipboardManager {
            private var selection: AnnotatedString? = null
            override fun getText(): AnnotatedString? = selection.takeIf { space.current() }
            override fun setText(annotatedString: AnnotatedString) {
                if (space.current()) selection = annotatedString
            }
            fun clear() { selection = null }
        }
    }
    val noLearning = remember {
        PlatformTextInputInterceptor { request, next ->
            next.startInputMethod(PlatformTextInputMethodRequest { attrs ->
                request.createInputConnection(attrs).also {
                    attrs.imeOptions = attrs.imeOptions or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
                }
            })
        }
    }
    DisposableEffect(clipboard) { onDispose { clipboard.clear() } }
    CompositionLocalProvider(LocalClipboardManager provides clipboard) {
        InterceptPlatformTextInput(interceptor = noLearning, content = content)
    }
}
