package uk.co.traynor.privategallery.core.media;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.CRC32;
/** Preserve compressed pixels, palette/transparency and bounded colour scalars. Remove all
 * text/XMP/EXIF/ICC/application chunks; rebuild only orientation/optional-date EXIF.
 * Animated PNG is rejected rather than silently exporting a different animation. */
public final class PngPrivacy {
 private static final byte[] SIGNATURE={(byte)137,80,78,71,13,10,26,10};
 private PngPrivacy(){}
 public static byte[] clean(byte[] source,int orientation,String date)throws IOException {
  if(source.length<20||!Arrays.equals(Arrays.copyOf(source,8),SIGNATURE))throw new IOException("Not PNG");
  if(orientation<1||orientation>8)throw new IOException("Invalid orientation");
  if(date!=null&&!date.matches("[0-9]{4}:[0-9]{2}:[0-9]{2} [0-9]{2}:[0-9]{2}:[0-9]{2}"))throw new IOException("Invalid date");
  ByteArrayOutputStream out=new ByteArrayOutputStream(source.length);out.write(SIGNATURE);
  int pos=8;boolean header=false,pixels=false,endedPixels=false,transparency=false;int colour=-1,depth=0,palette=0;
  while(pos<source.length) {
   if(source.length-pos<12)throw new IOException("Truncated PNG");
   long length=((long)(source[pos]&255)<<24)|((long)(source[pos+1]&255)<<16)|((long)(source[pos+2]&255)<<8)|(source[pos+3]&255);
   if(length>source.length-pos-12)throw new IOException("Invalid PNG length");
   int size=(int)length;String type=new String(source,pos+4,4,StandardCharsets.US_ASCII);
   CRC32 crc=new CRC32();crc.update(source,pos+4,size+4);
   int tail=pos+8+size;long expected=((long)(source[tail]&255)<<24)|((long)(source[tail+1]&255)<<16)|((long)(source[tail+2]&255)<<8)|(source[tail+3]&255);
   if(crc.getValue()!=expected)throw new IOException("PNG CRC mismatch");
   if(!header) {
    if(!type.equals("IHDR")||size!=13)throw new IOException("Missing PNG header");
    depth=source[pos+16]&255;colour=source[pos+17]&255;
    boolean bits=(colour==0&&(depth==1||depth==2||depth==4||depth==8||depth==16))||((colour==2||colour==4||colour==6)&&(depth==8||depth==16))||(colour==3&&(depth==1||depth==2||depth==4||depth==8));
    if(!bits||source[pos+18]!=0||source[pos+19]!=0||(source[pos+20]!=0&&source[pos+20]!=1))throw new IOException("Unsupported PNG header");
    long width=((long)(source[pos+8]&255)<<24)|((long)(source[pos+9]&255)<<16)|((long)(source[pos+10]&255)<<8)|(source[pos+11]&255);
    long height=((long)(source[pos+12]&255)<<24)|((long)(source[pos+13]&255)<<16)|((long)(source[pos+14]&255)<<8)|(source[pos+15]&255);
    if(width<1||height<1||width>2147483647L||height>2147483647L)throw new IOException("Invalid PNG dimensions");
    header=true;out.write(source,pos,size+12);
    if(orientation!=1||date!=null)writeChunk(out,"eXIf",Arrays.copyOfRange(JpegPrivacy.exif(orientation,date),6,date==null?32:114));
   } else {
    if(type.equals("IHDR")||type.equals("acTL")||type.equals("fcTL")||type.equals("fdAT"))throw new IOException("Unsupported PNG structure/animation");
    if(type.equals("IDAT")){if(colour==3&&palette==0)throw new IOException("Missing PNG palette");if(endedPixels)throw new IOException("Nonconsecutive image data");pixels=true;out.write(source,pos,size+12);}
    else {
     if(pixels)endedPixels=true;
     if(type.equals("IEND")) {
      if(size!=0||!pixels||tail+4!=source.length)throw new IOException("Invalid PNG ending");
      out.write(source,pos,size+12);return out.toByteArray();
     }
     if(type.equals("PLTE")||type.equals("tRNS")) {
      if(pixels)throw new IOException("Late PNG palette/transparency");
      if(type.equals("PLTE")) {
       if(palette!=0||transparency||colour==0||colour==4||size<3||size>768||size%3!=0)throw new IOException("Invalid PNG palette");
       palette=size/3;if(colour==3&&palette>(1<<depth))throw new IOException("PNG palette exceeds depth");
      } else {
       if(transparency||colour==4||colour==6)throw new IOException("Invalid PNG transparency");
       if((colour==0&&size!=2)||(colour==2&&size!=6)||(colour==3&&(palette==0||size<1||size>palette)))throw new IOException("Invalid PNG transparency length");
       if(colour==0&&depth<16&&(((source[pos+8]&255)<<8)|(source[pos+9]&255))>=(1<<depth))throw new IOException("Invalid gray transparency");
       if(colour==2&&depth==8&&(source[pos+8]!=0||source[pos+10]!=0||source[pos+12]!=0))throw new IOException("Invalid RGB transparency");
       transparency=true;
      }
      out.write(source,pos,size+12);
     } else if((source[pos+4]&32)==0)throw new IOException("Unsupported critical PNG chunk");
     else if((type.equals("sRGB")&&size==1)||(type.equals("gAMA")&&size==4)||(type.equals("cHRM")&&size==32)) {
      if(pixels)throw new IOException("Late PNG colour metadata");out.write(source,pos,size+12);
     }
    }
   }
   pos=tail+4;
  }
  throw new IOException("Missing PNG ending");
 }
 private static void writeChunk(OutputStream out,String type,byte[] data)throws IOException {
  DataOutputStream d=new DataOutputStream(out);byte[] name=type.getBytes(StandardCharsets.US_ASCII);
  d.writeInt(data.length);d.write(name);d.write(data);CRC32 crc=new CRC32();crc.update(name);crc.update(data);d.writeInt((int)crc.getValue());
 }
}
