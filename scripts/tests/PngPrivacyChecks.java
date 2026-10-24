import java.io.*;
import java.util.*;
import java.util.zip.CRC32;
import uk.co.traynor.privategallery.core.media.PngPrivacy;
public class PngPrivacyChecks {
 static byte[] chunk(String name,byte[] data)throws Exception {var out=new ByteArrayOutputStream();var d=new DataOutputStream(out);d.writeInt(data.length);byte[] type=name.getBytes("US-ASCII");d.write(type);d.write(data);var crc=new CRC32();crc.update(type);crc.update(data);d.writeInt((int)crc.getValue());return out.toByteArray();}
 public static void main(String[] args)throws Exception {
  var o=new ByteArrayOutputStream();o.write(new byte[]{(byte)137,80,78,71,13,10,26,10});o.write(chunk("IHDR",new byte[]{0,0,0,1,0,0,0,1,8,2,0,0,0}));o.write(chunk("iTXt","XMP GPS private".getBytes()));o.write(chunk("IDAT",new byte[]{1,2,3}));o.write(chunk("IEND",new byte[0]));
  byte[] original=o.toByteArray();byte[] clean=PngPrivacy.clean(original,1,null);
  if(new String(clean,"ISO-8859-1").contains("private"))throw new AssertionError("metadata retained");
  if(!Arrays.equals(original,o.toByteArray()))throw new AssertionError("original changed");
  byte[] corrupt=original.clone();corrupt[30]^=1;
  try{PngPrivacy.clean(corrupt,1,null);throw new AssertionError("CRC corruption accepted");}catch(IOException expected){}
  byte[] trailing=Arrays.copyOf(original,original.length+1);
  try{PngPrivacy.clean(trailing,1,null);throw new AssertionError("trailing data accepted");}catch(IOException expected){}
  var invalid=new ByteArrayOutputStream();invalid.write(Arrays.copyOfRange(original,0,33));invalid.write(chunk("tRNS","GPS payload".getBytes()));invalid.write(Arrays.copyOfRange(original,33,original.length));
  try{PngPrivacy.clean(invalid.toByteArray(),1,null);throw new AssertionError("malformed transparency retained");}catch(IOException expected){}
  System.out.println("PNG metadata removal/CRC/trailing data checks passed");
 }
}
