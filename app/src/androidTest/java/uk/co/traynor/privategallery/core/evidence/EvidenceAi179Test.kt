package uk.co.traynor.privategallery.core.evidence

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.traynor.privategallery.core.editor.AiHttpRequest
import uk.co.traynor.privategallery.core.editor.AiHttpResponse
import uk.co.traynor.privategallery.core.vault.VaultItem
import uk.co.traynor.privategallery.core.vault.VaultItemState

/** No paid provider calls, real credentials, or default network. Requires Android JSON/bitmap runtime. */
@RunWith(AndroidJUnit4::class)
class EvidenceAi179Test {
 private class Fixture(val bytes:ByteArray="original".toByteArray(),val mime:String="text/plain",val restricted:Boolean=false) {
  val id=UUID.randomUUID().toString();val sourceId=UUID.randomUUID().toString()
  val hash=MessageDigest.getInstance("SHA-256").digest(bytes)
  val item=VaultItem(sourceId,mime,"PRIVATE_FILENAME_GPS",1,bytes.size.toLong(),hash,ByteArray(12),VaultItemState.COMPLETE,vaultOnly=restricted)
  val evidence=EvidenceCatalog.Evidence(id,sourceId,"Selected title",mime,hash.joinToString(""){ "%02x".format(it.toInt() and 255)},bytes.size.toLong(),1,
   EvidenceCatalog.Metadata("PRIVATE_FILENAME_GPS",0,0,0,0,0,0,mapOf("GPS" to "PRIVATE_GPS"),restricted),"Selected description","Selected note",listOf("Selected tag"),listOf("Selected participant"),emptyList(),emptyList())
  val catalog=EvidenceCatalog.empty(EvidenceCatalog.Owner.PRIMARY).upsertEvidence(evidence,1,"IMPORT")
  var active=true;var changed=false;var eof=false;var readCount=0;var liveItem=item;var afterConsume:()->Unit={}
  val reader=object:EvidenceSourceReader {
   override val owner=EvidenceCatalog.Owner.PRIMARY
   override fun current()=active
   override fun inspect(sourceId:String)=EvidenceOriginal(if(changed)liveItem.copy(vaultOnly=true)else liveItem,evidence.metadata())
   override fun <T> readVerified(sourceId:String,consume:(EvidenceOriginal,InputStream)->T):T {
    readCount++
    val input=object:ByteArrayInputStream(bytes){override fun read(b:ByteArray,off:Int,len:Int):Int=super.read(b,off,len).also{if(it<0)eof=true}}
    return consume(inspect(sourceId),input).also{check(eof);afterConsume()}
   }
  }
 }
 private class Route(override val owner:EvidenceCatalog.Owner=EvidenceCatalog.Owner.PRIMARY):EvidenceRoute {
  override val label="Controlled fixture";override val identity=UUID.randomUUID().toString()
  var active=true;var posts=0;var payload="";var authorization="";var onExecute:()->Unit={};var onWrite:()->Unit={};var lastResponse:ByteArray?=null
  var response=envelope("""{"summary":"Advisory result","suggestions":[],"events":[]}""")
  override fun current()=active
  override suspend fun execute(request:AiHttpRequest):AiHttpResponse {
   posts++;assertEquals(EvidenceAi.ENDPOINT,request.url);assertEquals("POST",request.method)
   authorization=request.headers["Authorization"].orEmpty()
   payload=object:ByteArrayOutputStream(){override fun write(b:ByteArray,off:Int,len:Int){onWrite();super.write(b,off,len)}}.use{request.body!!.writeTo(it);it.toString("UTF-8")};onExecute()
   val bytes=response.toByteArray();lastResponse=bytes;return AiHttpResponse(200,"application/json",bytes)
  }
  override fun close(){active=false}
  companion object {
   fun envelope(text:String,status:String="completed")=JSONObject().put("status",status).put("output",org.json.JSONArray().put(JSONObject().put("type","message").put("content",org.json.JSONArray().put(JSONObject().put("type","output_text").put("text",text))))).put("usage",JSONObject().put("input_tokens",10).put("output_tokens",3)).toString()
  }
 }
 private class Keys:EvidenceCredentials.Backend {
  var bytes:ByteArray?="fixture-key".toByteArray();var reads=0
  override fun configured()=bytes!=null
  override fun read():ByteArray? {reads++;return bytes?.copyOf()}
  override fun save(value:ByteArray){bytes?.fill(0);bytes=value.copyOf()}
  override fun clear(){bytes?.fill(0);bytes=null}
 }
 private fun credentials(keys:Keys,owner:EvidenceCatalog.Owner=EvidenceCatalog.Owner.PRIMARY,current:()->Boolean={true})=
  EvidenceCredentials(owner,current,{action->check(current());action();check(current())},keys,{})
 private suspend fun preview(f:Fixture,r:Route,images:List<String> = emptyList(),model:EvidenceAi.Model=EvidenceAi.Model.LUNA)=
  EvidenceAi.prepare(f.catalog,listOf(f.id),images,model,EvidenceAi.Task.FACTUAL_SUMMARY,f.reader,r)
 private suspend fun rejected(action:suspend()->Unit){try{action();fail("operation unexpectedly permitted")}catch(_:EvidenceAiFailure){}catch(_:SecurityException){}catch(_:IllegalArgumentException){}catch(_:IllegalStateException){}}

 @Test fun prepareMakesZeroPostsAndExactFramingHasOnlySelectedContent()=runBlocking {
  val f=Fixture();val route=Route();val keys=Keys();val p=preview(f,route)
  try {
   assertEquals(0,route.posts);assertEquals(0,keys.reads);assertTrue(f.eof)
   val exact=p.exactText
   for(s in listOf("Selected title","Selected description","Selected note","Selected tag","Selected participant"))assertTrue(exact.contains(s))
   assertFalse(exact.contains("PRIVATE_FILENAME_GPS"));assertFalse(exact.contains("PRIVATE_GPS"));assertTrue(p.images.isEmpty())
   assertTrue(p.estimatedUsd>0);assertTrue(p.disclosure.contains("30 days"))
   val analysis=EvidenceAi.analyze(p,credentials(keys)){EvidenceEgress.digest(f.catalog)}
   val body=JSONObject(route.payload)
   assertEquals(exact,body.getJSONArray("input").getJSONObject(0).getJSONArray("content").getJSONObject(0).getString("text"))
   assertFalse(body.getBoolean("store"));assertFalse(body.getBoolean("background"));assertFalse(body.getBoolean("stream"))
   assertEquals("none",body.getJSONObject("reasoning").getString("effort"));assertEquals(2048,body.getInt("max_output_tokens"));assertEquals(0,body.getJSONArray("tools").length())
   assertEquals(listOf(f.id),analysis.evidenceIds());assertFalse(analysis.applied());assertEquals(10L,analysis.inputTokens());assertTrue(analysis.text().contains("ADVISORY"))
   assertEquals(1,keys.reads);assertEquals("Bearer fixture-key",route.authorization)
   rejected{EvidenceAi.analyze(p,credentials(keys)){EvidenceEgress.digest(f.catalog)}};assertEquals(1,route.posts)
  }finally{p.close()}
 }
 @Test fun imagesAreExplicitBoundedReencodedAndThumbnailCopiesAreIndependent()=runBlocking {
  val b=Bitmap.createBitmap(2048,512,Bitmap.Config.ARGB_8888)
  val bytes=try{ByteArrayOutputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it);it.toByteArray()}}finally{b.eraseColor(0);b.recycle()}
  val f=Fixture(bytes,"image/png");val first=preview(f,Route());assertTrue(first.images.isEmpty());first.close()
  val route=Route();val p=preview(f,route,listOf(f.id));val image=p.images.single()
  assertTrue(image.width<=1024&&image.height<=1024);assertTrue(image.encodedBytes<=2*1024*1024)
  val thumbnail=image.copyThumbnail();thumbnail.fill(0);assertTrue(image.copyThumbnail().any{it!=0.toByte()})
  EvidenceAi.analyze(p,credentials(Keys())){EvidenceEgress.digest(f.catalog)}
  val content=JSONObject(route.payload).getJSONArray("input").getJSONObject(0).getJSONArray("content")
  assertEquals(2,content.length());assertEquals("input_image",content.getJSONObject(1).getString("type"));assertEquals("high",content.getJSONObject(1).getString("detail"))
  rejected{image.copyThumbnail()};assertEquals(1,route.posts)
 }
 @Test fun foreignOwnerVaultOnlyAndChangedSourcesBlockBeforeCredentialReadOrPost()=runBlocking {
  val f=Fixture();rejected{preview(f,Route(EvidenceCatalog.Owner.HIDDEN))}
  rejected{preview(Fixture(restricted=true),Route())}
  val route=Route();val p=preview(f,route);f.changed=true;val keys=Keys()
  rejected{EvidenceAi.analyze(p,credentials(keys)){EvidenceEgress.digest(f.catalog)}}
  assertEquals(0,keys.reads);assertEquals(0,route.posts);p.close()
 }
 @Test fun changedDigestRevisionRouteAndForeignCredentialRetireConsent()=runBlocking {
  val f=Fixture();val keys=Keys()
  var route=Route();var p=preview(f,route)
  rejected{EvidenceAi.analyze(p,credentials(keys)){"changed"}};assertEquals(0,route.posts);p.close()
  route=Route();p=preview(f,route);route.close();rejected{EvidenceAi.analyze(p,credentials(keys)){EvidenceEgress.digest(f.catalog)}};p.close()
  route=Route();p=preview(f,route);rejected{EvidenceAi.analyze(p,credentials(keys,EvidenceCatalog.Owner.HIDDEN)){EvidenceEgress.digest(f.catalog)}};p.close()
  assertEquals(0,keys.reads)
  val luna=preview(f,Route());val terra=preview(f,Route(),model=EvidenceAi.Model.TERRA)
  assertNotEquals(luna.consentDigest,terra.consentDigest);luna.close();terra.close()
 }
 @Test fun revocationAfterSendNeverPublishesAndProviderFailuresNeverEchoSecrets()=runBlocking {
  val f=Fixture();val route=Route();val p=preview(f,route);route.onExecute={f.active=false}
  rejected{EvidenceAi.analyze(p,credentials(Keys())){EvidenceEgress.digest(f.catalog)}};assertEquals(1,route.posts)
  val fresh=Fixture();val bad=Route();bad.response="fixture-key Selected note INVALID JSON"
  try{EvidenceAi.analyze(preview(fresh,bad),credentials(Keys())){EvidenceEgress.digest(fresh.catalog)};fail("bad response")}
  catch(failure:EvidenceAiFailure){assertFalse(failure.message.orEmpty().contains("fixture-key"));assertFalse(failure.message.orEmpty().contains("Selected note"))}
 }
 @Test fun refusalIncompleteAndForeignStructuredReferencesRemainBoundedAdvisory()=runBlocking {
  for((status,content,label)in listOf(Triple("incomplete",JSONObject().put("type","output_text").put("text","{\"summary\":\"Partial\",\"suggestions\":[],\"events\":[]}").toString(),"INCOMPLETE"),Triple("completed","{\"type\":\"refusal\",\"refusal\":\"Cannot assist\"}","REFUSAL"))){
   val f=Fixture();val r=Route();r.response="{\"status\":\"$status\",\"output\":[{\"type\":\"message\",\"content\":[$content]}]}"
   val a=EvidenceAi.analyze(preview(f,r),credentials(Keys())){EvidenceEgress.digest(f.catalog)};assertTrue(a.text().contains(label));assertFalse(a.applied())
  }
  val f=Fixture();val r=Route();r.response=JSONObject().put("status","completed").put("output",org.json.JSONArray().put(JSONObject().put("type","message").put("content",org.json.JSONArray().put(JSONObject().put("type","output_text").put("text","{\"evidence_ids\":[\"foreign\"]}"))))).toString()
  rejected{EvidenceAi.analyze(preview(f,r),credentials(Keys())){EvidenceEgress.digest(f.catalog)}}
 }
 @Test fun selectedTextAndResponseLimitsAreRejectedWithoutTruncatingConsent()=runBlocking {
  val f=Fixture();rejected{EvidenceAi.prepare(f.catalog,listOf(f.id,f.id),emptyList(),EvidenceAi.Model.LUNA,EvidenceAi.Task.FACTUAL_SUMMARY,f.reader,Route())}
  rejected{EvidenceAi.prepare(f.catalog,listOf(f.id),listOf(UUID.randomUUID().toString()),EvidenceAi.Model.LUNA,EvidenceAi.Task.FACTUAL_SUMMARY,f.reader,Route())}
  val e=f.evidence
  val large=EvidenceCatalog.Evidence(e.id(),e.sourceId(),e.name(),e.mime(),e.sha256(),e.size(),e.importedAt(),e.metadata(),"d".repeat(60000),"n".repeat(60000),emptyList(),emptyList(),emptyList(),emptyList())
  val largeCatalog=EvidenceCatalog.empty(EvidenceCatalog.Owner.PRIMARY).upsertEvidence(large,1,"IMPORT")
  val noPost=Route();rejected{EvidenceAi.prepare(largeCatalog,listOf(f.id),emptyList(),EvidenceAi.Model.LUNA,EvidenceAi.Task.FACTUAL_SUMMARY,f.reader,noPost)};assertEquals(0,noPost.posts)
  val r=Route();r.response="x".repeat(2*1024*1024+1)
  rejected{EvidenceAi.analyze(preview(f,r),credentials(Keys())){EvidenceEgress.digest(f.catalog)}}
 }
 @Test fun retainedUnrelatedIncidentAndAnalysisTextNeverEnterDirectSelectedPayload()=runBlocking {
  val f=Fixture();val restricted=Fixture(restricted=true)
  val incident=EvidenceCatalog.Incident(UUID.randomUUID().toString(),"UNRELATED RETAINED TITLE",0,0,"UNRELATED DESCRIPTION","UNRELATED INCIDENT NOTES",emptyList(),emptyList(),emptyList(),false,false,listOf(EvidenceEgress.fingerprint(restricted.evidence)))
  val prior=EvidenceCatalog.Analysis(UUID.randomUUID().toString(),2,"fixture","fixture",EvidenceEgress.digest(f.catalog),listOf(f.id),"UNRELATED EARLIER ANALYSIS",0,0,false,listOf(EvidenceEgress.fingerprint(f.evidence)))
  val catalog=EvidenceCatalog(f.catalog.owner(),f.catalog.revision()+1,f.catalog.evidence(),listOf(incident),f.catalog.audit(),listOf(prior),emptyList())
  val p=EvidenceAi.prepare(catalog,listOf(f.id),emptyList(),EvidenceAi.Model.LUNA,EvidenceAi.Task.STRUCTURE,f.reader,Route())
  try{assertFalse(p.exactText.contains("UNRELATED"));assertEquals(listOf(EvidenceEgress.fingerprint(f.evidence)),p.sourceFingerprints);assertTrue(p.exactText.contains("Unknown fields are null"));assertEquals(1,f.readCount)}finally{p.close()}
 }
 @Test fun actualKeyEchoIncludingEscapedJsonNeverEntersAdvisory()=runBlocking {
  fun structured(summary:String)=JSONObject().put("summary",summary).put("suggestions",org.json.JSONArray()).put("events",org.json.JSONArray()).toString()
  val selected=Fixture()
  val safeSummary="Unsafe fixture-public"
  val positive=Route().also{it.response=Route.envelope(structured(safeSummary))}
  val accepted=EvidenceAi.analyze(preview(selected,positive),credentials(Keys())){EvidenceEgress.digest(selected.catalog)}
  assertEquals(safeSummary,EvidenceAi.details(accepted).summary);assertFalse(accepted.applied());assertEquals(1,positive.posts)

  val literal=Route.envelope(structured("Unsafe fixture-key"))
  val outerEscaped=literal.replace("fixture-key","fixture-\\u006bey")
  val nestedEscaped=Route.envelope(structured("Unsafe fixture-key").replace("fixture-key","fixture-\\u006bey"))
  fun outputText(envelope:String)=JSONObject(envelope).getJSONArray("output").getJSONObject(0).getJSONArray("content").getJSONObject(0).getString("text")
  // All three decode to the same valid schema. The nested case only becomes the
  // actual key after parsing the structured JSON, exercising the final echo guard.
  for(envelope in listOf(literal,outerEscaped,nestedEscaped))assertEquals("Unsafe fixture-key",JSONObject(outputText(envelope)).getString("summary"))
  assertTrue(outputText(outerEscaped).contains("fixture-key"));assertFalse(outputText(nestedEscaped).contains("fixture-key"))
  val refusal=JSONObject().put("status","completed").put("output",org.json.JSONArray().put(JSONObject().put("type","message").put("content",org.json.JSONArray().put(JSONObject().put("type","refusal").put("refusal","Unsafe fixture-key"))))).toString()
  for(envelope in listOf(literal,outerEscaped,nestedEscaped,refusal)){
   val f=Fixture();val route=Route().also{it.response=envelope};var analysis:EvidenceCatalog.Analysis?=null
   try{analysis=EvidenceAi.analyze(preview(f,route),credentials(Keys())){EvidenceEgress.digest(f.catalog)};fail("credential echo returned an Analysis")}
   catch(failure:EvidenceAiFailure){assertEquals("OpenAI returned an unsupported, oversized or invalid analysis response.",failure.message);assertFalse(failure.message.orEmpty().contains("fixture-key"))}
   assertNull(analysis);assertEquals(1,route.posts);assertTrue(route.lastResponse!!.all{it==0.toByte()})
  }
 }
 @Test fun bodyAndConsentModelDigestChangeAndClosedCopiesCannotBeUsed()=runBlocking {
  val f=Fixture();val route=Route();val first=preview(f,route)
  val second=preview(f,route,model=EvidenceAi.Model.TERRA)
  assertNotEquals(first.bodyDigest,second.bodyDigest);assertNotEquals(first.consentDigest,second.consentDigest)
  first.close();rejected{first.exactText};rejected{EvidenceAi.analyze(first,credentials(Keys())){EvidenceEgress.digest(f.catalog)}};assertEquals(0,route.posts);second.close()
 }
 @Test fun strictOuterAndOutputGrammarRejectsCommentQuotesSingleQuotesTrailingAndDeepInput()=runBlocking {
  val exploit="{\"ignored\":/*\"*/"+"[".repeat(12000)+"0"+"]".repeat(12000)+"/*\"*/}"
  for(value in listOf(exploit,"{'summary':'single quoted'}","{\"summary\":\"x\"} trailing","{\"summary\":\"x\",}","[".repeat(12000)+"0"+"]".repeat(12000))){
   val f=Fixture();val r=Route();r.response=Route.envelope(value)
   try{EvidenceAi.analyze(preview(f,r),credentials(Keys())){EvidenceEgress.digest(f.catalog)};fail("unchecked output grammar")}
   catch(failure:EvidenceAiFailure){assertFalse(failure.message.orEmpty().contains("ignored"))}
  }
  for(modify in listOf<(String)->String>({it.replace("\"status\"","'status'")},{"/* comment */"+it},{it+"{}"},{it.dropLast(1)+",\"ignored\":/*\"*/"+"[".repeat(12000)+"0"+"]".repeat(12000)+"/*\"*/}"})){
   val f=Fixture();val r=Route();r.response=modify(r.response);rejected{EvidenceAi.analyze(preview(f,r),credentials(Keys())){EvidenceEgress.digest(f.catalog)}}
  }
 }
 @Test fun onlySingleStrictObjectAcceptsSelectedStructuredReferencesAndWrappersFailClosed()=runBlocking {
  val f=Fixture()
  fun value(id:String)="""{"summary":"Selected structure","suggestions":[{"kind":"GROUP","evidence_ids":["$id"],"title":"x","reasons":["x"]}],"events":[]}"""
  val selected=value(f.id)
  val accepted=Route().also{it.response=Route.envelope(selected)}
  assertTrue(EvidenceAi.analyze(preview(f,accepted),credentials(Keys())){EvidenceEgress.digest(f.catalog)}.text().contains("Selected structure"))
  for(raw in listOf(value("foreign"),"```json\n"+value("foreign")+"\n```","Explanation:\n"+value("foreign"),"["+selected+"]",selected+"\n"+selected,"```json\n"+selected+"\n```",selected.dropLast(1)+",\"summary\":\"duplicate\"}")){
   val r=Route().also{it.response=Route.envelope(raw)}
   rejected{EvidenceAi.analyze(preview(f,r),credentials(Keys())){EvidenceEgress.digest(f.catalog)}}
  }
  val multiple=Route().also{route->val outer=JSONObject(Route.envelope(selected));val parts=outer.getJSONArray("output").getJSONObject(0).getJSONArray("content");parts.put(JSONObject().put("type","output_text").put("text",selected));route.response=outer.toString()}
  rejected{EvidenceAi.analyze(preview(f,multiple),credentials(Keys())){EvidenceEgress.digest(f.catalog)}}
 }
 @Test fun simultaneousOneShotAttemptsAdmitAtMostOnePostAndRetireBothResults()=runBlocking {
  val f=Fixture();val r=Route();val keys=Keys();val credential=credentials(keys);val p=preview(f,r)
  val entered=java.util.concurrent.CountDownLatch(1);val release=java.util.concurrent.CountDownLatch(1)
  r.onExecute={entered.countDown();check(release.await(5,java.util.concurrent.TimeUnit.SECONDS))}
  kotlinx.coroutines.supervisorScope {
   val first=async(kotlinx.coroutines.Dispatchers.IO){try{EvidenceAi.analyze(p,credential){EvidenceEgress.digest(f.catalog)}}catch(_:EvidenceAiFailure){null}}
   try {
    assertTrue(kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO){entered.await(5,java.util.concurrent.TimeUnit.SECONDS)})
    rejected{EvidenceAi.analyze(p,credential){EvidenceEgress.digest(f.catalog)}};assertEquals(1,r.posts);assertEquals(1,keys.reads)
    release.countDown();assertNull(kotlinx.coroutines.withTimeout(5000){first.await()});assertFalse(r.active)
   }finally{release.countDown();first.cancel();p.close()}
  }
 }
 @Test fun credentialRemovalDuringBodyWriteCancelsRouteAndPublishesNoAnalysis()=runBlocking {
  val f=Fixture();val r=Route();val credential=credentials(Keys());val p=preview(f,r)
  val entered=java.util.concurrent.CountDownLatch(1);val release=java.util.concurrent.CountDownLatch(1)
  r.onWrite={entered.countDown();check(release.await(5,java.util.concurrent.TimeUnit.SECONDS))}
  kotlinx.coroutines.supervisorScope {
   val pending=async(kotlinx.coroutines.Dispatchers.IO){try{EvidenceAi.analyze(p,credential){EvidenceEgress.digest(f.catalog)}}catch(_:EvidenceAiFailure){null}}
   try{
    assertTrue(kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO){entered.await(5,java.util.concurrent.TimeUnit.SECONDS)})
    credential.remove();assertFalse(r.active);release.countDown();assertNull(kotlinx.coroutines.withTimeout(5000){pending.await()});assertEquals(1,r.posts)
   }finally{release.countDown();pending.cancel();p.close()}
  }
 }
 @Test fun actualIdentityRevisionCorrectionForeignReaderAndFailedAuthenticatedEofRejectConsent()=runBlocking {
  for(change in listOf("hash","size","mime")){
   val f=Fixture();val r=Route();val p=preview(f,r);val keys=Keys()
   f.liveItem=when(change){"hash"->f.item.copy(plaintextSha256=ByteArray(32){7});"size"->f.item.copy(plaintextSize=f.bytes.size+1L);else->f.item.copy(mimeType="text/csv")}
   rejected{EvidenceAi.analyze(p,credentials(keys)){EvidenceEgress.digest(f.catalog)}};assertEquals(0,r.posts);assertEquals(0,keys.reads)
  }
  val f=Fixture();val r=Route();val p=preview(f,r)
  val next=EvidenceCatalog(f.catalog.owner(),f.catalog.revision()+1,f.catalog.evidence(),f.catalog.incidents(),f.catalog.audit(),f.catalog.analyses(),f.catalog.rejectedSuggestions())
  rejected{EvidenceAi.analyze(p,credentials(Keys())){EvidenceEgress.digest(next)}};assertEquals(0,r.posts)
  val ocr=EvidenceCatalog.Ocr(UUID.randomUUID().toString(),2,"FIXTURE",0,1,1,"raw transcript",-1f,emptyList(),emptyList(),1,false)
  val extracted=f.catalog.saveOcr(f.id,ocr,2);val corrected=extracted.reviseOcr(f.id,ocr.id(),"owner correction",3)
  val rr=Route();val pp=EvidenceAi.prepare(extracted,listOf(f.id),emptyList(),EvidenceAi.Model.LUNA,EvidenceAi.Task.FACTUAL_SUMMARY,f.reader,rr)
  rejected{EvidenceAi.analyze(pp,credentials(Keys())){EvidenceEgress.digest(corrected)}};assertEquals(0,rr.posts)
  val foreign=object:EvidenceSourceReader by f.reader {override val owner=EvidenceCatalog.Owner.HIDDEN}
  rejected{EvidenceAi.prepare(f.catalog,listOf(f.id),emptyList(),EvidenceAi.Model.LUNA,EvidenceAi.Task.FACTUAL_SUMMARY,foreign,Route())}
  f.afterConsume={error("authenticated EOF failed")};rejected{preview(f,Route())};assertTrue(f.eof)
 }
 @Test fun prepareCancellationAfterConsumedBytesBeforeReaderReturnPublishesNothing()=runBlocking {
  val bitmap=Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888)
  val bytes=try{ByteArrayOutputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it);it.toByteArray()}}finally{bitmap.eraseColor(0);bitmap.recycle()}
  val f=Fixture(bytes,"image/png");val saved=bytes.copyOf();val entered=java.util.concurrent.CountDownLatch(1);val release=java.util.concurrent.CountDownLatch(1)
  f.afterConsume={error("authenticated image EOF failed")};rejected{preview(f,Route(),listOf(f.id))}
  f.afterConsume={entered.countDown();check(release.await(5,java.util.concurrent.TimeUnit.SECONDS))}
  kotlinx.coroutines.coroutineScope {
   val pending=async(kotlinx.coroutines.Dispatchers.IO){preview(f,Route(),listOf(f.id))}
   try{
    assertTrue(kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO){entered.await(5,java.util.concurrent.TimeUnit.SECONDS)})
    pending.cancel();release.countDown();try{pending.await();fail("cancelled prepare returned preview")}catch(_:kotlinx.coroutines.CancellationException){}
    assertArrayEquals(saved,f.bytes)
   }finally{release.countDown();pending.cancel();saved.fill(0);bytes.fill(0)}
  }
 }
 @Test fun incompleteWithoutOutputIsLabelledAndOutputCharacterOverflowIsRejected()=runBlocking {
  val f=Fixture();val empty=Route().also{it.response="{\"status\":\"incomplete\",\"output\":[],\"usage\":{\"input_tokens\":0,\"output_tokens\":0}}"}
  val result=EvidenceAi.analyze(preview(f,empty),credentials(Keys())){EvidenceEgress.digest(f.catalog)};assertTrue(result.text().contains("INCOMPLETE"));assertTrue(result.text().contains("No analysis text"))
  val over=Route().also{it.response=Route.envelope(JSONObject().put("summary","x".repeat(100001)).put("suggestions",org.json.JSONArray()).put("events",org.json.JSONArray()).toString())}
  rejected{EvidenceAi.analyze(preview(f,over),credentials(Keys())){EvidenceEgress.digest(f.catalog)}}
  val emptyObject=JSONObject().put("summary","").put("suggestions",org.json.JSONArray()).put("events",org.json.JSONArray())
  val atProviderLimit=emptyObject.put("summary","x".repeat(100000-emptyObject.toString().length)).toString();assertEquals(100000,atProviderLimit.length)
  val receiptOverflow=Route().also{it.response=Route.envelope(atProviderLimit)}
  rejected{EvidenceAi.analyze(preview(f,receiptOverflow),credentials(Keys())){EvidenceEgress.digest(f.catalog)}};assertEquals(1,receiptOverflow.posts)
 }
 @Test fun typedDetailsReusesStrictSchemaAndSelectedIdsAndKeepsReceiptAppOwned()=runBlocking {
  val f=Fixture();val model="""{"summary":"Quoted source claim","suggestions":[{"kind":"GROUP","evidence_ids":["${f.id}"],"title":"Possible group","reasons":["Visible content"]}],"events":[{"evidence_ids":["${f.id}"],"title":"Possible event","date_time_quote":null,"provenance":"Source claim","uncertainty":"Time unknown"}]}"""
  val r=Route().also{it.response=Route.envelope(model)}
  val analysis=EvidenceAi.analyze(preview(f,r),credentials(Keys())){EvidenceEgress.digest(f.catalog)}
  val details=EvidenceAi.details(analysis)
  assertEquals("Quoted source claim",details.summary);assertEquals(listOf(f.id),details.suggestions.single().evidenceIds);assertNull(details.events.single().dateTimeQuote);assertEquals("completed",details.status);assertFalse(details.refusal)
  assertEquals(JSONObject(model).getJSONArray("events").toString(),JSONObject(analysis.text()).getJSONArray("events").toString());assertTrue(JSONObject(analysis.text()).has("_receipt"))
  try{(details.suggestions as MutableList<*>).clear();fail("mutable details")}catch(_:UnsupportedOperationException){}
  fun altered(text:String)=EvidenceCatalog.Analysis(analysis.id(),analysis.at(),analysis.model(),analysis.task(),analysis.inputDigest(),analysis.evidenceIds(),text,analysis.inputTokens(),analysis.outputTokens(),false,analysis.lineage())
  rejected{EvidenceAi.details(altered(analysis.text().replace(f.id,"foreign")))}
  val forged=JSONObject(model).put("_receipt",JSONObject().put("advisory",true));val forbidden=Route().also{it.response=Route.envelope(forged.toString())}
  rejected{EvidenceAi.analyze(preview(f,forbidden),credentials(Keys())){EvidenceEgress.digest(f.catalog)}}
 }
 @Test fun selectedRecordImageAndTextQuotaBoundariesHavePositiveAndNegativeCases()=runBlocking {
  fun reader(fixtures:List<Fixture>)=object:EvidenceSourceReader {
   override val owner=EvidenceCatalog.Owner.PRIMARY
   override fun current()=true
   override fun inspect(sourceId:String)=fixtures.single{it.sourceId==sourceId}.reader.inspect(sourceId)
   override fun <T> readVerified(sourceId:String,consume:(EvidenceOriginal,InputStream)->T):T=fixtures.single{it.sourceId==sourceId}.reader.readVerified(sourceId,consume)
  }
  fun catalog(fixtures:List<Fixture>)=EvidenceCatalog(EvidenceCatalog.Owner.PRIMARY,1,fixtures.map{it.evidence},emptyList(),emptyList(),emptyList(),emptyList())
  val records=List(32){Fixture()};val all=catalog(records)
  EvidenceAi.prepare(all,records.map{it.id},emptyList(),EvidenceAi.Model.LUNA,EvidenceAi.Task.FACTUAL_SUMMARY,reader(records),Route()).use{assertEquals(32,it.selectedIds.size)}
  rejected{EvidenceAi.prepare(all,records.map{it.id}+UUID.randomUUID().toString(),emptyList(),EvidenceAi.Model.LUNA,EvidenceAi.Task.FACTUAL_SUMMARY,reader(records),Route())}
  val f=Fixture();val e=f.evidence
  fun textCatalog(extra:Int):EvidenceCatalog {
   val record=EvidenceCatalog.Evidence(e.id(),e.sourceId(),"n",e.mime(),e.sha256(),e.size(),e.importedAt(),e.metadata(),"d".repeat(50000+extra),"n".repeat(49999),emptyList(),emptyList(),emptyList(),emptyList())
   return EvidenceCatalog.empty(EvidenceCatalog.Owner.PRIMARY).upsertEvidence(record,1,"IMPORT")
  }
  EvidenceAi.prepare(textCatalog(0),listOf(f.id),emptyList(),EvidenceAi.Model.LUNA,EvidenceAi.Task.FACTUAL_SUMMARY,f.reader,Route()).close()
  rejected{EvidenceAi.prepare(textCatalog(1),listOf(f.id),emptyList(),EvidenceAi.Model.LUNA,EvidenceAi.Task.FACTUAL_SUMMARY,f.reader,Route())}
  val bitmap=Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888)
  val bytes=try{ByteArrayOutputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it);it.toByteArray()}}finally{bitmap.eraseColor(0);bitmap.recycle()}
  val images=List(9){Fixture(bytes.copyOf(),"image/png")};val imageCatalog=catalog(images)
  EvidenceAi.prepare(imageCatalog,images.take(8).map{it.id},images.take(8).map{it.id},EvidenceAi.Model.LUNA,EvidenceAi.Task.FACTUAL_SUMMARY,reader(images),Route()).use{assertEquals(8,it.images.size)}
  rejected{EvidenceAi.prepare(imageCatalog,images.map{it.id},images.map{it.id},EvidenceAi.Model.LUNA,EvidenceAi.Task.FACTUAL_SUMMARY,reader(images),Route())}
  bytes.fill(0);images.forEach{it.bytes.fill(0)}
 }
 @Test fun credentialReplacementDuringResponseWaitRejectsAndWipesLateBytes()=runBlocking {
  val f=Fixture();val r=Route();val credential=credentials(Keys());val p=preview(f,r)
  val entered=java.util.concurrent.CountDownLatch(1);val release=java.util.concurrent.CountDownLatch(1);val replacement="fixture-replacement-key".toCharArray()
  r.onExecute={entered.countDown();check(release.await(5,java.util.concurrent.TimeUnit.SECONDS))}
  kotlinx.coroutines.supervisorScope {
   val pending=async(kotlinx.coroutines.Dispatchers.IO){try{EvidenceAi.analyze(p,credential){EvidenceEgress.digest(f.catalog)}}catch(_:EvidenceAiFailure){null}}
   try{
    assertTrue(kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO){entered.await(5,java.util.concurrent.TimeUnit.SECONDS)})
    credential.setup(replacement);assertFalse(r.active);release.countDown();assertNull(kotlinx.coroutines.withTimeout(5000){pending.await()});assertTrue(r.lastResponse!!.all{it==0.toByte()})
   }finally{release.countDown();replacement.fill('\u0000');pending.cancel();p.close()}
  }
 }
}
