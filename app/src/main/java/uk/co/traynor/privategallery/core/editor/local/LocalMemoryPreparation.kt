package uk.co.traynor.privategallery.core.editor.local

/** UI-process caches only. Called on Main before opening the disposable inference worker. */
internal object LocalMemoryPreparation {
    private var cleanup: (() -> Unit)? = null

    @Synchronized fun attach(action: () -> Unit): () -> Unit {
        cleanup = action
        return { synchronized(this) { if (cleanup === action) cleanup = null } }
    }

    @Synchronized fun release() { cleanup?.invoke() }
}
