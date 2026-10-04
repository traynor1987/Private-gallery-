package uk.co.traynor.privategallery.core.security

/** Exact original bounded manifest-read array. Synchronous Native/classifier callbacks
 * run outside its private gate. Consumers must not retain bytes or await this original.
 * Independent transport/input children unblock reads before wipe waits for actual return. */
internal class OwnedByteBuffer(private val guard:ScopedIoGuard,private val original:ReleaseReservation,
    private val childIndex:Int,size:Int):AutoCloseable {
    private val gate=java.lang.Object()
    @Volatile private var retired=false
    private var activeThread:Thread?=null
    init{guard.requireNativeConstruction(original,childIndex)}
    // Last potentially allocating construction step; immediately bind the original child.
    private val actual=ByteArray(size.also{require(it in 1..64*1024)})
    private fun checkOpen(){guard.check();original.requireNativeUse(childIndex,this)}
    /** Permanent identity/shape preflight; never borrows or exposes backing bytes. */
    internal fun requireStagingBinding(expectedGuard:ScopedIoGuard,expectedOriginal:ReleaseReservation,size:Int) {
        check(guard===expectedGuard && original===expectedOriginal){"Foreign staging plaintext workspace"}
        require(actual.size==size){"Fixed staging plaintext workspace required"}
        guard.requireNativeOutsideAuthorityGate();checkOpen()
    }

    /** Only a Boolean classification escapes; no generic backing-array return. */
    fun useBytes(consume:(ByteArray)->Boolean):Boolean {
        checkOpen()
        synchronized(gate){check(!retired&&activeThread==null){"Original probe buffer unavailable"};activeThread=Thread.currentThread()}
        try {
            checkOpen()
            val result=consume(actual)
            checkOpen()
            synchronized(gate){check(!retired){"Original probe buffer retired"}}
            return result
        }catch(failure:Throwable){
            // Only an admitted exclusive borrow reaches here, after callback unwind.
            // Another rejected borrower cannot wipe an active Native invocation.
            actual.fill(0)
            throw failure
        }finally{synchronized(gate){activeThread=null;gate.notifyAll()}}
    }
    override fun close()=synchronized(gate){
        check(activeThread!==Thread.currentThread()){ "Probe invocation cannot retire its own buffer" }
        retired=true
        // wait releases the gate. Failed/interrupted actual disposal remains charged.
        while(activeThread!=null)gate.wait()
        actual.fill(0)
    }
}
