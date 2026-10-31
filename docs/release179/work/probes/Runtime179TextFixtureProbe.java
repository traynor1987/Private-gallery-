import java.nio.*;
import java.nio.charset.*;
import java.util.*;
import java.util.zip.*;
public class Runtime179TextFixtureProbe {
 public static void main(String[] args)throws Exception {
  byte[] original=new byte[256*1024];new Random(179).nextBytes(original);
  try{StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(original));throw new AssertionError("old fixture unexpectedly valid");}
  catch(MalformedInputException expected){System.out.println("OLD fixture strict UTF8 rejection reproduced: "+expected);}
  byte[] valid=new byte[256*1024];Random r=new Random(179);byte[] alphabet="ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".getBytes(StandardCharsets.US_ASCII);
  for(int i=0;i<valid.length;i++)valid[i]=alphabet[r.nextInt(alphabet.length)];
  if(StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(valid)).length()!=valid.length)throw new AssertionError();
  Deflater d=new Deflater();d.setInput(valid);d.finish();byte[] buffer=new byte[valid.length];int compressed=d.deflate(buffer);d.end();if(compressed<=65536)throw new AssertionError("fixture too compressible to block pipe");
  System.out.println("NEW valid UTF8 bytes="+valid.length+", compressed="+compressed+", exceeds65536=true");
 }
}
