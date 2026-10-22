package uk.co.traynor.privategallery.core.hidden;
import java.io.*;
import java.nio.file.Files;
import java.util.Arrays;
public final class HiddenChunkChecks {
 public static void main(String[] args)throws Exception {
  File root=Files.createTempDirectory("hidden-chunks").toFile(); byte[] key=new byte[32]; Arrays.fill(key,(byte)7);
  String id="12345678-1234-1234-1234-123456789abc";
  try {
   for(int mib:new int[]{20,100,500}) {
    long length=mib*1024L*1024+17;
    InputStream source=new InputStream(){long at;public int read(){return at<length?(int)(at++%251):-1;}
     public int read(byte[] b,int off,int n){if(at>=length)return -1;int count=(int)Math.min(n,length-at);for(int i=0;i<count;i++)b[off+i]=(byte)(at++%251);return count;}};
    HiddenChunkStore.Staged stored=HiddenChunkStore.stage(root,id,source,key,()->{});
    try(HiddenChunkStore.Reader r=new HiddenChunkStore.Reader(stored.file,id,stored.size,stored.hash,stored.nonce,key,()->{})) {
     r.verify();byte[] b=new byte[8192];
     for(long offset:new long[]{0,1024*1024-7,length-9000}){r.readAt(offset,b,0,b.length);for(int i=0;i<b.length;i++)if(b[i]!=(byte)((offset+i)%251))throw new AssertionError("seek");}
    }
    try(RandomAccessFile f=new RandomAccessFile(stored.file,"rw")){f.seek(HiddenChunkStore.HEADER_BYTES+30);int original=f.read();f.seek(HiddenChunkStore.HEADER_BYTES+30);f.write(original^1);}
    boolean rejected=false;try(HiddenChunkStore.Reader r=new HiddenChunkStore.Reader(stored.file,id,stored.size,stored.hash,stored.nonce,key,()->{})){r.verify();}catch(Exception expected){rejected=true;}
    if(!rejected)throw new AssertionError("tampering accepted");stored.file.delete();System.out.println("PASS Hidden authenticated stream/seek/tamper: "+mib+" MiB");
   }
   HiddenChunkStore.Staged small=HiddenChunkStore.stage(root,id,new ByteArrayInputStream(new byte[1024*1024+1]),key,()->{});
   for(int mode=0;mode<4;mode++) {
    boolean rejected=false;byte[] badKey=key.clone(),badNonce=small.nonce.clone(),badHash=small.hash.clone();String useId=id;
    if(mode==0)badKey[0]^=1;if(mode==1)badNonce[0]^=1;if(mode==2)useId="different-id";if(mode==3)badHash[0]^=1;
    try(HiddenChunkStore.Reader r=new HiddenChunkStore.Reader(small.file,useId,small.size,badHash,badNonce,badKey,()->{})){r.verify();}catch(Exception expected){rejected=true;}
    if(!rejected)throw new AssertionError("wrong authority accepted");
   }
   int[] reads={0};byte[] output=new byte[128];Arrays.fill(output,(byte)5);boolean revoked=false;
   try(HiddenChunkStore.Reader r=new HiddenChunkStore.Reader(small.file,id,small.size,small.hash,small.nonce,key,()->{if(++reads[0]>=5)throw new IOException("revoked");})){r.readAt(0,output,0,output.length);}catch(IOException expected){revoked=true;}
   if(!revoked || !Arrays.equals(output,new byte[128]))throw new AssertionError("revoked output not wiped");
   try(RandomAccessFile f=new RandomAccessFile(small.file,"rw")){f.setLength(f.length()-1);}
   boolean truncated=false;try(HiddenChunkStore.Reader r=new HiddenChunkStore.Reader(small.file,id,small.size,small.hash,small.nonce,key,()->{})){r.verify();}catch(Exception expected){truncated=true;}
   if(!truncated)throw new AssertionError("truncation accepted");small.file.delete();
   File full=new File(root.getPath()) {private static final long serialVersionUID=1L;@Override public long getUsableSpace(){return 0;}};
   boolean noSpace=false;try{HiddenChunkStore.stage(full,id,new ByteArrayInputStream(new byte[1]),key,()->{});}catch(IOException expected){noSpace=true;}
   if(!noSpace || root.listFiles().length!=0)throw new AssertionError("low-space staging published");
   System.out.println("PASS wrong key/id/nonce/hash, truncation, revoked-buffer wiping and low storage");
   int[] checks={0};boolean cancelled=false;try{HiddenChunkStore.stage(root,id,new ByteArrayInputStream(new byte[2*1024*1024]),key,()->{if(++checks[0]>2)throw new IOException("cancelled");});}catch(IOException expected){cancelled=true;}
   if(!cancelled || root.listFiles().length!=0)throw new AssertionError("partial staging retained");
  }finally{for(File f:root.listFiles())f.delete();root.delete();Arrays.fill(key,(byte)0);}
 }
}
