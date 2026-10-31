package uk.co.traynor.privategallery.core.browser.hidden;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Test observer only: read a bounded declared fixture response, never wait for Tor EOF. */
public final class FixtureHttpResponse178 {
 private FixtureHttpResponse178() {}
 public static String read(InputStream input)throws IOException {
  ByteArrayOutputStream header=new ByteArrayOutputStream();
  int tail=0;
  try {
   while(header.size()<16384) {
    int value=input.read();
    if(value<0)throw new IOException();
    header.write(value);tail=(tail<<8)|value;
    if(tail==0x0d0a0d0a)break;
   }
  }catch(IOException failure){throw new IOException("Fixture HTTP headers bytes="+header.size());}
  if(tail!=0x0d0a0d0a)throw new IOException("Fixture HTTP headers too large");
  String[] lines=header.toString(StandardCharsets.US_ASCII.name()).split("\r\n");
  if(!lines[0].matches("HTTP/1\\.[01] 200(?: .*)?"))throw new IOException("Fixture HTTP status rejected");
  int length=-1;
  for(int index=1;index<lines.length;index++) {
   int colon=lines[index].indexOf(':');
   if(colon<1)throw new IOException("Fixture HTTP malformed header");
   String name=lines[index].substring(0,colon).trim();
   if(name.equalsIgnoreCase("Transfer-Encoding"))throw new IOException("Fixture HTTP encoding rejected");
   if(name.equalsIgnoreCase("Content-Length")) {
    String number=lines[index].substring(colon+1).trim();
    if(length!=-1 || !number.matches("[0-9]{1,7}"))throw new IOException("Fixture HTTP length rejected");
    length=Integer.parseInt(number);
   }
  }
  if(length<1 || length>1048576)throw new IOException("Fixture HTTP length out of bounds");
  byte[] body=new byte[length];int received=0;
  try {
   while(received<length) {
    int count=input.read(body,received,length-received);
    if(count<1)throw new IOException("Fixture EOF or empty read");
    received+=count;
   }
   return new String(body,StandardCharsets.UTF_8);
  }catch(IOException failure){throw new IOException("Fixture HTTP body bytes="+received+" expected="+length+" cause="+failure.getClass().getSimpleName());}
  finally{Arrays.fill(body,(byte)0);}
 }
}
