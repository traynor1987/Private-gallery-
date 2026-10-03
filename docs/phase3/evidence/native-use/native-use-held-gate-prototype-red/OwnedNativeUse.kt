package uk.co.traynor.privategallery.core.security

internal class OwnedNativeUse<T:Any>(private val guard:ScopedIoGuard, private val original:ReleaseReservation,
    private val childIndex:Int, private val disposeNative:(T)->Unit) {
    private val gate=Any()
    private var actual:T?=null
    private var retired=false
    internal fun bind(value:T){actual=value}
    fun useInt(action:(T)->Int):Int=synchronized(gate){check(!retired);guard.check();action(checkNotNull(actual)).also{guard.check()}}
    fun useLong(action:(T)->Long):Long=synchronized(gate){check(!retired);guard.check();action(checkNotNull(actual)).also{guard.check()}}
    internal fun dispose(value:T)=synchronized(gate){retired=true;disposeNative(value)}
}
