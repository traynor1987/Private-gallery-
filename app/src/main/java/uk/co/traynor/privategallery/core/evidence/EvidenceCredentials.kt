package uk.co.traynor.privategallery.core.evidence

import android.content.Context
import java.nio.CharBuffer
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
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
  val gate=Any()
  var generation=0L
  val keys=mutableSetOf<Key>()
 }
 internal interface Backend {
  fun configured():Boolean
  fun save(value:ByteArray)
  fun read():ByteArray?
  fun clear()
 }
 constructor(context:Context,access:EvidenceAccess,onRemoved:()->Unit={}):this(access.owner,access::current,
  {action->access.leases.acquire().use{lease->lease.commit{access.checkCurrent();action();access.checkCurrent()}}},
  protectedBackend(context,access.owner),onRemoved,stateFor(context,access.owner))
 private val gate=shared.gate
 private var generation:Long
  get()=shared.generation
  set(value){shared.generation=value}
 private val activeKeys=shared.keys
 fun isConfigured():Boolean=synchronized(gate){check(current());backend.configured().also{check(current())}}
 /** Caller retains its input. All owned mutable copies are cleared even when authorization retires. */
 fun setup(value:CharArray) {
  require(value.isNotEmpty()&&value.size<=8192){"Enter an owner-supplied OpenAI API key"}
  val copy=value.copyOf();var bytes:ByteArray?=null
  try {
   require(copy.all{it.code in 33..126}){"API key must contain printable non-space ASCII characters"}
   val buffer=StandardCharsets.UTF_8.encode(CharBuffer.wrap(copy))
   try{bytes=ByteArray(buffer.remaining()).also{buffer.get(it)}}finally{if(buffer.hasArray())buffer.array().fill(0)}
   val retired=synchronized(gate){guarded{check(current());backend.save(checkNotNull(bytes));generation++;check(current())};activeKeys.toList()}
   retired.forEach{it.revoke()}
  }finally{copy.fill('\u0000');bytes?.fill(0)}
 }
 fun remove() {
  val retired=synchronized(gate){guarded{check(current());backend.clear();generation++;check(current())};activeKeys.toList()}
  retired.forEach{it.revoke()}
  // The coordinator closes outstanding leases/calls and clears any preview on removal.
  onRemoved()
 }
 internal fun <T> withSendKey(expectedOwner:EvidenceCatalog.Owner,consume:(ByteArray,()->Boolean)->T):T {
  require(owner==expectedOwner){"Credential belongs to another vault"}
  var key:ByteArray?=null;var captured=0L
  try {
   synchronized(gate){guarded{check(current());captured=generation;key=backend.read();check(current())}}
   val owned=key?:throw EvidenceAiFailure("Set up an OpenAI API key in this vault first.")
   require(owned.isNotEmpty()&&owned.size<=8192&&owned.all{it.toInt() in 33..126}){"Stored API key is invalid"}
   val valid={synchronized(gate){current()&&generation==captured}}
   check(valid());return consume(owned,valid).also{check(valid())}
  }finally{key?.fill(0)}
 }
 // A suspend request cannot borrow a key across a non-suspend callback. The send operation
 // captures this bounded disposable lease only after confirmation and wipes it in finally.
 internal fun acquireForSend(expectedOwner:EvidenceCatalog.Owner):Key {
  var pending:Key?=null
  try {
   withSendKey(expectedOwner){bytes,valid->
    val created=Key(bytes.copyOf(),valid){ended->synchronized(gate){activeKeys.remove(ended)}}
    pending=created;synchronized(gate){check(valid());activeKeys.add(created)}
   }
   return checkNotNull(pending)
  }
  catch(failure:Throwable){pending?.close();throw failure}
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
    override fun save(value:ByteArray)=store.save(id,value)
    override fun read()=store.read(id)
    override fun clear()=store.clear(id)
   }
  }
 }
}
