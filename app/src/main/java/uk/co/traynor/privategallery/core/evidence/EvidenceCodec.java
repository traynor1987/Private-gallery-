package uk.co.traynor.privategallery.core.evidence;

import uk.co.traynor.privategallery.core.evidence.EvidenceCatalog.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.*;

/** Explicit, versioned binary schema; no Java serialization or reflective object construction. */
final class EvidenceCodec {
 private EvidenceCodec(){}
 private static final int MAGIC=0x50474543, VERSION=2;
 // Decoder-only construction capability: callers cannot create a legacy mutation snapshot.
 static final class LegacyRead {private LegacyRead(){} int version(){return 1;}}
 private static final LegacyRead LEGACY_READ=new LegacyRead();
 private static final class Buffer extends ByteArrayOutputStream {
  private boolean closed;
  private void capacity(int length) {
   if(closed)throw new IllegalStateException("Evidence encoding buffer closed");
   if(length<0||count>EvidenceCatalog.MAX_BYTES-length)throw EvidenceCatalog.invalid("Encoded evidence quota exceeded");
   int required=count+length;if(required<=buf.length)return;
   int size=Math.max(required,Math.min(EvidenceCatalog.MAX_BYTES,buf.length*2));
   byte[] previous=buf;
   // If allocation fails, the original array remains owned and close() wipes it.
   byte[] grown=new byte[size];
   try {System.arraycopy(previous,0,grown,0,count);}
   catch(RuntimeException|Error failure){Arrays.fill(grown,(byte)0);throw failure;}
   finally {Arrays.fill(previous,(byte)0);}
   buf=grown;
  }
  // Never call a superclass write: its automatic growth would abandon plaintext arrays.
  @Override public synchronized void write(int value){capacity(1);buf[count++]=(byte)value;}
  @Override public synchronized void write(byte[] bytes,int offset,int length){Objects.checkFromIndexSize(offset,length,bytes.length);capacity(length);System.arraycopy(bytes,offset,buf,count,length);count+=length;}
  @Override public synchronized void close(){Arrays.fill(buf,(byte)0);count=0;closed=true;}
 }
 @FunctionalInterface private interface Write<T>{void put(DataOutputStream out,T value)throws IOException;}
 @FunctionalInterface private interface Read<T>{T get(DataInputStream in)throws IOException;}
 static byte[] encode(EvidenceCatalog catalog){try(Buffer bytes=new Buffer();DataOutputStream out=new DataOutputStream(bytes)){
  int version=catalog.encodingVersion();out.writeInt(MAGIC);out.writeInt(version);string(out,catalog.owner().name());out.writeLong(catalog.revision());
  list(out,catalog.evidence(),EvidenceCodec::evidence);list(out,catalog.incidents(),(o,i)->incident(o,i,version));list(out,catalog.audit(),EvidenceCodec::audit);list(out,catalog.analyses(),(o,a)->analysis(o,a,version));strings(out,catalog.rejectedSuggestions());out.flush();return bytes.toByteArray();
 }catch(IOException e){throw new IllegalArgumentException("Invalid evidence catalog",e);}}
 static EvidenceCatalog decode(byte[] bytes,Owner owner){Objects.requireNonNull(owner);if(bytes==null||bytes.length>EvidenceCatalog.MAX_BYTES)throw EvidenceCatalog.invalid("Encoded evidence quota exceeded");try(DataInputStream in=new DataInputStream(new ByteArrayInputStream(bytes))){
  if(in.readInt()!=MAGIC)throw new IOException("Unsupported evidence catalog");int version=in.readInt();if((version!=1&&version!=VERSION)||!string(in).equals(owner.name()))throw new IOException("Unsupported or foreign-owner evidence catalog");long revision=in.readLong();
  List<Evidence> evidence=list(in,4096,EvidenceCodec::evidence);List<Incident> incidents=list(in,1024,input->incident(input,version));List<Audit> audit=list(in,20000,EvidenceCodec::audit);List<Analysis> analyses=list(in,4096,input->analysis(input,version));List<String> rejected=strings(in,20000);if(in.read()!=-1)throw new IOException("Trailing evidence data");return version==1?new EvidenceCatalog(owner,revision,evidence,incidents,audit,analyses,rejected,LEGACY_READ):new EvidenceCatalog(owner,revision,evidence,incidents,audit,analyses,rejected);
 }catch(IOException|IllegalArgumentException|NullPointerException e){throw new IllegalArgumentException("Invalid evidence catalog",e);}}
 private static void string(DataOutputStream out,String value)throws IOException{byte[] utf8=value.getBytes(StandardCharsets.UTF_8);try{out.writeInt(utf8.length);out.write(utf8);}finally{Arrays.fill(utf8,(byte)0);}}
 private static String string(DataInputStream in)throws IOException{int n=in.readInt();if(n<0||n>400000||n>in.available())throw new IOException("Invalid text length");byte[] bytes=new byte[n];try{in.readFully(bytes);return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();}finally{Arrays.fill(bytes,(byte)0);}}
 private static boolean bool(DataInputStream in)throws IOException{int b=in.readUnsignedByte();if(b>1)throw new IOException("Noncanonical boolean");return b==1;}
 private static <T> void list(DataOutputStream out,List<T> values,Write<T> writer)throws IOException{out.writeInt(values.size());for(T value:values)writer.put(out,value);}
 private static <T> List<T> list(DataInputStream in,int max,Read<T> reader)throws IOException{int n=in.readInt();if(n<0||n>max||n>in.available())throw new IOException("Invalid collection length");List<T> values=new ArrayList<>(n);for(int i=0;i<n;i++)values.add(reader.get(in));return values;}
 private static void strings(DataOutputStream out,List<String> values)throws IOException{list(out,values,EvidenceCodec::string);}
 private static List<String> strings(DataInputStream in,int max)throws IOException{return list(in,max,EvidenceCodec::string);}
 private static void metadata(DataOutputStream out,Metadata m)throws IOException{string(out,m.sourceName());out.writeLong(m.fileCreatedAt());out.writeLong(m.fileModifiedAt());out.writeLong(m.dateAddedAt());out.writeLong(m.captureAt());out.writeInt(m.width());out.writeInt(m.height());out.writeInt(m.embedded().size());for(var e:new TreeMap<>(m.embedded()).entrySet()){string(out,e.getKey());string(out,e.getValue());}out.writeBoolean(m.vaultOnly());}
 private static Metadata metadata(DataInputStream in)throws IOException{String name=string(in);long created=in.readLong(),modified=in.readLong(),added=in.readLong(),capture=in.readLong();int width=in.readInt(),height=in.readInt(),n=in.readInt();if(n<0||n>256)throw new IOException("Metadata quota");Map<String,String> map=new LinkedHashMap<>();for(int i=0;i<n;i++){String key=string(in),value=string(in);if(map.putIfAbsent(key,value)!=null)throw new IOException("Duplicate metadata key");}return new Metadata(name,created,modified,added,capture,width,height,map,bool(in));}
 private static void word(DataOutputStream out,Word w)throws IOException{out.writeInt(w.pageIndex());string(out,w.text());out.writeFloat(w.confidence());out.writeInt(w.left());out.writeInt(w.top());out.writeInt(w.right());out.writeInt(w.bottom());}
 private static Word word(DataInputStream in)throws IOException{return new Word(in.readInt(),string(in),in.readFloat(),in.readInt(),in.readInt(),in.readInt(),in.readInt());}
 private static void time(DataOutputStream out,ExtractedTime t)throws IOException{out.writeInt(t.pageIndex());string(out,t.text());out.writeLong(t.epochMillis());string(out,t.provenance());out.writeFloat(t.confidence());}
 private static ExtractedTime time(DataInputStream in)throws IOException{return new ExtractedTime(in.readInt(),string(in),in.readLong(),string(in),in.readFloat());}
 private static void ocr(DataOutputStream out,Ocr o)throws IOException{string(out,o.id());out.writeLong(o.createdAt());string(out,o.engine());out.writeInt(o.rotation());out.writeInt(o.width());out.writeInt(o.height());string(out,o.text());out.writeFloat(o.confidence());list(out,o.words(),EvidenceCodec::word);list(out,o.times(),EvidenceCodec::time);out.writeInt(o.pageCount());out.writeBoolean(o.partial());}
 private static Ocr ocr(DataInputStream in)throws IOException{return new Ocr(string(in),in.readLong(),string(in),in.readInt(),in.readInt(),in.readInt(),string(in),in.readFloat(),list(in,100000,EvidenceCodec::word),list(in,4096,EvidenceCodec::time),in.readInt(),bool(in));}
 private static void correction(DataOutputStream out,Correction c)throws IOException{string(out,c.id());string(out,c.ocrId());out.writeLong(c.createdAt());string(out,c.text());}
 private static Correction correction(DataInputStream in)throws IOException{return new Correction(string(in),string(in),in.readLong(),string(in));}
 private static void evidence(DataOutputStream out,Evidence e)throws IOException{string(out,e.id());string(out,e.sourceId());string(out,e.name());string(out,e.mime());string(out,e.sha256());out.writeLong(e.size());out.writeLong(e.importedAt());metadata(out,e.metadata());string(out,e.description());string(out,e.notes());strings(out,e.tags());strings(out,e.participants());list(out,e.ocr(),EvidenceCodec::ocr);list(out,e.corrections(),EvidenceCodec::correction);}
 private static Evidence evidence(DataInputStream in)throws IOException{return new Evidence(string(in),string(in),string(in),string(in),string(in),in.readLong(),in.readLong(),metadata(in),string(in),string(in),strings(in,256),strings(in,256),list(in,8,EvidenceCodec::ocr),list(in,20000,EvidenceCodec::correction));}
 private static void event(DataOutputStream out,Event e)throws IOException{string(out,e.id());strings(out,e.evidenceIds());out.writeLong(e.timestamp());string(out,e.provenance());string(out,e.title());string(out,e.notes());}
 private static Event event(DataInputStream in)throws IOException{return new Event(string(in),strings(in,4096),in.readLong(),string(in),string(in),string(in));}
 private static void fingerprint(DataOutputStream out,SourceFingerprint f)throws IOException{string(out,f.sourceId());string(out,f.mime());string(out,f.sha256());out.writeLong(f.size());out.writeBoolean(f.vaultOnly());}
 private static SourceFingerprint fingerprint(DataInputStream in)throws IOException{return new SourceFingerprint(string(in),string(in),string(in),in.readLong(),bool(in));}
 private static void incident(DataOutputStream out,Incident i,int version)throws IOException{string(out,i.id());string(out,i.title());out.writeLong(i.startAt());out.writeLong(i.endAt());string(out,i.description());string(out,i.notes());strings(out,i.tags());strings(out,i.evidenceIds());list(out,i.events(),EvidenceCodec::event);out.writeBoolean(i.manualOrder());out.writeBoolean(i.archived());list(out,i.lineage(),EvidenceCodec::fingerprint);if(version>=2)strings(out,i.analysisIds());}
 private static Incident incident(DataInputStream in,int version)throws IOException{return new Incident(string(in),string(in),in.readLong(),in.readLong(),string(in),string(in),strings(in,256),strings(in,4096),list(in,20000,EvidenceCodec::event),bool(in),bool(in),list(in,4096,EvidenceCodec::fingerprint),version>=2?strings(in,4096):List.of());}
 private static void audit(DataOutputStream out,Audit a)throws IOException{string(out,a.id());out.writeLong(a.at());string(out,a.action());strings(out,a.targetIds());string(out,a.detail());}
 private static Audit audit(DataInputStream in)throws IOException{return new Audit(string(in),in.readLong(),string(in),strings(in,5120),string(in));}
 private static void analysis(DataOutputStream out,Analysis a,int version)throws IOException{string(out,a.id());out.writeLong(a.at());string(out,a.model());string(out,a.task());string(out,a.inputDigest());strings(out,a.evidenceIds());string(out,a.text());out.writeLong(a.inputTokens());out.writeLong(a.outputTokens());out.writeBoolean(a.applied());list(out,a.lineage(),EvidenceCodec::fingerprint);if(version>=2)list(out,a.sourceReferences(),(o,r)->{string(o,r.evidenceId());fingerprint(o,r.fingerprint());});}
 private static Analysis analysis(DataInputStream in,int version)throws IOException{return new Analysis(string(in),in.readLong(),string(in),string(in),string(in),strings(in,4096),string(in),in.readLong(),in.readLong(),bool(in),list(in,4096,EvidenceCodec::fingerprint),version>=2?list(in,4096,input->new SourceReference(string(input),fingerprint(input))):List.of());}
}
