package uk.co.traynor.privategallery.core.security.staging;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import uk.co.traynor.privategallery.core.security.*;

/** TEST ONLY. Real JVM JNI with linked syscall/region fault delegates and public byte37.
 * Reads known root arrays/metadata, not dead native stack, GC/JIT copies or owner data. */
public final class StagingEntropyFaultHarness {
    static { System.loadLibrary("pg_staging_entropy"); }
    private static native int nativeCalls();
    private static native int copiedBytes();
    private static native boolean nativeEntered();
    private static native void unblock();
    private static Object field(Object value,String name) throws Exception {Field f=value.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(value);}
    private static void require(boolean condition,String publicMessage) {if(!condition)throw new AssertionError(publicMessage);}
    private static Throwable unwrap(Throwable failure) {while(failure instanceof InvocationTargetException || failure instanceof FaultFailure)failure=failure.getCause();return failure;}
    private static final class FaultFailure extends RuntimeException {FaultFailure(Throwable failure){super(failure);}}
    private static OwnedEphemeralStagingKey create(OwnedFactoryScope scope,ScopedIoGuard guard,AtomicReference<Object> original) {
        try {
            original.set(field(scope,"original"));
            Object companion=OwnedEphemeralStagingKey.class.getField("Companion").get(null);
            for(Method method:companion.getClass().getDeclaredMethods())if(method.getName().startsWith("create")&&method.getParameterCount()==4)
                return (OwnedEphemeralStagingKey)method.invoke(companion,scope,"key",guard,new StagingChunkLayout(1));
            throw new AssertionError("Key factory ABI unavailable");
        }catch(Throwable failure){throw new FaultFailure(unwrap(failure));}
    }
    private static OwnedEphemeralStagingKey root(Object original) throws Exception {return (OwnedEphemeralStagingKey)((Object[])field(original,"actualIdentities"))[0];}
    private static boolean all(byte[] values,byte expected) {for(byte b:values)if(b!=expected)return false;return true;}
    private static void await(java.util.function.BooleanSupplier condition) {
        long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        while(!condition.getAsBoolean()){require(System.nanoTime()<until,"Bounded public fixture observation timed out");Thread.yield();}
    }
    public static void main(String[] args) throws Exception {
        String mode=System.getenv("PG_STAGING_ENTROPY_TEST_MODE");require(mode!=null,"Missing isolated native fixture mode");
        PrimarySessionAuthority authority=ReservedAuthorityFixturesKt.testPrimaryAuthority();authority.open(new byte[32]);
        PrimaryOperation owner=authority.operationOrNull(Collections.singleton(PrimaryScope.READ));require(owner!=null,"READ fixture unavailable");
        ScopedIoGuard guard=new ScopedIoGuard(owner,PrimaryScope.READ);AtomicReference<Object> original=new AtomicReference<>();
        ReleasePool pool=(ReleasePool)field(authority,"ioReleasePool");
        try {
            if(mode.equals("blocked")) {
                AtomicReference<Throwable> failure=new AtomicReference<>();
                Thread producer=new Thread(()->{try{guard.createOwned(OwnedResourceManifest.Companion.io("key","unused-plain","unused-cipher"),scope->create(scope,guard,original));failure.set(new AssertionError("Revoked native constructor succeeded"));}catch(Throwable t){failure.set(unwrap(t));}},"public-held-native-root");
                producer.start();
                try {
                    await(StagingEntropyFaultHarness::nativeEntered);
                    OwnedEphemeralStagingKey key=root(original.get());byte[] known=(byte[])field(key,"keyBytes");
                    require(all(known,(byte)37),"Public Java target not copied before actual Native hold");
                    CompletionStage<kotlin.Unit> stage=key.closeAcknowledged();require(!stage.toCompletableFuture().isDone(),"Actual native root acknowledged early");
                    require(stage.toCompletableFuture().complete(kotlin.Unit.INSTANCE),"Snapshot public completion unavailable");
                    original.get().getClass().getMethod("release").invoke(original.get());authority.revoke();
                    Object ticket=((List<?>)field(original.get(),"tickets")).get(0);Object ticketGate=field(ticket,"gate");
                    await(()->{try{synchronized(ticketGate){return (Boolean)field(ticket,"invocationReturned");}}catch(Exception e){throw new FaultFailure(e);}});
                    require(pool.getOccupied()==3,"Constructor physical slots returned before actual JNI return");
                    require(!stage.toCompletableFuture().isDone(),"Snapshot completed actual private root cell");
                    require(all(known,(byte)37),"Known Java target wiped during actual native borrow");
                    require(!authority.getCleanupComplete(),"Held actual JNI allowed fresh auth");
                    boolean denied=false;try{authority.open(new byte[32]);}catch(IllegalStateException expected){denied=true;}require(denied,"Held actual JNI fresh auth was admitted");
                }finally{unblock();producer.join(5000);require(!producer.isAlive(),"Public native producer did not return");}
                require(failure.get() instanceof IllegalStateException,"Actual post-JNI revoked constructor did not fail closed");
            }else if(mode.equals("success")) {
                OwnedResource<OwnedEphemeralStagingKey> owned=guard.createOwned(OwnedResourceManifest.Companion.io("key"),scope->create(scope,guard,original));
                try{require(all((byte[])field(owned.getValue(),"keyBytes"),(byte)37),"Public successful target copy unavailable");require((Boolean)field(owned.getValue(),"ready"),"Public success root not ready");}finally{owned.close();}
            }else {
                Throwable failure=null;
                try{guard.createOwned(OwnedResourceManifest.Companion.io("key","unused-plain","unused-cipher"),scope->create(scope,guard,original));}catch(Throwable t){failure=unwrap(t);}
                require(mode.equals("partial")?failure instanceof java.io.IOException:failure instanceof IllegalStateException,"Actual native fault was not propagated with original kind");
            }
            require(original.get()!=null,"Original absent before JNI");
            RetirementAcknowledgement retirement=(RetirementAcknowledgement)field(original.get(),"terminalRetirement");require(retirement.await(5,TimeUnit.SECONDS),"Actual original terminal/slot return absent");
            OwnedEphemeralStagingKey key=root(original.get());require(key!=null,"Actual root not attached before JNI");require(all((byte[])field(key,"keyBytes"),(byte)0),"Known Java key not wiped after actual JNI return");
            require((Boolean)field(key,"retired") && !(Boolean)field(key,"ready") && field(key,"activeThread")==null,"Root family metadata not retired after actual return");
            require(pool.getOccupied()==0,"Original physical slots not returned");require(nativeCalls()==1,"Native syscall invocation retried");
            require(copiedBytes()==(mode.equals("partial")?7:(mode.equals("success")||mode.equals("blocked"))?32:0),"Public JNI copy length audit mismatch");
            try{Method generate=key.getClass().getDeclaredMethod("generate");generate.setAccessible(true);generate.invoke(key);throw new AssertionError("Retired key generation was reused");}catch(InvocationTargetException expected){require(expected.getCause() instanceof IllegalStateException,"Retired generation wrong failure");}
            require(nativeCalls()==1,"Retired family reached JNI again");
            System.out.println("{\"mode\":\""+mode+"\",\"nativeCalls\":1,\"publicCopiedBytes\":"+copiedBytes()+",\"knownKeyWipedAfterReturn\":true,\"physicalSlots\":0,\"passed\":true}");
        }finally{owner.close();authority.revoke();}
    }
}
