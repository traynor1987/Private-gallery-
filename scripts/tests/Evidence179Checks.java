import uk.co.traynor.privategallery.core.evidence.EvidenceCatalog;
import uk.co.traynor.privategallery.core.evidence.EvidenceCatalog.*;
import uk.co.traynor.privategallery.core.evidence.EvidenceEncryptedStore;
import uk.co.traynor.privategallery.core.hidden.HiddenSession;
import uk.co.traynor.privategallery.core.hidden.HiddenRecordStore;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/** Host checks use real AES/GCM disk records and real HiddenSession revocation/commit authority. */
public final class Evidence179Checks {
 static int checks;
 static String id(){return UUID.randomUUID().toString();}
 static String hash="a".repeat(64);
 static int locate(byte[] bytes,String text){byte[] target=text.getBytes(StandardCharsets.UTF_8);outer:for(int i=0;i<=bytes.length-target.length;i++){for(int j=0;j<target.length;j++)if(bytes[i+j]!=target[j])continue outer;return i;}throw new AssertionError("test fixture text missing");}
 static void yes(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
 interface Work {void run()throws Exception;}
 static void fails(Work work,String why)throws Exception{try{work.run();}catch(Exception expected){checks++;return;}throw new AssertionError(why);}
 static Evidence evidence(String name,boolean restricted,long capture){return new Evidence(id(),id(),name,"image/png",hash,10,100,new Metadata("source.png",0,0,0,capture,10,20,Map.of("source","claim"),restricted),"description","notes",List.of("tag"),List.of("Alice"),List.of(),List.of());}
 static Evidence edit(Evidence e,String name,String notes,List<Ocr> ocr,List<Correction> corrections){return new Evidence(e.id(),e.sourceId(),name,e.mime(),e.sha256(),e.size(),e.importedAt(),e.metadata(),e.description(),notes,e.tags(),e.participants(),ocr,corrections);}
 static Incident incident(String title,List<String> ids,List<Event> events){return new Incident(id(),title,0,0,"description","notes",List.of(),ids,events,false,false,List.of());}
 static Ocr ocr(String text){return new Ocr(id(),200,"test",0,10,20,text,90,List.of(new Word(0,"word",80,0,0,5,5)),List.of(new ExtractedTime(0,"12:00",0,"zone and date unavailable",-1)),1,false);}
 static EvidenceCatalog base(Evidence... es){EvidenceCatalog c=EvidenceCatalog.empty(Owner.PRIMARY);for(Evidence e:es)c=c.upsertEvidence(e,100,"REGISTER");return c;}
 public static void main(String[] args)throws Exception {
  codec(); originalsAndOcr(); grouping(); searchAndSuggestions(); contextualSuggestions(); quotas(); timelineBoundary(); firstManualMove(); analysisLineage(); eventDetachment(); successorLineage(); encryptedStore(); codecBufferWiping();
  System.out.println("Evidence179 host checks passed: "+checks+" assertions (model/codec/lineage/search/real crypto/revocation/concurrency)");
 }
 static void codec()throws Exception {
  Evidence e=evidence("emoji 😀",true,123);EvidenceCatalog c=base(e);byte[] bytes=c.encode();
  EvidenceCatalog d=EvidenceCatalog.decode(bytes,Owner.PRIMARY);yes(d.evidence().equals(c.evidence())&&d.revision()==1,"roundtrip owner model");
  fails(()->EvidenceCatalog.decode(bytes,Owner.HIDDEN),"foreign owner accepted");
  fails(()->EvidenceCatalog.decode(Arrays.copyOf(bytes,bytes.length-1),Owner.PRIMARY),"truncation accepted");
  fails(()->EvidenceCatalog.decode(Arrays.copyOf(bytes,bytes.length+1),Owner.PRIMARY),"trailing accepted");
  byte[] bad=bytes.clone();Arrays.fill(bad,0,4,(byte)127);fails(()->EvidenceCatalog.decode(bad,Owner.PRIMARY),"version accepted");
  fails(()->new EvidenceCatalog(Owner.PRIMARY,0,List.of(e,e),List.of(),List.of(),List.of(),List.of()),"duplicate evidence accepted");
  byte[] utf=bytes.clone();utf[12]=(byte)0x80;fails(()->EvidenceCatalog.decode(utf,Owner.PRIMARY),"malformed UTF8 accepted");
  Evidence other=evidence("other",false,0);byte[] duplicate=base(e,other).encode();System.arraycopy(e.id().getBytes(StandardCharsets.UTF_8),0,duplicate,locate(duplicate,other.id()),36);fails(()->EvidenceCatalog.decode(duplicate,Owner.PRIMARY),"encoded duplicate ID accepted");
  fails(()->c.evidence().clear(),"mutable evidence list");fails(()->e.metadata().embedded().clear(),"mutable metadata");
  List<String> tags=new ArrayList<>(List.of("one"));Evidence copy=new Evidence(e.id(),e.sourceId(),e.name(),e.mime(),e.sha256(),e.size(),e.importedAt(),e.metadata(),"","",tags,List.of(),List.of(),List.of());tags.clear();yes(copy.tags().equals(List.of("one")),"defensive copy");
  yes(c.audit().size()==1&&c.audit().get(0).action().equals("REGISTER"),"audited register");
  yes(c.upsertEvidence(e,100,"NOOP")==c,"no-op fabricates mutation");
 }
 static void originalsAndOcr()throws Exception {
  Evidence e=evidence("original",true,10);EvidenceCatalog c=base(e);
  Evidence changed=new Evidence(e.id(),e.sourceId(),e.name(),e.mime(),"b".repeat(64),10,100,e.metadata(),"","",List.of(),List.of(),List.of(),List.of());
  fails(()->c.upsertEvidence(changed,101,"EDIT"),"original hash overwritten");
  Metadata relaxed=new Metadata("source.png",0,0,0,10,10,20,Map.of(),false);
  fails(()->c.upsertEvidence(new Evidence(e.id(),e.sourceId(),e.name(),e.mime(),hash,10,100,relaxed,"","",List.of(),List.of(),List.of(),List.of()),101,"EDIT"),"policy declassified");
  Ocr raw=ocr("raw message");EvidenceCatalog extracted=c.saveOcr(e.id(),raw,200);EvidenceCatalog fixed=extracted.reviseOcr(e.id(),raw.id(),"corrected message",201);
  yes(fixed.evidence().get(0).transcript().equals("corrected message"),"correction not displayed");
  yes(fixed.evidence().get(0).ocr().get(0).text().equals("raw message"),"raw OCR modified");
  yes(fixed.evidence().get(0).corrections().get(0).ocrId().equals(raw.id()),"correction reference lost");
  fails(()->fixed.upsertEvidence(edit(fixed.evidence().get(0),"rename","",List.of(),List.of()),202,"EDIT"),"history removed through generic upsert");
  fails(()->fixed.reviseOcr(e.id(),id(),"unknown",202),"unknown OCR corrected");
  EvidenceCatalog nine=fixed;for(int i=1;i<8;i++)nine=nine.saveOcr(e.id(),ocr("v"+i),201+i);EvidenceCatalog full=nine;
  fails(()->full.saveOcr(e.id(),ocr("ninth"),300),"ninth extraction discarded history");
  fails(()->new Ocr(id(),1,"test",0,10,10,"x",1,List.of(new Word(1,"x",1,0,0,1,1)),List.of(),1,false),"page out of range");
  byte[] round=fixed.encode();yes(EvidenceCatalog.decode(round,Owner.PRIMARY).evidence().equals(fixed.evidence()),"OCR codec loss");
 }
 static void grouping()throws Exception {
  Evidence a=evidence("restricted",true,40),b=evidence("second",false,60);EvidenceCatalog c=base(a,b);
  Event later=new Event(id(),List.of(a.id()),30,"SOURCE_CAPTURE","later","");Event unknown=new Event(id(),List.of(a.id()),0,"USER","unknown","");Event first=new Event(id(),List.of(a.id()),10,"USER","first","");
  Incident i=incident("Incident A",List.of(a.id()),List.of(later,unknown,first));c=c.upsertIncident(i,200,"CREATE");
  yes(c.incidents().get(0).lineage().size()==1&&c.incidents().get(0).lineage().get(0).vaultOnly(),"lineage missing");
  yes(c.timeline(i.id()).equals(List.of(first,later,unknown)),"chronology/unknown order");
  c=c.moveEvent(i.id(),unknown.id(),0,201);yes(c.timeline(i.id()).get(0).equals(unknown)&&c.incidents().get(0).manualOrder(),"manual order");
  Incident j=incident("Incident B",List.of(b.id()),List.of());c=c.upsertIncident(j,202,"CREATE");c=c.assign(List.of(a.id()),j.id(),true,203);
  yes(c.incidents().get(0).evidenceIds().isEmpty()&&c.incidents().get(0).lineage().get(0).vaultOnly(),"move lost lineage");
  c=c.unlink(List.of(a.id()),j.id(),204);yes(c.incidents().get(1).lineage().stream().anyMatch(SourceFingerprint::vaultOnly),"unlink declassified");
  c=c.assign(List.of(a.id()),j.id(),false,205).merge(i.id(),List.of(j.id()),206);
  yes(c.incidents().get(1).archived()&&c.incidents().get(0).evidenceIds().contains(b.id()),"merge failed/archive not retained");
  String split=id();c=c.split(i.id(),List.of(a.id()),split,"Split",207);yes(c.incidents().get(2).lineage().stream().anyMatch(SourceFingerprint::vaultOnly),"split lineage");
  c=c.removeEvidence(a.id(),208);yes(c.incidents().stream().noneMatch(x->x.evidenceIds().contains(a.id()))&&c.incidents().get(2).lineage().stream().anyMatch(SourceFingerprint::vaultOnly),"remove lost lineage/references");
  EvidenceCatalog current=c;fails(()->current.assign(List.of(id()),i.id(),false,300),"unknown evidence assigned");
  fails(()->current.upsertIncident(incident("bad",List.of(),List.of(new Event(id(),List.of(id()),0,"USER","x",""))),300,"EVENT"),"unknown event source accepted");
  Analysis analysis=new Analysis(id(),400,"model","task","c".repeat(64),List.of(b.id()),"advisory",1,2,false,List.of(new SourceFingerprint(b.sourceId(),b.mime(),b.sha256(),b.size(),true)));
  c=c.saveAnalysis(analysis,400);yes(!c.analyses().get(0).lineage().get(0).vaultOnly(),"analysis trusted caller policy");
  yes(EvidenceCatalog.decode(c.encode(),Owner.PRIMARY).incidents().equals(c.incidents()),"incident codec");
 }
 static void searchAndSuggestions()throws Exception {
  Evidence a=evidence("Invoice",false,1000),b=evidence("Receipt",false,2000);EvidenceCatalog c=base(a,b);c=c.saveOcr(a.id(),ocr("special transcript"),100);
  Incident i=incident("Holiday",List.of(a.id()),List.of());c=c.upsertIncident(i,101,"CREATE");
  yes(c.search("special",0,0,true).size()==1,"OCR search");yes(c.search("alice",0,0,true).size()==2,"participant search");yes(c.search("holiday",0,0,true).size()==1,"incident title search");
  yes(c.search("",1500,2500,true).equals(List.of(b)),"capture date filtering");c=c.archive(i.id(),true,102);yes(c.search("Invoice",0,0,false).isEmpty(),"archived search leak");
  List<Suggestion> suggestions=c.suggestions();yes(!suggestions.isEmpty(),"bounded signals produced no hints");Suggestion s=suggestions.get(0);yes(c.suggestions().equals(suggestions),"unstable suggestion identity");
  long revision=c.revision();EvidenceCatalog rejected=c.rejectSuggestion(s.id(),103);yes(rejected.suggestions().stream().noneMatch(x->x.id().equals(s.id()))&&c.revision()==revision,"reject mutated original/applied hint");
  EvidenceCatalog accepted=c.acceptSuggestion(s,id(),"Accepted",104);yes(accepted.incidents().size()==2&&accepted.revision()==revision+1,"accept must be one audited mutation");
 }
 static Evidence neutral(String name,String digest){return new Evidence(id(),id(),name,"image/png",digest,10,100,new Metadata(name,0,0,0,0,10,20,Map.of(),false),"","",List.of(),List.of(),List.of(),List.of());}
 static void contextualSuggestions()throws Exception {
  Evidence a=neutral("first","1".repeat(64)),b=neutral("second","2".repeat(64));
  Ocr oa=new Ocr(id(),200,"test",0,10,20,"Please arrange delivery tomorrow morning at reception",80,List.of(),List.of(new ExtractedTime(0,"2026-10-31T10:00:00Z",1793440800000L,"OCR_VISIBLE_UNVERIFIED",-1)),1,false);
  Ocr ob=new Ocr(id(),200,"test",0,10,20,"Please arrange delivery tomorrow morning at the main reception desk",70,List.of(),List.of(new ExtractedTime(0,"2026-10-31T10:10:00Z",1793441400000L,"OCR_VISIBLE_UNVERIFIED",-1)),1,false);
  EvidenceCatalog c=base(a,b).saveOcr(a.id(),oa,201).saveOcr(b.id(),ob,202);
  yes(c.suggestions().stream().anyMatch(x->x.reasons().stream().anyMatch(r->r.contains("OCR")&&r.contains("unverified"))),"OCR visible date signals missing");
  yes(c.suggestions().stream().anyMatch(x->x.reasons().stream().anyMatch(r->r.contains("Similar transcript"))),"similar conversation fragments missing");
  Incident target=incident("Existing incident",List.of(a.id()),List.of());c=c.upsertIncident(target,203,"CREATE");
  Suggestion assign=c.suggestions().stream().filter(x->x.kind().equals("ASSIGN")&&x.incidentId().equals(target.id())).findFirst().orElseThrow(()->new AssertionError("existing association hint missing"));
  yes(assign.evidenceIds().equals(List.of(b.id())),"association hint must contain only new references");
  EvidenceCatalog applied=c.acceptSuggestion(assign,id(),"unused",204);
  yes(applied.incidents().size()==1&&applied.incidents().get(0).evidenceIds().containsAll(List.of(a.id(),b.id())),"owner approval created duplicate/merged incident instead of linking");
  yes(applied.revision()==c.revision()+1&&applied.audit().size()==c.audit().size()+1&&applied.evidence().equals(c.evidence()),"association acceptance must be one audited reference-only mutation");
  yes(c.incidents().get(0).evidenceIds().equals(List.of(a.id())),"suggestion auto-applied");
  EvidenceCatalog rejected=c.rejectSuggestion(assign.id(),205);yes(rejected.suggestions().stream().noneMatch(x->x.id().equals(assign.id())),"association rejection not retained");
 }
 static void quotas()throws Exception {
  fails(()->ocr("x".repeat(100001)),"OCR quota");fails(()->new Metadata("x",-1,0,0,0,0,0,Map.of(),false),"negative metadata");
  Evidence e=evidence("x",false,0);fails(()->edit(e,"x","x".repeat(65537),List.of(),List.of()),"notes quota");
  fails(()->new EvidenceCatalog(Owner.PRIMARY,0,Collections.nCopies(4097,e),List.of(),List.of(),List.of(),List.of()),"evidence quota");
  fails(()->EvidenceCatalog.decode(new byte[4*1024*1024+1],Owner.PRIMARY),"encoded quota");
  fails(()->new EvidenceCatalog(Owner.PRIMARY,0,List.of(),List.of(),Collections.nCopies(20001,new Audit(id(),1,"TEST",List.of(),"")),List.of(),List.of()),"audit quota");
  List<Evidence> big=new ArrayList<>();for(int i=0;i<70;i++){Evidence x=evidence("big"+i,false,0);big.add(edit(x,x.name(),"x".repeat(65536),List.of(),List.of()));}
  fails(()->new EvidenceCatalog(Owner.PRIMARY,0,big,List.of(),List.of(),List.of(),List.of()),"aggregate encoded model quota");
 }
 static void timelineBoundary()throws Exception {
  Evidence a=evidence("edge",false,0);Event unknown=new Event(id(),List.of(a.id()),0,"USER","unknown","");Event known=new Event(id(),List.of(a.id()),Long.MAX_VALUE,"USER","known","");Incident i=incident("edge",List.of(a.id()),List.of(unknown,known));EvidenceCatalog c=base(a).upsertIncident(i,1,"CREATE");
  yes(c.timeline(i.id()).equals(List.of(known,unknown)),"unknown timestamps must follow every known timestamp, including Long.MAX_VALUE");
 }
 static void firstManualMove() {
  Event late=new Event(id(),List.of(),300,"USER","late","");
  Event early=new Event(id(),List.of(),100,"USER","early","");
  Event middle=new Event(id(),List.of(),200,"USER","middle","");
  Event unknown=new Event(id(),List.of(),0,"INFERRED","unknown","");
  Incident i=incident("chronology",List.of(),List.of(late,unknown,middle,early));
  EvidenceCatalog c=EvidenceCatalog.empty(Owner.PRIMARY).upsertIncident(i,1,"CREATE");
  yes(c.moveEvent(i.id(),early.id(),0,2)==c,"boundary move must not switch chronology to insertion order");
  EvidenceCatalog moved=c.moveEvent(i.id(),early.id(),1,2);
  yes(moved.timeline(i.id()).equals(List.of(middle,early,late,unknown)),"first move starts at visible chronology and moves exactly one row");
  yes(moved.incidents().get(0).manualOrder(),"first actual move records manual order");
  yes(moved.audit().get(moved.audit().size()-1).action().equals("MOVE_EVENT"),"manual move is audited");
  yes(moved.moveEvent(i.id(),unknown.id(),3,3)==moved,"manual last boundary remains no-op");
  yes(moved.moveEvent(i.id(),unknown.id(),2,3).timeline(i.id()).equals(List.of(middle,early,unknown,late)),"subsequent move uses persisted manual order");
 }
 static void analysisLineage()throws Exception {
  Evidence e=evidence("restricted analysis",true,1);EvidenceCatalog c=base(e);Analysis raw=new Analysis(id(),2,"model","task",hash,List.of(e.id()),"derived restricted content",1,1,false,List.of());c=c.saveAnalysis(raw,2).removeEvidence(e.id(),3);Analysis old=c.analyses().get(0);Analysis applied=new Analysis(old.id(),old.at(),old.model(),old.task(),old.inputDigest(),old.evidenceIds(),old.text(),old.inputTokens(),old.outputTokens(),true,List.of());c=c.saveAnalysis(applied,4);
  yes(c.analyses().get(0).lineage().size()==1&&c.analyses().get(0).lineage().get(0).vaultOnly(),"applying analysis after record removal declassified retained content");
  EvidenceCatalog retained=c;Evidence replacement=new Evidence(id(),e.sourceId(),e.name(),e.mime(),e.sha256(),e.size(),e.importedAt(),new Metadata("source.png",0,0,0,1,10,20,Map.of(),false),"","",List.of(),List.of(),List.of(),List.of());
  fails(()->retained.upsertEvidence(replacement,5,"REGISTER"),"removed/re-registered source conflicts with retained lineage");
 }
 static void eventDetachment()throws Exception {
  Evidence a=evidence("restricted source",true,1),b=evidence("other source",false,2);Event mixed=new Event(id(),List.of(a.id(),b.id()),10,"EXTRACTED","mixed provenance","mixed original text");Event onlyA=new Event(id(),List.of(a.id()),20,"USER","only A","owner notes");Event note=new Event(id(),List.of(),0,"USER","standalone note","keep here");Incident source=incident("source",List.of(a.id(),b.id()),List.of(mixed,onlyA,note)),target=incident("target",List.of(),List.of());EvidenceCatalog c=base(a,b).upsertIncident(source,1,"CREATE").upsertIncident(target,2,"CREATE");
  EvidenceCatalog unlinked=c.unlink(List.of(a.id()),source.id(),3);Incident u=unlinked.incidents().get(0);yes(u.events().stream().noneMatch(e->e.evidenceIds().contains(a.id())),"unlink leaves clickable source event references");yes(u.events().size()==3&&u.events().get(1).notes().equals(onlyA.notes())&&u.events().get(1).evidenceIds().isEmpty(),"unlink must retain provenance-only event text");yes(u.lineage().stream().anyMatch(SourceFingerprint::vaultOnly),"unlink lost restricted lineage");
  EvidenceCatalog moved=c.assign(List.of(a.id()),target.id(),true,4);Incident from=moved.incidents().get(0),to=moved.incidents().get(1);yes(from.events().stream().noneMatch(e->e.evidenceIds().contains(a.id()))&&from.events().stream().noneMatch(e->e.id().equals(onlyA.id()))&&from.events().contains(note),"move leaves clickable old links or moves standalone note");yes(to.events().size()==2&&to.events().get(0).evidenceIds().equals(List.of(a.id()))&&to.events().get(0).provenance().equals(mixed.provenance())&&to.events().get(1).notes().equals(onlyA.notes()),"move target event source/notes not preserved");yes(to.lineage().size()==2,"mixed derived event target must inherit all contributing lineage");
  EvidenceCatalog merged=moved.merge(source.id(),List.of(target.id()),5);yes(merged.incidents().get(0).events().stream().filter(e->e.id().equals(mixed.id())).count()==1&&merged.incidents().get(0).events().get(0).evidenceIds().containsAll(List.of(a.id(),b.id())),"merge failed to reunite split event references");
  String splitId=id();EvidenceCatalog split=c.split(source.id(),List.of(a.id()),splitId,"split",6);Incident sf=split.incidents().get(0),st=split.incidents().get(2);yes(sf.events().stream().noneMatch(e->e.evidenceIds().contains(a.id()))&&sf.events().stream().noneMatch(e->e.id().equals(onlyA.id()))&&sf.events().contains(note),"split source leaves clickable old links");yes(st.events().size()==2&&st.events().stream().allMatch(e->e.evidenceIds().equals(List.of(a.id())))&&st.events().get(0).notes().equals(mixed.notes()),"split target loses event provenance or copies unrelated IDs");yes(split.evidence().equals(c.evidence())&&moved.evidence().equals(c.evidence())&&unlinked.evidence().equals(c.evidence())&&split.revision()==c.revision()+1&&split.audit().get(split.audit().size()-1).action().equals("SPLIT_INCIDENT"),"reference operations mutate originals or audit incorrectly");
 }
 static EvidenceCatalog reconstructed(EvidenceCatalog before,List<Evidence> evidence,List<Incident> incidents,List<Analysis> analyses){List<Audit> log=new ArrayList<>(before.audit());log.add(new Audit(id(),700,"RECONSTRUCT",List.of(),"synthetic historical transition probe"));return new EvidenceCatalog(before.owner(),before.revision()+1,evidence,incidents,log,analyses,before.rejectedSuggestions());}
 static void validateTransition(EvidenceCatalog before,EvidenceCatalog after)throws Exception {java.lang.reflect.Method method=EvidenceCatalog.class.getDeclaredMethod("validateSuccessor",EvidenceCatalog.class);method.setAccessible(true);try{method.invoke(after,before);}catch(java.lang.reflect.InvocationTargetException failure){if(failure.getCause() instanceof Exception cause)throw cause;throw failure;}}
 static Incident derivedIncident(SourceFingerprint fingerprint){return new Incident(id(),"replacement",0,0,"copied derived source text","",List.of(),List.of(),List.of(),false,false,List.of(fingerprint));}
 static Analysis derivedAnalysis(List<String> evidenceIds,List<SourceFingerprint> lineage){return new Analysis(id(),1,"model","task",hash,evidenceIds,"advisory derived text",1,1,false,lineage);}
 static void successorLineage()throws Exception {
  Evidence restricted=evidence("restricted historical source",true,1);Incident source=incident("old incident",List.of(restricted.id()),List.of());EvidenceCatalog before=base(restricted).upsertIncident(source,2,"CREATE");SourceFingerprint canonical=before.incidents().get(0).lineage().get(0),relaxed=new SourceFingerprint(canonical.sourceId(),canonical.mime(),canonical.sha256(),canonical.size(),false),changedHash=new SourceFingerprint(canonical.sourceId(),canonical.mime(),"b".repeat(64),canonical.size(),true),unknown=new SourceFingerprint(id(),canonical.mime(),canonical.sha256(),canonical.size(),true);
  fails(()->validateTransition(before,reconstructed(before,List.of(),List.of(derivedIncident(relaxed)),List.of())),"replacement incident declassified historical policy");
  fails(()->validateTransition(before,reconstructed(before,List.of(),List.of(derivedIncident(changedHash)),List.of())),"replacement incident changed historical hash");
  fails(()->validateTransition(before,reconstructed(before,List.of(),List.of(derivedIncident(unknown)),List.of())),"replacement incident accepted unverified lineage");
  fails(()->validateTransition(before,reconstructed(before,List.of(),List.of(),List.of(derivedAnalysis(List.of(),List.of(relaxed))))),"new analysis declassified historical policy");
  fails(()->validateTransition(before,reconstructed(before,List.of(),List.of(),List.of(derivedAnalysis(List.of(),List.of(changedHash))))),"new analysis changed historical hash");
  fails(()->validateTransition(before,reconstructed(before,List.of(),List.of(),List.of(derivedAnalysis(List.of(),List.of(unknown))))),"new analysis accepted unverified lineage");
  fails(()->validateTransition(before,reconstructed(before,List.of(),List.of(),List.of(derivedAnalysis(List.of(),List.of(canonical))))),"new analysis accepted historical source outside validated selection");
  validateTransition(before,reconstructed(before,List.of(),List.of(derivedIncident(canonical)),List.of()));yes(true,"canonical retained incident lineage rejected");
  Evidence fresh=evidence("new source in same transaction",false,2);SourceFingerprint fp=new SourceFingerprint(fresh.sourceId(),fresh.mime(),fresh.sha256(),fresh.size(),fresh.metadata().vaultOnly());
  validateTransition(before,reconstructed(before,List.of(fresh),List.of(derivedIncident(fp)),List.of(derivedAnalysis(List.of(fresh.id()),List.of(fp)))));yes(true,"legitimate new original/incident/analysis in same update rejected");
  EvidenceCatalog selected=base(restricted,fresh);fails(()->validateTransition(selected,reconstructed(selected,selected.evidence(),List.of(),List.of(derivedAnalysis(List.of(fresh.id()),List.of(fp,canonical))))),"analysis accepted verified but unselected contributor");
  Incident oldIncident=before.incidents().get(0);Incident expanded=new Incident(oldIncident.id(),oldIncident.title(),oldIncident.startAt(),oldIncident.endAt(),oldIncident.description(),oldIncident.notes(),oldIncident.tags(),oldIncident.evidenceIds(),oldIncident.events(),oldIncident.manualOrder(),oldIncident.archived(),List.of(canonical,unknown));
  fails(()->validateTransition(before,reconstructed(before,before.evidence(),List.of(expanded),List.of())),"surviving incident accepted extra unverified lineage");
  EvidenceCatalog priorAnalysis=selected.saveAnalysis(derivedAnalysis(List.of(fresh.id()),List.of()),2);Analysis oldAnalysis=priorAnalysis.analyses().get(0);Analysis expandedAnalysis=new Analysis(oldAnalysis.id(),oldAnalysis.at(),oldAnalysis.model(),oldAnalysis.task(),oldAnalysis.inputDigest(),oldAnalysis.evidenceIds(),oldAnalysis.text(),oldAnalysis.inputTokens(),oldAnalysis.outputTokens(),oldAnalysis.applied(),List.of(fp,canonical));
  fails(()->validateTransition(priorAnalysis,reconstructed(priorAnalysis,priorAnalysis.evidence(),priorAnalysis.incidents(),List.of(expandedAnalysis))),"existing analysis accepted extra verified but unselected source");
  // Exercise the actual store admission boundary and verify rejection precedes overwrite.
  Path root=Files.createTempDirectory("evidence179-lineage-");try{SessionLeases leases=new SessionLeases();EvidenceEncryptedStore store=new EvidenceEncryptedStore(root.toFile(),Owner.PRIMARY,leases);store.update(c->c.upsertEvidence(restricted,1,"REGISTER").upsertIncident(source,2,"CREATE"));Path disk=ciphertexts(root).get(0);byte[] old=Files.readAllBytes(disk);fails(()->store.update(c->reconstructed(c,List.of(),List.of(derivedIncident(relaxed)),List.of())),"store admitted historical policy substitution");yes(Arrays.equals(old,Files.readAllBytes(disk)),"historical lineage rejection overwrote ciphertext");fails(()->store.update(c->reconstructed(c,List.of(),List.of(),List.of(derivedAnalysis(List.of(),List.of(changedHash))))),"store admitted historical analysis hash substitution");yes(Arrays.equals(old,Files.readAllBytes(disk)),"analysis lineage rejection overwrote ciphertext");}finally{try(var paths=Files.walk(root)){for(Path path:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);}}
 }
 static boolean zero(byte[] bytes){for(byte value:bytes)if(value!=0)return false;return true;}
 static void codecBufferWiping()throws Exception {
  Class<?> type=Class.forName("uk.co.traynor.privategallery.core.evidence.EvidenceCodec$Buffer");java.lang.reflect.Constructor<?> constructor=type.getDeclaredConstructor();constructor.setAccessible(true);java.lang.reflect.Field field=ByteArrayOutputStream.class.getDeclaredField("buf");field.setAccessible(true);
  ByteArrayOutputStream buffer=(ByteArrayOutputStream)constructor.newInstance();byte[] first=(byte[])field.get(buffer),second=null,current=null;byte[] secret=new byte[first.length];Arrays.fill(secret,(byte)85);
  try {buffer.write(secret,0,secret.length);buffer.write(42);second=(byte[])field.get(buffer);yes(second!=first,"growth probe did not replace backing array");yes(zero(first),"retired codec plaintext buffer not wiped after single-byte growth");buffer.writeBytes(new byte[second.length]);current=(byte[])field.get(buffer);yes(current!=second&&zero(second),"retired codec plaintext buffer not wiped after bulk growth");byte[] encoded=buffer.toByteArray();try{yes(encoded[0]==85&&encoded[secret.length]==42,"controlled growth corrupted plaintext encoding");}finally{Arrays.fill(encoded,(byte)0);}}finally{buffer.close();}
  yes(zero(first)&&zero(second)&&zero(current),"normal close did not wipe every owned codec array");
  ByteArrayOutputStream exact=(ByteArrayOutputStream)constructor.newInstance();byte[] exactOwned;try{exact.write(new byte[EvidenceCatalog.MAX_BYTES]);exactOwned=(byte[])field.get(exact);yes(exact.size()==EvidenceCatalog.MAX_BYTES&&exactOwned.length==EvidenceCatalog.MAX_BYTES,"exact4MiB capacity not preserved");fails(()->exact.write(1),"single-byte append exceeded exact4MiB quota");}finally{exact.close();}
  ByteArrayOutputStream exceptional=(ByteArrayOutputStream)constructor.newInstance();List<byte[]> arrays=new ArrayList<>();try {byte[] owned=(byte[])field.get(exceptional);arrays.add(owned);byte[] fill=new byte[owned.length];Arrays.fill(fill,(byte)99);exceptional.write(fill);exceptional.write(fill);arrays.add((byte[])field.get(exceptional));fails(()->exceptional.write(new byte[EvidenceCatalog.MAX_BYTES]),"buffer accepted growth over exact4MiB quota");yes(zero(owned),"retired array survives exceptional encode");}finally{exceptional.close();}yes(arrays.stream().allMatch(Evidence179Checks::zero),"exceptional close left codec plaintext arrays");
 }
 static final class SessionLeases implements EvidenceEncryptedStore.LeaseFactory {
  final HiddenSession session=new HiddenSession(()->1);final HiddenSession.Authority authority;volatile boolean revokeAtCommit,failAtCommit;int currentChecks,revokeOnCurrent;Runnable inCommit=()->{};byte[] captured;
  SessionLeases(){byte[] k=new byte[32];Arrays.fill(k,(byte)7);session.complete(session.beginAuthentication(),k);authority=session.authority();}
  public EvidenceEncryptedStore.KeyLease acquire(){HiddenSession.Operation op=session.beginOperation(authority);return new EvidenceEncryptedStore.KeyLease(){
   public byte[] key(){captured=op.key();return captured;}
   public boolean current(){if(++currentChecks==revokeOnCurrent)session.lock();return session.isCurrent(op);}
   public void commit(EvidenceEncryptedStore.CommitAction action)throws Exception{if(revokeAtCommit)session.lock();if(failAtCommit)throw new IOException("injected pre-promotion failure");AtomicReference<Exception> failure=new AtomicReference<>();session.commit(op,()->{inCommit.run();try{action.run();}catch(Exception e){failure.set(e);}});if(failure.get()!=null)throw failure.get();}
   public void close(){op.close();}
  };}
 }
 static List<Path> ciphertexts(Path root)throws IOException{try(var stream=Files.walk(root)){return stream.filter(p->p.toString().endsWith(".enc")).toList();}}
 static void encryptedStore()throws Exception {
  Path root=Files.createTempDirectory("evidence179-host-");try {
   SessionLeases leases=new SessionLeases();EvidenceEncryptedStore store=new EvidenceEncryptedStore(root.toFile(),Owner.PRIMARY,leases);Evidence e=evidence("SECRET OCR NAME",false,100);
   yes(store.read().revision()==0,"missing store not empty");leases.inCommit=()->{yes(Thread.holdsLock(leases.session),"promotion not in captured session commit gate");try(var files=Files.walk(root)){yes(files.anyMatch(p->p.getFileName().toString().startsWith("staged-")),"ciphertext must be fully staged before short commit monitor");}catch(IOException ex){throw new UncheckedIOException(ex);}};store.update(c->c.upsertEvidence(e,100,"REGISTER"));
   leases.inCommit=()->{};yes(store.read().evidence().get(0).equals(e),"encrypted roundtrip");yes(Arrays.equals(leases.captured,new byte[32]),"lease key not wiped");
   Path disk=ciphertexts(root).get(0);byte[] original=Files.readAllBytes(disk);yes(!new String(original,StandardCharsets.ISO_8859_1).contains("SECRET OCR NAME"),"plaintext on disk");
   // Check the exact specified HMAC domain and fixed Primary metadata UUID with existing cipher.
   byte[] vault=new byte[32];Arrays.fill(vault,(byte)7);Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(vault,"HmacSHA256"));byte[] derived=mac.doFinal("private-gallery:evidence:179:v1:PRIMARY".getBytes(StandardCharsets.UTF_8));
   byte[] decoded=new HiddenRecordStore(disk.getParent().toFile()).read("metadata","00000000-0000-4000-8000-000000017901",derived);yes(EvidenceCatalog.decode(decoded,Owner.PRIMARY).revision()==1,"wrong HMAC domain or record UUID");Arrays.fill(vault,(byte)0);Arrays.fill(derived,(byte)0);Arrays.fill(decoded,(byte)0);
   EvidenceEncryptedStore hidden=new EvidenceEncryptedStore(root.toFile(),Owner.HIDDEN,new SessionLeases());yes(hidden.read().evidence().isEmpty(),"owners shared catalogue");hidden.update(c->c.upsertEvidence(e,101,"REGISTER"));
   Path hiddenFile=ciphertexts(root).stream().filter(p->!p.equals(disk)).findFirst().orElseThrow();Files.write(hiddenFile,original);fails(hidden::read,"cross-owner swap decrypts");
   Files.write(disk,Arrays.copyOf(original,original.length-1));fails(store::read,"truncated ciphertext accepted");Files.write(disk,original);byte[] tampered=original.clone();tampered[tampered.length-1]^=1;Files.write(disk,tampered);fails(store::read,"tag tamper accepted");Files.write(disk,original);
   SessionLeases late=new SessionLeases();late.revokeOnCurrent=3;EvidenceEncryptedStore staleRead=new EvidenceEncryptedStore(root.toFile(),Owner.PRIMARY,late);fails(staleRead::read,"revocation after decrypt still published catalog");yes(Arrays.equals(late.captured,new byte[32]),"late-revoked read key not wiped");
   leases.failAtCommit=true;fails(()->store.update(c->c.upsertEvidence(edit(e,"changed","",List.of(),List.of()),102,"EDIT")),"failed promotion published");leases.failAtCommit=false;yes(Arrays.equals(original,Files.readAllBytes(disk)),"failed promotion overwrote old bytes");
   leases.revokeAtCommit=true;fails(()->store.update(c->c.removeEvidence(e.id(),103)),"revoked commit published");yes(Arrays.equals(original,Files.readAllBytes(disk)),"revoked promotion overwrote old");fails(store::read,"stale read accepted");
   SessionLeases fresh=new SessionLeases();EvidenceEncryptedStore one=new EvidenceEncryptedStore(root.toFile(),Owner.PRIMARY,fresh),two=new EvidenceEncryptedStore(root.resolve(".").toFile(),Owner.PRIMARY,fresh);
   ExecutorService pool=Executors.newFixedThreadPool(2);try {CountDownLatch ready=new CountDownLatch(1);Future<?> f1=pool.submit(()->{try{ready.await();one.update(c->c.upsertEvidence(evidence("thread1",false,1),300,"ADD"));}catch(Exception ex){throw new RuntimeException(ex);}});Future<?> f2=pool.submit(()->{try{ready.await();two.update(c->c.upsertEvidence(evidence("thread2",false,1),300,"ADD"));}catch(Exception ex){throw new RuntimeException(ex);}});ready.countDown();f1.get(10,TimeUnit.SECONDS);f2.get(10,TimeUnit.SECONDS);}finally{pool.shutdownNow();}
   yes(one.read().evidence().size()==3&&one.read().revision()==3,"cross-instance lost update");
   fails(()->one.update(c->EvidenceCatalog.empty(Owner.HIDDEN)),"operator changed owner");
   fails(()->one.update(c->{List<Evidence> all=new ArrayList<>(c.evidence());Evidence old=all.get(0);all.set(0,new Evidence(old.id(),old.sourceId(),old.name(),old.mime(),"b".repeat(64),old.size(),old.importedAt(),old.metadata(),old.description(),old.notes(),old.tags(),old.participants(),old.ocr(),old.corrections()));List<Audit> log=new ArrayList<>(c.audit());log.add(new Audit(id(),400,"FORGED_EDIT",List.of(old.id()),""));return new EvidenceCatalog(c.owner(),c.revision()+1,all,c.incidents(),log,c.analyses(),c.rejectedSuggestions());}),"generic update replaced immutable original");
   // Inject actual post-promotion directory fsync failure through the existing cipher's
   // DirectorySync seam; encryption, staging and atomic move remain the real implementation.
   EvidenceEncryptedStore uncertain=new EvidenceEncryptedStore(root.toFile(),Owner.PRIMARY,fresh);java.lang.reflect.Field field=EvidenceEncryptedStore.class.getDeclaredField("records");field.setAccessible(true);File evidenceRoot=disk.getParent().toFile();field.set(uncertain,new HiddenRecordStore(evidenceRoot,directory->{if(directory.equals(evidenceRoot))throw new IOException("injected post-promotion fsync failure");}));
   byte[] beforeUncertain=Files.readAllBytes(disk);fails(()->uncertain.update(c->c.upsertEvidence(evidence("after promotion",false,1),500,"ADD")),"post-promotion directory sync failure reported success");yes(!Arrays.equals(beforeUncertain,Files.readAllBytes(disk)),"post-promotion failure incorrectly promised old ciphertext");yes(one.read().revision()==4&&one.read().evidence().size()==4,"authoritative reread after uncertain durability");
   byte[] beforeQuota=Files.readAllBytes(disk);fails(()->one.update(c->{for(int i=0;i<70;i++){Evidence huge=evidence("quota"+i,false,0);c=c.upsertEvidence(edit(huge,huge.name(),"x".repeat(65536),List.of(),List.of()),600,"ADD");}return c;}),"quota update persisted");yes(Arrays.equals(beforeQuota,Files.readAllBytes(disk)),"quota failure overwrote ciphertext");
   try(var stream=Files.walk(root)){yes(stream.noneMatch(p->p.getFileName().toString().startsWith("staged-")),"ciphertext staging leaked");}
  } finally {try(var stream=Files.walk(root)){for(Path p:stream.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}}
 }
}
