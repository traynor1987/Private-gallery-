import java.lang.reflect.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import kotlin.Unit;
import kotlin.jvm.functions.*;
import org.junit.runner.*;
import uk.co.traynor.privategallery.core.security.*;

public class ActivityCleanupDiagnostic {
  public static void main(String[] args) throws Exception {
    ReleasePool pool=ProcessReleaseCapacity.INSTANCE.getPrimaryIo();
    Field allocation=ReleasePool.class.getDeclaredField("allocateInvocation");allocation.setAccessible(true);
    Object before=allocation.get(pool);
    CountDownLatch workerEntered=new CountDownLatch(1),allow=new CountDownLatch(1);
    Function2<Function0<Unit>,Function0<Unit>,ReleaseInvocation> hook=(action,returned)->new ReleaseInvocation(()->{
      workerEntered.countDown();
      try { if(!allow.await(5,TimeUnit.SECONDS))throw new AssertionError("diagnostic release not allowed"); }
      catch(InterruptedException e){throw new RuntimeException(e);}
      return action.invoke();
    },returned);
    allocation.set(pool,hook);
    Class<?> cls=Class.forName("uk.co.traynor.privategallery.MainActivitySessionTest");
    String prior="Activity jobs register before launch and close never-started key leases";
    String next="Activity teardown callback in cancelled scope discards work and closes its key lease";
    AtomicReference<Result> first=new AtomicReference<>();
    Thread caller=new Thread(()->first.set(new JUnitCore().run(Request.method(cls,prior))));
    try {
      caller.start();if(!workerEntered.await(5,TimeUnit.SECONDS))throw new AssertionError("no actual Job release invocation");
      caller.join(300);
      boolean early=!caller.isAlive();
      System.out.println("Prior fixture returned before actual release: "+early);
      System.out.println("Shared process retirement still pending: "+pool.getHasUnacknowledgedRetirement());
      if(early){
        System.out.println("Prior fixture failures: "+first.get().getFailureCount());
        Result after=new JUnitCore().run(Request.method(cls,next));
        System.out.println("Next fixture before release: "+after.getRunCount()+" tests, "+after.getFailureCount()+" failures");
        after.getFailures().forEach(f->System.out.println(f.getTrace()));
      }
      allow.countDown();caller.join(5000);if(caller.isAlive())throw new AssertionError("prior fixture never returned");
      long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
      while(pool.getHasUnacknowledgedRetirement()&&System.nanoTime()<deadline)Thread.yield();
      if(pool.getHasUnacknowledgedRetirement())throw new AssertionError("actual release failed to retire");
      Result after=new JUnitCore().run(Request.method(cls,next));
      System.out.println("Prior final failures: "+first.get().getFailureCount());
      System.out.println("Next fixture after actual release: "+after.getRunCount()+" tests, "+after.getFailureCount()+" failures");
      if(first.get().getFailureCount()!=0||after.getFailureCount()!=0)throw new AssertionError("post-release fixture failed");
    }finally{allow.countDown();caller.join(5000);allocation.set(pool,before);}
  }
}
