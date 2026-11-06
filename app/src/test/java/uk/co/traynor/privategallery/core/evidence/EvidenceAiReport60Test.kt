package uk.co.traynor.privategallery.core.evidence

import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Synthetic local fixtures only. No real evidence, API credentials or network. */
class EvidenceAiReport60Test {
 private val first=record("If you leave I will hurt you. Do not tell anyone.", "a".repeat(64))
 private val second=record("If you leave I will hurt you. Do not tell anyone.", "b".repeat(64))
 private fun record(text:String,hash:String,at:Long=1000,created:Long=0,modified:Long=0,added:Long=0,inlineTimes:Boolean=false):EvidenceCatalog.Evidence {
  val times=if(inlineTimes)EvidenceTextHints.scan(text).filter{it.kind()=="TIMESTAMP"}.map{EvidenceCatalog.ExtractedTime(0,it.text(),it.epochMillis(),it.provenance(),90f)}else emptyList()
  val ocr=EvidenceCatalog.Ocr(UUID.randomUUID().toString(),1,"SYNTHETIC_OFFLINE",0,0,0,text,90f,emptyList(),times,1,false)
  return EvidenceCatalog.Evidence(UUID.randomUUID().toString(),UUID.randomUUID().toString(),"PRIVATE_FILENAME","text/plain",hash,text.length.toLong(),1,
   EvidenceCatalog.Metadata("PRIVATE_SOURCE_FILENAME",created,modified,added,at,0,0,mapOf("GPS" to "PRIVATE_LOCATION"),false),"Owner claim","Owner note",emptyList(),emptyList(),listOf(ocr),emptyList())
 }
 private fun reference(record:EvidenceCatalog.Evidence,passage:String=record.transcript(),field:String="transcript")=JSONObject()
  .put("evidence_id",record.id()).put("field",field).put("passage",passage).put("timestamp_quote",JSONObject.NULL)
 private fun instance(record:EvidenceCatalog.Evidence,category:String="THREATS",passage:String=record.transcript())=JSONObject()
  .put("categories",JSONArray().put(category)).put("assessment","Possible threatening language; the context is incomplete")
  .put("confidence","LOW").put("intent","UNKNOWN").put("alternatives",JSONArray().put("May be a quote, fiction or missing conversational context"))
  .put("references",JSONArray().put(reference(record,passage)))
 private fun response(instances:JSONArray=JSONArray()):JSONObject=JSONObject().put("version",1).put("outcome","INSUFFICIENT_EVIDENCE")
  .put("assessment","Selected excerpts cannot establish a complete pattern").put("confidence","LOW")
  .put("alternatives",JSONArray().put("Ordinary disagreement or missing context may explain the material"))
  .put("limitations",JSONArray().put("Only selected material was reviewed"))
  .put("chronology",JSONArray()).put("instances",instances)
 private fun read(json:JSONObject,records:List<EvidenceCatalog.Evidence> = listOf(first,second))=
  EvidenceAiReport.validate(json.toString(),EvidenceAiReport.corpus(records))
 private fun reject(block:()->Unit){try{block();fail("Invalid report was admitted")}catch(_:IllegalArgumentException){}}

 @Test fun overlappingScreenshotsCountOnePotentialEventAndKeepBothReferences() {
  val report=read(response(JSONArray().put(instance(first)).put(instance(second,"MANIPULATION"))))
  assertEquals(1,report.instances.size);assertEquals(1,report.duplicateCount)
  assertEquals(2,report.instances.single().references.size)
  assertEquals(1,report.counts[EvidenceAiReport.Category.THREATS]);assertEquals(1,report.counts[EvidenceAiReport.Category.MANIPULATION])
  assertTrue(report.text().contains("overlap"));assertTrue(report.text().contains(EvidenceAiReport.DISCLAIMER))
 }
 @Test fun exactAnchorsRejectInventedPassagesFieldsTimesAndForeignIds() {
  for(ref in listOf(reference(first,"invented"),reference(first,field="GPS"),reference(first).put("timestamp_quote","Tomorrow at 2"),reference(first).put("evidence_id",UUID.randomUUID().toString()))) {
   reject{read(response(JSONArray().put(instance(first).put("references",JSONArray().put(ref)))))}
  }
 }
 @Test fun corpusShowsLocalProvenanceAndPartialContextButNoNamesOrLocation() {
  val payload=EvidenceAiReport.corpus(listOf(first)).json.toString()
  assertTrue(payload.contains("SYNTHETIC_OFFLINE"));assertTrue(payload.contains("partial"));assertTrue(payload.contains("capture_at"))
  for(secret in listOf("PRIVATE_FILENAME","PRIVATE_SOURCE_FILENAME","PRIVATE_LOCATION","GPS"))assertFalse(payload.contains(secret))
 }
 @Test fun storedBindingsRoundTripWithoutLiveEvidenceAndTamperingFailsClosed() {
  val accepted=EvidenceAiReport.validate(response(JSONArray().put(instance(first))).toString(),EvidenceAiReport.corpus(listOf(first)))
  val stored=EvidenceAiReport.encode(accepted)
  assertEquals(accepted.text(),EvidenceAiReport.readStored(stored,setOf(first.id())).text())
  reject{EvidenceAiReport.readStored(stored,setOf(second.id()))}
  val changed=JSONObject(stored).put("counts",JSONObject().put("THREATS",900))
  reject{EvidenceAiReport.readStored(changed.toString(),setOf(first.id()))}
 }
 @Test fun allAssessmentLabelsAndCountsAreQualifiedAndEmptyReportIsInsufficient() {
  val report=read(response())
  assertEquals("INSUFFICIENT_EVIDENCE",report.outcome)
  assertTrue(report.text().contains("Insufficient Evidence*"))
  for(category in EvidenceAiReport.Category.entries)assertTrue(report.text().contains("${category.label}*: 0*"))
  reject{read(response().put("outcome","ABUSE_PROVEN"))}
  reject{read(response().put("confidence","CERTAIN"))}
 }
 @Test fun explicitIntentNeedsLiteralIntentEvidenceAndAnnotationsCannotCountAlone() {
  reject{read(response(JSONArray().put(instance(first).put("intent","EXPLICIT"))))}
  reject{read(response(JSONArray().put(instance(first).put("references",JSONArray().put(reference(first,"Owner note","notes"))))))}
 }
 @Test fun repeatedContainedPassagesAndByteIdenticalCopiesCannotInflateCounts() {
  val copy=record(first.transcript(),first.sha256())
  val report=read(response(JSONArray().put(instance(first)).put(instance(second,passage="I will hurt you.")).put(instance(copy))),listOf(first,second,copy))
  assertEquals(1,report.instances.size);assertEquals(2,report.duplicateCount);assertEquals(1,report.counts[EvidenceAiReport.Category.THREATS])
 }
 @Test fun chronologyUsesValidatedLocalTimestampClaimsAndUnknownRemainsZero() {
  val event=JSONObject().put("references",JSONArray().put(reference(first))).put("context","Uncertain surrounding conversation").put("interpretation","Possible threat")
  assertEquals(0L,read(response().put("chronology",JSONArray().put(event))).chronology.single().at)
  event.getJSONArray("references").getJSONObject(0).put("timestamp_quote","1000")
  assertEquals(1000L,read(response().put("chronology",JSONArray().put(event))).chronology.single().at)
 }
 @Test fun forgedApplicationBindingsAndWrongHistoricalHashesFailClosed() {
  reject{read(response().put("_validated",JSONObject()))}
  val accepted=read(response(JSONArray().put(instance(first))))
  reject{EvidenceAiReport.readStored(EvidenceAiReport.encode(accepted),setOf(first.id()),mapOf(first.id() to "0".repeat(64)))}
 }
 @Test fun repeatedDistinctProposalsRemainDeduplicatedAfterHistoricalRoundTrip() {
  val accepted=read(response(JSONArray().put(instance(first)).put(instance(second,"MANIPULATION"))))
  val restored=EvidenceAiReport.readStored(EvidenceAiReport.encode(accepted),setOf(first.id(),second.id()))
  assertEquals(1,restored.instances.size);assertEquals(1,restored.duplicateCount);assertEquals(2,restored.instances.single().references.size)
  assertEquals(accepted.text(),restored.text())
 }
 @Test fun independentAnchoredEventsMaySupportOnlyAQualifiedPotentialPattern() {
  val other=record("I intend to control your money. Hand over your wages.","c".repeat(64))
  val root=response(JSONArray().put(instance(first)).put(instance(other,"BLACKMAIL_COERCION").put("intent","EXPLICIT"))).put("outcome","POTENTIAL_PATTERN")
  val report=read(root,listOf(first,other))
  assertEquals("POTENTIAL_PATTERN",report.outcome);assertEquals(2,report.instances.size)
  assertEquals(1,report.counts[EvidenceAiReport.Category.THREATS]);assertEquals(1,report.counts[EvidenceAiReport.Category.BLACKMAIL_COERCION])
  assertTrue(report.text().contains("Potential pattern in selected evidence*"))
 }

 @Test fun identicalOriginalsWithDistinctNonOverlappingMessagesKeepTwoEvents() {
  val text="I will hurt you. Hand over your wages or lose your home."
  val a=record(text,"d".repeat(64));val b=record(text,"d".repeat(64))
  val report=read(response(JSONArray().put(instance(a,passage="I will hurt you.")).put(instance(b,"BLACKMAIL_COERCION","Hand over your wages or lose your home."))),listOf(a,b))
  assertEquals(2,report.instances.size);assertEquals(0,report.duplicateCount)
 }
 @Test fun repeatedIdenticalOriginalsWithDifferentFileCaptureTimesCountOneEvent() {
  val a=record(first.transcript(),"e".repeat(64),1000);val b=record(first.transcript(),"e".repeat(64),2000)
  val one=instance(a).put("references",JSONArray().put(reference(a).put("timestamp_quote","1000")))
  val two=instance(b).put("references",JSONArray().put(reference(b).put("timestamp_quote","2000")))
  val report=read(response(JSONArray().put(one).put(two)),listOf(a,b))
  assertEquals(1,report.instances.size);assertEquals(1,report.counts[EvidenceAiReport.Category.THREATS])
 }
 @Test fun samePassageWithFileCreatedModifiedOrImportedDatesCannotMultiplyEvents() {
  val a=record(first.transcript(),"a".repeat(64),at=1000,created=2000,modified=3000,added=4000)
  val proposals=JSONArray()
  for(quote in listOf("1000","2000","3000","4000"))proposals.put(instance(a).put("references",JSONArray().put(reference(a).put("timestamp_quote",quote))))
  val report=read(response(proposals),listOf(a))
  assertEquals(1,report.instances.size);assertEquals(3,report.duplicateCount)
  assertTrue(report.text().contains("not message time"))
 }
 @Test fun overlappingPassagesWithDifferentFileTimesCannotMultiplyEvents() {
  val a=record(first.transcript(),"e".repeat(64),1000);val b=record(first.transcript(),"f".repeat(64),2000,added=3000)
  val one=instance(a).put("references",JSONArray().put(reference(a).put("timestamp_quote","1000")))
  val two=instance(b,passage="I will hurt you.").put("references",JSONArray().put(reference(b,"I will hurt you.").put("timestamp_quote","3000")))
  assertEquals(1,read(response(JSONArray().put(one).put(two)),listOf(a,b)).instances.size)
 }
 @Test fun separateInlineConversationTimestampClaimsCanSupportTwoOccurrences() {
  val early="2026-01-01T12:00:00Z";val later="2026-01-02T12:00:00Z"
  val a=record("$early If you leave I will hurt you. Do not tell anyone.","e".repeat(64),inlineTimes=true)
  val b=record("$later If you leave I will hurt you. Do not tell anyone.","f".repeat(64),inlineTimes=true)
  val one=instance(a).put("references",JSONArray().put(reference(a).put("timestamp_quote",early)))
  val two=instance(b).put("references",JSONArray().put(reference(b).put("timestamp_quote",later)))
  val report=read(response(JSONArray().put(one).put(two)),listOf(a,b))
  assertEquals(2,report.instances.size);assertEquals(0,report.duplicateCount)
  val restored=EvidenceAiReport.readStored(EvidenceAiReport.encode(report),setOf(a.id(),b.id()))
  assertEquals(2,restored.instances.size);assertEquals(report.text(),restored.text())
 }
 @Test fun timestampOutsideTheExactCitedPassageCannotEstablishAnotherOccurrence() {
  val early="2026-01-01T12:00:00Z";val later="2026-01-02T12:00:00Z"
  val a=record("$early ${first.transcript()}","e".repeat(64),inlineTimes=true)
  val b=record("$later ${first.transcript()}","f".repeat(64),inlineTimes=true)
  val one=instance(a,passage=first.transcript()).put("references",JSONArray().put(reference(a,first.transcript()).put("timestamp_quote",early)))
  val two=instance(b,passage=first.transcript()).put("references",JSONArray().put(reference(b,first.transcript()).put("timestamp_quote",later)))
  assertEquals(1,read(response(JSONArray().put(one).put(two)),listOf(a,b)).instances.size)
 }
 @Test fun samePassageContainingSeveralConversationTimesHasNoUniqueOccurrenceAnchor() {
  val early="2026-01-01T12:00:00Z";val later="2026-01-02T12:00:00Z"
  val a=record("$early ${first.transcript()}\n$later ${first.transcript()}","e".repeat(64),inlineTimes=true)
  val one=instance(a).put("references",JSONArray().put(reference(a).put("timestamp_quote",early)))
  val two=instance(a).put("references",JSONArray().put(reference(a).put("timestamp_quote",later)))
  assertEquals(1,read(response(JSONArray().put(one).put(two)),listOf(a)).instances.size)
 }
 @Test fun undatedOverlapCannotBridgeTwoDistinctInlineConversationEvents() {
  val early="2026-01-01T12:00:00Z";val later="2026-01-02T12:00:00Z"
  val a=record("$early ${first.transcript()}","1".repeat(64),inlineTimes=true)
  val b=record("$later ${first.transcript()}","2".repeat(64),inlineTimes=true)
  val one=instance(a).put("references",JSONArray().put(reference(a).put("timestamp_quote",early)))
  val two=instance(b).put("references",JSONArray().put(reference(b).put("timestamp_quote",later)))
  val report=read(response(JSONArray().put(instance(first)).put(one).put(two)),listOf(first,a,b))
  assertEquals(2,report.instances.size);assertEquals(1,report.duplicateCount)
  val restored=EvidenceAiReport.readStored(EvidenceAiReport.encode(report),setOf(first.id(),a.id(),b.id()))
  assertEquals(2,restored.instances.size);assertEquals(report.text(),restored.text())
 }

 @Test fun shortRepeatedBodyWithDifferentUnzonedDatePrefixesCountsOnce() {
  val a=record("2026-01-01 I hate you","3".repeat(64),inlineTimes=true)
  val b=record("2026-01-02 I hate you","4".repeat(64),inlineTimes=true)
  val one=instance(a).put("references",JSONArray().put(reference(a).put("timestamp_quote","2026-01-01")))
  val two=instance(b).put("references",JSONArray().put(reference(b).put("timestamp_quote","2026-01-02")))
  // Date-only claims are locally extracted but have no known date/time/offset authority.
  val withDates=listOf(a,b).map{source->
   val raw=source.ocr().single();val times=EvidenceTextHints.scan(raw.text()).filter{it.kind()!="PARTICIPANT_CANDIDATE"}.map{EvidenceCatalog.ExtractedTime(0,it.text(),it.epochMillis(),it.provenance(),90f)}
   EvidenceCatalog.Evidence(source.id(),source.sourceId(),source.name(),source.mime(),source.sha256(),source.size(),source.importedAt(),source.metadata(),source.description(),source.notes(),source.tags(),source.participants(),listOf(EvidenceCatalog.Ocr(raw.id(),raw.createdAt(),raw.engine(),raw.rotation(),raw.width(),raw.height(),raw.text(),raw.confidence(),raw.words(),times,raw.pageCount(),raw.partial())),source.corrections())
  }
  val report=read(response(JSONArray().put(one).put(two)),withDates)
  assertEquals(1,report.instances.size);assertEquals(1,report.counts[EvidenceAiReport.Category.THREATS])
 }
 @Test fun aggregateConflictingConversationTimesCannotEvadeSameEventDeduplication() {
  val early="2026-01-01T12:00:00Z";val later="2026-01-02T12:00:00Z"
  val a=record("$early ${first.transcript()}","5".repeat(64),inlineTimes=true)
  val b=record("$later ${first.transcript()}","6".repeat(64),inlineTimes=true)
  val earlyRef=reference(a).put("timestamp_quote",early);val lateRef=reference(b).put("timestamp_quote",later)
  val ambiguous=instance(a).put("references",JSONArray().put(earlyRef).put(lateRef))
  val one=instance(a).put("references",JSONArray().put(earlyRef))
  val report=read(response(JSONArray().put(ambiguous).put(one)),listOf(a,b))
  assertEquals(1,report.instances.size);assertEquals(2,report.instances.single().references.size)
  assertEquals(report.text(),EvidenceAiReport.readStored(EvidenceAiReport.encode(report),setOf(a.id(),b.id())).text())
 }
 @Test fun aggregateAmbiguousReferenceCannotBridgeIndependentDatedEventsOnReopen() {
  val early="2026-01-01T12:00:00Z";val later="2026-01-02T12:00:00Z"
  val a=record("$early ${first.transcript()}","7".repeat(64),inlineTimes=true)
  val b=record("$later ${first.transcript()}","8".repeat(64),inlineTimes=true)
  val earlyRef=reference(a).put("timestamp_quote",early);val lateRef=reference(b).put("timestamp_quote",later)
  val ambiguous=instance(a).put("references",JSONArray().put(earlyRef).put(lateRef))
  val report=read(response(JSONArray().put(ambiguous).put(instance(a).put("references",JSONArray().put(earlyRef))).put(instance(b).put("references",JSONArray().put(lateRef)))),listOf(a,b))
  assertEquals(2,report.instances.size)
  val restored=EvidenceAiReport.readStored(EvidenceAiReport.encode(report),setOf(a.id(),b.id()))
  assertEquals(2,restored.instances.size);assertEquals(report.text(),restored.text())
 }

 @Test fun unrelatedTimestampReferenceCannotSeparateAnIdenticalUndatedPassage() {
  val early="2026-01-01T12:00:00Z";val later="2026-01-02T12:00:00Z"
  val a=record("$early I will hurt you.\n${first.transcript()}","9".repeat(64),inlineTimes=true)
  val b=record("$later I will hurt you.\n${first.transcript()}","0".repeat(64),inlineTimes=true)
  val one=instance(a).put("references",JSONArray().put(reference(a,"$early I will hurt you.").put("timestamp_quote",early)).put(reference(a,first.transcript())))
  val two=instance(b).put("references",JSONArray().put(reference(b,"$later I will hurt you.").put("timestamp_quote",later)).put(reference(b,first.transcript())))
  val report=read(response(JSONArray().put(one).put(two)),listOf(a,b))
  assertEquals(1,report.instances.size);assertEquals(4,report.instances.single().references.size)
  assertEquals(report.text(),EvidenceAiReport.readStored(EvidenceAiReport.encode(report),setOf(a.id(),b.id())).text())
 }

 @Test fun omittedChronologyFallsBackToCanonicalQuotedFactsKnownTimesFirstAndStableHistory() {
  val early="2026-01-01T12:00:00Z";val later="2026-01-02T12:00:00Z"
  val a=record("$early Hand over your wages or I will lock you out.","a".repeat(64),inlineTimes=true)
  val b=record("$later I will smash your phone.","b".repeat(64),inlineTimes=true)
  val c=record("You are worthless and nobody cares about you.","c".repeat(64))
  val one=instance(a,"BLACKMAIL_COERCION").put("references",JSONArray().put(reference(a).put("timestamp_quote",early)))
  val two=instance(b).put("references",JSONArray().put(reference(b).put("timestamp_quote",later)))
  val report=read(response(JSONArray().put(two).put(instance(c,"EMOTIONAL_ABUSE")).put(one)),listOf(a,b,c))
  assertEquals(3,report.chronology.size)
  assertEquals(listOf(1767268800000L,1767355200000L,0L),report.chronology.map{it.at})
  assertTrue(report.chronology.first().fact.contains(a.transcript()));assertTrue(report.chronology.last().fact.contains(c.transcript()))
  assertTrue(report.chronology.all{it.context.contains("No additional context established")})
  assertEquals(report.text(),EvidenceAiReport.readStored(EvidenceAiReport.encode(report),setOf(a.id(),b.id(),c.id())).text())
 }

 @Test fun mergedThirtyThreePassagesKeepAllFallbackChronologyReferencesAcrossHistory() {
  val words=(1..50).map{"word$it"};val source=record(words.joinToString(" "),"d".repeat(64))
  val proposals=JSONArray()
  for(length in 8..40)proposals.put(instance(source,passage=words.take(length).joinToString(" ")))
  val report=read(response(proposals),listOf(source))
  assertEquals(1,report.instances.size);assertEquals(33,report.instances.single().references.size)
  assertEquals(1,report.chronology.size);assertEquals(33,report.chronologyReferences.single().size)
  val restored=EvidenceAiReport.readStored(EvidenceAiReport.encode(report),setOf(source.id()))
  assertEquals(33,restored.chronologyReferences.single().size);assertEquals(report.text(),restored.text())
  val invalidProvider=JSONObject().put("references",JSONArray().also{array->for(length in 8..40)array.put(reference(source,words.take(length).joinToString(" ")))}).put("context","Synthetic context").put("interpretation","Uncertain interpretation")
  reject{read(response().put("chronology",JSONArray().put(invalidProvider)),listOf(source))}
 }

}
