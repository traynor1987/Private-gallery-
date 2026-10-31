package uk.co.traynor.privategallery.core.evidence;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Bounded owned plaintext; replaced allocations and the final allocation are wiped. */
final class EvidenceWipingBuffer extends ByteArrayOutputStream {
 private final int maximum;
 private final BooleanSupplier current;
 private final Consumer<byte[]> allocation;
 private boolean closed;
 EvidenceWipingBuffer(int maximum,BooleanSupplier current){this(maximum,current,ignored->{});}
 // Package-visible allocation observer is for deterministic ownership tests, never publication.
 EvidenceWipingBuffer(int maximum,BooleanSupplier current,Consumer<byte[]> allocation){
  super(Math.min(32,maximum));if(maximum<1)throw new IllegalArgumentException("Invalid buffer bound");
  this.maximum=maximum;this.current=Objects.requireNonNull(current);this.allocation=Objects.requireNonNull(allocation);allocation.accept(buf);
 }
 private void capacity(int length){
  if(closed||!current.getAsBoolean())throw new IllegalStateException("Evidence buffer retired");
  if(length<0||count>maximum-length)throw new IllegalArgumentException("Evidence buffer quota exceeded");
  int required=count+length;if(required<=buf.length)return;
  byte[] previous=buf;byte[] grown=new byte[Math.max(required,(int)Math.min(maximum,(long)buf.length*2))];
  try{System.arraycopy(previous,0,grown,0,count);}catch(RuntimeException|Error failure){Arrays.fill(grown,(byte)0);throw failure;}
  finally{Arrays.fill(previous,(byte)0);}
  buf=grown;allocation.accept(grown);
 }
 @Override public synchronized void write(int value){capacity(1);buf[count++]=(byte)value;}
 @Override public synchronized void write(byte[] bytes,int offset,int length){Objects.checkFromIndexSize(offset,length,bytes.length);capacity(length);System.arraycopy(bytes,offset,buf,count,length);count+=length;}
 @Override public synchronized byte[] toByteArray(){if(closed||!current.getAsBoolean())throw new IllegalStateException("Evidence buffer retired");return Arrays.copyOf(buf,count);}
 @Override public synchronized void close(){Arrays.fill(buf,(byte)0);count=0;closed=true;}
}
