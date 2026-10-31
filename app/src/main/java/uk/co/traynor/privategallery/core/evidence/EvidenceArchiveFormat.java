package uk.co.traynor.privategallery.core.evidence;

import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.zip.*;

/** Strict, bounded portable container/ZIP grammar. Never writes plaintext files. */
final class EvidenceArchiveFormat {
 static final long MAX_EXPANDED=2L*1024*1024*1024, MAX_CIPHER=MAX_EXPANDED+32L*1024*1024;
 static final int HEADER=52, META=4*1024*1024, REPORT=20*1024*1024, TRANSCRIPT=2*1024*1024;
 private static final byte[] PREFIX={80,71,69,86,73,68,49,55,0,0,0,1,0,0,(byte)128,0,0,0,0,8,0,0,0,1};
 static byte[] header(byte[] salt,byte[] nonce){if(salt.length!=16||nonce.length!=12)throw new IllegalArgumentException();byte[] h=new byte[HEADER];System.arraycopy(PREFIX,0,h,0,24);System.arraycopy(salt,0,h,24,16);System.arraycopy(nonce,0,h,40,12);return h;}
 static void validateHeader(byte[] h)throws IOException{if(h.length!=HEADER)throw new IOException("Invalid evidence container");for(int i=0;i<PREFIX.length;i++)if(h[i]!=PREFIX[i])throw new IOException("Unsupported evidence container");}
 static byte[] password(char[] value)throws IOException{try{ByteBuffer b=StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(value));byte[] out=new byte[b.remaining()];b.get(out);if(b.hasArray())Arrays.fill(b.array(),(byte)0);return out;}catch(CharacterCodingException bad){throw new IOException("Invalid passphrase encoding");}}
 interface EntryConsumer {void read(String name,InputStream input)throws IOException;}
 private record Entry(String name,int flags,int method,long crc,long packed,long size,long offset){}
 private static IOException bad(){return new IOException("Invalid or over-limit evidence ZIP");}
 static void check(BooleanSupplier current)throws IOException{if(!current.getAsBoolean())throw new IOException("Evidence authority retired");}
 static void readZip(InputStream input,BooleanSupplier current,EntryConsumer consume)throws IOException {
  Cursor in=new Cursor(input,current);List<Entry> entries=new ArrayList<>();Set<String> names=new HashSet<>();long expanded=0;long sig=in.u32();
  while(sig==0x04034b50L){
   if(entries.size()>=256)throw bad();long offset=in.position-4;int version=in.u16(),flags=in.u16(),method=in.u16();in.u16();in.u16();long crc=in.u32(),packed=in.u32(),size=in.u32();int nameLen=in.u16(),extra=in.u16();
   if(version>20||(flags&~0x808)!=0||(method!=0&&method!=8)||method==0&&(flags&8)!=0||extra!=0)throw bad();String name=in.name(nameLen);if(!names.add(name))throw bad();
   if((flags&8)!=0&&(crc!=0||packed!=0||size!=0))throw bad();
   Body body=new Body(in,method,packed,MAX_EXPANDED-expanded);
   byte[] discard=new byte[32768];try{consume.read(name,body);while(body.read(discard)!=-1){}}finally{Arrays.fill(discard,(byte)0);body.release();}
   if((flags&8)!=0){if(in.u32()!=0x08074b50L)throw bad();crc=in.u32();packed=in.u32();size=in.u32();}
   if(crc!=body.crc.getValue()||packed!=body.packed||size!=body.size)throw bad();expanded+=size;if(expanded>MAX_EXPANDED)throw bad();entries.add(new Entry(name,flags,method,crc,packed,size,offset));sig=in.u32();
  }
  long centralStart=in.position-4;int index=0;
  while(sig==0x02014b50L){
   if(index>=entries.size())throw bad();Entry e=entries.get(index++);int made=in.u16(),needed=in.u16(),flags=in.u16(),method=in.u16();in.u16();in.u16();long crc=in.u32(),packed=in.u32(),size=in.u32();int n=in.u16(),extra=in.u16(),comment=in.u16(),disk=in.u16(),internal=in.u16();long attrs=in.u32(),offset=in.u32();String name=in.name(n);
   int mode=(int)(attrs>>>16),type=mode&0170000;
   if(needed>20||extra!=0||comment!=0||disk!=0||internal>1||(attrs&16)!=0||(type!=0&&type!=0100000)||!name.equals(e.name)||flags!=e.flags||method!=e.method||crc!=e.crc||packed!=e.packed||size!=e.size||offset!=e.offset)throw bad();sig=in.u32();
  }
  long centralSize=in.position-4-centralStart;
  if(sig!=0x06054b50L||in.u16()!=0||in.u16()!=0||in.u16()!=entries.size()||in.u16()!=entries.size()||index!=entries.size()||in.u32()!=centralSize||in.u32()!=centralStart||in.u16()!=0||in.read()!=-1)throw bad();check(current);
 }
 private static final class Cursor extends PushbackInputStream {
  long position;final BooleanSupplier current;
  Cursor(InputStream input,BooleanSupplier current){super(input,32768);this.current=current;}
  @Override public int read()throws IOException{check(current);int b=super.read();if(b!=-1)position++;if(position>MAX_CIPHER)throw bad();return b;}
  @Override public int read(byte[] b,int off,int len)throws IOException{check(current);int n=super.read(b,off,len);if(n>0)position+=n;if(position>MAX_CIPHER)throw bad();return n;}
  void back(byte[] b,int off,int len)throws IOException{super.unread(b,off,len);position-=len;}
  int u16()throws IOException{return byteValue()|(byteValue()<<8);}
  long u32()throws IOException{return Integer.toUnsignedLong(u16()|(u16()<<16));}
  int byteValue()throws IOException{int b=read();if(b<0)throw bad();return b;}
  String name(int n)throws IOException{if(n<1||n>200)throw bad();byte[] b=new byte[n];for(int i=0;i<n;i++)b[i]=(byte)byteValue();String name;try{name=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(b)).toString();}catch(CharacterCodingException invalid){throw bad();}if(!name.matches("[A-Za-z0-9._/-]+")||name.startsWith("/")||name.endsWith("/")||Arrays.stream(name.split("/",-1)).anyMatch(p->p.isEmpty()||p.equals(".")||p.equals("..")))throw bad();return name;}
 }
 private static final class Body extends InputStream {
  final Cursor input;final Inflater inflater;final CRC32 crc=new CRC32();final byte[] compressed=new byte[32768];final long maximum;long packed,size,remaining;int fed;boolean ended;
  Body(Cursor input,int method,long packed,long max)throws IOException{this.input=input;inflater=method==8?new Inflater(true):null;remaining=packed;maximum=max;if(packed>MAX_EXPANDED)throw bad();}
  @Override public int read()throws IOException{byte[] b=new byte[1];return read(b,0,1)==-1?-1:b[0]&255;}
  @Override public int read(byte[] b,int off,int len)throws IOException{
   if(len==0)return 0;check(input.current);if(ended)return -1;int n;
   if(inflater==null){if(remaining==0){ended=true;return -1;}n=input.read(b,off,(int)Math.min(len,remaining));if(n<0)throw bad();remaining-=n;packed+=n;}
   else {while(true){try{n=inflater.inflate(b,off,len);}catch(DataFormatException invalid){throw bad();}if(n>0)break;if(inflater.finished()){int left=inflater.getRemaining();if(left>0)input.back(compressed,fed-left,left);packed-=left;ended=true;return -1;}if(inflater.needsDictionary())throw bad();if(!inflater.needsInput())throw bad();fed=input.read(compressed);if(fed<1)throw bad();packed+=fed;inflater.setInput(compressed,0,fed);}}
   size+=n;if(size>maximum)throw bad();crc.update(b,off,n);return n;
  }
  void release(){if(inflater!=null)inflater.end();Arrays.fill(compressed,(byte)0);}
  @Override public void close(){} // Entry consumers cannot close the authenticated outer stream.
 }
}
