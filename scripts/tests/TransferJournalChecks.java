import java.io.*;
import java.nio.file.*;
import java.util.*;
import uk.co.traynor.privategallery.core.hidden.TransferJournal;
public class TransferJournalChecks {
 static int checks; static void check(boolean b){checks++;if(!b)throw new AssertionError("check "+checks);}
 public static void main(String[]args)throws Exception {
  File root=Files.createTempDirectory("transfer176").toFile(); byte[]key=new byte[32];Arrays.fill(key,(byte)7);
  try {
   TransferJournal journal=new TransferJournal(root,key,Runnable::run);
   var op=journal.create("PRIMARY_TO_HIDDEN",true,Arrays.asList("a","b","a"));
   check(op.items.size()==2);check(journal.list().size()==1);
   var a=op.items.get(0);a.destination="destination-a";a.size=100;a.phase="VERIFIED";journal.save(op);
   var recovered=new TransferJournal(root,key,Runnable::run).load(op.id);check(recovered.items.get(0).phase.equals("VERIFIED"));check(recovered.items.get(1).phase.equals("PENDING"));
   byte[]disk=Files.readAllBytes(journal.file(op.id).toPath());check(!new String(disk,java.nio.charset.StandardCharsets.ISO_8859_1).contains("destination-a"));
   byte[]wrong=key.clone();wrong[0]++;boolean denied=false;try{new TransferJournal(root,wrong,Runnable::run).load(op.id);}catch(Exception e){denied=true;}check(denied);
   var cancelled=journal.create("HIDDEN_TO_PRIMARY",false,Arrays.asList("x"));cancelled.status="CANCELLED";journal.save(cancelled);journal.clearFinished();check(journal.list().size()==1);
   // Atomic encryption staging never replaces a committed record on authority rejection.
   TransferJournal deniedWriter=new TransferJournal(root,key,commit->{throw new IllegalStateException("locked");});
   recovered.status="COMPLETE";denied=false;try{deniedWriter.save(recovered);}catch(Exception e){denied=true;}check(denied);check(journal.load(op.id).status.equals("PENDING"));
   // Persisted states are independent of the old Java object/process.
   for(int i=0;i<500;i++){if(i==0)continue;}
   var ids=new ArrayList<String>();for(int i=0;i<500;i++)ids.add("synthetic-"+i);
   var large=journal.create("PRIMARY_TO_HIDDEN",false,ids);check(journal.load(large.id).items.size()==500);
   disk[disk.length-1]^=1;Files.write(journal.file(op.id).toPath(),disk);denied=false;try{journal.load(op.id);}catch(Exception e){denied=true;}check(denied);check(journal.file(op.id).exists());
   System.out.println("Transfer journal: "+checks+" checks PASS");
  } finally {try(var files=Files.walk(root.toPath())){files.sorted(Comparator.reverseOrder()).forEach(p->{try{Files.deleteIfExists(p);}catch(Exception e){throw new RuntimeException(e);}});}Arrays.fill(key,(byte)0);}
 }
}
