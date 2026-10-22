package uk.co.traynor.privategallery.core.hidden

import android.content.Context
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import uk.co.traynor.privategallery.core.editor.*
import uk.co.traynor.privategallery.core.vault.VaultItem

/** One editor/creation owner. No Primary key, repository or public media destination is accepted. */
internal class HiddenEditorAccess(
    private val context:Context,
    private val session:HiddenSession,
    private val authorized:()->Boolean={true},
    private val beforePublication:()->Unit={},
):AutoCloseable {
    private val authority=session.authority()
    private val gate=Any()
    @Volatile private var closed=false
    fun isActive():Boolean=runCatching {checkCurrent();true}.getOrDefault(false)
    private fun checkCurrent(cancelled:()->Boolean={false}) {
        check(!closed && authorized() && !cancelled()) {"Hidden editing cancelled"}
        session.beginOperation(authority).use {check(session.isCurrent(it))}
    }
    private fun repository(cancelled:()->Boolean):HiddenRepository {
        checkCurrent(cancelled)
        return HiddenRepository(context,session,publishIfAllowed={commit ->
            beforePublication()
            synchronized(gate) {checkCurrent(cancelled);commit()}
        })
    }
    fun read(id:String,cancelled:()->Boolean):ByteArray {
        val bytes=repository(cancelled).readForEditing(id) {!isActive() || cancelled()}
        try {checkCurrent(cancelled);return bytes} catch(failure:Throwable) {bytes.fill(0);throw failure}
    }
    /** Takes ownership of the caller's output buffer, including every failure path. */
    fun saveCopy(parentId:String,bytes:ByteArray,provenance:AiEditProvenance?,cancelled:()->Boolean):VaultItem = try {
        val repo=repository(cancelled)
        val item=repo.importEditedCopy(parentId,bytes,provenance)
        repo.verify(item.id);checkCurrent(cancelled);item
    } finally {bytes.fill(0)}
    fun saveGenerated(bytes:ByteArray,modelId:String,cancelled:()->Boolean):VaultItem = try {
        val repo=repository(cancelled)
        val item=repo.importGeneratedImage(bytes,modelId)
        repo.verify(item.id);checkCurrent(cancelled);item
    } finally {bytes.fill(0)}
    suspend fun generate(request:GenerationRequest,onStage:(String)->Unit):VaultItem {
        checkCurrent()
        val job=currentCoroutineContext()[kotlinx.coroutines.Job]
        val cancelled={job?.isActive!=true || !isActive()}
        var token:ByteArray?=null
        var remote:ByteArray?=null
        var clean:ByteArray?=null
        try {
            val consent=AiConsentStore(context)
            check(consent.hasConsent(ReplicateSeedreamProvider.ID)) {"Confirm Replicate remote processing first."}
            token=AiCredentialStore(context).read(ReplicateSeedreamProvider.ID)
                ?: error("Set up Replicate in AI editing settings.")
            val effective=PromptEnhancementStore(context).effective(request.prompt,PromptKind.CREATE,request.model.modelId,request.enhancePrompt)
            // Caller-supplied encoded references are never accepted as vault authority.
            val references=request.referenceItemIds.map {id ->
                currentCoroutineContext().ensureActive()
                val plain=read(id,cancelled)
                try {
                    val prepared=ReplicateImagePreparation.prepare(plain)
                    try {"data:image/jpeg;base64,"+java.util.Base64.getEncoder().encodeToString(prepared)} finally {prepared.fill(0)}
                } finally {plain.fill(0)}
            }
            checkCurrent(cancelled)
            remote=ReplicateImageGenerationApi(PrivateAiHttpTransport()).generate(token!!,
                request.copy(prompt=effective,references=references,referenceItemIds=emptyList())) {stage ->if(!cancelled())onStage(stage)}
            currentCoroutineContext().ensureActive();checkCurrent(cancelled)
            clean=PhotoRenderer.sanitize(remote!!)
            remote?.fill(0);remote=null
            onStage("Encrypting in Hidden Vault…")
            return saveGenerated(clean!!,request.model.modelId,cancelled)
        } finally {token?.fill(0);remote?.fill(0);clean?.fill(0)}
    }
    override fun close() {synchronized(gate) {closed=true}}
}
