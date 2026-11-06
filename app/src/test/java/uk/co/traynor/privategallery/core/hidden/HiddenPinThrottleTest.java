package uk.co.traynor.privategallery.core.hidden;
import org.junit.Test;
import static org.junit.Assert.*;
public final class HiddenPinThrottleTest {
 @Test public void fifthFailureStartsCooldownAndFailuresRetainUntilSuccess(){
  var state=HiddenPinThrottle.empty();for(int i=0;i<4;i++){state=state.failed(100+i,1000+i,1);assertEquals(0,state.remaining(100+i));}
  state=state.failed(104,1004,1);assertEquals(30000,state.remaining(104));assertEquals(1,state.remaining(30103));assertEquals(0,state.remaining(30104));
  for(long expected:new long[]{60000,120000,240000,300000,300000}){state=state.failed(100,1000,1);assertEquals(expected,state.remaining(100));}
  assertEquals(0,HiddenPinThrottle.empty().failures);
 }
 @Test public void persistedStateRebasesOnceAcrossBootAndBoundsClockRollback(){
  var original=HiddenPinThrottle.recovery(100,1000,3);var restored=HiddenPinThrottle.decode(original.encode());assertEquals(299900,restored.remaining(200));
  var boot=restored.rebase(10,2000,4);assertEquals(299000,boot.remaining(10));assertSame(boot,boot.rebase(11,1,4));
  var rollback=restored.rebase(1,1,4);assertEquals(300000,rollback.remaining(1));assertEquals(0,rollback.remaining(300001));
  assertEquals(0,restored.rebase(1,Long.MAX_VALUE,4).remaining(1));
 }
 @Test public void malformedMetadataDoesNotDecodeAsAnUnthrottledState(){
  assertThrows(IllegalArgumentException.class,()->HiddenPinThrottle.decode(new byte[39]));byte[] bad=HiddenPinThrottle.empty().encode();bad[7]=99;assertThrows(IllegalArgumentException.class,()->HiddenPinThrottle.decode(bad));
 }
}
