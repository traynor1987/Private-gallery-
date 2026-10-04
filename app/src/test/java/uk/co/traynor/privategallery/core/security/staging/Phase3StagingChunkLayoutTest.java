package uk.co.traynor.privategallery.core.security.staging;

import java.util.Arrays;
import org.bouncycastle.crypto.InvalidCipherTextException;
import org.junit.Test;
import static org.junit.Assert.*;

/** Public temporary-frame fixtures; no durable F1 format or original/key/FD authority. */
public class Phase3StagingChunkLayoutTest {
    private static final long MAX = 8L * 1024 * 1024 * 1024;
    @Test public void geometryIsExactForEveryFirstAndLastChunkBoundary() {
        long[][] expected = {{1,1,1,17},{65535,1,65535,65551},{65536,1,65536,65552},
                {65537,2,1,65569},{131071,2,65535,131103},{131072,2,65536,131104},
                {MAX-1,131072,65535,8592031743L},{MAX,131072,65536,8592031744L}};
        for (long[] e : expected) {
            StagingChunkLayout layout = new StagingChunkLayout(e[0]);
            assertEquals(e[0],layout.plaintextBytes()); assertEquals(e[1],layout.chunkCount());
            assertEquals(e[2],layout.chunkPlaintextBytes(e[1]-1)); assertEquals(e[3],layout.ciphertextBytes());
            assertEquals((e[1]-1)*65552,layout.chunkCiphertextOffset(e[1]-1));
            assertEquals(e[3],layout.chunkCiphertextOffset(e[1]-1)+layout.chunkPlaintextBytes(e[1]-1)+16);
        }
    }
    @Test public void invalidWholeLengthsDenyIncludingLongOverflowWithoutAllocatingMedia() {
        for (long total : new long[]{Long.MIN_VALUE,-1,0,MAX+1,Long.MAX_VALUE})
            assertThrows(IllegalArgumentException.class,()->new StagingChunkLayout(total));
    }
    @Test public void invalidIndexesDenyEveryGeometryOperationBeforeBufferMutation() {
        StagingChunkLayout layout = new StagingChunkLayout(65537);
        for (long index : new long[]{Long.MIN_VALUE,-1,2,Long.MAX_VALUE}) {
            byte[] nonce=filled(12,7),aad=filled(32,9);
            assertThrows(IllegalArgumentException.class,()->layout.chunkPlaintextBytes(index));
            assertThrows(IllegalArgumentException.class,()->layout.chunkCiphertextOffset(index));
            assertThrows(IllegalArgumentException.class,()->layout.writeFraming(index,nonce,aad));
            assertArrayEquals(filled(12,7),nonce);assertArrayEquals(filled(32,9),aad);
        }
    }
    @Test public void literalFirstLastAndMaximumFrameBytesPinEveryFieldAndEndian() {
        assertFrame(65537,0,"504754310000000000000000","5047544d50303031010001000000000000000000000000000000010000000100");
        assertFrame(65537,1,"504754310100000000000000","5047544d50303031010001000000000001000000000000000100000000000100");
        assertFrame(MAX,131071,"50475431ffff010000000000","5047544d503030310000000002000000ffff0100000000000000010000000100");
    }
    @Test public void everyMaximumIndexHasExactUniqueFixedTwelveByteNonce() {
        StagingChunkLayout layout=new StagingChunkLayout(MAX);byte[] nonce=new byte[12],aad=new byte[32];
        for(long index=0;index<131072;index++) {
            layout.writeFraming(index,nonce,aad);
            assertEquals('P',nonce[0]);assertEquals('G',nonce[1]);assertEquals('T',nonce[2]);assertEquals('1',nonce[3]);
            for(int b=0;b<8;b++)assertEquals((byte)(index >>> (8*b)),nonce[4+b]);
        }
    }
    @Test public void bufferShapeDenialDoesNotPartiallyOverwriteEitherCallerArray() {
        StagingChunkLayout layout=new StagingChunkLayout(1);
        for(int n:new int[]{0,11,13,65552}) {byte[] nonce=filled(n,7),aad=filled(32,9);
            assertThrows(IllegalArgumentException.class,()->layout.writeFraming(0,nonce,aad));
            assertArrayEquals(filled(n,7),nonce);assertArrayEquals(filled(32,9),aad);
        }
        for(int n:new int[]{0,31,33,65552}) {byte[] nonce=filled(12,7),aad=filled(n,9);
            assertThrows(IllegalArgumentException.class,()->layout.writeFraming(0,nonce,aad));
            assertArrayEquals(filled(12,7),nonce);assertArrayEquals(filled(n,9),aad);
        }
        byte[] nonce=filled(12,7),aad=filled(32,9);
        assertThrows(IllegalArgumentException.class,()->layout.writeFraming(0,null,aad));
        assertThrows(IllegalArgumentException.class,()->layout.writeFraming(0,nonce,null));
        assertArrayEquals(filled(12,7),nonce);assertArrayEquals(filled(32,9),aad);
    }
    @Test public void indexTotalAndPartialLengthFramingActuallyAuthenticateWithThePrivatePrimitive() throws Exception {
        for(long total:new long[]{1,65535,65536,65537,MAX}) {
            StagingChunkLayout layout=new StagingChunkLayout(total);long index=layout.chunkCount()-1;
            byte[] key=filled(32,11),nonce=new byte[12],aad=new byte[32];layout.writeFraming(index,nonce,aad);
            byte[] plaintext=filled(layout.chunkPlaintextBytes(index),37),sealed=new byte[plaintext.length+16];
            assertEquals(sealed.length,new FixedStagingChunkCipher().perform(true,key,nonce,aad,plaintext,plaintext.length,sealed));
            byte[] output=filled(65552,9);assertEquals(plaintext.length,new FixedStagingChunkCipher().perform(false,key,nonce,aad,sealed,sealed.length,output));
            assertArrayEquals(plaintext,Arrays.copyOf(output,plaintext.length));
            for(int b=0;b<aad.length;b++) {byte[] wrong=aad.clone();wrong[b]^=1;Arrays.fill(output,(byte)9);
                assertThrows(InvalidCipherTextException.class,()->new FixedStagingChunkCipher().perform(false,key,nonce,wrong,sealed,sealed.length,output));
                assertArrayEquals(new byte[output.length],output);
            }
        }
    }
    @Test public void anotherValidTotalCannotAuthenticateIdenticalFirstFullChunkCiphertext() throws Exception {
        byte[] key=filled(32,11),nonce=new byte[12],aad=new byte[32],plain=filled(65536,37),sealed=new byte[65552];
        new StagingChunkLayout(65537).writeFraming(0,nonce,aad);
        new FixedStagingChunkCipher().perform(true,key,nonce,aad,plain,plain.length,sealed);
        new StagingChunkLayout(65538).writeFraming(0,nonce,aad);byte[] out=filled(65552,9);
        assertThrows(InvalidCipherTextException.class,()->new FixedStagingChunkCipher().perform(false,key,nonce,aad,sealed,sealed.length,out));
        assertArrayEquals(new byte[out.length],out);
    }
    private static void assertFrame(long total,long index,String nonceHex,String aadHex) {
        byte[] nonce=filled(12,7),aad=filled(32,9);new StagingChunkLayout(total).writeFraming(index,nonce,aad);
        assertArrayEquals(hex(nonceHex),nonce);assertArrayEquals(hex(aadHex),aad);
    }
    private static byte[] filled(int n,int value){byte[] b=new byte[n];Arrays.fill(b,(byte)value);return b;}
    private static byte[] hex(String value){byte[] b=new byte[value.length()/2];for(int i=0;i<b.length;i++)b[i]=(byte)Integer.parseInt(value.substring(i*2,i*2+2),16);return b;}
}
