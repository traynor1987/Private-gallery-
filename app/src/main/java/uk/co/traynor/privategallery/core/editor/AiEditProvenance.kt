package uk.co.traynor.privategallery.core.editor

/** Non-private provider/model identifiers only; never prompts, filenames or image metadata. */
data class AiEditProvenance(val processing: AiProcessing, val providerId: String, val modelId: String?) {
    init {
        require(providerId.matches(Regex("[a-zA-Z0-9._/-]{1,100}")))
        require(modelId == null || modelId.matches(Regex("[a-zA-Z0-9._/-]{1,100}")))
    }
}
