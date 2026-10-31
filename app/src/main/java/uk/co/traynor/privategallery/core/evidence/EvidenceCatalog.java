package uk.co.traynor.privategallery.core.evidence;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.Function;

/** Owner-local, immutable evidence annotations. Source metadata is a claim, never certification. */
public final class EvidenceCatalog {
 public static final int MAX_BYTES=4*1024*1024, MAX_EVIDENCE=4096, MAX_INCIDENTS=1024, MAX_AUDIT=20000;
 public enum Owner {PRIMARY,HIDDEN}
 public record Metadata(String sourceName,long fileCreatedAt,long fileModifiedAt,long dateAddedAt,long captureAt,int width,int height,Map<String,String> embedded,boolean vaultOnly) {
  public Metadata {sourceName=EvidenceCatalog.text(sourceName,1024);nonnegative(fileCreatedAt,fileModifiedAt,dateAddedAt,captureAt,width,height);Objects.requireNonNull(embedded);if(embedded.size()>256)throw invalid("Embedded metadata quota");Map<String,String> values=new LinkedHashMap<>();for(var entry:embedded.entrySet())values.put(text(entry.getKey(),256),text(entry.getValue(),65536));embedded=Collections.unmodifiableMap(values);}
 }
 public record Word(int pageIndex,String text,float confidence,int left,int top,int right,int bottom) {
  public Word {nonnegative(pageIndex,left,top,right,bottom);if(right<left||bottom<top)throw invalid("Word geometry");text=EvidenceCatalog.text(text,100000);EvidenceCatalog.confidence(confidence);}
 }
 public record ExtractedTime(int pageIndex,String text,long epochMillis,String provenance,float confidence) {
  public ExtractedTime {nonnegative(pageIndex,epochMillis);text=EvidenceCatalog.text(text,4096);provenance=EvidenceCatalog.text(provenance,4096);if(provenance.isBlank())throw invalid("Time provenance required");EvidenceCatalog.confidence(confidence);}
 }
 public record Ocr(String id,long createdAt,String engine,int rotation,int width,int height,String text,float confidence,List<Word> words,List<ExtractedTime> times,int pageCount,boolean partial) {
  public Ocr {uuid(id);nonnegative(createdAt,width,height);engine=EvidenceCatalog.text(engine,256);if(engine.isBlank()||!Set.of(0,90,180,270).contains(rotation)||pageCount<1||pageCount>32)throw invalid("OCR extraction fields");text=EvidenceCatalog.text(text,100000);EvidenceCatalog.confidence(confidence);words=copy(words,100000);times=copy(times,4096);for(Word word:words)if(word.pageIndex()>=pageCount)throw invalid("Word page index");for(ExtractedTime time:times)if(time.pageIndex()>=pageCount)throw invalid("Time page index");}
 }
 public record Correction(String id,String ocrId,long createdAt,String text) {
  public Correction {uuid(id);uuid(ocrId);nonnegative(createdAt);text=EvidenceCatalog.text(text,100000);}
 }
 public record Evidence(String id,String sourceId,String name,String mime,String sha256,long size,long importedAt,Metadata metadata,String description,String notes,List<String> tags,List<String> participants,List<Ocr> ocr,List<Correction> corrections) {
  public Evidence {uuid(id);uuid(sourceId);name=EvidenceCatalog.text(name,1024);mime=EvidenceCatalog.mime(mime);digest(sha256);nonnegative(size,importedAt);Objects.requireNonNull(metadata);description=EvidenceCatalog.text(description,65536);notes=EvidenceCatalog.text(notes,65536);tags=strings(tags,256,256);participants=strings(participants,256,256);ocr=copy(ocr,8);unique(ocr,Ocr::id);corrections=copy(corrections,20000);unique(corrections,Correction::id);Set<String> ocrIds=new HashSet<>();for(Ocr value:ocr)ocrIds.add(value.id());for(Correction correction:corrections)if(!ocrIds.contains(correction.ocrId()))throw invalid("Correction references unavailable OCR");}
  public String transcript(){if(ocr.isEmpty())return "";String latest=ocr.get(ocr.size()-1).id();for(int i=corrections.size()-1;i>=0;i--)if(corrections.get(i).ocrId().equals(latest))return corrections.get(i).text();return ocr.get(ocr.size()-1).text();}
 }
 public record Event(String id,List<String> evidenceIds,long timestamp,String provenance,String title,String notes) {
  public Event {uuid(id);evidenceIds=ids(evidenceIds,MAX_EVIDENCE);nonnegative(timestamp);if(!Set.of("SOURCE_CAPTURE","EXTRACTED","USER","INFERRED","AI_INFERRED").contains(provenance))throw invalid("Event provenance");title=EvidenceCatalog.text(title,1024);notes=EvidenceCatalog.text(notes,65536);}
 }
 public record SourceFingerprint(String sourceId,String mime,String sha256,long size,boolean vaultOnly) {
  public SourceFingerprint {uuid(sourceId);mime=EvidenceCatalog.mime(mime);digest(sha256);nonnegative(size);}
 }
 public record Incident(String id,String title,long startAt,long endAt,String description,String notes,List<String> tags,List<String> evidenceIds,List<Event> events,boolean manualOrder,boolean archived,List<SourceFingerprint> lineage,List<String> analysisIds) {
  public Incident(String id,String title,long startAt,long endAt,String description,String notes,List<String> tags,List<String> evidenceIds,List<Event> events,boolean manualOrder,boolean archived,List<SourceFingerprint> lineage){this(id,title,startAt,endAt,description,notes,tags,evidenceIds,events,manualOrder,archived,lineage,List.of());}
  public Incident {uuid(id);title=EvidenceCatalog.text(title,1024);nonnegative(startAt,endAt);if(startAt>0&&endAt>0&&endAt<startAt)throw invalid("Incident time range");description=EvidenceCatalog.text(description,65536);notes=EvidenceCatalog.text(notes,65536);tags=strings(tags,256,256);evidenceIds=ids(evidenceIds,MAX_EVIDENCE);events=copy(events,20000);unique(events,Event::id);lineage=fingerprints(lineage);analysisIds=ids(analysisIds,MAX_EVIDENCE);}
 }
 public record Audit(String id,long at,String action,List<String> targetIds,String detail) {
  public Audit {uuid(id);nonnegative(at);action=EvidenceCatalog.text(action,256);if(action.isBlank())throw invalid("Audit action required");targetIds=strings(targetIds,MAX_EVIDENCE+MAX_INCIDENTS,256);detail=EvidenceCatalog.text(detail,65536);}
 }
 public record SourceReference(String evidenceId,SourceFingerprint fingerprint) {
  public SourceReference {uuid(evidenceId);Objects.requireNonNull(fingerprint);}
 }
 public record Analysis(String id,long at,String model,String task,String inputDigest,List<String> evidenceIds,String text,long inputTokens,long outputTokens,boolean applied,List<SourceFingerprint> lineage,List<SourceReference> sourceReferences) {
  public Analysis(String id,long at,String model,String task,String inputDigest,List<String> evidenceIds,String text,long inputTokens,long outputTokens,boolean applied,List<SourceFingerprint> lineage){this(id,at,model,task,inputDigest,evidenceIds,text,inputTokens,outputTokens,applied,lineage,List.of());}
  public Analysis {uuid(id);nonnegative(at,inputTokens,outputTokens);model=EvidenceCatalog.text(model,256);task=EvidenceCatalog.text(task,1024);digest(inputDigest);evidenceIds=ids(evidenceIds,MAX_EVIDENCE);text=EvidenceCatalog.text(text,100000);lineage=fingerprints(lineage);sourceReferences=copy(sourceReferences,MAX_EVIDENCE);unique(sourceReferences,SourceReference::evidenceId);for(SourceReference ref:sourceReferences)if(!lineage.contains(ref.fingerprint()))throw invalid("Unverified analysis source reference");}
 }
 public record Suggestion(String id,String kind,List<String> evidenceIds,String incidentId,String title,List<String> reasons) {
  public Suggestion {digest(id);kind=EvidenceCatalog.text(kind,64);evidenceIds=ids(evidenceIds,MAX_EVIDENCE);incidentId=EvidenceCatalog.text(incidentId,36);if(!incidentId.isEmpty())uuid(incidentId);title=EvidenceCatalog.text(title,1024);reasons=strings(reasons,16,256);}
 }
 private final Owner owner;
 private final long revision;
 private final List<Evidence> evidence;
 private final List<Incident> incidents;
 private final List<Audit> audit;
 private final List<Analysis> analyses;
 private final List<String> rejectedSuggestions;
 public EvidenceCatalog(Owner owner,long revision,List<Evidence> evidence,List<Incident> incidents,List<Audit> audit,List<Analysis> analyses,List<String> rejectedSuggestions) {
  this.owner=Objects.requireNonNull(owner);nonnegative(revision);this.revision=revision;
  this.evidence=copy(evidence,MAX_EVIDENCE);this.incidents=copy(incidents,MAX_INCIDENTS);this.audit=copy(audit,MAX_AUDIT);this.analyses=copy(analyses,MAX_EVIDENCE).stream().map(a->{if(!a.sourceReferences().isEmpty())return a;List<SourceReference> refs=new ArrayList<>();for(String id:a.evidenceIds())refs.add(new SourceReference(id,fingerprint(requireEvidence(id))));return withAnalysis(a,a.evidenceIds(),a.applied(),a.lineage(),refs);}).toList();this.rejectedSuggestions=strings(rejectedSuggestions,MAX_AUDIT,64);
  unique(this.evidence,Evidence::id);unique(this.incidents,Incident::id);unique(this.audit,Audit::id);unique(this.analyses,Analysis::id);for(String rejected:this.rejectedSuggestions)digest(rejected);
  Map<String,Evidence> originals=new HashMap<>();for(Evidence value:this.evidence){Evidence old=originals.putIfAbsent(value.sourceId(),value);if(old!=null&&!fingerprint(old).equals(fingerprint(value)))throw invalid("Conflicting source identity");}
  List<SourceFingerprint> allSources=new ArrayList<>();for(Evidence value:this.evidence)allSources.add(fingerprint(value));for(Incident value:this.incidents)allSources.addAll(value.lineage());for(Analysis value:this.analyses)allSources.addAll(value.lineage());unionSources(allSources);
  for(Incident incident:this.incidents){validateReferences(incident.evidenceIds());for(Event event:incident.events())validateReferences(event.evidenceIds());List<SourceFingerprint> required=contributions(incident);if(!incident.lineage().containsAll(required))throw invalid("Missing incident source lineage");for(String id:incident.analysisIds()){Analysis a=requireAnalysis(id);if(!a.applied()||!incident.lineage().containsAll(a.lineage()))throw invalid("Missing applied incident analysis lineage");}}
  for(Analysis analysis:this.analyses){validateReferences(analysis.evidenceIds());for(String id:analysis.evidenceIds())if(!analysis.sourceReferences().contains(new SourceReference(id,fingerprint(requireEvidence(id)))))throw invalid("Missing analysis source reference");for(SourceReference ref:analysis.sourceReferences()){int at=index(this.evidence,ref.evidenceId(),Evidence::id);if(at>=0&&!fingerprint(this.evidence.get(at)).equals(ref.fingerprint()))throw invalid("Conflicting historical evidence reference");}}
  // Bound the complete object, including duplicated strings/lineage, before accepting any mutation.
  byte[] checked=EvidenceCodec.encode(this);Arrays.fill(checked,(byte)0);
 }
 public static EvidenceCatalog empty(Owner owner){return new EvidenceCatalog(owner,0,List.of(),List.of(),List.of(),List.of(),List.of());}
 public Owner owner(){return owner;}
 public long revision(){return revision;}
 public List<Evidence> evidence(){return evidence;}
 public List<Incident> incidents(){return incidents;}
 public List<Audit> audit(){return audit;}
 public List<Analysis> analyses(){return analyses;}
 public List<String> rejectedSuggestions(){return rejectedSuggestions;}
 public byte[] encode(){return EvidenceCodec.encode(this);}
 public static EvidenceCatalog decode(byte[] bytes,Owner owner){return EvidenceCodec.decode(bytes,owner);}

 public EvidenceCatalog upsertEvidence(Evidence value,long now,String action) {
  Objects.requireNonNull(value);List<Evidence> values=new ArrayList<>(evidence);int index=index(evidence,value.id(),Evidence::id);
  if(index>=0){Evidence old=evidence.get(index);if(!fingerprint(old).equals(fingerprint(value))||old.importedAt()!=value.importedAt()||!old.metadata().equals(value.metadata()))throw invalid("Original identity/metadata is immutable");if(!prefix(value.ocr(),old.ocr())||!prefix(value.corrections(),old.corrections()))throw invalid("OCR and correction history is append-only");if(old.equals(value))return this;values.set(index,value);}else values.add(value);
  return changed(values,incidents,analyses,rejectedSuggestions,now,action,List.of(value.id()),"Evidence annotations updated");
 }
 public EvidenceCatalog removeEvidence(String id,long now) {
  requireEvidence(id);List<Evidence> values=evidence.stream().filter(e->!e.id().equals(id)).toList();List<Incident> groups=new ArrayList<>();
  for(Incident incident:incidents){List<Event> events=new ArrayList<>();for(Event event:incident.events())events.add(new Event(event.id(),without(event.evidenceIds(),List.of(id)),event.timestamp(),event.provenance(),event.title(),event.notes()));groups.add(withIncident(incident,without(incident.evidenceIds(),List.of(id)),events,incident.manualOrder(),incident.archived(),incident.lineage()));}
  List<Analysis> results=new ArrayList<>();for(Analysis a:analyses)results.add(withAnalysis(a,without(a.evidenceIds(),List.of(id)),a.applied(),a.lineage(),a.sourceReferences()));
  return changed(values,groups,results,rejectedSuggestions,now,"REMOVE_EVIDENCE",List.of(id),"Record removed; original retained; source fingerprints retained");
 }
 public EvidenceCatalog upsertIncident(Incident value,long now,String action) {
  Objects.requireNonNull(value);validateReferences(value.evidenceIds());for(Event event:value.events())validateReferences(event.evidenceIds());
  int index=index(incidents,value.id(),Incident::id);Incident old=index<0?null:incidents.get(index);List<SourceFingerprint> sources=new ArrayList<>();if(old!=null)sources.addAll(old.lineage());sources.addAll(contributions(value));
  // Callers may incorporate a saved analysis, but may never forge/relabel source identities.
  for(SourceFingerprint source:value.lineage()){boolean known=sources.contains(source)||evidence.stream().anyMatch(e->fingerprint(e).equals(source))||analyses.stream().anyMatch(a->a.lineage().contains(source));if(!known)throw invalid("Unverified incident lineage");sources.add(source);}
  List<String> associations=old==null?value.analysisIds():union(old.analysisIds(),value.analysisIds());for(String id:associations)sources.addAll(requireAnalysis(id).lineage());
  Incident updated=new Incident(value.id(),value.title(),value.startAt(),value.endAt(),value.description(),value.notes(),value.tags(),value.evidenceIds(),value.events(),value.manualOrder(),value.archived(),unionSources(sources),associations);if(updated.equals(old))return this;
  List<Incident> groups=new ArrayList<>(incidents);if(index<0)groups.add(updated);else groups.set(index,updated);
  return changed(evidence,groups,analyses,rejectedSuggestions,now,action,List.of(value.id()),"Incident updated; contributing source lineage retained");
 }
 public EvidenceCatalog deleteIncident(String id,long now) {requireIncident(id);return changed(evidence,incidents.stream().filter(i->!i.id().equals(id)).toList(),analyses,rejectedSuggestions,now,"DELETE_INCIDENT",List.of(id),"Incident references removed; originals retained");}
 public EvidenceCatalog assign(List<String> ids,String incidentId,boolean move,long now) {
  ids=ids(ids,MAX_EVIDENCE);validateReferences(ids);Incident target=requireIncident(incidentId);if(ids.isEmpty())return this;
  List<Event> transferred=new ArrayList<>();List<SourceFingerprint> targetSources=new ArrayList<>(target.lineage());
  if(move)for(Incident incident:incidents)if(!incident.id().equals(incidentId)){List<Event> selected=selectedEvents(incident.events(),ids);if(!selected.isEmpty()){transferred=combineEvents(transferred,selected);targetSources.addAll(incident.lineage());}}
  List<Incident> groups=new ArrayList<>();
  for(Incident incident:incidents){boolean destination=incident.id().equals(target.id());List<String> links=destination?union(incident.evidenceIds(),ids):move?without(incident.evidenceIds(),ids):incident.evidenceIds();List<Event> events=destination?combineEvents(incident.events(),transferred):move?detachEvents(incident.events(),ids,true):incident.events();List<SourceFingerprint> sources=new ArrayList<>(destination?targetSources:incident.lineage());for(String linked:links)sources.add(fingerprint(requireEvidence(linked)));groups.add(withIncident(incident,links,events,incident.manualOrder(),incident.archived(),unionSources(sources)));}
  if(groups.equals(incidents))return this;return changed(evidence,groups,analyses,rejectedSuggestions,now,move?"MOVE_EVIDENCE":"ASSIGN_EVIDENCE",union(ids,List.of(incidentId)),"Incident assignment; moved event source links transferred with provenance and lineage");
 }
 public EvidenceCatalog unlink(List<String> ids,String incidentId,long now) {
  ids=ids(ids,MAX_EVIDENCE);validateReferences(ids);Incident old=requireIncident(incidentId);List<String> remaining=without(old.evidenceIds(),ids);List<Event> events=detachEvents(old.events(),ids,false);if(remaining.equals(old.evidenceIds())&&events.equals(old.events()))return this;
  List<Incident> groups=new ArrayList<>(incidents);groups.set(index(incidents,incidentId,Incident::id),withIncident(old,remaining,events,old.manualOrder(),old.archived(),old.lineage()));
  return changed(evidence,groups,analyses,rejectedSuggestions,now,"UNLINK_EVIDENCE",union(ids,List.of(incidentId)),"Record and event references unlinked; event text and source lineage retained");
 }
 public EvidenceCatalog merge(String targetId,List<String> sourceIds,long now) {
  Incident target=requireIncident(targetId);sourceIds=ids(sourceIds,MAX_INCIDENTS);if(sourceIds.contains(targetId))throw invalid("Cannot merge incident into itself");for(String source:sourceIds)requireIncident(source);if(sourceIds.isEmpty())return this;
  List<String> links=target.evidenceIds(),tags=target.tags(),associations=target.analysisIds();List<Event> events=new ArrayList<>(target.events());List<SourceFingerprint> lineage=new ArrayList<>(target.lineage());String description=target.description(),notes=target.notes();long start=target.startAt(),end=target.endAt();
  for(String sourceId:sourceIds){Incident source=requireIncident(sourceId);associations=union(associations,source.analysisIds());links=union(links,source.evidenceIds());tags=union(tags,source.tags());events=combineEvents(events,source.events());lineage.addAll(source.lineage());description=join(description,source.title()+"\n"+source.description());notes=join(notes,source.notes());if(source.startAt()>0&&(start==0||source.startAt()<start))start=source.startAt();end=Math.max(end,source.endAt());}
  Incident merged=new Incident(target.id(),target.title(),start,end,description,notes,tags,links,events,target.manualOrder(),target.archived(),unionSources(lineage),associations);List<Incident> groups=new ArrayList<>();
  for(Incident incident:incidents)groups.add(incident.id().equals(targetId)?merged:sourceIds.contains(incident.id())?withIncident(incident,incident.evidenceIds(),incident.events(),incident.manualOrder(),true,incident.lineage()):incident);
  if(groups.equals(incidents))return this;return changed(evidence,groups,analyses,rejectedSuggestions,now,"MERGE_INCIDENT",union(List.of(targetId),sourceIds),"Source incidents archived; their information and lineage preserved");
 }
 public EvidenceCatalog split(String sourceId,List<String> ids,String newId,String title,long now) {
  Incident source=requireIncident(sourceId);ids=ids(ids,MAX_EVIDENCE);validateReferences(ids);uuid(newId);if(ids.isEmpty()||!source.evidenceIds().containsAll(ids)||index(incidents,newId,Incident::id)>=0)throw invalid("Invalid split selection");
  List<Event> selected=selectedEvents(source.events(),ids);
  Incident created=new Incident(newId,title,source.startAt(),source.endAt(),source.description(),source.notes(),source.tags(),ids,selected,source.manualOrder(),false,source.lineage(),source.analysisIds());List<Incident> groups=new ArrayList<>(incidents);
  groups.set(index(incidents,sourceId,Incident::id),withIncident(source,without(source.evidenceIds(),ids),detachEvents(source.events(),ids,true),source.manualOrder(),source.archived(),source.lineage()));groups.add(created);
  return changed(evidence,groups,analyses,rejectedSuggestions,now,"SPLIT_INCIDENT",List.of(sourceId,newId),"Selected record and event source links moved to a new incident; event content and source lineage retained");
 }
 public EvidenceCatalog archive(String id,boolean archived,long now) {Incident old=requireIncident(id);if(old.archived()==archived)return this;return upsertIncident(withIncident(old,old.evidenceIds(),old.events(),old.manualOrder(),archived,old.lineage()),now,archived?"ARCHIVE_INCIDENT":"RESTORE_INCIDENT");}
 public EvidenceCatalog saveOcr(String evidenceId,Ocr value,long now) {
  Evidence old=requireEvidence(evidenceId);Objects.requireNonNull(value);if(old.ocr().size()>=8)throw invalid("OCR extraction history limit reached");if(index(old.ocr(),value.id(),Ocr::id)>=0)throw invalid("Duplicate extraction");List<Ocr> history=new ArrayList<>(old.ocr());history.add(value);return upsertEvidence(withText(old,history,old.corrections()),now,"SAVE_OCR");
 }
 public EvidenceCatalog reviseOcr(String evidenceId,String ocrId,String text,long now) {
  Evidence old=requireEvidence(evidenceId);if(index(old.ocr(),ocrId,Ocr::id)<0)throw invalid("OCR extraction unavailable");List<Correction> corrections=new ArrayList<>(old.corrections());
  String current=old.ocr().stream().filter(o->o.id().equals(ocrId)).findFirst().orElseThrow().text();for(Correction correction:corrections)if(correction.ocrId().equals(ocrId))current=correction.text();if(current.equals(text))return this;
  corrections.add(new Correction(newId(),ocrId,now,text));return upsertEvidence(withText(old,old.ocr(),corrections),now,"REVISE_OCR");
 }
 public EvidenceCatalog saveAnalysis(Analysis value,long now) {
  Objects.requireNonNull(value);validateReferences(value.evidenceIds());List<SourceFingerprint> sources=new ArrayList<>();for(String id:value.evidenceIds())sources.add(fingerprint(requireEvidence(id)));int at=index(analyses,value.id(),Analysis::id);if(at>=0)sources.addAll(analyses.get(at).lineage());
  List<SourceReference> refs=at>=0?analyses.get(at).sourceReferences():value.evidenceIds().stream().map(id->new SourceReference(id,fingerprint(requireEvidence(id)))).toList();
  Analysis updated=withAnalysis(value,value.evidenceIds(),value.applied(),unionSources(sources),refs);List<Analysis> results=new ArrayList<>(analyses);
  if(at>=0){Analysis old=analyses.get(at);if(updated.equals(old))return this;if(!old.id().equals(updated.id())||old.at()!=updated.at()||!old.model().equals(updated.model())||!old.task().equals(updated.task())||!old.inputDigest().equals(updated.inputDigest())||!old.evidenceIds().equals(updated.evidenceIds())||!old.text().equals(updated.text())||old.inputTokens()!=updated.inputTokens()||old.outputTokens()!=updated.outputTokens()||old.applied()||!updated.applied())throw invalid("Analysis is append-only except explicit application");results.set(at,updated);}else results.add(updated);
  return changed(evidence,incidents,results,rejectedSuggestions,now,updated.applied()?"APPLY_ANALYSIS":"SAVE_ANALYSIS",List.of(updated.id()),"Advisory analysis; lineage derived from selected originals");
 }
 /** Explicit owner-approved association; source references were captured when the advisory was saved. */
 public EvidenceCatalog associateAnalysis(String analysisId,String incidentId,long now) {
  Analysis a=requireAnalysis(analysisId);Incident i=requireIncident(incidentId);if(a.sourceReferences().isEmpty())throw invalid("Analysis has no validated source references");
  if(i.analysisIds().contains(analysisId))return this;
  List<Analysis> results=new ArrayList<>(analyses);results.set(index(analyses,analysisId,Analysis::id),withAnalysis(a,a.evidenceIds(),true,a.lineage(),a.sourceReferences()));
  List<SourceFingerprint> sources=new ArrayList<>(i.lineage());sources.addAll(a.lineage());
  Incident updated=new Incident(i.id(),i.title(),i.startAt(),i.endAt(),i.description(),i.notes(),i.tags(),i.evidenceIds(),i.events(),i.manualOrder(),i.archived(),unionSources(sources),union(i.analysisIds(),List.of(analysisId)));
  List<Incident> groups=new ArrayList<>(incidents);groups.set(index(incidents,incidentId,Incident::id),updated);
  return changed(evidence,groups,results,rejectedSuggestions,now,"ASSOCIATE_ANALYSIS",List.of(analysisId,incidentId),"Owner applied advisory to incident; references and lineage retained");
 }
 public EvidenceCatalog moveEvent(String incidentId,String eventId,int index,long now) {
  Incident old=requireIncident(incidentId);int from=index(old.events(),eventId,Event::id);if(from<0||index<0||index>=old.events().size())throw invalid("Invalid event destination");if(from==index&&old.manualOrder())return this;List<Event> events=new ArrayList<>(old.events());events.add(index,events.remove(from));return upsertIncident(withIncident(old,old.evidenceIds(),events,true,old.archived(),old.lineage()),now,"MOVE_EVENT");
 }
 public EvidenceCatalog rejectSuggestion(String suggestionId,long now) {digest(suggestionId);if(rejectedSuggestions.contains(suggestionId))return this;if(suggestions().stream().noneMatch(s->s.id().equals(suggestionId)))throw invalid("Suggestion unavailable");List<String> rejected=new ArrayList<>(rejectedSuggestions);rejected.add(suggestionId);return changed(evidence,incidents,analyses,rejected,now,"REJECT_SUGGESTION",List.of(suggestionId),"Suggestion rejected; no grouping applied");}
 public EvidenceCatalog acceptSuggestion(Suggestion value,String newIncidentId,String title,long now) {
  Objects.requireNonNull(value);Suggestion actual=suggestions().stream().filter(s->s.id().equals(value.id())).findFirst().orElseThrow(()->invalid("Suggestion unavailable"));if(!actual.equals(value))throw invalid("Suggestion changed");uuid(newIncidentId);if(index(incidents,newIncidentId,Incident::id)>=0)throw invalid("Incident already exists");
  if(actual.kind().equals("ASSIGN")) {
   Incident target=requireIncident(actual.incidentId());if(target.archived())throw invalid("Suggested incident archived");
   List<SourceFingerprint> lineage=new ArrayList<>(target.lineage());for(String id:actual.evidenceIds())lineage.add(fingerprint(requireEvidence(id)));
   Incident updated=withIncident(target,union(target.evidenceIds(),actual.evidenceIds()),target.events(),target.manualOrder(),target.archived(),unionSources(lineage));
   List<Incident> groups=new ArrayList<>(incidents);groups.set(index(incidents,target.id(),Incident::id),updated);List<String> rejected=new ArrayList<>(rejectedSuggestions);rejected.add(actual.id());
   return changed(evidence,groups,analyses,rejected,now,"ACCEPT_ASSIGN_SUGGESTION",union(List.of(actual.id(),target.id()),actual.evidenceIds()),"Owner linked suggested references; no incident merged or original changed");
  }
  List<SourceFingerprint> sources=new ArrayList<>();for(String id:actual.evidenceIds())sources.add(fingerprint(requireEvidence(id)));Incident created=new Incident(newIncidentId,title,0,0,"","",List.of(),actual.evidenceIds(),List.of(),false,false,unionSources(sources));List<Incident> groups=new ArrayList<>(incidents);groups.add(created);List<String> rejected=new ArrayList<>(rejectedSuggestions);rejected.add(actual.id());return changed(evidence,groups,analyses,rejected,now,"ACCEPT_SUGGESTION",union(List.of(actual.id(),newIncidentId),actual.evidenceIds()),"Owner accepted grouping hint: "+String.join(", ",actual.reasons()));
 }
 public List<Event> timeline(String incidentId) {Incident incident=requireIncident(incidentId);if(incident.manualOrder())return incident.events();List<Event> values=new ArrayList<>(incident.events());values.sort(Comparator.comparing((Event e)->e.timestamp()==0).thenComparingLong(Event::timestamp));return List.copyOf(values);}
 public List<Evidence> search(String query,long from,long through,boolean includeArchived) {
  query=text(query,4096).strip().toLowerCase(Locale.ROOT);nonnegative(from,through);if(from>0&&through>0&&from>through)throw invalid("Invalid search date range");List<Evidence> matches=new ArrayList<>();String[] terms=query.isEmpty()?new String[0]:query.split("\\s+");
  for(Evidence value:evidence){List<Incident> linked=incidents.stream().filter(i->i.evidenceIds().contains(value.id())||i.events().stream().anyMatch(e->e.evidenceIds().contains(value.id()))).toList();if(!includeArchived&&!linked.isEmpty()&&linked.stream().allMatch(Incident::archived))continue;
   long at=value.metadata().captureAt()>0?value.metadata().captureAt():value.importedAt();if((from>0&&at<from)||(through>0&&at>through))continue;
   StringBuilder hay=new StringBuilder(value.name()).append('\n').append(value.description()).append('\n').append(value.notes()).append('\n').append(String.join(" ",value.tags())).append('\n').append(String.join(" ",value.participants())).append('\n').append(value.transcript());for(Ocr raw:value.ocr())hay.append('\n').append(raw.text());for(Incident incident:linked)if(includeArchived||!incident.archived())hay.append('\n').append(incident.title());String lower=hay.toString().toLowerCase(Locale.ROOT);boolean match=true;for(String term:terms)if(!lower.contains(term)){match=false;break;}if(match)matches.add(value);
  }return List.copyOf(matches);
 }
 public List<Suggestion> suggestions() {
  // A deterministic, bounded candidate set avoids all-pairs work on a 4096-record library.
  Map<String,LinkedHashSet<String>> signals=new TreeMap<>();Map<String,String> labels=new HashMap<>();
  for(Evidence value:evidence){
   signal(signals,labels,"hash:"+value.sha256(),"Matching original SHA-256",value.id());
   for(String participant:value.participants())signal(signals,labels,"participant:"+participant.toLowerCase(Locale.ROOT),"Shared explicit participant",value.id());
   long at=value.metadata().captureAt();if(at>0)signal(signals,labels,"time:"+(at/3600000),"Source capture times in the same hour",value.id());
   String text=value.transcript().strip().toLowerCase(Locale.ROOT).replaceAll("\\s+"," ");
   if(text.length()>=16)signal(signals,labels,"text:"+sha(text.getBytes(StandardCharsets.UTF_8)),"Matching extracted/corrected text",value.id());
   // Bounded transcript shingles suggest similarity; they never establish shared participants or facts.
   java.util.regex.Matcher tokens=java.util.regex.Pattern.compile("[\\p{L}\\p{N}]{3,}").matcher(text);List<String> fragment=new ArrayList<>();
   while(tokens.find()&&fragment.size()<64)fragment.add(tokens.group());
   for(int n=0;n+2<fragment.size()&&n<32;n++){
    List<String> phrase=fragment.subList(n,n+3);if(new HashSet<>(phrase).size()<3)continue;
    signal(signals,labels,"fragment:"+sha(String.join(" ",phrase).getBytes(StandardCharsets.UTF_8)),"Similar transcript fragments (unverified)",value.id());
   }
   if(!value.ocr().isEmpty())for(ExtractedTime time:value.ocr().get(value.ocr().size()-1).times().stream().limit(32).toList()){
    if(time.epochMillis()>0)signal(signals,labels,"ocr-time:"+(time.epochMillis()/3600000),"OCR visible timestamps in the same hour (unverified)",value.id());
    else {String raw=time.text().strip().toLowerCase(Locale.ROOT);if(raw.matches(".*[0-9]{4}[-/][0-9]{1,2}[-/][0-9]{1,2}.*")&&raw.length()<=128)signal(signals,labels,"ocr-date:"+raw,"Matching raw OCR date claim (unverified; timezone/precision unknown)",value.id());}
   }
  }
  Map<String,List<String>> candidates=new TreeMap<>();Map<String,List<String>> reasons=new HashMap<>();int scanned=0;
  for(var entry:signals.entrySet()){if(++scanned>8192)break;if(entry.getValue().size()<2)continue;List<String> ids=entry.getValue().stream().sorted().limit(128).toList();String key=String.join(",",ids);candidates.putIfAbsent(key,ids);List<String> why=reasons.computeIfAbsent(key,k->new ArrayList<>());String label=labels.get(entry.getKey());if(!why.contains(label))why.add(label);if(candidates.size()>=256)break;}
  Map<String,LinkedHashSet<Incident>> associations=new HashMap<>();
  for(Incident incident:incidents)if(!incident.archived())for(String evidenceId:incident.evidenceIds())associations.computeIfAbsent(evidenceId,k->new LinkedHashSet<>()).add(incident);
  List<Suggestion> values=new ArrayList<>();
  for(var entry:candidates.entrySet()){
   if(values.size()>=256)break;List<String> ids=entry.getValue();LinkedHashSet<Incident> related=new LinkedHashSet<>();for(String id:ids)related.addAll(associations.getOrDefault(id,new LinkedHashSet<>()));
   if(related.stream().anyMatch(i->i.evidenceIds().containsAll(ids)))continue;
   String id=sha(("evidence179:GROUP:"+entry.getKey()).getBytes(StandardCharsets.UTF_8));
   if(!rejectedSuggestions.contains(id))values.add(new Suggestion(id,"GROUP",ids,"","Possible related evidence",reasons.get(entry.getKey())));
   for(Incident target:related){
    if(values.size()>=256)break;List<String> unlinked=ids.stream().filter(e->!target.evidenceIds().contains(e)).toList();if(unlinked.isEmpty())continue;
    String suggestionId=sha(("evidence179:ASSIGN:"+target.id()+":"+String.join(",",unlinked)).getBytes(StandardCharsets.UTF_8));
    if(rejectedSuggestions.contains(suggestionId)||values.stream().anyMatch(v->v.id().equals(suggestionId)))continue;
    List<String> why=new ArrayList<>(reasons.get(entry.getKey()));if(why.size()<16)why.add("Related evidence already linked to this incident; owner approval required");
    values.add(new Suggestion(suggestionId,"ASSIGN",unlinked,target.id(),"Add related evidence to incident",why));
   }
  }
  return List.copyOf(values);
 }
 private static void signal(Map<String,LinkedHashSet<String>> values,Map<String,String> labels,String key,String label,String id){if(values.size()>=8192&&!values.containsKey(key))return;LinkedHashSet<String> set=values.computeIfAbsent(key,k->new LinkedHashSet<>());if(set.size()<128)set.add(id);labels.put(key,label);}
 /** A callback may compose domain mutations, but cannot use decoded objects to rewrite history. */
 void validateSuccessor(EvidenceCatalog before) {
  if(owner!=before.owner||revision<=before.revision||audit.size()<=before.audit.size()||revision-before.revision!=(long)audit.size()-before.audit.size()||!audit.subList(0,before.audit.size()).equals(before.audit))throw invalid("Evidence update must preserve audit and advance one revision per audit");
  // Historical sources and newly registered live originals form the sole identity authority.
  // A new derived-object ID must not reset the identity/policy of a historical source UUID.
  Map<String,SourceFingerprint> canonicalSources=new HashMap<>();
  for(Evidence old:before.evidence)admitCanonicalSource(canonicalSources,fingerprint(old));
  for(Incident old:before.incidents)for(SourceFingerprint source:old.lineage())admitCanonicalSource(canonicalSources,source);
  for(Analysis old:before.analyses)for(SourceFingerprint source:old.lineage())admitCanonicalSource(canonicalSources,source);
  for(Evidence value:evidence){admitCanonicalSource(canonicalSources,fingerprint(value));int at=index(before.evidence,value.id(),Evidence::id);if(at>=0){Evidence old=before.evidence.get(at);if(!old.sourceId().equals(value.sourceId())||!old.metadata().equals(value.metadata())||old.importedAt()!=value.importedAt()||!prefix(value.ocr(),old.ocr())||!prefix(value.corrections(),old.corrections()))throw invalid("Original metadata and extraction history are immutable");}}
  for(Incident value:incidents){for(SourceFingerprint source:value.lineage())requireCanonicalSource(canonicalSources,source);int at=index(before.incidents,value.id(),Incident::id);if(at>=0&&(!value.lineage().containsAll(before.incidents.get(at).lineage())||!value.analysisIds().containsAll(before.incidents.get(at).analysisIds())))throw invalid("Incident source lineage cannot shrink");}
  for(Analysis value:analyses){for(SourceFingerprint source:value.lineage())requireCanonicalSource(canonicalSources,source);Set<SourceFingerprint> expected=new HashSet<>();int at=index(before.analyses,value.id(),Analysis::id);if(at>=0)expected.addAll(before.analyses.get(at).lineage());for(String id:value.evidenceIds())expected.add(fingerprint(requireEvidence(id)));List<SourceReference> expectedRefs=at>=0?before.analyses.get(at).sourceReferences():value.evidenceIds().stream().map(id->new SourceReference(id,fingerprint(requireEvidence(id)))).toList();if(!value.sourceReferences().equals(expectedRefs))throw invalid("Analysis historical references are immutable");if(!expected.equals(Set.copyOf(value.lineage())))throw invalid("Analysis lineage must derive from selected evidence and retained analysis sources");}
  for(Analysis old:before.analyses){int at=index(analyses,old.id(),Analysis::id);if(at<0)throw invalid("Analysis history cannot be removed");Analysis value=analyses.get(at);if(old.at()!=value.at()||!old.model().equals(value.model())||!old.task().equals(value.task())||!old.inputDigest().equals(value.inputDigest())||!old.text().equals(value.text())||old.inputTokens()!=value.inputTokens()||old.outputTokens()!=value.outputTokens()||old.applied()&&!value.applied()||!value.lineage().containsAll(old.lineage()))throw invalid("Analysis history and lineage are immutable");if(!old.evidenceIds().stream().filter(id->index(evidence,id,Evidence::id)>=0).toList().equals(value.evidenceIds()))throw invalid("Analysis selection is immutable except removed record references");}
 }
 private static void admitCanonicalSource(Map<String,SourceFingerprint> sources,SourceFingerprint value){SourceFingerprint previous=sources.putIfAbsent(value.sourceId(),value);if(previous!=null&&!previous.equals(value))throw invalid("Original source identity is immutable");}
 private static void requireCanonicalSource(Map<String,SourceFingerprint> sources,SourceFingerprint value){if(!value.equals(sources.get(value.sourceId())))throw invalid("Unverified or changed historical source lineage");}
 private EvidenceCatalog changed(List<Evidence> values,List<Incident> groups,List<Analysis> results,List<String> rejected,long now,String action,List<String> targets,String detail) {nonnegative(now);if(revision==Long.MAX_VALUE)throw invalid("Revision exhausted");List<Audit> log=new ArrayList<>(audit);log.add(new Audit(newId(),now,action,targets,detail));return new EvidenceCatalog(owner,revision+1,values,groups,log,results,rejected);}
 private Evidence requireEvidence(String id){uuid(id);return evidence.stream().filter(e->e.id().equals(id)).findFirst().orElseThrow(()->invalid("Evidence unavailable"));}
 private Analysis requireAnalysis(String id){uuid(id);return analyses.stream().filter(a->a.id().equals(id)).findFirst().orElseThrow(()->invalid("Analysis unavailable"));}
 private Incident requireIncident(String id){uuid(id);return incidents.stream().filter(i->i.id().equals(id)).findFirst().orElseThrow(()->invalid("Incident unavailable"));}
 private void validateReferences(List<String> ids){for(String id:ids)requireEvidence(id);}
 private List<SourceFingerprint> contributions(Incident value){List<SourceFingerprint> sources=new ArrayList<>();for(String id:value.evidenceIds())sources.add(fingerprint(requireEvidence(id)));for(Event event:value.events())for(String id:event.evidenceIds())sources.add(fingerprint(requireEvidence(id)));return unionSources(sources);}
 private static Event withEventSources(Event event,List<String> ids){return new Event(event.id(),ids,event.timestamp(),event.provenance(),event.title(),event.notes());}
 private static List<Event> selectedEvents(List<Event> events,List<String> ids){Set<String> selected=new HashSet<>(ids);List<Event> result=new ArrayList<>();for(Event event:events){List<String> links=event.evidenceIds().stream().filter(selected::contains).toList();if(!links.isEmpty())result.add(withEventSources(event,links));}return List.copyOf(result);}
 private static List<Event> detachEvents(List<Event> events,List<String> ids,boolean moving){List<Event> result=new ArrayList<>();for(Event event:events){List<String> remaining=without(event.evidenceIds(),ids);if(remaining.equals(event.evidenceIds()))result.add(event);else if(!moving||!remaining.isEmpty())result.add(withEventSources(event,remaining));}return List.copyOf(result);}
 private static List<Event> combineEvents(List<Event> original,List<Event> incoming){List<Event> result=new ArrayList<>(original);for(Event event:incoming){int at=index(result,event.id(),Event::id);if(at<0)result.add(event);else {Event old=result.get(at);if(old.timestamp()!=event.timestamp()||!old.provenance().equals(event.provenance())||!old.title().equals(event.title())||!old.notes().equals(event.notes()))throw invalid("Conflicting merged event content");result.set(at,withEventSources(old,union(old.evidenceIds(),event.evidenceIds())));}}return result;}
 private static Evidence withText(Evidence e,List<Ocr> ocr,List<Correction> corrections){return new Evidence(e.id(),e.sourceId(),e.name(),e.mime(),e.sha256(),e.size(),e.importedAt(),e.metadata(),e.description(),e.notes(),e.tags(),e.participants(),ocr,corrections);}
 private static Incident withIncident(Incident i,List<String> ids,List<Event> events,boolean manual,boolean archived,List<SourceFingerprint> lineage){return new Incident(i.id(),i.title(),i.startAt(),i.endAt(),i.description(),i.notes(),i.tags(),ids,events,manual,archived,lineage,i.analysisIds());}
 private static Analysis withAnalysis(Analysis a,List<String> ids,boolean applied,List<SourceFingerprint> lineage,List<SourceReference> refs){return new Analysis(a.id(),a.at(),a.model(),a.task(),a.inputDigest(),ids,a.text(),a.inputTokens(),a.outputTokens(),applied,lineage,refs);}
 private static SourceFingerprint fingerprint(Evidence e){return new SourceFingerprint(e.sourceId(),e.mime(),e.sha256(),e.size(),e.metadata().vaultOnly());}
 private static List<SourceFingerprint> unionSources(List<SourceFingerprint> values){LinkedHashMap<String,SourceFingerprint> seen=new LinkedHashMap<>();for(SourceFingerprint value:values){SourceFingerprint old=seen.putIfAbsent(value.sourceId(),value);if(old!=null&&!old.equals(value))throw invalid("Conflicting contributing source identity");}return List.copyOf(seen.values());}
 private static List<SourceFingerprint> fingerprints(List<SourceFingerprint> values){values=copy(values,MAX_EVIDENCE);if(unionSources(values).size()!=values.size())throw invalid("Duplicate source fingerprint");return values;}
 private static <T> boolean prefix(List<T> full,List<T> prefix){return full.size()>=prefix.size()&&full.subList(0,prefix.size()).equals(prefix);}
 private static <T> List<T> copy(List<T> values,int max){Objects.requireNonNull(values);if(values.size()>max)throw invalid("Collection quota exceeded");return List.copyOf(values);}
 private static List<String> strings(List<String> values,int max,int chars){values=copy(values,max);Set<String> seen=new HashSet<>();for(String value:values){text(value,chars);if(!seen.add(value))throw invalid("Duplicate list value");}return values;}
 private static List<String> ids(List<String> values,int max){values=strings(values,max,36);for(String id:values)uuid(id);return values;}
 private static <T> void unique(List<T> values,Function<T,String> id){Set<String> seen=new HashSet<>();for(T value:values)if(!seen.add(id.apply(value)))throw invalid("Duplicate id");}
 private static <T> int index(List<T> values,String id,Function<T,String> getter){for(int i=0;i<values.size();i++)if(getter.apply(values.get(i)).equals(id))return i;return -1;}
 private static <T> List<T> union(List<T> a,List<T> b){LinkedHashSet<T> values=new LinkedHashSet<>(a);values.addAll(b);return List.copyOf(values);}
 private static <T> List<T> without(List<T> a,List<T> b){Set<T> remove=new HashSet<>(b);return a.stream().filter(v->!remove.contains(v)).toList();}
 private static String join(String a,String b){return a.isEmpty()?b:b.isEmpty()?a:a+"\n\n"+b;}
 static IllegalArgumentException invalid(String message){return new IllegalArgumentException(message);}
 static String text(String value,int max){Objects.requireNonNull(value);if(value.length()>max)throw invalid("Text quota exceeded");for(int i=0;i<value.length();i++){char c=value.charAt(i);if(Character.isHighSurrogate(c)){if(i+1>=value.length()||!Character.isLowSurrogate(value.charAt(++i)))throw invalid("Invalid UTF-16");}else if(Character.isLowSurrogate(c))throw invalid("Invalid UTF-16");}return value;}
 private static String mime(String value){text(value,256);if(!value.matches("[A-Za-z0-9!#$&^_.+\\-]+/[A-Za-z0-9!#$&^_.+\\-]+"))throw invalid("Invalid MIME");return value;}
 private static void uuid(String value){if(value==null||!value.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))throw invalid("Invalid UUID");}
 private static void digest(String value){if(value==null||!value.matches("[0-9a-f]{64}"))throw invalid("Invalid SHA-256");}
 private static void nonnegative(long... values){for(long value:values)if(value<0)throw invalid("Negative source field");}
 private static void confidence(float value){if(!Float.isFinite(value)||value!=-1&&(value<0||value>100))throw invalid("Invalid confidence");}
 private static String newId(){return UUID.randomUUID().toString();}
 private static String sha(byte[] bytes){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}finally{Arrays.fill(bytes,(byte)0);}}
}
