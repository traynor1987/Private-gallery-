package uk.co.traynor.privategallery.core.evidence;
import java.io.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import org.bouncycastle.crypto.engines.AESEngine;
import org.bouncycastle.crypto.modes.GCMBlockCipher;
import org.bouncycastle.crypto.params.AEADParameters;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.crypto.generators.SCrypt;

/** Lightweight GCM emits bounded chunks; JCE GCM provider buffering is deliberately avoided. */
final class EvidenceArchiveCrypto {
 static byte[] key(char[] passphrase,byte[] header)throws IOException{
  EvidenceArchiveFormat.validateHeader(header);char[] owned=passphrase.clone();byte[] encoded=null;
  try{encoded=EvidenceArchiveFormat.password(owned);return SCrypt.generate(encoded,Arrays.copyOfRange(header,24,40),32768,8,1,32);}
  finally{Arrays.fill(owned,'\0');if(encoded!=null)Arrays.fill(encoded,(byte)0);}
 }
 private static GCMBlockCipher cipher(boolean encrypt,byte[] key,byte[] header){GCMBlockCipher c=new GCMBlockCipher(new AESEngine());c.init(encrypt,new AEADParameters(new KeyParameter(key),128,Arrays.copyOfRange(header,40,52),header));return c;}
 static final class Encrypt extends OutputStream {
  final OutputStream output;final GCMBlockCipher cipher;final BooleanSupplier current;final byte[] encrypted=new byte[32768+32];boolean finished;
  Encrypt(OutputStream output,byte[] key,byte[] header,BooleanSupplier current){this.output=output;cipher=cipher(true,key,header);this.current=current;}
  @Override public void write(int b)throws IOException{write(new byte[]{(byte)b});}
  @Override public void write(byte[] b,int off,int len)throws IOException{if(finished)throw new IOException("Closed encryption");while(len>0){EvidenceArchiveFormat.check(current);int count=Math.min(len,32768);int n=cipher.processBytes(b,off,count,encrypted,0);output.write(encrypted,0,n);off+=count;len-=count;}}
  void finish()throws IOException{if(finished)return;EvidenceArchiveFormat.check(current);try{int n=cipher.doFinal(encrypted,0);output.write(encrypted,0,n);output.flush();finished=true;}catch(Exception bad){throw new IOException("Evidence encryption failed");}finally{Arrays.fill(encrypted,(byte)0);cipher.reset();}}
  void dispose(){Arrays.fill(encrypted,(byte)0);cipher.reset();finished=true;}
  @Override public void close()throws IOException{finish();}
 }
 static final class Decrypt extends InputStream {
  final InputStream input;final GCMBlockCipher cipher;final BooleanSupplier current;final byte[] encrypted=new byte[32768],plain=new byte[32768+32];int at,available;boolean ended;long total;
  Decrypt(InputStream input,byte[] key,byte[] header,BooleanSupplier current){this.input=input;cipher=cipher(false,key,header);this.current=current;}
  @Override public int read()throws IOException{byte[] b=new byte[1];return read(b,0,1)<0?-1:b[0]&255;}
  @Override public int read(byte[] b,int off,int len)throws IOException{
   if(len==0)return 0;EvidenceArchiveFormat.check(current);
   while(at==available){Arrays.fill(plain,(byte)0);at=available=0;if(ended)return -1;int n=input.read(encrypted);if(n<0){try{available=cipher.doFinal(plain,0);ended=true;}catch(Exception bad){throw new IOException("Evidence authentication failed");}}else{total+=n;if(total>EvidenceArchiveFormat.MAX_CIPHER)throw new IOException("Evidence container too large");available=cipher.processBytes(encrypted,0,n,plain,0);}}
   int n=Math.min(len,available-at);System.arraycopy(plain,at,b,off,n);Arrays.fill(plain,at,at+n,(byte)0);at+=n;return n;
  }
  @Override public void close()throws IOException{try{input.close();}finally{Arrays.fill(plain,(byte)0);Arrays.fill(encrypted,(byte)0);cipher.reset();ended=true;}}
 }
}
