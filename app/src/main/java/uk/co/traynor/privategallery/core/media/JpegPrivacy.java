package uk.co.traynor.privategallery.core.media;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/** Copy-only JPEG sanitisation. Drop every APP/COM segment, including EXIF, XMP and IPTC,
 * across all progressive scans. Retain only codec markers and a newly constructed,
 * allowlisted EXIF containing orientation and optionally the chosen display date.
 * Reject truncation, unsupported extension markers and data after EOI. */
public final class JpegPrivacy {
 private JpegPrivacy() {}
 public static byte[] clean(byte[] input,int orientation,String date) throws IOException {
  if(input.length<4 || (input[0]&255)!=255 || (input[1]&255)!=216)throw new IOException("Not JPEG");
  if(orientation<1||orientation>8)throw new IOException("Invalid orientation");
  if(date!=null&&!date.matches("[0-9]{4}:[0-9]{2}:[0-9]{2} [0-9]{2}:[0-9]{2}:[0-9]{2}"))throw new IOException("Invalid date");
  ByteArrayOutputStream out=new ByteArrayOutputStream(input.length);
  out.write(255);out.write(216);
  byte[] exif=exif(orientation,date);out.write(255);out.write(225);out.write((exif.length+2)>>8);out.write((exif.length+2)&255);out.write(exif,0,exif.length);
  int pos=2;boolean scanSeen=false;
  while(pos<input.length) {
   int start=pos;
   if((input[pos++]&255)!=255)throw new IOException("Invalid marker");
   while(pos<input.length&&(input[pos]&255)==255)pos++;
   if(pos>=input.length)throw new IOException("Truncated marker");
   int marker=input[pos++]&255;
   if(marker==217) {
    if(!scanSeen||pos!=input.length)throw new IOException("Invalid JPEG ending");
    out.write(255);out.write(217);return out.toByteArray();
   }
   if(pos+2>input.length)throw new IOException("Truncated length");
   int length=((input[pos]&255)<<8)|(input[pos+1]&255);
   if(length<2||length>input.length-pos)throw new IOException("Truncated segment");
   boolean metadata=(marker>=224&&marker<=239)||marker==254;
   boolean codec=(marker>=192&&marker<=207)||marker==218||marker==219||marker==220||marker==221;
   if(!metadata&&!codec)throw new IOException("Unsupported JPEG extension");
   int end=pos+length;
   if(!metadata)out.write(input,start,end-start);
   pos=end;
   if(marker==218) {
    scanSeen=true;int scanStart=pos;
    while(pos<input.length) {
     if((input[pos]&255)!=255){pos++;continue;}
     int next=pos+1;while(next<input.length&&(input[next]&255)==255)next++;
     if(next>=input.length)throw new IOException("Truncated scan");
     int code=input[next]&255;
     if(code==0||(code>=208&&code<=215)){pos=next+1;continue;}
     break;
    }
    out.write(input,scanStart,pos-scanStart);
   }
  }
  throw new IOException("Missing end marker");
 }
 private static byte[] exif(int orientation,String date) {
  ByteBuffer b=ByteBuffer.allocate(date==null?32:114).order(ByteOrder.LITTLE_ENDIAN);
  b.put(new byte[]{69,120,105,102,0,0,73,73,42,0,8,0,0,0});
  b.putShort((short)(date==null?1:3));
  b.putShort((short)0x112).putShort((short)3).putInt(1).putShort((short)orientation).putShort((short)0);
  if(date==null){b.putInt(0);return b.array();}
  b.putShort((short)0x132).putShort((short)2).putInt(20).putInt(50);
  b.putShort((short)0x8769).putShort((short)4).putInt(1).putInt(70);
  b.putInt(0);b.put(date.getBytes(StandardCharsets.US_ASCII)).put((byte)0);
  b.putShort((short)1).putShort((short)0x9003).putShort((short)2).putInt(20).putInt(88).putInt(0);
  b.put(date.getBytes(StandardCharsets.US_ASCII)).put((byte)0);
  return b.array();
 }
}
