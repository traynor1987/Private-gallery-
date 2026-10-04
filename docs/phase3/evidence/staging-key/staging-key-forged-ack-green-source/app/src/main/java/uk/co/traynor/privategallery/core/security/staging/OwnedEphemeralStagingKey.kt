package uk.co.traynor.privategallery.core.security.staging

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import uk.co.traynor.privategallery.core.security.*

/** Fixed private key/framing lifetime, not FD verification, durable format or publication authority.
 * One original owns the key and both typed workspaces. Native generation and cipher calls are
 * outside all authority/private gates. Actual synchronous return precedes wipe/acknowledgement. */
internal class OwnedEphemeralStagingKey private constructor(private val guard:ScopedIoGuard,
    private val original:ReleaseReservation,private val index:Int,private val layout:StagingChunkLayout):AcknowledgedCloseable {
    private val gate=Any()
    private val acknowledged=CompletableFuture<Unit>()
    private val acknowledgementView=StagingAcknowledgementView(acknowledged)
    private val nonce=ByteArray(12)
    private val aad=ByteArray(32)
    private val sequence=StagingSealSequence(layout)
    private var activeThread:Thread?=null
    private var retired=false
    private var attempted=false
    private var ready=false
    private var sealedChunks=0L
    private var lastCipher:FixedStagingChunkCipher?=null
    // LAST allocation before immediate direct attachment. Never accepts/copies a caller master key.
    private val keyBytes=ByteArray(32)

    private fun generate() {
        guard.requireStagingKeyGeneration(original,index,this)
        synchronized(gate){check(!retired&&!attempted&&activeThread==null);attempted=true;activeThread=Thread.currentThread()}
        try {
            guard.requireStagingKeyGeneration(original,index,this)
            // The preclaimed root covers this indivisible no-handle Native invocation's known
            // scratch and actual return. Close merely marks retired and returns a pending stage.
            check(StagingEntropyInvocation.fill(keyBytes)==32){"Fixed private staging entropy unavailable"}
            guard.requireStagingKeyGeneration(original,index,this)
            synchronized(gate){check(!retired){"Staging entropy root retired"};ready=true}
        }catch(failure:Throwable){failFamily();throw failure}
        finally{leave()}
    }
    private fun checkedState(seal:Boolean,chunk:Long) {
        check(!retired&&ready&&activeThread==null){"Original staging invocation unavailable"}
        check(if(seal)chunk==sealedChunks&&sealedChunks<layout.chunkCount() else sealedChunks==layout.chunkCount()){
            "Staging family index or completion unavailable"
        }
    }
    private fun preflight(seal:Boolean,chunk:Long,length:Int,plain:OwnedByteBuffer,cipher:OwnedNativeOutputBuffer) {
        guard.requireStagingKeyUse(original,index,this)
        val expected=layout.chunkPlaintextBytes(chunk)+(if(seal)0 else 16)
        require(length==expected){"Fixed staging chunk length required"}
        plain.requireStagingBinding(guard,original,65536)
        cipher.requireStagingBinding(guard,original,65552)
        synchronized(gate){checkedState(seal,chunk)}
    }
    private fun enter(seal:Boolean,chunk:Long) {
        guard.requireStagingKeyUse(original,index,this)
        synchronized(gate){checkedState(seal,chunk);activeThread=Thread.currentThread()}
    }
    fun seal(chunk:Long,length:Int,plain:OwnedByteBuffer,cipher:OwnedNativeOutputBuffer):Int =
        invoke(true,chunk,length,plain,cipher)
    fun open(chunk:Long,length:Int,cipher:OwnedNativeOutputBuffer,plain:OwnedByteBuffer):Int =
        invoke(false,chunk,length,plain,cipher)
    private fun invoke(seal:Boolean,chunk:Long,length:Int,plain:OwnedByteBuffer,cipher:OwnedNativeOutputBuffer):Int {
        preflight(seal,chunk,length,plain,cipher)
        // Claim root exclusivity BEFORE either workspace. A late duplicate/racing loser cannot
        // enter a workspace and erase a winner's completed ciphertext on root admission denial.
        enter(seal,chunk)
        try {
            val primitive=FixedStagingChunkCipher()
            lastCipher=primitive // Bind every known algorithm array before any private key copy.
            var result=0
            plain.useBytes{plaintext->
                try {
                    cipher.useBytes{ciphertext->
                        guard.requireStagingKeyUse(original,index,this)
                        if(seal)sequence.claim(chunk,length,nonce,aad) else layout.writeFraming(chunk,nonce,aad)
                        result=if(seal)primitive.perform(true,keyBytes,nonce,aad,plaintext,length,ciphertext)
                            else primitive.perform(false,keyBytes,nonce,aad,ciphertext,length,plaintext)
                        guard.requireStagingKeyUse(original,index,this)
                        check(result==layout.chunkPlaintextBytes(chunk)+(if(seal)16 else 0))
                        result
                    }
                    true
                }finally{if(seal)plaintext.fill(0)}
            }
            guard.requireStagingKeyUse(original,index,this)
            synchronized(gate){check(!retired){"Original staging key retired"};if(seal)sealedChunks++}
            return result
        }catch(failure:Throwable){failFamily();throw failure}
        finally{leave()}
    }
    private fun failFamily() {
        synchronized(gate){retired=true;ready=false;sequence.retire()}
        original.release() // Enqueue all already funded independent children; never await here.
    }
    private fun wipePrimitiveAndFrames() {
        try{lastCipher?.wipeKnownState()}finally{try{nonce.fill(0)}finally{aad.fill(0)}}
    }
    private fun leave() {
        synchronized(gate){check(activeThread===Thread.currentThread()){"Foreign staging borrower"}}
        var failure:Throwable?=null
        try{wipePrimitiveAndFrames()}catch(t:Throwable){failure=t}
        val complete=synchronized(gate){
            if(failure!=null||original.retiring){retired=true;ready=false;sequence.retire()}
            try{if(retired)keyBytes.fill(0)}finally{activeThread=null}
            retired
        }
        if(failure!=null){original.release();acknowledged.completeExceptionally(failure);throw failure}
        if(complete)acknowledged.complete(Unit)
    }
    override fun closeAcknowledged():CompletionStage<Unit> {
        val complete=synchronized(gate){retired=true;ready=false;sequence.retire();activeThread==null}
        if(complete) {
            try{try{wipePrimitiveAndFrames()}finally{keyBytes.fill(0)};acknowledged.complete(Unit)}
            catch(failure:Throwable){acknowledged.completeExceptionally(failure)}
        }
        return acknowledgementView
    }
    companion object {
        internal fun create(scope:OwnedFactoryScope,name:String,guard:ScopedIoGuard,layout:StagingChunkLayout):OwnedEphemeralStagingKey {
            val index=scope.declaredChildIndex(name)
            guard.requireStagingKeyConstruction(scope.original,index)
            val key=OwnedEphemeralStagingKey(guard,scope.original,index,layout)
            scope.original.attach(index,key) // Actual acknowledged root, never a synchronous wrapper.
            key.generate()
            return key
        }
    }
}
internal fun OwnedFactoryScope.stagingKey(name:String,guard:ScopedIoGuard,layout:StagingChunkLayout)=
    OwnedEphemeralStagingKey.create(this,name,guard,layout)
