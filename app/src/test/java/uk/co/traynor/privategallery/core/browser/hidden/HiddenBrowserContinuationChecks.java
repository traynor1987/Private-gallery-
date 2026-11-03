package uk.co.traynor.privategallery.core.browser.hidden;
import java.util.concurrent.atomic.AtomicLong;
import uk.co.traynor.privategallery.core.hidden.HiddenSession;
public final class HiddenBrowserContinuationChecks {
 static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
 static HiddenSession session(AtomicLong t){HiddenSession s=new HiddenSession(t::get);check(s.complete(s.beginAuthentication(),new byte[32]),"setup");return s;}
 public static void main(String[] args) {
  AtomicLong t=new AtomicLong(100);HiddenSession s=session(t);Object endpoint=new Object();
  HiddenBrowserContinuation c=new HiddenBrowserContinuation(s,t::get,endpoint,10,20);
  check(!c.owns(new Object(),10,20)&&!c.owns(endpoint,10,21),"foreign child inherited continuation");
  HiddenSession.Authority old=s.authority();
  check(c.suspend(100,30_000,true),"valid grace rejected");s.onBackgroundAt(30_000,100);
  check(!s.isAuthorityCurrent(old)&&c.current(),"retention granted operation authority");
  t.set(1000);check(c.suspend(1000,300_000,true)&&c.stoppedAt()==100,"duplicate suspension extended deadline");
  long a=c.reconnect();check(a>=0,"reconnect refused");
  check(!c.activate(a,false,true)&&!c.activate(a,true,false)&&!c.activate(a-1,true,true),"unverified/stale route admitted");
  s.onForeground();check(c.activate(a,true,true),"verified same authentication resume refused");
  check(!s.isAuthorityCurrent(old)&&s.isAuthorityCurrent(s.authority()),"old authority revived");
  check(c.suspend(1000,30_000,true),"second real background");t.set(31_000);check(!c.current(),"boundary expiry ignored");
  s=session(t);c=new HiddenBrowserContinuation(s,t::get,endpoint,10,20);check(!c.suspend(31_000,0,true),"zero grace retained");
  s=session(t);c=new HiddenBrowserContinuation(s,t::get,endpoint,10,20);check(!c.suspend(31_000,30_000,false),"live routable pin retained");
  s=session(t);c=new HiddenBrowserContinuation(s,t::get,endpoint,10,20);c.suspend(31_000,30_000,true);s.complete(s.beginAuthentication(),new byte[32]);check(!c.current(),"replacement auth inherited old page");
  s=session(t);c=new HiddenBrowserContinuation(s,t::get,endpoint,10,20);t.decrementAndGet();check(!c.current(),"clock rollback admitted");
  s=session(t);c=new HiddenBrowserContinuation(s,t::get,endpoint,10,20);c.terminate();check(c.reconnect()<0,"termination revived");
  System.out.println("Hidden continuation: exact child/auth, no IO capability, grace zero/boundary/repeat, clock, route and attempt fencing passed");
 }
}
