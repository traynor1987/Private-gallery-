package uk.co.traynor.privategallery.core.camera;
import java.io.*;
import java.nio.file.*;
import java.util.*;
public final class CaptureJournalChecks {
 private static int count;
 private static void check(boolean b){count++;if(!b)throw new AssertionError("journal "+count);}
 public static void main(String[] args)throws Exception {
  File root=Files.createTempDirectory("capture-check").toFile(); byte[] vault=new byte[32];Arrays.fill(vault,(byte)8);
  CaptureJournal journal=new CaptureJournal(root,"hidden");
  CaptureJournal.Ticket ticket=journal.create(vault,"video/mp4",1234,true);
  byte[] source=new byte[150000];new Random(7).nextBytes(source);Files.write(ticket.plain.toPath(),source);
  check(!journal.entries().isEmpty());
  try{new CaptureJournal(root,"hidden").openForRecovery(ticket.id,vault);throw new AssertionError("live capture recovered");}catch(IOException expected){count++;}
  journal.seal(ticket,true,4500);
  check(!ticket.plain.exists());
  CaptureJournal.Ticket opened=journal.open(ticket.id,vault);
  check(opened.complete);check(opened.duration==4500);check(opened.timestamp==1234);check(opened.audio);
  try(var reader=journal.reader(opened)){check(Arrays.equals(source,reader.stream().readAllBytes()));}
  try{journal.open(ticket.id,new byte[32]);throw new AssertionError("wrong key");}catch(java.security.GeneralSecurityException expected){count++;}
  try{new CaptureJournal(root,"primary").open(ticket.id,vault);throw new AssertionError("wrong vault");}catch(java.security.GeneralSecurityException expected){count++;}
  CaptureJournal.Ticket crash=journal.create(vault,"video/mp4",2222,false);Files.write(crash.plain.toPath(),source);crash.close();
  CaptureJournal.Ticket recovered=journal.openForRecovery(crash.id,vault);journal.seal(recovered,false,0);check(!recovered.complete);check(!recovered.plain.exists());
  CaptureJournal.Ticket empty=journal.create(vault,"image/jpeg",3333,false);
  try{journal.seal(empty,true,0);throw new AssertionError("empty accepted");}catch(IOException expected){count++;}
  check(journal.entries().contains(empty.id));
  // A damaged authenticated pending record is retained, never silently replaced/deleted.
  byte[] data=Files.readAllBytes(new File(root,ticket.id+"/media.enc").toPath());data[data.length-1]^=1;Files.write(new File(root,ticket.id+"/media.enc").toPath(),data);
  try(var reader=journal.reader(opened)){reader.verify();throw new AssertionError("tamper");}catch(Exception expected){count++;}
  check(journal.entries().contains(ticket.id));
  opened.close();ticket.close();recovered.close();empty.close();
  System.out.println(count+" capture journal checks passed");
 }
}
