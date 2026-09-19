package uk.co.traynor.privategallery.core.ui

/** Confidence gate for screenshot-like dominant-content rectangle candidates. */
object DominantContentCropPolicy {
    fun confidence(
        retainedArea: Float,
        boundaryCount: Int,
        averageBoundaryStrength: Float,
        interiorVariance: Float,
        surroundingVariance: Float,
    ): Float? {
        if (retainedArea !in .32f.. .93f || boundaryCount < 2) return null
        val textureSeparation = ((interiorVariance - surroundingVariance) / 80f).coerceIn(0f, 1f)
        val trim = (1f - retainedArea).coerceIn(0f, 1f)
        val score = averageBoundaryStrength.coerceIn(0f, 1f) * .50f +
            textureSeparation * .30f + trim * .12f + retainedArea * .08f
        return score.takeIf { it >= .57f }
    }
}
