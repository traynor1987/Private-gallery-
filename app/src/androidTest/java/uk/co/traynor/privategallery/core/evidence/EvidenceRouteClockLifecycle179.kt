package uk.co.traynor.privategallery.core.evidence

import android.app.Instrumentation
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Base64
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Actual in-process receiver/shell controls, separate from the six route samples. */
internal class EvidenceRouteClockLifecycle179(
 private val instrumentation:Instrumentation, private val arguments:Bundle,
 private val parentNonce:String, private val source:String
) {
 private val names=listOf("competing_samples","cancel_before_claim","cancel_after_claim","timeout_before_read","completion_deadline_race","late_stale_request","cleanup_throws","cancelled_caller")
 private val method="ownedVpnLossCancelsAdmittedPostAndRejectsOldRoute"
 private fun nonce()=UUID.randomUUID().toString().replace("-","")
 private suspend fun until(test:()->Boolean){withTimeout(4000){while(!test())delay(1)}}
 private fun parsed(event:JSONObject):Pair<Int,JSONObject?> {
  val match=checkNotNull(Regex("Broadcasting: Intent \\{[^\\r\\n]*}\\r?\\nBroadcast completed: result=(0|178|179)(?:, data=\"([A-Za-z0-9_-]+)\")?\\r?\\n\\r?\\nE179_CLOCK_STATUS=0\\r?\\n").matchEntire(event.getString("raw")))
  val code=match.groupValues[1].toInt();val encoded=match.groupValues[2]
  check((code==0)==encoded.isEmpty())
  return code to if(encoded.isEmpty())null else JSONObject(Base64.decode(encoded,Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING).toString(Charsets.UTF_8))
 }
 suspend fun run(output:File)=withTimeout(45_000){
  val scenarios=JSONArray();var identity:JSONObject?=null
  for(name in names){val result=scenario(name);identity=identity?:result.second;check(identity.toString()==result.second.toString());scenarios.put(result.first)}
  val receipt=JSONObject().put("schema",1).put("kind","android_clock_lifecycle_controls").put("source_sha",source)
   .put("case","vpn_revoke").put("method","ownedVpnPostCancellationAndAuthorityRevocationNeverFallback")
   .put("classname",EvidenceOwnedRoute179Test::class.java.name).put("parent_route_nonce",parentNonce).put("identity",checkNotNull(identity)).put("scenarios",scenarios)
  output.mkdirs();val file=File(output,"evidence179-clock-lifecycle.json");check(!file.exists());file.writeText(receipt.toString())
 }
 private suspend fun scenario(name:String):Pair<JSONObject,JSONObject> = withTimeout(8000){supervisorScope {
  val run=nonce();val routeNonce=nonce();val token=nonce()
  val args=Bundle(arguments).apply{putString("evidence179ClockRunNonce",run)}
  val clock=EvidenceRouteClock179(instrumentation.targetContext,args,"vpn_loss",method,routeNonce,source,instrumentation.javaClass.name)
  val identity=clock.identity();val events=mutableListOf<JSONObject>()
  val arm=JSONObject().put("token",token).put("sequence",1)
  var claim:EvidenceRouteClock179.Claim?=null
  var closeSucceeded=false;var closeFailure:Throwable?=null;var action=0L;var callerCancelled=false;var primary="";var deadlineTarget=0L;var deadlineRelease=0L
  suspend fun send(op:String,stale:Boolean=false,submitted:CompletableDeferred<Unit>?=null):JSONObject=withContext(Dispatchers.IO){
   val extras=linkedMapOf("op" to op,"run_nonce" to run,"source_sha" to source,"case" to "vpn_loss","method" to method,"phase" to "loss")
   if(op=="SAMPLE")extras.putAll(mapOf("nonce" to routeNonce,"arm_token" to if(stale)nonce() else token,"arm_sequence" to "1","request_nonce" to nonce()))
   val argv=mutableListOf("timeout","3","am","broadcast","--user",identity.getInt("user").toString(),"--receiver-foreground","-a","uk.co.traynor.privategallery.EVIDENCE179_CLOCK_$run","-p",identity.getString("package"))
   for((key,value) in extras){argv.addAll(listOf("--es",key,value))}
   check(argv.all{it.matches(Regex("[A-Za-z0-9_./:#=+\\-]+"))})
   val script=argv.joinToString(" "){"'$it'"}+"\n"+"""
e179_status=${'$'}?
printf '\nE179_CLOCK_STATUS=%s\n' "${'$'}e179_status"
exit "${'$'}e179_status"
""".trimIndent()+"\n"
   val sent=SystemClock.elapsedRealtimeNanos()
   // API34+: descriptors are [stdout, stdin, stderr]. Runtime.exec receives
   // only the two-token constant; shell syntax is transported verbatim on stdin.
   val fds=instrumentation.uiAutomation.executeShellCommandRwe("sh -s")
   try {
    check(fds.size==3)
    val out=async(Dispatchers.IO){ParcelFileDescriptor.AutoCloseInputStream(fds[0]).use{it.readNBytes(16385)}}
    val err=async(Dispatchers.IO){ParcelFileDescriptor.AutoCloseInputStream(fds[2]).use{it.readNBytes(16385)}}
    ParcelFileDescriptor.AutoCloseOutputStream(fds[1]).use{it.write(script.toByteArray(Charsets.UTF_8))}
    submitted?.complete(Unit)
    val bytes=out.await();val errors=err.await();val received=SystemClock.elapsedRealtimeNanos()
    check(bytes.size<=16384&&errors.size<=16384)
    fun encoded(value:ByteArray)=Base64.encodeToString(value,Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    val event=JSONObject().put("argv",JSONArray(argv)).put("entrypoint","sh -s").put("script",script)
     .put("stdout_b64",encoded(bytes)).put("stderr_b64",encoded(errors)).put("raw",bytes.toString(Charsets.UTF_8))
     .put("sent_elapsed_ns",sent).put("received_elapsed_ns",received)
    synchronized(events){events.add(event)};check(errors.isEmpty());parsed(event);event
   } finally {
    var failure:Throwable?=null
    for(fd in fds)try{fd.close()}catch(caught:Throwable){val prior=failure;if(prior==null)failure=caught else prior.addSuppressed(caught)}
    failure?.let{throw it}
   }
  }
  fun retired():Boolean=clock.observation().let{!it.getBoolean("registered")&&!it.getBoolean("thread_alive")}
  suspend fun close(cleanup:suspend ()->Unit={}) {
   check(!retired())
   try{retireRouteClock179(clock,cleanup);closeSucceeded=true}catch(failure:Throwable){closeFailure=failure}
   check(retired())
  }
  try {
   val prepared=async{clock.prepare("loss",arm)}
   until{clock.observation().getJSONArray("claims").length()==1}
   val ready=parsed(send("STATE"));check(ready.first==179&&ready.second!!.getString("kind")=="ready")
   check(ready.second!!.getString("arm_token")==token&&ready.second!!.getString("nonce")==routeNonce)
   when(name){
    "cancel_before_claim" -> {
     prepared.cancelAndJoin();callerCancelled=prepared.isCancelled
     check(runCatching{prepared.await()}.exceptionOrNull() is CancellationException)
     check(parsed(send("SAMPLE")).first==178);close()
    }
    "late_stale_request" -> {
     val failure=runCatching{prepared.await()}.exceptionOrNull();check(failure is TimeoutCancellationException)
     check(parsed(send("SAMPLE")).first==178);check(parsed(send("SAMPLE",stale=true)).first==178);close()
    }
    "competing_samples" -> {
     val gate=CompletableDeferred<Unit>();val submitted=List(2){CompletableDeferred<Unit>()}
     val replies=List(2){index->async(Dispatchers.IO){gate.await();send("SAMPLE",submitted=submitted[index])}}
     gate.complete(Unit);claim=prepared.await();submitted.forEach{it.await()}
     // An ordered delivery remains active through goAsync. Finish this one actual
     // read/reply so the queued duplicate can reach the consumed-request guard.
     action=clock.actionTimestamp(checkNotNull(claim))
     check(replies.map{parsed(it.await()).first}.sorted()==listOf(178,179))
     until{!clock.observation().isNull("poison")};close()
    }
    else -> {
     val reply=async(Dispatchers.IO){send("SAMPLE")};claim=prepared.await()
     when(name){
      "timeout_before_read" -> {check(parsed(reply.await()).first==178);close()}
      "completion_deadline_race" -> {
       // One completion attempt scheduled at the real claim deadline. Android decides
       // the winner; no action retry and no timestamp is moved after observation.
       val at=clock.observation().getJSONArray("claims").getJSONObject(0).getLong("claimed_elapsed_ns")+1_000_000_000L
       deadlineTarget=at
       val gate=CompletableDeferred<Unit>()
       val handler=Handler(Looper.getMainLooper());val release=Runnable{gate.complete(Unit)}
       check(handler.postDelayed(release,((at-SystemClock.elapsedRealtimeNanos()).coerceAtLeast(0))/1_000_000))
       try{gate.await()}finally{handler.removeCallbacks(release)}
       deadlineRelease=SystemClock.elapsedRealtimeNanos()
       check(deadlineRelease-at in -1_000_000L..50_000_000L)
       val attempted=runCatching{clock.actionTimestamp(checkNotNull(claim))};action=attempted.getOrDefault(0L)
       val result=parsed(reply.await())
       if(action>0L){check(result.first==179&&result.second!!.getLong("sample_elapsed_ns")==action)}
       else {check(attempted.exceptionOrNull() is IllegalStateException&&result.first==178)}
       close()
      }
      "cleanup_throws" -> {close{throw IllegalStateException("injected-route-cleanup")};check(closeFailure?.message=="injected-route-cleanup");check(closeFailure!!.suppressed.size==1);primary="injected-route-cleanup"}
      "cancel_after_claim" -> {
       val entered=CompletableDeferred<Unit>()
       val operation=async{close{entered.complete(Unit);awaitCancellation()}}
       entered.await();operation.cancelAndJoin();callerCancelled=operation.isCancelled;primary="cancelled"
       check(runCatching{operation.await()}.exceptionOrNull() is CancellationException)
       check(closeFailure is CancellationException&&closeFailure!!.suppressed.size==1)
      }
      "cancelled_caller" -> {
       val operation=async{currentCoroutineContext().cancel();close{currentCoroutineContext().ensureActive()}}
       operation.join();callerCancelled=operation.isCancelled;primary="cancelled"
       check(runCatching{operation.await()}.exceptionOrNull() is CancellationException)
       check(closeFailure is CancellationException&&closeFailure!!.suppressed.size==1)
      }
      else -> error("Unknown lifecycle scenario")
     }
     if(name!="completion_deadline_race")check(parsed(reply.await()).first==178)
    }
   }
   check(retired())
   val observation=clock.observation();val state=observation.getJSONArray("claims").getJSONObject(0)
   val accepted=if(name in listOf("cancel_before_claim","late_stale_request"))0 else 1
   check(state.getInt("accepted")==accepted&&state.getInt("finish_count")==accepted&&!state.getBoolean("pending"))
   check(state.getLong("finish_thread_id")==if(accepted==0)0L else observation.getLong("owner_thread_id"))
   val won=name=="completion_deadline_race"&&action>0L
   check(closeSucceeded==won&&observation.isNull("poison")==won)
   if(!won){
    val expected=when(name){
     "competing_samples"->"Rejected clock command: java.lang.IllegalStateException"
     "cancel_before_claim","late_stale_request"->"Clock rendezvous cancelled/expired"
     "timeout_before_read","completion_deadline_race"->"Clock sample deadline expired"
     else->"Clock closed without sample"
    }
    check(observation.getString("poison")==expected)
   }
   check(state.getLong("sample_elapsed_ns")==action)
   if(name=="competing_samples"){
    check(action>0L&&state.getLong("sample_order")<state.getLong("finish_order")&&state.getLong("finish_order")<observation.getLong("poison_order"))
    check(action<=state.getLong("finish_elapsed_ns")&&state.getLong("finish_elapsed_ns")<=observation.getLong("poison_elapsed_ns"))
   }
   if(!won)check(closeFailure is IllegalStateException||closeFailure is CancellationException)
   if(name in listOf("cancel_before_claim","cancel_after_claim","cancelled_caller"))check(callerCancelled)
   val post=runCatching{clock.actionTimestamp(claim?:EvidenceRouteClock179.Claim("loss",token,1))}.exceptionOrNull()
   check(post is IllegalStateException)
   check(post.message==if(observation.isNull("poison"))"Clock is closing" else observation.getString("poison"))
   check(parsed(send("STATE")).first==0) // Actual OS delivery after unregister finds no receiver.
   val ordered=synchronized(events){events.sortedBy{it.getLong("sent_elapsed_ns")}}
   if(name=="competing_samples"){
    val samples=ordered.filter{it.getJSONArray("argv").toString().contains("SAMPLE")}
    check(samples.size==2&&samples.maxOf{it.getLong("sent_elapsed_ns")}<=samples.minOf{it.getLong("received_elapsed_ns")})
   }
   JSONObject().put("name",name).put("run_nonce",run).put("nonce",routeNonce).put("arm_token",token)
    .put("events",JSONArray(ordered)).put("observation",observation).put("close_succeeded",closeSucceeded)
    .put("post_close_action_error",post.message).put("action_elapsed_ns",action).put("primary_error",primary).put("caller_cancelled",callerCancelled)
    .put("deadline_target_elapsed_ns",deadlineTarget).put("deadline_release_elapsed_ns",deadlineRelease)
    .put("close_error_type",closeFailure?.javaClass?.name?:"").put("close_error_message",closeFailure?.message?:"")
    .put("suppressed_close_errors",JSONArray(closeFailure?.suppressed.orEmpty().map{JSONObject().put("type",it.javaClass.name).put("message",it.message?:"")})) to identity
  } catch(failure:Throwable){
   coroutineContext.cancelChildren()
   if(!retired())retireRouteClock179(clock){throw failure}
   throw failure
  }
 }}
}
