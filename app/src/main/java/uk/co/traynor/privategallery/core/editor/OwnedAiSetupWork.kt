package uk.co.traynor.privategallery.core.editor

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import kotlinx.coroutines.*
import uk.co.traynor.privategallery.core.security.*

/** Scoped setup candidate/outer-producer manifest. The consumer must neither retain
 * bytes nor start unjoined children. Saved-token provider/native acquisition is separate. */
internal fun launchAiSetupWork(owner:PrimaryOperation,scope:CoroutineScope,candidateText:String?,
    block:suspend CoroutineScope.(ByteArray?)->Unit):Job {
    val guard=ScopedIoGuard(owner,PrimaryScope.REMOTE_AI_EGRESS)
    guard.requireScope(PrimaryScope.CREDENTIALS);guard.check()
    var producer:Job?=null
    val owned=guard.createOwned(OwnedResourceManifest.io("candidate","producer")) {
        val candidate=AiSetupCandidate(guard,original,candidateText)
        // Literal original index: no name lookup/fallible factory between allocation and binding.
        original.attach(0,candidate)
        guard.check()
        producer=original.createJob(1) {attach->
            scope.launch(start=CoroutineStart.LAZY) {
                candidate.consume {bytes->block(bytes)}
            }.also(attach)
        }.actualJob
        candidate
    }
    try {
        val job=checkNotNull(producer)
        // No await/close/factory in the completion callback; only bounded original dispatch.
        job.invokeOnCompletion{owned.close()}
        guard.check()
        if(!job.start())throw CancellationException("Setup producer unavailable")
        return job
    }catch(failure:Throwable){owned.close();throw failure}
}

/** Acknowledgement remains pending while the provider actually borrows the original.
 * No release worker waits on the producer, its dispatcher or a Job completion future. */
private class AiSetupCandidate(private val guard:ScopedIoGuard,private val original:ReleaseReservation,
    text:String?):AcknowledgedCloseable {
    private val gate=Any()
    private val acknowledged=CompletableFuture<Unit>()
    private var retired=false
    private var borrowed=false
    private var wiped=false
    // Last allocating step. Bound/validate the existing UI String without making
    // a trimmed immutable credential copy or retaining it in this resource.
    private val actual:ByteArray?=run {
        guard.requireAiSetupCandidateConstruction(original)
        if(text==null)null else {
            var start=0;var end=text.length
            while(start<end&&text[start].isWhitespace())start++
            while(end>start&&text[end-1].isWhitespace())end--
            val size=end-start
            if(size>8192||(start until end).any{text[it].code !in 33..126})
                throw AiEditFailure("Enter a valid API key.")
            if(size==0)null else ByteArray(size){index->text[start+index].code.toByte()}
        }
    }

    private fun requireLiveAdmission() {
        try { guard.check();original.requireNativeUse(0,this) }
        catch (_:IllegalStateException) {
            // Only these fixed helper authority/original checks denote stale admission.
            // No non-cancellation cause is carried into genuine Job teardown accounting.
            throw CancellationException("Setup original unavailable")
        }
    }
    suspend fun consume(block:suspend (ByteArray?)->Unit) {
        requireLiveAdmission()
        synchronized(gate){
            if(retired)throw CancellationException("Setup original retired")
            check(!borrowed&&!wiped){"Original setup candidate already consumed"};borrowed=true
        }
        try {
            requireLiveAdmission()
            block(actual)
            currentCoroutineContext().ensureActive();requireLiveAdmission()
        }finally {
            val complete=synchronized(gate){actual?.fill(0);wiped=true;borrowed=false;retired}
            if(complete)acknowledged.complete(Unit)
        }
    }
    override fun closeAcknowledged():CompletionStage<Unit> {
        val complete=synchronized(gate){
            retired=true
            if(borrowed)false else{if(!wiped){actual?.fill(0);wiped=true};true}
        }
        if(complete)acknowledged.complete(Unit)
        return acknowledged
    }
}
