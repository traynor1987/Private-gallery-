package uk.co.traynor.privategallery.core.hidden;

import java.io.File;
import java.nio.file.Files;
import java.util.UUID;

/** Real storage callback checks that durability IO cannot retain revocation's monitor. */
public final class HiddenSyncChecks {
    public static void main(String[] args) throws Exception {
        File root=Files.createTempDirectory("hidden-sync").toFile();
        HiddenSession session=new HiddenSession(()->1);
        session.complete(session.beginAuthentication(),new byte[32]);
        int[] syncs={0};
        HiddenRecordStore records=new HiddenRecordStore(root,directory->{
            if(directory.equals(root)) {
                if(Thread.holdsLock(session))throw new AssertionError("Directory sync blocks session revocation");
                syncs[0]++;
                Thread revoker=new Thread(session::lock);
                revoker.start();
                try {revoker.join(1000);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new java.io.IOException(e);}
                if(revoker.isAlive())throw new AssertionError("Storage sync delayed session lock");
            }
        });
        String id=UUID.randomUUID().toString();
        try(HiddenSession.Operation op=session.beginOperation()) {
            File prepared=records.stage("media",id,new byte[]{1,2,3},op.key());
            session.commit(op,()->{try{records.promote(prepared,"media",id);}catch(java.io.IOException e){throw new java.io.UncheckedIOException(e);}});
            records.syncPromotions();
            if(syncs[0]!=1)throw new AssertionError("Missing directory durability sync");
            boolean rejected=false;
            try {session.commit(op,()->{throw new AssertionError("Stale metadata promotion ran");});}
            catch(IllegalStateException expected){rejected=true;}
            if(!rejected)throw new AssertionError("Stale metadata promotion accepted");
            if(session.isCurrent(op))throw new AssertionError("Revocation failed");
            System.out.println("Hidden directory durability runs outside session revocation monitor");
        } finally {session.lock();for(File f:root.listFiles())f.delete();root.delete();}
    }
}
