package uk.co.traynor.privategallery.core.security.staging;

import org.junit.Test;
import static org.junit.Assert.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import org.bouncycastle.crypto.InvalidCipherTextException;
import org.bouncycastle.crypto.modes.ChaCha20Poly1305;
import org.bouncycastle.crypto.params.*;

/** Public fixtures; primitive proof is distinct from original/nonce/descriptor/native ownership. */
public class Phase3FixedStagingCipherTest {
    private static final byte[] KEY=hex("808182838485868788898a8b8c8d8e8f909192939495969798999a9b9c9d9e9f");
    private static final byte[] NONCE=hex("070000004041424344454647");
    private static final byte[] AAD=hex("50515253c0c1c2c3c4c5c6c7");
    private static final byte[] PLAIN=("Ladies and Gentlemen of the class of '99: If I could offer you only one tip for the future, sunscreen would be it.").getBytes(StandardCharsets.US_ASCII);
    private static final byte[] SEALED=hex("d31a8d34648e60db7b86afbc53ef7ec2a4aded51296e08fea9e2b5a736ee62d63dbea45e8ca9671282fafb69da92728b1a71de0a9e060b2905d6a5b67ecd3b3692ddbd7f2d778b8c9803aee328091b58fab324e4fad675945585808b4831d7bc3ff4def08e4b7a9de576d26586cec64b61161ae10b594f09e26a7e902ecbd0600691");
    @Test public void rfcSealMatchesEveryByteAndClearsAllPreallocatedState() throws Exception {
        FixedStagingChunkCipher c=new FixedStagingChunkCipher();List<Object> arrays=inventory(c);byte[] output=filled(65552,9);
        assertEquals(SEALED.length,c.perform(true,KEY,NONCE,AAD,PLAIN,PLAIN.length,output));
        assertArrayEquals(SEALED,Arrays.copyOf(output,SEALED.length));zeroTail(output,SEALED.length);wiped(arrays);assertArrayEquals(hex("808182838485868788898a8b8c8d8e8f909192939495969798999a9b9c9d9e9f"),KEY);
    }
    @Test public void rfcOpenAuthenticatesThenReturnsExactPlaintextWithZeroTail() throws Exception {
        FixedStagingChunkCipher c=new FixedStagingChunkCipher();List<Object> arrays=inventory(c);byte[] output=filled(65552,9);
        assertEquals(PLAIN.length,c.perform(false,KEY,NONCE,AAD,SEALED,SEALED.length,output));assertArrayEquals(PLAIN,Arrays.copyOf(output,PLAIN.length));zeroTail(output,PLAIN.length);wiped(arrays);
    }
    @Test public void everyCiphertextAndTagByteTamperWipesWholeOutputAndAllState() throws Exception {
        for(int i=0;i<SEALED.length;i++){FixedStagingChunkCipher c=new FixedStagingChunkCipher();List<Object> arrays=inventory(c);byte[] changed=SEALED.clone();changed[i]^=1;byte[] output=filled(65552,9);
            assertThrows(InvalidCipherTextException.class,()->c.perform(false,KEY,NONCE,AAD,changed,changed.length,output));zeroTail(output,0);wiped(arrays);
            Arrays.fill(output,(byte)7);assertThrows(IllegalStateException.class,()->c.perform(false,KEY,NONCE,AAD,SEALED,SEALED.length,output));assertArrayEquals(filled(output.length,7),output);
        }
    }
    @Test public void independentlyAlteredNonceAndAadDenyAndClearAllPlaintext() throws Exception {
        for(int position:new int[]{0,NONCE.length-1}){byte[] nonce=NONCE.clone();nonce[position]^=1;deniedAuthentication(nonce,AAD);}
        for(int position:new int[]{0,AAD.length-1}){byte[] aad=AAD.clone();aad[position]^=1;deniedAuthentication(NONCE,aad);}
    }
    @Test public void blockAndChunkBoundariesMatchExistingPinnedBcPrimitive() throws Exception {
        int n=0;for(int length:new int[]{0,1,15,16,17,63,64,65,255,32768,65536})for(int aadLength:new int[]{0,1,16,63,64}){
            byte[] nonce=NONCE.clone();nonce[0]=(byte)n++;byte[] input=new byte[length];for(int i=0;i<length;i++)input[i]=(byte)(i*17);byte[] aad=filled(aadLength,21);
            byte[] expected=new byte[length+16];ChaCha20Poly1305 reference=new ChaCha20Poly1305();reference.init(true,new ParametersWithIV(new KeyParameter(KEY),nonce));reference.processAADBytes(aad,0,aad.length);int count=reference.processBytes(input,0,length,expected,0);count+=reference.doFinal(expected,count);assertEquals(expected.length,count);
            FixedStagingChunkCipher c=new FixedStagingChunkCipher();List<Object> arrays=inventory(c);byte[] actual=filled(65552,9);assertEquals(expected.length,c.perform(true,KEY,nonce,aad,input,length,actual));assertArrayEquals(expected,Arrays.copyOf(actual,expected.length));zeroTail(actual,expected.length);wiped(arrays);
            FixedStagingChunkCipher open=new FixedStagingChunkCipher();List<Object> openArrays=inventory(open);byte[] output=filled(65552,9);assertEquals(length,open.perform(false,KEY,nonce,aad,expected,expected.length,output));assertArrayEquals(input,Arrays.copyOf(output,length));zeroTail(output,length);wiped(openArrays);
        }
    }
    @Test public void invalidBoundsDenyBeforeOutputOrPrivateKeyMutationAndDoNotConsume() throws Exception {
        FixedStagingChunkCipher c=new FixedStagingChunkCipher();List<Object> arrays=inventory(c);byte[] output=filled(65552,9);
        for(int length:new int[]{-1,Integer.MAX_VALUE,65537})assertThrows(IllegalArgumentException.class,()->c.perform(true,KEY,NONCE,AAD,new byte[65537],length,output));
        assertThrows(IllegalArgumentException.class,()->c.perform(false,KEY,NONCE,AAD,new byte[15],15,output));
        assertThrows(IllegalArgumentException.class,()->c.perform(true,new byte[31],NONCE,AAD,PLAIN,PLAIN.length,output));
        assertThrows(IllegalArgumentException.class,()->c.perform(true,KEY,new byte[13],AAD,PLAIN,PLAIN.length,output));
        assertThrows(IllegalArgumentException.class,()->c.perform(true,KEY,NONCE,new byte[65],PLAIN,PLAIN.length,output));
        assertThrows(IllegalArgumentException.class,()->c.perform(true,KEY,NONCE,AAD,PLAIN,PLAIN.length,new byte[PLAIN.length+15]));
        assertThrows(IllegalArgumentException.class,()->c.perform(true,KEY,NONCE,AAD,PLAIN,PLAIN.length,new byte[65553]));
        assertArrayEquals(filled(output.length,9),output);wiped(arrays);assertEquals(SEALED.length,c.perform(true,KEY,NONCE,AAD,PLAIN,PLAIN.length,output));wiped(arrays);
    }
    @Test public void secretInputAndOutputAliasesDenyWithoutClearingCallerArrays() throws Exception {
        for(int kind=0;kind<4;kind++){FixedStagingChunkCipher c=new FixedStagingChunkCipher();List<Object> arrays=inventory(c);boolean seal=kind!=1;byte[] shared=filled(kind==1?12:32,7);byte[] key=kind==0?shared:KEY,nonce=kind==1?shared:NONCE,aad=kind==2?shared:AAD,input=kind==3?shared:new byte[16];
            // Each request otherwise meets its key/nonce/AAD/input/output size bounds.
            IllegalArgumentException failure=assertThrows(IllegalArgumentException.class,()->c.perform(seal,key,nonce,aad,input,16,shared));
            assertEquals("Distinct cipher arrays required",failure.getMessage());assertArrayEquals(filled(shared.length,7),shared);wiped(arrays);
        }
    }
    @Test public void privateKeyCannotAliasPlaintextEvenForAnEmptyAdmittedPrefix() throws Exception {
        FixedStagingChunkCipher c=new FixedStagingChunkCipher();List<Object> arrays=inventory(c);byte[] key=KEY.clone(),output=filled(65552,9);
        IllegalArgumentException failure=assertThrows(IllegalArgumentException.class,()->c.perform(true,key,NONCE,AAD,key,0,output));
        assertEquals("Distinct cipher arrays required",failure.getMessage());assertArrayEquals(KEY,key);assertArrayEquals(filled(output.length,9),output);wiped(arrays);
    }
    @Test public void privateKeyCannotBeUsedAsAuthenticatedFraming() throws Exception {
        FixedStagingChunkCipher c=new FixedStagingChunkCipher();List<Object> arrays=inventory(c);byte[] key=KEY.clone(),output=filled(65552,9);
        IllegalArgumentException failure=assertThrows(IllegalArgumentException.class,()->c.perform(true,key,NONCE,key,PLAIN,PLAIN.length,output));
        assertEquals("Distinct cipher arrays required",failure.getMessage());assertArrayEquals(KEY,key);assertArrayEquals(filled(output.length,9),output);wiped(arrays);
    }
    @Test public void successfulOriginalCannotBeUsedForAnotherNonceOrDirection() throws Exception {
        FixedStagingChunkCipher c=new FixedStagingChunkCipher();byte[] output=new byte[65552];c.perform(true,KEY,NONCE,AAD,PLAIN,PLAIN.length,output);byte[] saved=output.clone();byte[] nonce=NONCE.clone();nonce[0]^=1;
        assertThrows(IllegalStateException.class,()->c.perform(true,KEY,nonce,AAD,PLAIN,PLAIN.length,output));assertArrayEquals(saved,output);
        assertThrows(IllegalStateException.class,()->c.perform(false,KEY,NONCE,AAD,SEALED,SEALED.length,output));assertArrayEquals(saved,output);
    }
    @Test public void racingOriginalCallsHaveExactlyOneWinnerAndUntouchedDeniedOutput() throws Exception {
        FixedStagingChunkCipher c=new FixedStagingChunkCipher();List<Object> arrays=inventory(c);byte[][] output={filled(65552,9),filled(65552,9)};Throwable[] errors=new Throwable[2];int[] counts=new int[2];CountDownLatch start=new CountDownLatch(1);Thread[] threads=new Thread[2];
        for(int i=0;i<2;i++){final int index=i;threads[i]=new Thread(()->{try{assertTrue(start.await(5,TimeUnit.SECONDS));counts[index]=c.perform(true,KEY,NONCE,AAD,PLAIN,PLAIN.length,output[index]);}catch(Throwable t){errors[index]=t;}});threads[i].start();}
        start.countDown();for(Thread t:threads){t.join(5000);assertFalse(t.isAlive());}int winner=errors[0]==null?0:1,loser=1-winner;assertNull(errors[winner]);assertTrue(errors[loser] instanceof IllegalStateException);assertEquals(SEALED.length,counts[winner]);assertArrayEquals(SEALED,Arrays.copyOf(output[winner],SEALED.length));assertArrayEquals(filled(65552,9),output[loser]);wiped(arrays);
    }
    @Test public void streamCountersAndMacKeyScalarsClearAfterSuccessAndAuthenticationFailure() throws Exception {
        for(int mode=0;mode<3;mode++){FixedStagingChunkCipher c=new FixedStagingChunkCipher();byte[] output=new byte[65552];
            if(mode==0)c.perform(true,KEY,NONCE,AAD,PLAIN,PLAIN.length,output);
            else if(mode==1)c.perform(false,KEY,NONCE,AAD,SEALED,SEALED.length,output);
            else {byte[] changed=SEALED.clone();changed[changed.length-1]^=1;assertThrows(InvalidCipherTextException.class,()->c.perform(false,KEY,NONCE,AAD,changed,changed.length,output));}
            scalarWiped(c,Collections.newSetFromMap(new IdentityHashMap<>()));
        }
    }
    private static void scalarWiped(Object object,Set<Object> seen)throws Exception {
        if(object==null||!seen.add(object))return;
        for(Class<?> cls=object.getClass();cls!=null&&cls!=Object.class;cls=cls.getSuperclass())for(Field f:cls.getDeclaredFields()){
            if(Modifier.isStatic(f.getModifiers()))continue;f.setAccessible(true);
            if(f.getType()==int.class){if(cls.getSimpleName().equals("StagingSalsa20Engine")&&f.getName().equals("rounds"))assertEquals(20,f.getInt(object));else assertEquals(f.getName(),0,f.getInt(object));}
            else if(f.getType()==long.class)assertEquals(f.getName(),0,f.getLong(object));
            else if(f.getType()==boolean.class)assertFalse(f.getName(),f.getBoolean(object));
            else {Object child=f.get(object);if(child!=null&&child.getClass().getName().startsWith("uk.co.traynor.privategallery.core.security.staging."))scalarWiped(child,seen);}
        }
    }
    private static void deniedAuthentication(byte[] nonce,byte[] aad)throws Exception {FixedStagingChunkCipher c=new FixedStagingChunkCipher();List<Object> arrays=inventory(c);byte[] output=filled(65552,9);assertThrows(InvalidCipherTextException.class,()->c.perform(false,KEY,nonce,aad,SEALED,SEALED.length,output));zeroTail(output,0);wiped(arrays);}
    private static List<Object> inventory(Object root)throws Exception {List<Object> arrays=new ArrayList<>();walk(root,Collections.newSetFromMap(new IdentityHashMap<>()),arrays);List<Integer> bytes=new ArrayList<>(),ints=new ArrayList<>();for(Object a:arrays)if(a instanceof byte[])bytes.add(((byte[])a).length);else ints.add(((int[])a).length);Collections.sort(bytes);Collections.sort(ints);assertEquals(Arrays.asList(1,12,12,16,16,16,32,32,32,64,64,80),bytes);assertEquals(Arrays.asList(16,16),ints);return arrays;}
    private static void walk(Object object,Set<Object> seen,List<Object> arrays)throws Exception {if(object==null||!seen.add(object))return;if(object instanceof byte[]||object instanceof int[]){arrays.add(object);return;}for(Class<?> cls=object.getClass();cls!=null&&cls!=Object.class;cls=cls.getSuperclass())for(Field f:cls.getDeclaredFields()){if(Modifier.isStatic(f.getModifiers())||f.getType().isPrimitive())continue;f.setAccessible(true);Object child=f.get(object);if(child instanceof byte[]||child instanceof int[]||child!=null&&(child.getClass().getName().startsWith("uk.co.traynor.privategallery.core.security.staging.")||child.getClass().getName().startsWith("org.bouncycastle.crypto.params.")))walk(child,seen,arrays);}}
    private static void wiped(List<Object> arrays){for(Object a:arrays)if(a instanceof byte[])zeroTail((byte[])a,0);else for(int value:(int[])a)assertEquals(0,value);}
    private static void zeroTail(byte[] array,int start){for(int i=start;i<array.length;i++)if(array[i]!=0)fail("uncleared byte"+i);}
    private static byte[] filled(int length,int value){byte[] result=new byte[length];Arrays.fill(result,(byte)value);return result;}
    private static byte[] hex(String s){byte[] result=new byte[s.length()/2];for(int i=0;i<result.length;i++)result[i]=(byte)Integer.parseInt(s.substring(2*i,2*i+2),16);return result;}
}
