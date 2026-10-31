import java.util.concurrent.atomic.AtomicLong;
import uk.co.traynor.privategallery.core.hidden.HiddenSession;

public class Runtime179ReauthOrderProbe {
 static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 static class Fixture implements AutoCloseable {
  final AtomicLong clock=new AtomicLong(1000);
  final HiddenSession hidden=new HiddenSession(clock::get);
  final HiddenSession.Authentication old;
  boolean pending=true;int dismissals,delivered,reads,accesses=1;
  Fixture(){check(hidden.complete(hidden.beginAuthentication(),new byte[32]),"initial auth");old=hidden.authentication();hidden.onBackground(60000);clock.set(61000);}
  void foreground(){hidden.onForeground();check(!hidden.isAuthenticationCurrent(old)&&!hidden.isUnlocked(),"foreground must expire old auth");}
  void newAuthentication(){check(hidden.complete(hidden.beginAuthentication(),new byte[32]),"new auth");check(hidden.isUnlocked()&&!hidden.isAuthenticationCurrent(old),"new identity required");}
  void hostAdmission(){if(!hidden.isAuthenticationCurrent(old)){dismissals++;pending=false;}}
  void callback(){if(pending)delivered++;}
  void requireLiveDispatch(){check(dismissals==0&&pending&&accesses==1&&reads==0,"original pending owner was already dismissed");}
  void requireFinal(){check(dismissals>0&&reads==0&&accesses==1&&!hidden.isAuthenticationCurrent(old)&&hidden.isUnlocked(),"final rejection assertions");}
  public void close(){hidden.lock();}
 }
 public static void main(String[] args){
  try(var f=new Fixture()){
   f.foreground();f.hostAdmission();f.newAuthentication();f.callback();f.requireFinal();
   check(f.delivered==0,"old split should expose missing delivery");
   System.out.println("OLD split Main turns: finalAssertions=true deliveredToPending=0 (vacuous counterexample)");
  }
  try(var f=new Fixture()){
   // Actual test uses the existing ON_START reconciliation followed by the earliest
   // registered ON_RESUME observer; no queued Host admission can split this Main turn.
   f.foreground();f.newAuthentication();f.requireLiveDispatch();f.callback();
   check(f.dismissals==0&&f.delivered==1,"live callback must precede Host rejection");
   f.hostAdmission();f.requireFinal();
   System.out.println("NEW ordered resume turn: preDispatchDismissals=0 deliveredToPending=1 subsequentRejection=true");
  }
  try(var f=new Fixture()){
   f.foreground();f.hostAdmission();f.newAuthentication();boolean rejected=false;
   try{f.requireLiveDispatch();}catch(AssertionError expected){rejected=true;}
   check(rejected&&f.delivered==0,"predismissed owner must fail precondition");
   System.out.println("NEW precondition on predismissed owner: rejected=true callbackNotDispatched=true");
  }
 }
}
