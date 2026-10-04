package uk.co.traynor.privategallery.core.security.staging;

import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import static org.junit.Assert.*;

/** A bounded per-instance framing ledger only; a fresh owned key factory must bind its lifetime. */
public class Phase3StagingSealSequenceTest {
    @Test public void sequentialFullAndPartialClaimsIssueOnlyTheExpectedFrame() {
        StagingSealSequence sequence=new StagingSealSequence(new StagingChunkLayout(65537));
        byte[] nonce=new byte[12],aad=new byte[32],expectedNonce=new byte[12],expectedAad=new byte[32];
        sequence.claim(0,65536,nonce,aad);new StagingChunkLayout(65537).writeFraming(0,expectedNonce,expectedAad);
        assertArrayEquals(expectedNonce,nonce);assertArrayEquals(expectedAad,aad);
        sequence.claim(1,1,nonce,aad);new StagingChunkLayout(65537).writeFraming(1,expectedNonce,expectedAad);
        assertArrayEquals(expectedNonce,nonce);assertArrayEquals(expectedAad,aad);
        assertThrows(IllegalArgumentException.class,()->sequence.claim(2,1,nonce,aad));
    }
    @Test public void successfulClaimCannotRepeatItsNonceOrSkipTheNextIndex() {
        StagingSealSequence sequence=new StagingSealSequence(new StagingChunkLayout(131073));byte[] nonce=new byte[12],aad=new byte[32];
        sequence.claim(0,65536,nonce,aad);byte[] savedNonce=nonce.clone(),savedAad=aad.clone();
        assertThrows(IllegalStateException.class,()->sequence.claim(0,65536,nonce,aad));
        assertThrows(IllegalStateException.class,()->sequence.claim(2,1,nonce,aad));
        assertArrayEquals(savedNonce,nonce);assertArrayEquals(savedAad,aad);
        sequence.claim(1,65536,nonce,aad);sequence.claim(2,1,nonce,aad);
    }
    @Test public void invalidIndexLengthOrShapeDoesNotClaimOrChangeOutput() {
        StagingSealSequence sequence=new StagingSealSequence(new StagingChunkLayout(65537));byte[] nonce=filled(12,7),aad=filled(32,9);
        for(int n:new int[]{-1,0,65535,65537,Integer.MAX_VALUE})
            assertThrows(IllegalArgumentException.class,()->sequence.claim(0,n,nonce,aad));
        assertThrows(IllegalArgumentException.class,()->sequence.claim(-1,65536,nonce,aad));
        assertThrows(IllegalArgumentException.class,()->sequence.claim(0,65536,new byte[11],aad));
        assertThrows(IllegalArgumentException.class,()->sequence.claim(0,65536,nonce,new byte[31]));
        assertArrayEquals(filled(12,7),nonce);assertArrayEquals(filled(32,9),aad);
        sequence.claim(0,65536,nonce,aad);sequence.claim(1,1,nonce,aad);
    }
    @Test public void retiredAfterClaimCannotRetryOrAdvance() {
        StagingSealSequence sequence=new StagingSealSequence(new StagingChunkLayout(65537));byte[] nonce=new byte[12],aad=new byte[32];
        sequence.claim(0,65536,nonce,aad);sequence.retire();sequence.retire();byte[] savedNonce=nonce.clone(),savedAad=aad.clone();
        assertThrows(IllegalStateException.class,()->sequence.claim(0,65536,nonce,aad));
        assertThrows(IllegalStateException.class,()->sequence.claim(1,1,nonce,aad));
        assertArrayEquals(savedNonce,nonce);assertArrayEquals(savedAad,aad);
    }
    @Test public void retirementBeforeFirstClaimDeniesWithoutFramingMutation() {
        StagingSealSequence sequence=new StagingSealSequence(new StagingChunkLayout(1));sequence.retire();
        byte[] nonce=filled(12,7),aad=filled(32,9);assertThrows(IllegalStateException.class,()->sequence.claim(0,1,nonce,aad));
        assertArrayEquals(filled(12,7),nonce);assertArrayEquals(filled(32,9),aad);
    }
    @Test public void racingClaimsHaveOneWinnerAndLeaveLoserArraysUnchanged() throws Exception {
        StagingSealSequence sequence=new StagingSealSequence(new StagingChunkLayout(1));CountDownLatch start=new CountDownLatch(1);
        byte[][] nonces={filled(12,7),filled(12,7)},aads={filled(32,9),filled(32,9)};Throwable[] failures=new Throwable[2];Thread[] threads=new Thread[2];
        for(int i=0;i<2;i++){final int j=i;threads[i]=new Thread(()->{try{assertTrue(start.await(5,TimeUnit.SECONDS));sequence.claim(0,1,nonces[j],aads[j]);}catch(Throwable t){failures[j]=t;}});threads[i].start();}
        start.countDown();for(Thread t:threads){t.join(5000);assertFalse("Framing worker hung",t.isAlive());}
        int wins=0,loses=0;for(int i=0;i<2;i++){if(failures[i]==null){wins++;assertFalse(Arrays.equals(filled(12,7),nonces[i]));}else{loses++;assertTrue(failures[i] instanceof IllegalStateException);assertArrayEquals(filled(12,7),nonces[i]);assertArrayEquals(filled(32,9),aads[i]);}}
        assertEquals(1,wins);assertEquals(1,loses);
    }
    @Test public void everyMaximumFileClaimFitsFixedBuffersWithoutWholeFileAllocation() {
        StagingSealSequence sequence=new StagingSealSequence(new StagingChunkLayout(8L*1024*1024*1024));byte[] nonce=new byte[12],aad=new byte[32];
        for(long index=0;index<131072;index++){sequence.claim(index,65536,nonce,aad);for(int b=0;b<8;b++)assertEquals((byte)(index >>> (8*b)),nonce[4+b]);}
        assertThrows(IllegalStateException.class,()->sequence.claim(131071,65536,nonce,aad));
    }
    private static byte[] filled(int n,int value){byte[] b=new byte[n];Arrays.fill(b,(byte)value);return b;}
}
