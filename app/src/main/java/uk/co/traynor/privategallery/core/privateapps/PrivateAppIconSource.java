package uk.co.traynor.privategallery.core.privateapps;

import java.io.*;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

/** Selected image bytes stay in memory; neither a URI nor a public copy is retained. */
public final class PrivateAppIconSource {
 public static final int MAX_SOURCE_BYTES=8*1024*1024;
 private PrivateAppIconSource() {}
 private static final class SensitiveBuffer extends ByteArrayOutputStream {
  SensitiveBuffer(){super(MAX_SOURCE_BYTES);}
  @Override public void close(){Arrays.fill(buf,(byte)0);reset();}
 }
 public static byte[] read(InputStream source,BooleanSupplier current) throws IOException {
  byte[] chunk=new byte[16*1024];
  try(InputStream input=source;SensitiveBuffer output=new SensitiveBuffer()) {
   while(true) {
    if(!current.getAsBoolean())throw new IllegalStateException("Icon selection expired");
    int count=input.read(chunk,0,Math.min(chunk.length,MAX_SOURCE_BYTES-output.size()+1));
    if(count<0)break;
    if(count==0)throw new IOException("Image source made no progress");
    if(output.size()+count>MAX_SOURCE_BYTES)throw new IOException("Image exceeds size limit");
    output.write(chunk,0,count);
   }
   if(!current.getAsBoolean())throw new IllegalStateException("Icon selection expired");
   if(output.size()==0)throw new IOException("Empty image");
   return output.toByteArray();
  } finally {Arrays.fill(chunk,(byte)0);}
 }
 /** A result belongs to one editor selection, never to a replacement editor or unlocked session. */
 public static final class Requests implements AutoCloseable {
  private final BooleanSupplier authorized;
  private final AtomicLong generation=new AtomicLong();
  private volatile boolean closed;
  public Requests(BooleanSupplier authorized){this.authorized=authorized;}
  public long begin(){if(closed || !authorized.getAsBoolean())throw new IllegalStateException("Icon selection expired");return generation.incrementAndGet();}
  public boolean current(long ticket){return !closed && generation.get()==ticket && authorized.getAsBoolean();}
  public void cancel(){generation.incrementAndGet();}
  @Override public void close(){closed=true;cancel();}
 }
}
