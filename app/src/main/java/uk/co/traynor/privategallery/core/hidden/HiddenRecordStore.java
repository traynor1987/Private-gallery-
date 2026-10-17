package uk.co.traynor.privategallery.core.hidden;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** Hidden v1 only. No Primary format, URI, cache, backup or export dependency. */
public final class HiddenRecordStore {
    public static final int MAX_MEDIA_BYTES = 16 * 1024 * 1024;
    private static final byte[] HEADER = {'P','G','H','V',1};
    private final File root;
    public interface DirectorySync { void sync(File directory) throws IOException; }
    private final DirectorySync directorySync;
    public HiddenRecordStore(File root) { this(root, directory -> {
        try(java.nio.channels.FileChannel channel=java.nio.channels.FileChannel.open(directory.toPath(),java.nio.file.StandardOpenOption.READ)) {channel.force(true);}
    }); }
    public HiddenRecordStore(File root,DirectorySync directorySync) { this.root=root;this.directorySync=directorySync; }

    public File file(String kind,String id) {
        limit(kind);
        if(!id.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) throw new IllegalArgumentException("Invalid Hidden id");
        return new File(root,kind+"-"+id+".enc");
    }
    public File stage(String kind,String id,byte[] plaintext,byte[] key)throws Exception {
        file(kind,id);
        if(plaintext.length>limit(kind))throw new IOException("Hidden record exceeds limit");
        if(!root.isDirectory()) {
            if(!root.mkdirs())throw new IOException("Hidden storage unavailable");
        }
        // Also retry a parent sync that failed after an earlier mkdir succeeded.
        directorySync.sync(root.getParentFile());
        File temporary=File.createTempFile("staged-",".ciphertext",root);
        byte[] nonce=new byte[12];new SecureRandom().nextBytes(nonce);
        byte[] sealed=null, verified=null;
        try {
            sealed=cipher(Cipher.ENCRYPT_MODE,key,nonce,kind,id).doFinal(plaintext);
            try(FileOutputStream output=new FileOutputStream(temporary)) {
                output.write(HEADER);output.write(nonce);output.write(sealed);output.getFD().sync();
            }
            verified=readFile(temporary,kind,id,key);
            if(!MessageDigest.isEqual(plaintext,verified))throw new IOException("Hidden verification failed");
            return temporary;
        } catch(Exception|Error failure) {temporary.delete();throw failure;}
        finally {if(sealed!=null)Arrays.fill(sealed,(byte)0);if(verified!=null)Arrays.fill(verified,(byte)0);}
    }
    /** Invoke only inside the authenticated session's short commit gate. */
    public void promote(File temporary,String kind,String id)throws IOException {
        File destination=file(kind,id);
        if(!temporary.getCanonicalFile().getParentFile().equals(root.getCanonicalFile()) || !temporary.getName().startsWith("staged-"))throw new IOException("Invalid staging path");
        Files.move(temporary.toPath(),destination.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
    }
    /** Call after promotion, outside the session monitor, before publishing dependent metadata. */
    public void syncPromotions() throws IOException { directorySync.sync(root); }
    public byte[] read(String kind,String id,byte[] key)throws Exception{return readFile(file(kind,id),kind,id,key);}
    private byte[] readFile(File source,String kind,String id,byte[] key)throws Exception {
        long size=source.length();
        if(size<HEADER.length+12+16 || size>limit(kind)+(long)HEADER.length+12+16)throw new IOException("Invalid Hidden record size");
        byte[] disk=new byte[(int)size];
        try {
            try(DataInputStream input=new DataInputStream(new FileInputStream(source))){input.readFully(disk);if(input.read()!=-1)throw new IOException("Hidden record changed");}
            for(int i=0;i<HEADER.length;i++)if(disk[i]!=HEADER[i])throw new IOException("Unsupported Hidden record");
            return cipher(Cipher.DECRYPT_MODE,key,Arrays.copyOfRange(disk,HEADER.length,HEADER.length+12),kind,id).doFinal(disk,HEADER.length+12,disk.length-HEADER.length-12);
        } finally {Arrays.fill(disk,(byte)0);}
    }
    public void clearStaging()throws IOException {
        File[] files=root.listFiles();if(files==null)return;
        for(File f:files)if(f.getName().startsWith("staged-") && !f.delete())throw new IOException("Hidden staging cleanup failed");
    }
    private static int limit(String kind) {
        switch(kind){case "media":return MAX_MEDIA_BYTES;case "thumbnail":return 1024*1024;case "metadata":return 4*1024*1024;default:throw new IllegalArgumentException("Invalid Hidden record kind");}
    }
    private static Cipher cipher(int mode,byte[] key,byte[] nonce,String kind,String id)throws Exception {
        if(key.length!=32)throw new IllegalArgumentException("Invalid Hidden key");
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,nonce));
        cipher.updateAAD(HEADER);
        cipher.updateAAD(("private-gallery:hidden:v1:"+kind+":"+id).getBytes(StandardCharsets.UTF_8));
        return cipher;
    }
}
