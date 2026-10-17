package uk.co.traynor.privategallery.core.hidden;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public final class HiddenRecordChecks {
    public static void main(String[] args) throws Exception {
        File root = Files.createTempDirectory("hidden-records").toFile();
        byte[] key = new byte[32]; Arrays.fill(key,(byte)3);
        byte[] plain = "DISPOSABLE secret filename and synthetic pixels".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        HiddenRecordStore store = new HiddenRecordStore(root);
        String id=UUID.randomUUID().toString();
        try {
            File staged=store.stage("media",id,plain,key);
            if(store.file("media",id).exists()) throw new AssertionError("Premature publication");
            store.promote(staged,"media",id);
            if(!Arrays.equals(plain,store.read("media",id,key))) throw new AssertionError("Roundtrip");
            byte[] disk=Files.readAllBytes(store.file("media",id).toPath());
            if(new String(disk,java.nio.charset.StandardCharsets.ISO_8859_1).contains("DISPOSABLE")) throw new AssertionError("Plaintext on disk");
            fails(()->store.read("media",id,new byte[32]));
            Files.copy(store.file("media",id).toPath(),store.file("thumbnail",id).toPath());
            fails(()->store.read("thumbnail",id,key));
            disk[disk.length-1]^=1;Files.write(store.file("media",id).toPath(),disk);
            fails(()->store.read("media",id,key));
            if(!store.file("media",id).exists()) throw new AssertionError("Corruption deleted evidence");
            fails(()->store.stage("media","../escape",plain,key));
            fails(()->store.stage("thumbnail",UUID.randomUUID().toString(),new byte[1024*1024+1],key));
            File interrupted=store.stage("media",UUID.randomUUID().toString(),plain,key);
            HiddenRecordStore restarted=new HiddenRecordStore(root);restarted.clearStaging();
            if(interrupted.exists())throw new AssertionError("Interrupted ciphertext not cleaned");
            String uncertain=UUID.randomUUID().toString();
            HiddenRecordStore syncFailure=new HiddenRecordStore(root,directory->{throw new IOException("Synthetic sync failure");});
            File prepared=syncFailure.stage("media",uncertain,plain,key);
            fails(()->syncFailure.promote(prepared,"media",uncertain));
            if(!Arrays.equals(plain,store.read("media",uncertain,key)))throw new AssertionError("Uncertain commit lost ciphertext");
            File unavailable=new File(root,"not-a-directory");Files.write(unavailable.toPath(),new byte[]{1});
            fails(()->new HiddenRecordStore(unavailable).stage("media",UUID.randomUUID().toString(),plain,key));
            System.out.println("Hidden encrypted record roundtrip/domain/corruption/bounds/recovery checks passed");
        } finally { for(File f:Objects.requireNonNull(root.listFiles()))f.delete();root.delete(); }
    }
    interface Checked {void run()throws Exception;}
    static void fails(Checked c)throws Exception{try{c.run();}catch(Exception expected){return;}throw new AssertionError("Expected rejection");}
}
