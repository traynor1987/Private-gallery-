package uk.co.traynor.privategallery.ui

/** Closed vocabulary: never accept a filename, URI, exception message or media metadata. */
enum class VaultPlaybackEvent {
    READ_STARTED, READ_PROGRESS, AUTHENTICATED, READ_MEMORY_LIMIT, READ_AUTHENTICATION_FAILED,
    READ_FILE_UNAVAILABLE, READ_FAILED, VIEWER_CLOSED,
    PLAYER_PREPARING, PLAYER_STATE, VIDEO_TRACK_SUPPORTED, FIRST_FRAME, POSITION_ADVANCED,
    PLAY_WHEN_READY, SUPPRESSION, LIFECYCLE_STARTED, LIFECYCLE_STOPPED, PLAYER_ERROR, RELEASED,
}

/** Last attempt only, process memory only. No bytes, identifiers, timestamps or disk/log writes. */
object VaultPlaybackDiagnostics {
    private val events = ArrayDeque<String>()

    @Synchronized fun begin() {
        events.clear()
        record(VaultPlaybackEvent.READ_STARTED)
    }

    @Synchronized fun record(event: VaultPlaybackEvent, code: Int? = null) {
        val entry = event.name + (code?.let { "=$it" } ?: "")
        if (events.lastOrNull() == entry) return
        if (events.size == 24) events.removeFirst()
        events.addLast(entry)
    }

    @Synchronized fun summary(): String = events.joinToString("\n").ifEmpty { "No Vault video attempt in this session." }
}
