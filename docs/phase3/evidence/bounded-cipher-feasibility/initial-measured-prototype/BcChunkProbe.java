import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.lang.management.ManagementFactory;
import org.bouncycastle.crypto.*;
import org.bouncycastle.crypto.engines.*;
import org.bouncycastle.crypto.macs.Poly1305;
import org.bouncycastle.crypto.modes.ChaCha20Poly1305;
import org.bouncycastle.crypto.params.*;

/** Isolated public-vector feasibility probe, NOT a production owned cipher. */
public class BcChunkProbe {
  static final byte[] KEY=hex("808182838485868788898a8b8c8d8e8f909192939495969798999a9b9c9d9e9f");
  static final byte[] NONCE=hex("070000004041424344454647");
  static final byte[] AAD=hex("50515253c0c1c2c3c4c5c6c7");
  static final byte[] PLAIN=("Ladies and Gentlemen of the class of '99: If I could offer you only one tip for the future, sunscreen would be it.").getBytes(StandardCharsets.US_ASCII);
  static final byte[] SEALED=hex("d31a8d34648e60db7b86afbc53ef7ec2a4aded51296e08fea9e2b5a736ee62d63dbea45e8ca9671282fafb69da92728b1a71de0a9e060b2905d6a5b67ecd3b3692ddbd7f2d778b8c9803aee328091b58fab324e4fad675945585808b4831d7bc3ff4def08e4b7a9de576d26586cec64b61161ae10b594f09e26a7e902ecbd0600691");
  static byte[] hex(String s) { return HexFormat.of().parseHex(s); }
  static void require(boolean b,String message) { if(!b) throw new AssertionError(message); }
  static boolean zero(byte[] b) { for(byte x:b)if(x!=0)return false;return true; }
  static Field field(Class<?> c,String n) throws Exception { Field f=c.getDeclaredField(n);f.setAccessible(true);return f; }
  static void schema(Class<?> c,String names) {
    Set<String> actual=new TreeSet<>();for(Field f:c.getDeclaredFields())if(!Modifier.isStatic(f.getModifiers()))actual.add(f.getName());
    Set<String> expected=new TreeSet<>(names.isEmpty()?Collections.emptyList():Arrays.asList(names.split(",")));require(actual.equals(expected),"unknown layout: "+c.getName()+" "+actual);
  }
  static final class BorrowedKey extends KeyParameter {
    byte[] active;
    BorrowedKey() { super(new byte[0]); }
    @Override public byte[] getKey() { if(active==null)throw new IllegalStateException("no live borrow");return active; }
    @Override public int getKeyLength() { return getKey().length; }
    @Override public void copyTo(byte[] target,int offset,int length) {
      if(length!=getKeyLength())throw new IllegalArgumentException("key length");System.arraycopy(getKey(),0,target,offset,length);
    }
  }
  static final class WipingMac extends Poly1305 {
    byte[] lastCopy; boolean throwAfterInit;
    @Override public void init(CipherParameters parameters) {
      require(parameters.getClass()==KeyParameter.class,"only library-created one-time key clone");
      lastCopy=((KeyParameter)parameters).getKey();
      try { super.init(parameters);if(throwAfterInit)throw new IllegalStateException("injected post-derivation failure"); }
      finally { Arrays.fill(lastCopy,(byte)0); }
    }
  }
  static final class FixedCipher implements AutoCloseable {
    final WipingMac mac=new WipingMac();
    final ChaCha20Poly1305 engine=new ChaCha20Poly1305(mac);
    final BorrowedKey key=new BorrowedKey();
    final Object stream;
    final Field[] arrays;
    final Object[] objects;
    final Field[] macScalars;
    boolean closed;
    FixedCipher() throws Exception {
      schema(ChaCha20Poly1305.class,"chacha20,poly1305,key,nonce,buf,mac,initialAAD,aadCount,dataCount,state,bufPos");
      schema(ChaCha7539Engine.class,"");
      schema(Salsa20Engine.class,"rounds,index,engineState,x,keyStream,initialised,cW0,cW1,cW2");
      schema(Poly1305.class,"cipher,singleByte,r0,r1,r2,r3,r4,s1,s2,s3,s4,k0,k1,k2,k3,currentBlock,currentBlockOffset,h0,h1,h2,h3,h4");
      stream=field(ChaCha20Poly1305.class,"chacha20").get(engine);
      require(stream.getClass()==ChaCha7539Engine.class,"unexpected stream engine");
      require(field(Poly1305.class,"cipher").get(mac)==null,"no secondary block cipher");
      List<Field> fs=new ArrayList<>();List<Object> os=new ArrayList<>();
      for(String n:new String[]{"key","nonce","buf","mac","initialAAD"}) { fs.add(field(ChaCha20Poly1305.class,n));os.add(engine); }
      for(String n:new String[]{"engineState","x","keyStream"}) { fs.add(field(Salsa20Engine.class,n));os.add(stream); }
      for(String n:new String[]{"singleByte","currentBlock"}) { fs.add(field(Poly1305.class,n));os.add(mac); }
      arrays=fs.toArray(new Field[0]);objects=os.toArray();
      macScalars=Arrays.stream(Poly1305.class.getDeclaredFields()).filter(f->!Modifier.isStatic(f.getModifiers())&&f.getType()==int.class).toArray(Field[]::new);
      for(Field f:macScalars)f.setAccessible(true);
      // All reflection metadata is resolved before any private key is borrowed.
      for(Field f:arrays)require(f.getType()==byte[].class||f.getType()==int[].class,"unknown array representation");
    }
    int perform(boolean seal,byte[] sourceKey,byte[] nonce,byte[] aad,byte[] input,int length,byte[] output) throws Exception {
      if(closed)throw new IllegalStateException("closed");
      require(sourceKey.length==32&&nonce.length==12&&aad.length<=64,"fixed argument bounds");
      require(length>=0&&length<=(seal?65536:65552)&&length<=input.length&&(!seal||length+16<=output.length)&&(seal||length>=16&&length-16<=output.length),"fixed chunk bounds");
      Arrays.fill(output,(byte)0);
      key.active=sourceKey;
      try {
        engine.init(seal,new ParametersWithIV(key,nonce));
        engine.processAADBytes(aad,0,aad.length);
        int count=engine.processBytes(input,0,length,output,0);
        count+=engine.doFinal(output,count);
        return count;
      } catch(Exception|Error failure) {
        Arrays.fill(output,(byte)0);
        try { close(); } catch(Exception cleanup) { failure.addSuppressed(cleanup); }
        throw failure;
      } finally { key.active=null; }
    }
    @Override public void close() throws Exception {
      closed=true;
      for(int i=0;i<arrays.length;i++) {
        Object v=arrays[i].get(objects[i]);
        if(v instanceof byte[])Arrays.fill((byte[])v,(byte)0);
        else if(v instanceof int[])Arrays.fill((int[])v,0);
        else require(v==null,"unexpected field value");
      }
      for(Field f:macScalars)f.setInt(mac,0);
    }
    void assertWiped() throws Exception {
      for(int i=0;i<arrays.length;i++) {
        Object v=arrays[i].get(objects[i]);
        if(v instanceof byte[])require(zero((byte[])v),"array not wiped: "+arrays[i]);
        if(v instanceof int[])for(int x:(int[])v)require(x==0,"state not wiped: "+arrays[i]);
      }
      for(Field f:macScalars)require(f.getInt(mac)==0,"MAC scalar not wiped: "+f);
      require(key.active==null,"borrow retained");
      require(mac.lastCopy==null||zero(mac.lastCopy),"one-time key clone retained");
    }
  }
  static void baselineReset() throws Exception {
    ChaCha20Poly1305 engine=new ChaCha20Poly1305();byte[] output=new byte[PLAIN.length+16];
    engine.init(true,new ParametersWithIV(new KeyParameter(KEY),NONCE));engine.processAADBytes(AAD,0,AAD.length);
    int count=engine.processBytes(PLAIN,0,PLAIN.length,output,0);engine.doFinal(output,count);engine.reset();
    require(zero((byte[])field(ChaCha20Poly1305.class,"key").get(engine)),"baseline reset retains its copied key");
  }
  public static void main(String[] args) throws Exception {
    if(args.length>0&&args[0].equals("baseline")) { baselineReset();return; }
    int checks=0;byte[] output=new byte[65552];
    FixedCipher seal=new FixedCipher();int count=seal.perform(true,KEY,NONCE,AAD,PLAIN,PLAIN.length,output);
    require(count==SEALED.length&&Arrays.equals(SEALED,Arrays.copyOf(output,count)),"RFC8439 AEAD vector");
    require(zero(seal.mac.lastCopy),"temporary one-time key clone");seal.close();seal.assertWiped();checks++;System.out.println("RFC8439 seal and actual-copy disposal: PASS");
    FixedCipher open=new FixedCipher();Arrays.fill(output,(byte)9);count=open.perform(false,KEY,NONCE,AAD,SEALED,SEALED.length,output);
    require(count==PLAIN.length&&Arrays.equals(PLAIN,Arrays.copyOf(output,count)),"RFC8439 open");for(int i=count;i<output.length;i++)require(output[i]==0,"unused tail");open.close();open.assertWiped();checks++;System.out.println("RFC8439 authenticated open and complete tail clearing: PASS");
    for(int i=0;i<SEALED.length;i++) {
      byte[] altered=SEALED.clone();altered[i]^=1;FixedCipher failed=new FixedCipher();Arrays.fill(output,(byte)9);
      try { failed.perform(false,KEY,NONCE,AAD,altered,altered.length,output);throw new AssertionError("accepted alteration "+i); }
      catch(InvalidCipherTextException expected) { require(zero(output),"partial plaintext retained");failed.assertWiped(); }
      try { failed.perform(false,KEY,NONCE,AAD,SEALED,SEALED.length,output);throw new AssertionError("reused failed cipher"); }catch(IllegalStateException expected) {}
    }
    checks++;System.out.println("130 independently altered ciphertext/tag positions, whole output wipe and terminal denial: PASS");
    FixedCipher injected=new FixedCipher();injected.mac.throwAfterInit=true;
    try { injected.perform(true,KEY,NONCE,AAD,PLAIN,PLAIN.length,output);throw new AssertionError("injected failure accepted"); }catch(IllegalStateException expected) { injected.assertWiped();require(zero(output),"failure output"); }
    checks++;System.out.println("post-MAC-derivation injected failure and actual copied-key wipe: PASS");
    byte[] input=new byte[65536];Arrays.fill(input,(byte)11);byte[] nonce=NONCE.clone();
    FixedCipher repeated=new FixedCipher();
    for(int i=0;i<100;i++){nonce[0]=(byte)i;repeated.perform(true,KEY,nonce,AAD,input,input.length,output);}
    com.sun.management.ThreadMXBean bean=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();require(bean.isThreadAllocatedMemorySupported(),"allocation measurement unsupported");bean.setThreadAllocatedMemoryEnabled(true);
    long id=Thread.currentThread().getId(),before=bean.getThreadAllocatedBytes(id);
    for(int i=100;i<164;i++){nonce[0]=(byte)i;repeated.perform(true,KEY,nonce,AAD,input,input.length,output);}
    long bytes=bean.getThreadAllocatedBytes(id)-before;
    require(bytes<=64L*8192,"per-call copies exceed bound: "+bytes);repeated.close();repeated.assertWiped();checks++;System.out.println("64 full64KiB encrypt calls allocated "+bytes+" bytes total, <=8192/call: PASS");
    FixedCipher bounds=new FixedCipher();
    try { bounds.perform(true,KEY,NONCE,AAD,new byte[65537],65537,output);throw new AssertionError("oversize accepted"); }catch(AssertionError expected){require(expected.getMessage().equals("fixed chunk bounds"),"wrong bounds rejection");}
    require(bounds.mac.lastCopy==null,"oversize touched private key");bounds.close();bounds.assertWiped();checks++;System.out.println("oversize denied before private key initialization: PASS");
    require(Arrays.equals(KEY,hex("808182838485868788898a8b8c8d8e8f909192939495969798999a9b9c9d9e9f")),"borrowed caller key mutated");
    System.out.println("Measured feasibility checks: "+checks+"; production ownership/nonce/file/Android integration NOT proved");
  }
}
