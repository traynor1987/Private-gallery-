package uk.co.traynor.privategallery.core.evidence

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.os.SystemClock
import android.util.Base64
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Instrumentation-process-only clock channel. This receiver cannot invoke route actions. */
internal class EvidenceRouteClock179(
 private val context:Context, arguments:Bundle, private val case:String,
 private val method:String, private val nonce:String, private val source:String,
 private val instrumentationName:String
) {
 private val runNonce=checkNotNull(arguments.getString("evidence179ClockRunNonce"))
 private val serial=checkNotNull(arguments.getString("evidence179ClockSerial"))
 private val user=checkNotNull(arguments.getString("evidence179ClockUser")).toInt()
 private val pid=Process.myPid()
 private val uid=Process.myUid()
 // Read independently in the actual target process. No shell/argument/privilege fallback.
 private val boot=File("/proc/sys/kernel/random/boot_id").inputStream().use { stream ->
  val bytes=stream.readNBytes(128)
  check(bytes.size<128)
  bytes.toString(Charsets.US_ASCII).also { check(it.matches(Regex("[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}\\n?"))) }.trim()
 }
 private val phases=when(case){"vpn_revoke","tor_revoke"->listOf("cancel","revoke");"vpn_loss"->listOf("loss");"tor_death"->listOf("death");else->error("Unknown clock case")}
 private val lock=Any()
 private var poison:String?=null
 private var poisonAt=0L
 private var poisonOrder=0L
 private var lifecycleOrder=0L
 private var closing=false
 private val requests=mutableSetOf<String>()
 private val claims=mutableListOf<Claim>()
 private val thread=HandlerThread("evidence179-clock")
 private val handler:Handler
 private var registered=false
 private val actionName="uk.co.traynor.privategallery.EVIDENCE179_CLOCK_$runNonce"
 internal class Claim(val phase:String,val token:String,val sequence:Long) {
  val ready=CompletableDeferred<Unit>()
  var requestNonce:String?=null
  var claimedAt=0L
  var sample=0L
  // PendingResult and its finisher flag are accessed ONLY on the dedicated HandlerThread.
  var pending:BroadcastReceiver.PendingResult?=null
  var finished=false
  var encoded:String?=null
  var accepted=0
  var finishCount=0
  var finishThreadId=0L
  var sampleOrder=0L
  var finishOrder=0L
  var finishedAt=0L
 }
 private fun bad(reason:String){synchronized(lock){if(poison==null){poison=reason;poisonAt=SystemClock.elapsedRealtimeNanos();poisonOrder=++lifecycleOrder}}}
 private fun healthy(){check(poison==null&&!closing){poison?:"Clock is closing"}}
 private fun base(kind:String)=JSONObject().put("schema",1).put("kind",kind).put("source_sha",source)
  .put("run_nonce",runNonce).put("case",case).put("method",method).put("classname",EvidenceOwnedRoute179Test::class.java.name)
  .put("nonce",nonce).put("package",context.packageName).put("pid",pid).put("uid",uid).put("user",user)
  .put("boot_id",boot).put("instrumentation",instrumentationName)
 private fun encode(value:JSONObject)=Base64.encodeToString(value.toString().toByteArray(Charsets.UTF_8),Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
 private fun withPhase(value:JSONObject,claim:Claim)=value.put("phase",claim.phase).put("arm_token",claim.token).put("arm_sequence",claim.sequence)
 private fun finish(claim:Claim,error:String?=null) {
  check(Thread.currentThread()===thread)
  val pending=claim.pending?:return
  if(claim.finished)return
  claim.finished=true
  try {
   val payload=synchronized(lock){
    if(error!=null){bad(error);null}
    else if(poison!=null||claim.sample<=0L){bad("Missing/poisoned clock sample");null}
    else withPhase(base("sample"),claim).put("request_nonce",checkNotNull(claim.requestNonce)).put("sample_elapsed_ns",claim.sample)
   }
   if(payload==null){pending.setResultCode(178);pending.setResultData(encode(JSONObject().put("error","clock_failed")))}
   else {val encoded=encode(payload);synchronized(lock){claim.encoded=encoded};pending.setResultCode(179);pending.setResultData(encoded)}
  }catch(failure:Throwable){bad("Clock reply failed: ${failure.javaClass.name}");pending.setResultCode(178)}
  finally {
   synchronized(lock){claim.finishCount++;claim.finishThreadId=Thread.currentThread().id}
   try{pending.finish();synchronized(lock){claim.finishedAt=SystemClock.elapsedRealtimeNanos();claim.finishOrder=++lifecycleOrder}}finally{synchronized(lock){claim.pending=null}}
  }
 }
 private val receiver=object:BroadcastReceiver(){
  override fun onReceive(unused:Context,intent:Intent){
   check(Thread.currentThread()===thread)
   try {
    synchronized(lock){healthy()}
    check(intent.action==actionName&&intent.`package`==context.packageName)
    val extras=checkNotNull(intent.extras)
    val op=extras.getString("op")
    val common=setOf("op","run_nonce","source_sha","case","method","phase")
    check(extras.keySet()==if(op=="SAMPLE")common+setOf("nonce","arm_token","arm_sequence","request_nonce") else common)
    check(common.all{extras.get(it) is String})
    check(extras.getString("run_nonce")==runNonce&&extras.getString("source_sha")==source&&extras.getString("case")==case&&extras.getString("method")==method)
    val phase=checkNotNull(extras.getString("phase"));check(phase in phases)
    if(op=="STATE") {
     val reply=synchronized(lock){
      healthy();val current=claims.lastOrNull()
      if(current!=null&&current.phase==phase&&current.requestNonce==null)withPhase(base("ready"),current) else base("not_ready")
     }
     setResultCode(179);setResultData(encode(reply));return
    }
    check(op=="SAMPLE")
    val request=checkNotNull(extras.getString("request_nonce"));check(request.matches(Regex("[a-f0-9]{32}")))
    val claim=synchronized(lock){
     healthy();val current=checkNotNull(claims.lastOrNull())
     check(current.phase==phase&&current.requestNonce==null&&current.sample==0L&&requests.add(request))
     check(extras.getString("nonce")==nonce&&extras.getString("arm_token")==current.token&&extras.getString("arm_sequence")==current.sequence.toString())
     current.requestNonce=request;current.claimedAt=SystemClock.elapsedRealtimeNanos();current
    }
    // Only this HandlerThread owns/finishes PendingResult. It never waits on itself.
    val pending=goAsync()
    synchronized(lock){claim.pending=pending;claim.accepted++}
    handler.postDelayed({finish(claim,if(synchronized(lock){claim.sample==0L})"Clock sample deadline expired" else null)},1000)
    claim.ready.complete(Unit)
   }catch(failure:Throwable){bad("Rejected clock command: ${failure.javaClass.name}");setResultCode(178);setResultData(encode(JSONObject().put("error","clock_rejected")))}
  }
 }
 init {
  check(runNonce.matches(Regex("[a-f0-9]{32}"))&&serial.matches(Regex("[A-Za-z0-9._:-]+")))
  check(user>=0&&uid/100000==user&&context.applicationInfo.uid==uid)
  check(context.packageName=="uk.co.traynor.privategallery.hiddenprototype"&&instrumentationName=="androidx.test.runner.AndroidJUnitRunner")
  thread.start();handler=Handler(thread.looper)
  try {context.registerReceiver(receiver,IntentFilter(actionName),"android.permission.DUMP",handler,Context.RECEIVER_EXPORTED);registered=true}
  catch(failure:Throwable){thread.quitSafely();thread.join(1000);throw failure}
 }
 suspend fun prepare(phase:String,arm:JSONObject):Claim {
  val claim=synchronized(lock){
   healthy();check(claims.size<phases.size&&phases[claims.size]==phase)
   check(claims.all{it.sample>0L&&it.encoded!=null})
   Claim(phase,arm.getString("token"),arm.getLong("sequence")).also{claims.add(it)}
  }
  try {withTimeout(1000){claim.ready.await()};return claim}
  catch(failure:Throwable){bad("Clock rendezvous cancelled/expired");handler.post{finish(claim,"Clock rendezvous cancelled/expired")};throw failure}
 }
 fun actionTimestamp(claim:Claim):Long=synchronized(lock){
  healthy();check(claims.lastOrNull()===claim&&claim.requestNonce!=null&&claim.sample==0L)
  val at=SystemClock.elapsedRealtimeNanos()
  check(at>=claim.claimedAt&&at-claim.claimedAt<=1_000_000_000L)
  claim.sample=at;claim.sampleOrder=++lifecycleOrder
  // No serialization, host ACK, I/O, sleep or await on the action path.
  check(handler.post{finish(claim)})
  at
 }
 // Read-only copies for actual instrumentation lifecycle controls; no mutation/bypass.
 fun identity():JSONObject=JSONObject().put("pid",pid).put("uid",uid).put("user",user).put("package",context.packageName).put("boot_id",boot).put("instrumentation",instrumentationName)
 fun observation():JSONObject=synchronized(lock){
  JSONObject().put("registered",registered).put("thread_alive",thread.isAlive).put("owner_thread_id",thread.id)
   .put("poison",poison?:JSONObject.NULL).put("poison_elapsed_ns",poisonAt).put("poison_order",poisonOrder).put("claims",JSONArray(claims.map { claim ->
    JSONObject().put("phase",claim.phase).put("accepted",claim.accepted).put("finish_count",claim.finishCount)
     .put("finish_thread_id",claim.finishThreadId).put("pending",claim.pending!=null).put("sample_elapsed_ns",claim.sample)
     .put("claimed_elapsed_ns",claim.claimedAt).put("sample_order",claim.sampleOrder).put("finish_order",claim.finishOrder).put("finish_elapsed_ns",claim.finishedAt)
   }))
 }
 suspend fun closeAndReceipt():JSONObject=withContext(NonCancellable){
  val drained=CompletableDeferred<Unit>()
  try {
   check(handler.post{
    try {
     synchronized(lock){closing=true}
     for(claim in claims)finish(claim,if(synchronized(lock){claim.sample==0L})"Clock closed without sample" else null)
     drained.complete(Unit)
    }catch(failure:Throwable){bad("Clock shutdown failed");drained.completeExceptionally(failure)}
   })
   withTimeout(2000){drained.await()}
  } finally {
   // Route cleanup failure/cancellation cannot skip receiver/thread retirement.
   try {if(registered){context.unregisterReceiver(receiver);registered=false}}
   finally {thread.quitSafely();withContext(Dispatchers.IO){thread.join(2000)};check(!thread.isAlive){"Clock HandlerThread still alive"}}
  }
  synchronized(lock){
   check(poison==null){checkNotNull(poison)}
   check(claims.map{it.phase}==phases&&claims.all{it.sample>0L&&it.finished&&it.pending==null&&it.encoded!=null})
   JSONObject().put("run_nonce",runNonce).put("serial",serial).put("user",user).put("boot_id",boot)
    .put("pid",pid).put("uid",uid).put("package",context.packageName).put("instrumentation",instrumentationName)
    .put("samples",JSONArray(claims.map{checkNotNull(it.encoded)})).put("closed",true)
  }
 }
}


/** Used by the real route finally AND injected cleanup-failure/cancellation controls. */
internal suspend fun retireRouteClock179(clock:EvidenceRouteClock179?,cleanup:suspend ()->Unit):JSONObject? {
 var failure:Throwable?=null
 try{cleanup()}catch(caught:Throwable){failure=caught}
 var receipt:JSONObject?=null
 try{receipt=clock?.closeAndReceipt()}catch(caught:Throwable){
  val primary=failure
  if(primary==null)failure=caught else primary.addSuppressed(caught)
 }
 failure?.let{throw it}
 return receipt
}
