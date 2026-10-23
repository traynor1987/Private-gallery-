package uk.co.traynor.privategallery.core.camera;

import uk.co.traynor.privategallery.core.hidden.HiddenChunkStore;
import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import javax.crypto.Cipher;
import javax.crypto.spec.*;

/** Capture-only write authority. It cannot decrypt any existing vault media after lock.
 * Roots are app-private/no-backup and are never exposed by FileProvider. */
public final class CaptureJournal {
 private static final Set<String> LIVE = new HashSet<>();
 private final File root; private final String domain; private static final SecureRandom RANDOM=new SecureRandom();
 public CaptureJournal(File root,String domain){if(!domain.equals("primary")&&!domain.equals("hidden"))throw new IllegalArgumentException();this.root=root;this.domain=domain;}
 public static final class Ticket implements AutoCloseable {
  public final String id,mime;public final File plain;public final long timestamp;public final boolean audio;
  public boolean complete;public long duration,size;public byte[] hash,nonce;
  private final byte[] key;private boolean closed;private String lease;
  private Ticket(String id,String mime,File directory,long timestamp,boolean audio,byte[] key){this.id=id;this.mime=mime;this.plain=new File(directory,"capture.tmp");this.timestamp=timestamp;this.audio=audio;this.key=key;}
  @Override public synchronized void close(){Arrays.fill(key,(byte)0);closed=true;if(lease!=null){synchronized(LIVE){LIVE.remove(lease);}lease=null;}}
 }
 public synchronized List<String> entries(){File[] dirs=root.listFiles();List<String> ids=new ArrayList<>();if(dirs!=null)for(File d:dirs)if(d.isDirectory()&&d.getName().matches("[0-9a-f-]{36}"))ids.add(d.getName());return ids;}
 public synchronized Ticket create(byte[] vaultKey,String mime,long timestamp,boolean audio)throws Exception {
  if(!mime.equals("image/jpeg")&&!mime.equals("video/mp4"))throw new IllegalArgumentException();
  if(vaultKey.length!=32)throw new IllegalArgumentException();
  if(!root.isDirectory()&&!root.mkdirs())throw new IOException("Capture storage unavailable");
  if(root.getUsableSpace()<64L*1024*1024)throw new IOException("Insufficient capture storage");
  String id=UUID.randomUUID().toString();File dir=new File(root,id);if(!dir.mkdir())throw new IOException("Capture unavailable");
  byte[] key=new byte[32];RANDOM.nextBytes(key);
  Ticket t=new Ticket(id,mime,dir,timestamp,audio,key);
  t.lease=dir.getCanonicalPath();synchronized(LIVE){LIVE.add(t.lease);}
  try {
   ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(DataOutputStream out=new DataOutputStream(bytes)){out.writeInt(1);out.writeUTF(mime);out.writeLong(timestamp);out.writeBoolean(audio);out.write(key);}
   byte[] metadata=bytes.toByteArray();try{writeEncrypted(new File(dir,"owner.enc"),metadata,vaultKey,aad(id,"owner"));}finally{Arrays.fill(metadata,(byte)0);}
   if(!t.plain.createNewFile())throw new IOException("Capture unavailable");
   t.plain.setReadable(false,false);t.plain.setWritable(false,false);if(!t.plain.setReadable(true,true)||!t.plain.setWritable(true,true))throw new IOException("Capture access unavailable");
   sync(dir);sync(root);return t;
  }catch(Exception failure){t.close();throw failure;}
 }
 public Ticket openForRecovery(String id,byte[] vaultKey)throws Exception {
  String lease=directory(id).getCanonicalPath();
  synchronized(LIVE){if(!LIVE.add(lease))throw new IOException("Capture still finalising");}
  try{Ticket t=open(id,vaultKey);t.lease=lease;return t;}catch(Exception failure){synchronized(LIVE){LIVE.remove(lease);}throw failure;}
 }
 public synchronized Ticket open(String id,byte[] vaultKey)throws Exception {
  File dir=directory(id);byte[] data=readEncrypted(new File(dir,"owner.enc"),vaultKey,aad(id,"owner"));Ticket t;
  try(DataInputStream in=new DataInputStream(new ByteArrayInputStream(data))){if(in.readInt()!=1)throw new IOException("Capture version");String mime=in.readUTF();long time=in.readLong();boolean audio=in.readBoolean();byte[] key=new byte[32];in.readFully(key);t=new Ticket(id,mime,dir,time,audio,key);}finally{Arrays.fill(data,(byte)0);}
  try {
   File state=new File(dir,"state.enc");if(state.exists()){
    data=readEncrypted(state,t.key,aad(id,"state"));try(DataInputStream in=new DataInputStream(new ByteArrayInputStream(data))){t.complete=in.readBoolean();t.duration=in.readLong();t.size=in.readLong();t.hash=new byte[32];in.readFully(t.hash);t.nonce=new byte[12];in.readFully(t.nonce);}finally{Arrays.fill(data,(byte)0);}
   }
   return t;
  }catch(Exception failure){t.close();throw failure;}
 }
 /** Must be called only after CameraX Finalize/photo callback or authenticated process recovery. */
 public synchronized void seal(Ticket t,boolean complete,long duration)throws Exception {
  if(t.closed||t.lease==null)throw new IOException("Capture write authority retired");
  File dir=directory(t.id),media=new File(dir,"media.enc");
  if(new File(dir,"state.enc").exists()) {try(HiddenChunkStore.Reader r=reader(t)){r.verify();}if(t.plain.exists()&&!t.plain.delete())throw new IOException("Private cleanup pending");return;}
  if(t.plain.length()==0)throw new IOException("No completed capture bytes; retained for recovery");
  HiddenChunkStore.Staged staged;try(InputStream in=new FileInputStream(t.plain)){staged=HiddenChunkStore.stage(dir,t.id,in,t.key,()->{});}
  // A crash between media and state publication keeps the original plaintext. Retry never removes it.
  Files.move(staged.file.toPath(),media.toPath(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);sync(dir);
  t.size=staged.size;t.hash=staged.hash;t.nonce=staged.nonce;t.complete=complete;t.duration=Math.max(0,duration);
  ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(DataOutputStream out=new DataOutputStream(bytes)){out.writeBoolean(t.complete);out.writeLong(t.duration);out.writeLong(t.size);out.write(t.hash);out.write(t.nonce);}
  byte[] state=bytes.toByteArray();try{writeEncrypted(new File(dir,"state.enc"),state,t.key,aad(t.id,"state"));}finally{Arrays.fill(state,(byte)0);}
  try(HiddenChunkStore.Reader r=reader(t)){r.verify();}
  if(!t.plain.delete())throw new IOException("Private cleanup pending");sync(dir);
 }
 public synchronized HiddenChunkStore.Reader reader(Ticket t)throws Exception {
  if(t.closed||t.hash==null)throw new IOException("Capture not sealed");
  return new HiddenChunkStore.Reader(new File(directory(t.id),"media.enc"),t.id,t.size,t.hash,t.nonce,t.key,()->{if(t.closed)throw new IOException("Capture key retired");});
 }
 /** Only after verified destination import; never called on a failed capture/import. */
 public synchronized void imported(Ticket t)throws IOException {
  File dir=directory(t.id);File[] files=dir.listFiles();if(files!=null)for(File f:files)if(!f.delete())throw new IOException("Encrypted cleanup pending");if(!dir.delete())throw new IOException("Encrypted cleanup pending");sync(root);
 }
 private File directory(String id)throws IOException {if(!id.matches("[0-9a-f-]{36}"))throw new IOException("Invalid capture identity");File dir=new File(root,id);if(!dir.getCanonicalFile().getParentFile().equals(root.getCanonicalFile()))throw new IOException("Invalid capture root");return dir;}
 private byte[] aad(String id,String kind){return ("private-gallery-camera-v1|"+domain+"|"+id+"|"+kind).getBytes(java.nio.charset.StandardCharsets.UTF_8);}
 private static void writeEncrypted(File file,byte[] data,byte[] key,byte[] aad)throws Exception {
  byte[] iv=new byte[12];RANDOM.nextBytes(iv);Cipher c=cipher(Cipher.ENCRYPT_MODE,key,iv,aad);byte[] encrypted=c.doFinal(data);File temp=new File(file.getParentFile(),file.getName()+".new");
  try(FileOutputStream out=new FileOutputStream(temp)){out.write(iv);out.write(encrypted);out.getFD().sync();}finally{Arrays.fill(encrypted,(byte)0);}
  Files.move(temp.toPath(),file.toPath(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);sync(file.getParentFile());
 }
 private static byte[] readEncrypted(File file,byte[] key,byte[] aad)throws Exception {
  if(file.length()<28||file.length()>4096)throw new IOException("Invalid capture metadata");byte[] data=Files.readAllBytes(file.toPath());
  try{return cipher(Cipher.DECRYPT_MODE,key,Arrays.copyOf(data,12),aad).doFinal(data,12,data.length-12);}finally{Arrays.fill(data,(byte)0);}
 }
 private static Cipher cipher(int mode,byte[] key,byte[] iv,byte[] aad)throws GeneralSecurityException {Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(mode,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,iv));c.updateAAD(aad);return c;}
 private static void sync(File dir)throws IOException {try(FileChannel c=FileChannel.open(dir.toPath(),StandardOpenOption.READ)){c.force(true);}}
}
