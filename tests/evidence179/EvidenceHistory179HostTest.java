package uk.co.traynor.privategallery.core.evidence;
import java.io.*;
import java.util.*;
import uk.co.traynor.privategallery.core.evidence.EvidenceCatalog.*;

public final class EvidenceHistory179HostTest {
 static int passed;
 static void check(boolean value){if(!value)throw new AssertionError();passed++;}
 static String id(int n){return String.format("00000000-0000-4000-8000-%012d",n);}
 static void reject(Runnable action){try{action.run();throw new AssertionError("accepted invalid history");}catch(IllegalArgumentException expected){passed++;}}
 static Evidence evidence(int n){return new Evidence(id(n),id(n+100),"record","text/plain","0".repeat(64),3,1,new Metadata("source",0,0,0,0,0,0,new LinkedHashMap<>(Map.of("z","last","a","first")),false),"","unrelated notes",List.of(),List.of(),List.of(),List.of());}
 static SourceFingerprint fp(Evidence e){return new SourceFingerprint(e.sourceId(),e.mime(),e.sha256(),e.size(),false);}
 static void string(DataOutputStream o,String s)throws Exception{byte[] bytes=s.getBytes(java.nio.charset.StandardCharsets.UTF_8);o.writeInt(bytes.length);o.write(bytes);}
 static void strings(DataOutputStream o,List<String> ss)throws Exception{o.writeInt(ss.size());for(String s:ss)string(o,s);}
 static void fingerprint(DataOutputStream o,SourceFingerprint f)throws Exception{string(o,f.sourceId());string(o,f.mime());string(o,f.sha256());o.writeLong(f.size());o.writeBoolean(f.vaultOnly());}
 // Independent v1 writer: exact old schema, without new association/reference extension fields.
 static byte[] versionOne(EvidenceCatalog c)throws Exception{
  var bytes=new ByteArrayOutputStream();var o=new DataOutputStream(bytes);o.writeInt(0x50474543);o.writeInt(1);string(o,c.owner().name());o.writeLong(c.revision());
  var writer=EvidenceCodec.class.getDeclaredMethod("evidence",DataOutputStream.class,Evidence.class);writer.setAccessible(true);
  o.writeInt(c.evidence().size());for(Evidence e:c.evidence())writer.invoke(null,o,e);
  o.writeInt(c.incidents().size());for(Incident i:c.incidents()){
   string(o,i.id());string(o,i.title());o.writeLong(i.startAt());o.writeLong(i.endAt());string(o,i.description());string(o,i.notes());strings(o,i.tags());strings(o,i.evidenceIds());o.writeInt(0);o.writeBoolean(i.manualOrder());o.writeBoolean(i.archived());o.writeInt(i.lineage().size());for(var f:i.lineage())fingerprint(o,f);
  }
  o.writeInt(0);o.writeInt(c.analyses().size());for(Analysis a:c.analyses()){
   string(o,a.id());o.writeLong(a.at());string(o,a.model());string(o,a.task());string(o,a.inputDigest());strings(o,a.evidenceIds());string(o,a.text());o.writeLong(a.inputTokens());o.writeLong(a.outputTokens());o.writeBoolean(a.applied());o.writeInt(a.lineage().size());for(var f:a.lineage())fingerprint(o,f);
  }o.writeInt(0);return bytes.toByteArray();
 }
 public static void main(String[] args)throws Exception{
  for(Owner owner:Owner.values()){
   Evidence a=evidence(1),b=evidence(2);var c=EvidenceCatalog.empty(owner).upsertEvidence(a,1,"IMPORT").upsertEvidence(b,1,"IMPORT");
   Analysis analysis=new Analysis(id(3),2,"test","SUMMARY","0".repeat(64),List.of(a.id(),b.id()),"RAW accepted JSON references retained verbatim",3,2,false,List.of());
   c=c.saveAnalysis(analysis,2);var saved=c.analyses().get(0);check(saved.sourceReferences().equals(List.of(new SourceReference(a.id(),fp(a)),new SourceReference(b.id(),fp(b)))));
   var i=new Incident(id(4),"selected",0,0,"","",List.of(),List.of(a.id(),b.id()),List.of(),false,false,List.of(fp(a),fp(b)));
   c=c.upsertIncident(i,3,"CREATE").associateAnalysis(saved.id(),i.id(),4);check(c.incidents().get(0).analysisIds().equals(List.of(saved.id())));check(c.analyses().get(0).applied());
   var associated=c;
   var compatibleEdit=new Incident(i.id(),"edited",0,0,"","",List.of(),i.evidenceIds(),i.events(),false,false,i.lineage());
   check(c.upsertIncident(compatibleEdit,5,"EDIT").incidents().get(0).analysisIds().equals(List.of(saved.id())));
   var unlinked=c.unlink(List.of(b.id()),i.id(),5);unlinked.validateSuccessor(associated);check(unlinked.evidence().size()==2);check(unlinked.incidents().get(0).analysisIds().equals(List.of(saved.id())));
   var removed=unlinked.removeEvidence(b.id(),6);removed.validateSuccessor(unlinked);check(removed.analyses().get(0).evidenceIds().equals(List.of(a.id())));check(removed.analyses().get(0).sourceReferences().equals(saved.sourceReferences()));
   var decoded=EvidenceCatalog.decode(removed.encode(),owner);check(decoded.analyses().equals(removed.analyses()));check(decoded.incidents().equals(removed.incidents()));
   var split=associated.split(i.id(),List.of(b.id()),id(5),"split",7);check(split.incidents().get(1).analysisIds().equals(List.of(saved.id())));split.validateSuccessor(associated);
   var merged=split.merge(i.id(),List.of(id(5)),8);merged.validateSuccessor(split);check(merged.incidents().get(0).analysisIds().equals(List.of(saved.id())));
   var legacy=EvidenceCatalog.decode(versionOne(associated),owner);check(legacy.analyses().get(0).sourceReferences().equals(saved.sourceReferences()));check(legacy.incidents().get(0).analysisIds().isEmpty());check(legacy.incidents().get(0).lineage().equals(associated.incidents().get(0).lineage()));
   var legacyRemoved=EvidenceCatalog.decode(versionOne(removed),owner);check(legacyRemoved.analyses().get(0).sourceReferences().equals(List.of(new SourceReference(a.id(),fp(a)))));check(legacyRemoved.analyses().get(0).text().equals(saved.text()));
   var foreign=new SourceReference(id(99),fp(a));var forged=new Analysis(saved.id(),saved.at(),saved.model(),saved.task(),saved.inputDigest(),List.of(a.id()),saved.text(),3,2,true,saved.lineage(),List.of(new SourceReference(a.id(),fp(a)),foreign));
   var candidate=new EvidenceCatalog(owner,removed.revision(),removed.evidence(),removed.incidents(),removed.audit(),List.of(forged),List.of());reject(()->candidate.validateSuccessor(unlinked));
   var mismatch=new SourceReference(a.id(),fp(b));reject(()->new EvidenceCatalog(owner,0,List.of(a),List.of(),List.of(),List.of(new Analysis(id(8),0,"test","SUMMARY","0".repeat(64),List.of(a.id()),"",0,0,false,List.of(fp(b)),List.of(mismatch))),List.of()));
   check(EvidenceCatalog.decode(associated.encode(),owner).evidence().get(0).metadata().embedded().equals(a.metadata().embedded()));
  }
  System.out.println("EvidenceHistory179HostTest PASS "+passed);
 }
}
