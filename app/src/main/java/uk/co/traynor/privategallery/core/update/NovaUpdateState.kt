package uk.co.traynor.privategallery.core.update

enum class UpdatePhase { IDLE, CHECKING, UP_TO_DATE, AVAILABLE, DOWNLOADING, VERIFYING, READY, INSTALLATION_REQUESTED, CANCELLED, FAILED }
data class NovaUpdateState(
    val phase: UpdatePhase = UpdatePhase.IDLE,
    val message: String = "Not checked",
    val release: ReleaseMetadata? = null,
    val bytes: Long = 0,
    val total: Long = -1,
    val bytesPerSecond: Long? = null,
) {
    val busy get() = phase in setOf(UpdatePhase.CHECKING, UpdatePhase.DOWNLOADING, UpdatePhase.VERIFYING, UpdatePhase.INSTALLATION_REQUESTED)
    val progress get() = if (total > 0) (bytes.toDouble()/total).coerceIn(0.0,1.0).toFloat() else null
}
