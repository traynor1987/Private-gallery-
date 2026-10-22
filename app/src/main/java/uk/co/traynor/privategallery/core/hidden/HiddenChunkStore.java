package uk.co.traynor.privategallery.core.hidden;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Arrays;
import javax.crypto.*;
import javax.crypto.spec.*;

/** Hidden-only v2 payloads. Stages ciphertext; callers own authenticated atomic publication. */
public final class HiddenChunkStore {
 public static final int CHUNK_BYTES=64*1024, HEADER_BYTES=64;
 public static final long MAX_BYTES=2L*1024*1024*1024;
 private static final byte[] MAGIC={'P','G','H','C',2,0,0,0};
 private static final SecureRandom RANDOM=new SecureRandom();
 public interface Check {void run() throws Exception;}
 public static final class Staged {
  public final File file;public final long size;public final byte[] hash,nonce;
  Staged(File file,long size,byte[] hash,byte[] nonce){this.file=file;this.size=size;this.hash=hash;this.nonce=nonce;}
 }
 private static void check(Check check)throws Exception{if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();check.run();}
 public static Staged stage(File root,String id,InputStream input,byte[] key,Check allowed)throws Exception {
  if(!root.isDirectory()&&!root.mkdirs())throw new IOException("Hidden storage unavailable");
  File temporary=File.createTempFile("staged-",".ciphertext",root);
  byte[] buffer=new byte[CHUNK_BYTES],nonce=new byte[12];do{RANDOM.nextBytes(nonce);}while(Arrays.equals(nonce,new byte[12]));
  MessageDigest digest=MessageDigest.getInstance("SHA-256");long total=0;int index=0;
  try {
   try(RandomAccessFile out=new RandomAccessFile(temporary,"rw")){
    out.write(new byte[HEADER_BYTES]);
    while(true){check(allowed);int filled=0;
     while(filled<buffer.length){check(allowed);int n=input.read(buffer,filled,buffer.length-filled);if(n<0)break;if(n==0)throw new IOException("Import stalled");filled+=n;}
     if(filled==0)break;
     if(total+filled>MAX_BYTES)throw new IOException("Hidden media exceeds 2 GiB");
     if(root.getUsableSpace()<filled+2L*CHUNK_BYTES)throw new IOException("Insufficient storage");
     byte[] iv=new byte[12];RANDOM.nextBytes(iv);
     byte[] encrypted=crypt(Cipher.ENCRYPT_MODE,key,iv,aad(id,nonce,index,filled),buffer,filled);
     try{out.write(iv);out.write(encrypted);}finally{Arrays.fill(encrypted,(byte)0);}
     digest.update(buffer,0,filled);Arrays.fill(buffer,(byte)0);total+=filled;index++;
    }
    if(total==0)throw new IOException("Empty media");
    byte[] prefix=ByteBuffer.allocate(32).put(MAGIC).putInt(CHUNK_BYTES).putLong(total).put(nonce).array();
    out.seek(0);out.write(prefix);out.write(mac(id,prefix,key));check(allowed);out.getFD().sync();
   }
   Staged result=new Staged(temporary,total,digest.digest(),nonce);
   try(Reader reader=new Reader(temporary,id,total,result.hash,nonce,key,allowed)){reader.verify();}
   check(allowed);return result;
  }catch(Exception|Error failure){temporary.delete();throw failure;}finally{Arrays.fill(buffer,(byte)0);}
 }
 public static final class Reader implements AutoCloseable {
  private final RandomAccessFile file;private final String id;private final byte[] key,hash,nonce;private final Check allowed;
  public final long size;private boolean closed;
  public Reader(File source,String id,long size,byte[] hash,byte[] nonce,byte[] key,Check allowed)throws Exception{
   this.id=id;this.size=size;this.hash=hash.clone();this.nonce=nonce.clone();this.allowed=allowed;
   file=new RandomAccessFile(source,"r");this.key=key.clone();
   try{check(allowed);byte[] header=new byte[HEADER_BYTES];file.readFully(header);ByteBuffer h=ByteBuffer.wrap(header);byte[] magic=new byte[8];h.get(magic);
    if(!Arrays.equals(magic,MAGIC)||h.getInt()!=CHUNK_BYTES||h.getLong()!=size||size<1||size>MAX_BYTES)throw new IOException("Invalid Hidden chunk header");
    byte[] actualNonce=new byte[12];h.get(actualNonce);byte[] signature=new byte[32];h.get(signature);
    if(!Arrays.equals(actualNonce,nonce)||!MessageDigest.isEqual(signature,mac(id,Arrays.copyOf(header,32),key)))throw new IOException("Hidden header authentication failed");
    long chunks=(size-1)/CHUNK_BYTES+1;if(file.length()!=HEADER_BYTES+size+28*chunks)throw new IOException("Incomplete Hidden payload");
   }catch(Exception|Error failure){close();throw failure;}
  }
  public synchronized int readAt(long position,byte[] target,int offset,int length)throws Exception{
   if(closed)throw new IOException("Hidden reader closed");check(allowed);
   if(position<0||offset<0||length<0||offset>target.length-length)throw new IllegalArgumentException();
   if(length==0)return 0;if(position>=size)return -1;int done=0;
   try{while(done<length&&position<size){check(allowed);int index=(int)(position/CHUNK_BYTES),within=(int)(position%CHUNK_BYTES);
    byte[] plain=chunk(index);try{int n=Math.min(length-done,plain.length-within);System.arraycopy(plain,within,target,offset+done,n);position+=n;done+=n;}finally{Arrays.fill(plain,(byte)0);}}
    check(allowed);return done;
   }catch(Exception|Error failure){Arrays.fill(target,offset,offset+length,(byte)0);throw failure;}
  }
  private byte[] chunk(int index)throws Exception{
   check(allowed);int length=(int)Math.min(CHUNK_BYTES,size-(long)index*CHUNK_BYTES);
   file.seek(HEADER_BYTES+(long)index*(CHUNK_BYTES+28));byte[] iv=new byte[12],encrypted=new byte[length+16];file.readFully(iv);file.readFully(encrypted);
   try{return crypt(Cipher.DECRYPT_MODE,key,iv,aad(id,nonce,index,length),encrypted,encrypted.length);}finally{Arrays.fill(encrypted,(byte)0);}
  }
  public synchronized void verify()throws Exception{
   if(closed)throw new IOException("Hidden reader closed");MessageDigest digest=MessageDigest.getInstance("SHA-256");
   for(int i=0;i<(size-1)/CHUNK_BYTES+1;i++){byte[] plain=chunk(i);try{digest.update(plain);}finally{Arrays.fill(plain,(byte)0);}}
   check(allowed);if(!MessageDigest.isEqual(hash,digest.digest()))throw new IOException("Hidden payload hash mismatch");
  }
  public InputStream stream(){return new InputStream(){long position;
   public int read()throws IOException{byte[] one=new byte[1];return read(one,0,1)<0?-1:one[0]&255;}
   public int read(byte[] b,int off,int len)throws IOException{try{int n=readAt(position,b,off,len);if(n>0)position+=n;return n;}catch(Exception e){throw new IOException("Hidden stream unavailable",e);}}
  };}
  @Override public synchronized void close()throws IOException{closed=true;Arrays.fill(key,(byte)0);file.close();}
 }
 private static byte[] aad(String id,byte[] nonce,int index,int length)throws IOException{
  ByteArrayOutputStream b=new ByteArrayOutputStream();try(DataOutputStream out=new DataOutputStream(b)){out.writeUTF("private-gallery:hidden:chunk:v2");out.writeUTF(id);out.write(nonce);out.writeInt(index);out.writeInt(length);}return b.toByteArray();
 }
 private static byte[] mac(String id,byte[] prefix,byte[] key)throws Exception{
  Mac derive=Mac.getInstance("HmacSHA256");derive.init(new SecretKeySpec(key,"HmacSHA256"));byte[] derived=derive.doFinal("private-gallery:hidden:header:v2".getBytes(StandardCharsets.UTF_8));
  try{Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(derived,"HmacSHA256"));mac.update(id.getBytes(StandardCharsets.UTF_8));return mac.doFinal(prefix);}finally{Arrays.fill(derived,(byte)0);}
 }
 private static byte[] crypt(int mode,byte[] key,byte[] nonce,byte[] aad,byte[] input,int length)throws Exception{
  if(key.length!=32)throw new IllegalArgumentException();Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(mode,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,nonce));cipher.updateAAD(aad);return cipher.doFinal(input,0,length);
 }
}
