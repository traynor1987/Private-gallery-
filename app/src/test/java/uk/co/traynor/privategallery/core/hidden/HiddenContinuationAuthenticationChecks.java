package uk.co.traynor.privategallery.core.hidden;
import java.util.concurrent.atomic.*;
/** The browser may retain a denied presentation, never an operation capability. */
public final class HiddenContinuationAuthenticationChecks {
 public static void main(String[] args) throws Exception {
  AtomicLong clock=new AtomicLong(100);HiddenSession s=new HiddenSession(clock::get);
  s.complete(s.beginAuthentication(),new byte[32]);HiddenSession.Authentication auth=s.authentication();
  AtomicInteger hard=new AtomicInteger(),ops=new AtomicInteger();
  java.lang.reflect.Method register;
  try {register=HiddenSession.class.getMethod("onAuthenticationRevoked",HiddenSession.Authentication.class,Runnable.class);}
  catch(NoSuchMethodException e){throw new AssertionError("Missing independent hard-revocation signal for denied continuation",e);}
  register.invoke(s,auth,(Runnable)hard::incrementAndGet);s.onRevoked(s.authority(),ops::incrementAndGet);
  s.onBackground(30_000);if(hard.get()!=0||ops.get()!=1)throw new AssertionError("Background must revoke only operation authority during grace");
  clock.set(30_100);if(s.isUnlocked()||hard.get()!=1)throw new AssertionError("Expiry must synchronously hard revoke retained continuation");
  s.onForeground();s.complete(s.beginAuthentication(),new byte[32]);register.invoke(s,auth,(Runnable)hard::incrementAndGet);
  if(hard.get()!=2)throw new AssertionError("Old auth registration inherited replacement authentication");
  HiddenSession.Authentication replacement=s.authentication();register.invoke(s,replacement,(Runnable)hard::incrementAndGet);
  s.lock();if(hard.get()!=3)throw new AssertionError("Explicit lock must synchronously revoke presentation");
  System.out.println("Hidden continuation authentication: operation/hard revocation separation, expiry and replacement passed");
 }
}
