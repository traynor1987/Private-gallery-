package uk.co.traynor.privategallery.core.evidence

import android.content.Context
import java.nio.CharBuffer
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import uk.co.traynor.privategallery.core.editor.AiCredentialStore

/** Captured originating authentication, not a global preference or a catalogue field. */
internal class EvidenceCredentials internal constructor(
 val owner:EvidenceCatalog.Owner,
 private val current:()->Boolean,
 private val guarded:((()->Unit)->Unit),
 private val backend:Backend,
 private val onRemoved:()->Unit,
 private val shared:SharedState=SharedState(),
) {
 internal class SharedState {
  // Long owner/file operations never run under authentication or state monitors.
  val operationGate=Any()
  val gate=Any()
  val generation=AtomicLong()
  val keys=mutableSetOf<Key>()
 }
 internal interface Backend {
  fun configured():Boolean
  fun stage(value:ByteArray):AiCredentialStore.PreparedChange
  fun read():ByteArray?
  fun removal():AiCredentialStore.PreparedChange
 }
 constructor(context:Context,access:EvidenceAccess,onRemoved:()->Unit={}):this(access.owner,access::current,
  {action->access.leases.acquire().use{lease->lease.commit{access.checkCurrent();action();access.checkCurrent()}}},
  protectedBackend(context,access.owner),onRemoved,stateFor(context,access.owner))
 private val gate=shared.gate
 private val activeKeys=shared.keys
 // Lock order is operationGate -> captured authentication -> state gate. No path
 // holding state enters authentication, waits on operationGate, or calls Key/route cleanup.
 // Key validity reads the generation atomically, without taking the state gate.
 private fun captureGeneration():Long {var captured=0L;guarded{check(current());captured=shared.generation.get()};return captured}
 private fun valid(captured:Long)=current()&&shared.generation.get()==captured
 fun isConfigured():Boolean {
  val captured=captureGeneration()
  return backend.configured().also{guarded{check(valid(captured))}}
 }
 /** Caller retains its input. All owned mutable copies are cleared even when authorization retires. */
 fun setup(value:CharArray) {
  require(value.isNotEmpty()&&value.size<=8192){"Enter an owner-supplied OpenAI API key"}
  val copy=value.copyOf();var bytes:ByteArray?=null
  try {
   require(copy.all{it.code in 33..126}){"API key must contain printable non-space ASCII characters"}
   val buffer=StandardCharsets.UTF_8.encode(CharBuffer.wrap(copy))
   try{bytes=ByteArray(buffer.remaining()).also{buffer.get(it)}}finally{if(buffer.hasArray())buffer.array().fill(0)}
   synchronized(shared.operationGate) {
    val captured=captureGeneration()
    // Keystore lookup, encryption, file write and sync happen before commit.
    backend.stage(checkNotNull(bytes)).use{change->promote(change,captured,false)}
   }
  }finally{copy.fill('\u0000');bytes?.fill(0)}
 }
 fun remove()=synchronized(shared.operationGate) {
  val captured=captureGeneration()
  backend.removal().use{change->promote(change,captured,true)}
 }
 private fun promote(change:AiCredentialStore.PreparedChange,captured:Long,removing:Boolean) {
  var retired=emptyList<Key>();var attempted=false;var notificationFailure:Throwable?=null
  try {
   guarded {
    check(valid(captured))
    synchronized(gate) {
     check(shared.generation.get()==captured)
     attempted=true
     try{change.commit()}finally {
      // Even an uncertain/failed transition retires old send leases. A failed
      // pre-promotion write still leaves the authoritative old ciphertext intact.
      shared.generation.incrementAndGet();retired=activeKeys.toList();activeKeys.clear()
     }
    }
   }
  }finally {
   retired.forEach{it.revoke()}
   if(removing&&attempted)notificationFailure=runCatching(onRemoved).exceptionOrNull()
  }
  // Directory durability and ciphertext tombstone deletion may block; neither
  // runs under authentication/state. Failure is reported, never blindly retried.
  try{change.finish()}catch(failure:Throwable){notificationFailure?.let{failure.addSuppressed(it)};throw failure}
  notificationFailure?.let{throw IllegalStateException("Credential removed; preview cleanup failed.",it)}
 }
 internal fun acquireForSend(expectedOwner:EvidenceCatalog.Owner):Key {
  require(owner==expectedOwner){"Credential belongs to another vault"}
  val captured=captureGeneration()
  var bytes:ByteArray?=null;var pending:Key?=null
  try {
   bytes=backend.read()?:throw EvidenceAiFailure("Set up an OpenAI API key in this vault first.")
   val owned=checkNotNull(bytes)
   require(owned.isNotEmpty()&&owned.size<=8192&&owned.all{it.toInt() in 33..126}){"Stored API key is invalid"}
   // Admission and generation registration use the SAME captured authentication
   // as the precheck. A stale completed decrypt can never become a send key.
   guarded {
    check(valid(captured))
    synchronized(gate) {
     check(shared.generation.get()==captured)
     val created=Key(owned.copyOf(),{valid(captured)}){ended->synchronized(gate){activeKeys.remove(ended)}}
     pending=created;activeKeys.add(created)
    }
   }
   return checkNotNull(pending).also{check(it.current())}
  }catch(failure:Throwable){pending?.close();throw failure}
  finally{bytes?.fill(0)}
 }
 internal class Key(private val bytes:ByteArray,private val valid:()->Boolean,private val released:(Key)->Unit):AutoCloseable {
  private var closed=false
  private var cancel:(()->Unit)?=null
  @Synchronized fun current()=!closed&&valid()
  @Synchronized fun authorization():String {check(current());return "Bearer "+String(bytes,StandardCharsets.US_ASCII)}
  @Synchronized fun rejectEcho(text:String) {
   check(current())
   if(bytes.size>text.length)return
   for(start in 0..text.length-bytes.size){var same=true;for(i in bytes.indices)if(text[start+i].code!=bytes[i].toInt()){same=false;break};if(same)throw EvidenceAiFailure("The provider returned unsafe credential-bearing output.")}
  }
  fun cancelOnRevocation(action:()->Unit) {val revoked=synchronized(this){if(current()){cancel=action;false}else true};if(revoked){action();error("Credential retired")}}
  fun revoke(){val action=synchronized(this){val value=cancel;cancel=null;value};runCatching{action?.invoke()};close()}
  override fun close(){val ended=synchronized(this){if(closed)false else{closed=true;bytes.fill(0);cancel=null;true}};if(ended)released(this)}
 }
 companion object {
  private val states=ConcurrentHashMap<String,SharedState>()
  private fun stateFor(context:Context,owner:EvidenceCatalog.Owner)=states.computeIfAbsent(context.applicationContext.noBackupFilesDir.canonicalPath+":"+owner.name){SharedState()}
  private fun protectedBackend(context:Context,owner:EvidenceCatalog.Owner):Backend {
   val store=AiCredentialStore(context.applicationContext)
   val id="evidence-openai-179-"+owner.name.lowercase()
   return object:Backend {
    override fun configured()=store.isConfigured(id)
    override fun stage(value:ByteArray)=store.stageEvidence(id,value)
    override fun read()=store.read(id)
    override fun removal()=store.removeEvidence(id)
   }
  }
 }
}
