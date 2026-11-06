package uk.co.traynor.privategallery.core.hidden

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Auxiliary device-bound metadata only. The legacy credential record/key/format is never replaced. */
internal class HiddenProtectedRecord(context:Context,namespace:String,purpose:String) {
    init { require(namespace.matches(Regex("[a-zA-Z0-9-]{1,64}")));require(purpose in setOf("attempts-v1","calculator-entry-v1")) }
    private val root=File(context.noBackupFilesDir,"hidden-v1-$namespace")
    private val file=File(root,purpose)
    private val atomic=AtomicFile(file)
    private val alias="${context.packageName}.hidden.v1.$namespace"
    private val aad="${context.packageName}/hidden/$namespace/$purpose".encodeToByteArray()
    private fun key():SecretKey=KeyStore.getInstance("AndroidKeyStore").apply{load(null)}.getKey(alias,null) as? SecretKey
        ?:throw IllegalStateException("Authentication unavailable; existing credentials retained")
    fun read():ByteArray?=synchronized(HiddenCredentialStore.gate) {
        if(!file.exists()&&!File(file.path+".bak").exists()&&!File(file.path+".new").exists())return@synchronized null
        val sealed=atomic.openRead().use{input->val bytes=input.readNBytes(285);require(bytes.size in 29..284&&input.read()==-1){"Invalid protected metadata"};bytes}
        require(sealed[0].toInt()==1)
        val cipher=Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,sealed.copyOfRange(1,13)));cipher.updateAAD(aad)
        cipher.doFinal(sealed,13,sealed.size-13)
    }
    fun write(bytes:ByteArray)=synchronized(HiddenCredentialStore.gate) {
        require(bytes.size<=255)
        val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key());cipher.updateAAD(aad)
        require(cipher.iv.size==12)
        val sealed=byteArrayOf(1)+cipher.iv+cipher.doFinal(bytes)
        check(root.isDirectory||root.mkdirs())
        val out=atomic.startWrite()
        try{out.write(sealed);atomic.finishWrite(out)}catch(failure:Throwable){atomic.failWrite(out);throw failure}
        val verified=checkNotNull(read());try{check(MessageDigest.isEqual(bytes,verified))}finally{verified.fill(0)}
    }
}

internal data class HiddenAttemptTime(val elapsed:Long,val wall:Long,val boot:Long)
internal class HiddenPinCooldown(val remainingMillis:Long):IllegalStateException("Too many attempts. Try again in ${(remainingMillis+999)/1000} seconds.")
internal class HiddenPinUnavailable(cause:Throwable):IllegalStateException("Authentication temporarily unavailable; existing credentials retained",cause)

/** Persist before comparison: interruption cannot discard an attempted PIN. Never a permanent lockout. */
internal class HiddenPinAttemptStore(context:Context,namespace:String,private val clock:()->HiddenAttemptTime={
    HiddenAttemptTime(android.os.SystemClock.elapsedRealtime(),System.currentTimeMillis(),android.provider.Settings.Global.getInt(context.contentResolver,android.provider.Settings.Global.BOOT_COUNT,0).toLong())
}) {
    private val record=HiddenProtectedRecord(context,namespace,"attempts-v1")
    fun reserve()=synchronized(HiddenCredentialStore.gate) {
        try {
            val time=clock()
            val old=try{record.read()?.let{bytes->try{HiddenPinThrottle.decode(bytes)}finally{bytes.fill(0)}}?:HiddenPinThrottle.empty()}
                catch(failure:Exception){HiddenPinThrottle.recovery(time.elapsed,time.wall,time.boot).also{save(it)}}
            val state=old.rebase(time.elapsed,time.wall,time.boot)
            if(state!==old)save(state)
            val delay=state.remaining(time.elapsed)
            if(delay>0)throw HiddenPinCooldown(delay)
            save(state.failed(time.elapsed,time.wall,time.boot))
        }catch(failure:HiddenPinCooldown){throw failure}catch(failure:Exception){throw HiddenPinUnavailable(failure)}
    }
    fun succeeded()=synchronized(HiddenCredentialStore.gate) {
        try{save(HiddenPinThrottle.empty())}catch(failure:Exception){throw HiddenPinUnavailable(failure)}
    }
    private fun save(value:HiddenPinThrottle){val bytes=value.encode();try{record.write(bytes)}finally{bytes.fill(0)}}
}

/** Opt-in metadata is outside Space preferences and all portable Backup categories. No PIN/sequence text. */
internal class HiddenCalculatorEntryStore(context:Context,namespace:String="default") {
    private val record=HiddenProtectedRecord(context,namespace,"calculator-entry-v1")
    fun enabled():Boolean=synchronized(HiddenCredentialStore.gate){
        runCatching{record.read()?.let{bytes->try{require(bytes.size==1&&bytes[0].toInt() in 0..1);bytes[0].toInt()==1}finally{bytes.fill(0)}}?:false}.getOrDefault(false)
    }
    fun setEnabled(value:Boolean,session:HiddenSession,identity:HiddenSession.Authentication,current:()->Boolean)=synchronized(HiddenCredentialStore.gate){
        // Final publication is bound to the same independent Hidden authentication.
        synchronized(session){check(current()&&session.isAuthenticationCurrent(identity)){"Hidden settings authority expired"};record.write(byteArrayOf(if(value)1 else 0));check(current()&&session.isAuthenticationCurrent(identity))}
    }
}
