package uk.co.traynor.privategallery.core.editor

import kotlinx.coroutines.*
import uk.co.traynor.privategallery.core.security.PrimaryOperation

/** Setup-only producer seam; receives no image data. */
internal fun launchAiSetupWork(owner:PrimaryOperation,scope:CoroutineScope,candidateText:String?,
    block:suspend CoroutineScope.(ByteArray?)->Unit):Job {
    val candidate=candidateText?.trim()?.takeIf{it.isNotEmpty()}?.toByteArray(Charsets.UTF_8)
    return scope.launch(start=CoroutineStart.UNDISPATCHED) {
        try {
            owner.own(checkNotNull(currentCoroutineContext()[Job]))
            block(candidate)
        }finally{candidate?.fill(0)}
    }
}
