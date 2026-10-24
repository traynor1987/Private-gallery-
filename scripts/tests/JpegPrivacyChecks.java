import java.io.*;
import java.util.*;
import uk.co.traynor.privategallery.core.media.JpegPrivacy;
public class JpegPrivacyChecks {
 static byte[] segment(int marker,byte[] data)throws Exception {var o=new ByteArrayOutputStream();o.write(255);o.write(marker);o.write((data.length+2)>>8);o.write((data.length+2)&255);o.write(data);return o.toByteArray();}
 static byte[] jpeg(byte[] metadata)throws Exception {var o=new ByteArrayOutputStream();o.write(new byte[]{(byte)255,(byte)216});o.write(segment(225,metadata));o.write(segment(237,"IPTC location".getBytes()));o.write(segment(254,"private comment".getBytes()));o.write(segment(218,new byte[]{1,1,0,0,63,0}));o.write(new byte[]{1,2,(byte)255,0,3,(byte)255,(byte)208,4,(byte)255,(byte)217});return o.toByteArray();}
 public static void main(String[] a)throws Exception {
  byte[] original=jpeg("Exif GPS XMP secret".getBytes());byte[] copy=original.clone();
  byte[] clean=JpegPrivacy.clean(original,6,null);
  if(!Arrays.equals(original,copy))throw new AssertionError("original modified");
  String text=new String(clean,java.nio.charset.StandardCharsets.ISO_8859_1);
  for(String word:new String[]{"GPS","XMP","secret","IPTC","private comment"})if(text.contains(word))throw new AssertionError("retained "+word);
  if(!text.contains("Exif"))throw new AssertionError("orientation lost");
  byte[] dated=JpegPrivacy.clean(original,1,"2026:10:24 12:34:56");
  if(!new String(dated,java.nio.charset.StandardCharsets.ISO_8859_1).contains("2026:10:24 12:34:56"))throw new AssertionError("date missing");
  for(byte[] bad:new byte[][]{new byte[]{1,2,3},Arrays.copyOf(original,original.length-1),Arrays.copyOf(original,original.length+1)}) {
   try {JpegPrivacy.clean(bad,1,null);throw new AssertionError("accepted malformed/trailing container");}catch(IOException expected){}
  }
  var reserved=new ByteArrayOutputStream();reserved.write(new byte[]{(byte)255,(byte)216});reserved.write(segment(200,"GPS secret extension".getBytes()));reserved.write(Arrays.copyOfRange(original,2,original.length));
  try {JpegPrivacy.clean(reserved.toByteArray(),1,null);throw new AssertionError("reserved extension accepted");}catch(IOException expected){}
  System.out.println("JPEG metadata removal/original preservation/orientation/date/malformed checks passed");
 }
}
