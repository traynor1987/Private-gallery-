package uk.co.traynor.privategallery.core.hidden;

import java.io.*;
import java.util.*;
import java.util.function.Consumer;

/** Authenticated Hidden-only transfer ledger. Contains identifiers, never names or thumbnails.
 * The key is borrowed from a revocable Hidden operation. Call only from a serialized owner.
 * Unknown/corrupt records throw and remain on disk; clearing never touches media files.
 */
public final class TransferJournal {
 private static final int VERSION=1, MAX_ITEMS=10_000, MAX_OPERATIONS=1000;
 private final File root; private final byte[] key; private final Consumer<Runnable> publish;
 private final HiddenRecordStore records;
 public TransferJournal(File root,byte[] key,Consumer<Runnable> publish){
  this.root=root;this.key=key;this.publish=publish;this.records=new HiddenRecordStore(root);
 }
 public static final class Item {
  public final String source; public String destination="",phase="PENDING"; public long size=0;
  public Item(String source){this.source=source;}
 }
 public static final class Operation {
  public final String id,direction;public final boolean move;public final long started;
  public long ended;public String status="PENDING";public final List<Item> items;
  private Operation(String id,String direction,boolean move,long started,List<Item> items){this.id=id;this.direction=direction;this.move=move;this.started=started;this.items=items;}
  public int verified(){return (int)items.stream().filter(i->i.phase.equals("VERIFIED")||i.phase.equals("MOVED")||i.phase.equals("SKIPPED")).count();}
  public int failed(){return (int)items.stream().filter(i->i.phase.equals("FAILED")).count();}
 }
 public File file(String id){return records.file("metadata",id);}
 public Operation create(String direction,boolean move,List<String> ids)throws Exception {
  if(!direction.equals("PRIMARY_TO_HIDDEN")&&!direction.equals("HIDDEN_TO_PRIMARY"))throw new IllegalArgumentException("Invalid direction");
  LinkedHashSet<String> unique=new LinkedHashSet<>(ids);
  if(unique.isEmpty()||unique.size()>MAX_ITEMS)throw new IllegalArgumentException("Invalid batch size");
  if(list().size()>=MAX_OPERATIONS)throw new IOException("Clear completed transfer history before starting another batch");
  List<Item> items=new ArrayList<>();for(String id:unique){if(id==null||id.isBlank()||id.length()>200)throw new IllegalArgumentException("Invalid source");items.add(new Item(id));}
  Operation op=new Operation(UUID.randomUUID().toString(),direction,move,System.currentTimeMillis(),items);save(op);return op;
 }
 public void save(Operation op)throws Exception {
  ByteArrayOutputStream buffer=new ByteArrayOutputStream();
  try(DataOutputStream out=new DataOutputStream(buffer)) {
   out.writeInt(VERSION);out.writeUTF(op.id);out.writeUTF(op.direction);out.writeBoolean(op.move);out.writeLong(op.started);out.writeLong(op.ended);out.writeUTF(op.status);out.writeInt(op.items.size());
   for(Item item:op.items){out.writeUTF(item.source);out.writeUTF(item.destination);out.writeUTF(item.phase);out.writeLong(item.size);}
  }
  byte[] plain=buffer.toByteArray();File stage=null;
  try {
   // Decode before publishing so invalid internal mutations cannot replace a valid journal.
   decode(plain,op.id);
   stage=records.stage("metadata",op.id,plain,key);final File prepared=stage;
   publish.accept(()->{try{records.promote(prepared,"metadata",op.id);}catch(IOException e){throw new UncheckedIOException(e);}});
   records.syncPromotions();
  } finally {Arrays.fill(plain,(byte)0);if(stage!=null)stage.delete();buffer.reset();}
 }
 public Operation load(String id)throws Exception {byte[] plain=records.read("metadata",id,key);try{return decode(plain,id);}finally{Arrays.fill(plain,(byte)0);}}
 private Operation decode(byte[] plain,String expected)throws IOException {
  try(DataInputStream in=new DataInputStream(new ByteArrayInputStream(plain))){
   if(in.readInt()!=VERSION)throw new IOException("Unsupported transfer journal");String id=in.readUTF(),direction=in.readUTF();
   if(!id.equals(expected)||(!direction.equals("PRIMARY_TO_HIDDEN")&&!direction.equals("HIDDEN_TO_PRIMARY")))throw new IOException("Invalid transfer identity");
   boolean move=in.readBoolean();long start=in.readLong(),end=in.readLong();String status=in.readUTF();int count=in.readInt();
   if(count<1||count>MAX_ITEMS||!Set.of("PENDING","RUNNING","INTERRUPTED","COMPLETE","PARTIAL","CANCELLED").contains(status))throw new IOException("Invalid transfer state");
   List<Item> items=new ArrayList<>();Set<String> unique=new HashSet<>();
   for(int n=0;n<count;n++){String source=in.readUTF();if(source.isBlank()||source.length()>200||!unique.add(source))throw new IOException("Invalid transfer source");Item item=new Item(source);item.destination=in.readUTF();item.phase=in.readUTF();item.size=in.readLong();
    if(item.destination.length()>200||item.size<0||!Set.of("PENDING","VERIFIED","MOVED","FAILED","SKIPPED").contains(item.phase))throw new IOException("Invalid transfer item");items.add(item);}
   if(in.read()!=-1)throw new IOException("Trailing transfer data");Operation op=new Operation(id,direction,move,start,items);op.ended=end;op.status=status;return op;
  }
 }
 public List<Operation> list()throws Exception {
  if(!root.exists())return new ArrayList<>();File[] files=root.listFiles();if(files==null)throw new IOException("Transfer history unavailable");List<Operation> result=new ArrayList<>();
  for(File file:files){if(file.getName().matches("metadata-[0-9a-f-]{36}\\.enc")){if(result.size()>=MAX_OPERATIONS)throw new IOException("Transfer history capacity exceeded");result.add(load(file.getName().substring(9,45)));}}
  result.sort(Comparator.comparingLong((Operation o)->o.started).reversed());return result;
 }
 public void clearFinished()throws Exception {
  for(Operation op:list()){if(op.status.equals("COMPLETE")||op.status.equals("CANCELLED")){publish.accept(()->{if(!file(op.id).delete())throw new UncheckedIOException(new IOException("History cleanup incomplete"));});}}
  if(root.exists())records.syncPromotions();
 }
}
