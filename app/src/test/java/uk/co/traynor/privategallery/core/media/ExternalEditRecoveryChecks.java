package uk.co.traynor.privategallery.core.media;
import java.io.*;
import java.nio.file.*;
import java.util.*;
public final class ExternalEditRecoveryChecks {
 public static void main(String[] args)throws Exception {
  File root=Files.createTempDirectory("editor-recovery").toFile();byte[] key=new byte[32];Arrays.fill(key,(byte)7);
  ExternalEditRecoveryStore a=new ExternalEditRecoveryStore(root,"primary");
  String id=UUID.randomUUID().toString();a.write(id,"original","content://media/external/images/media/17","",false,key);
  ExternalEditRecoveryStore b=new ExternalEditRecoveryStore(root,"primary");
  if(!b.read(id,key).exportUri.equals("content://media/external/images/media/17"))throw new AssertionError("Process recovery lost published receipt");
  if(new String(Files.readAllBytes(b.file(id).toPath()),java.nio.charset.StandardCharsets.ISO_8859_1).contains("content://"))throw new AssertionError("Plaintext URI");
  try {b.read(id,new byte[32]);throw new AssertionError("Wrong key read receipt");}catch(javax.crypto.AEADBadTagException expected){}
  if(!new ExternalEditRecoveryStore(root,"hidden").ids().isEmpty())throw new AssertionError("Cross vault receipt");
  ExternalEditRecoveryStore hidden=new ExternalEditRecoveryStore(root,"hidden");hidden.file(id).getParentFile().mkdirs();Files.copy(b.file(id).toPath(),hidden.file(id).toPath());
  try {hidden.read(id,key);throw new AssertionError("Moved cross-domain ciphertext accepted");}catch(IOException expected){}hidden.acknowledge(id);
  String moved=UUID.randomUUID().toString();Files.copy(b.file(id).toPath(),b.file(moved).toPath());
  try {b.read(moved,key);throw new AssertionError("ID tamper accepted");}catch(javax.crypto.AEADBadTagException expected){}b.acknowledge(moved);
  if(!b.ids().contains(id))throw new AssertionError("Cancel must preserve receipt");
  b.write(id,"original","content://media/external/images/media/17","content://media/19",true,"verified-import-id",key);
  if(!new ExternalEditRecoveryStore(root,"primary").read(id,key).saved || !b.read(id,key).importedId.equals("verified-import-id"))throw new AssertionError("Saved recovery");
  b.acknowledge(id);if(!b.ids().isEmpty())throw new AssertionError("Acknowledgement did not retire receipt");
  System.out.println("External editor durable encrypted receipt checks passed");
 }
}
