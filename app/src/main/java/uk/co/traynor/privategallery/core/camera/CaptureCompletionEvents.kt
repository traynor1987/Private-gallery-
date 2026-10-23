package uk.co.traynor.privategallery.core.camera

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** In-process wake-up only: no vault identity, filenames, keys or capture details. */
internal object CaptureCompletionEvents {
    private val mutableRevision=MutableStateFlow(0L)
    val revision=mutableRevision.asStateFlow()
    @Synchronized fun completed(){mutableRevision.value=mutableRevision.value+1}
}
