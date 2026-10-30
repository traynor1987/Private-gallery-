import java.io.*;
import java.nio.charset.StandardCharsets;
import java.net.SocketTimeoutException;
import uk.co.traynor.privategallery.core.browser.hidden.FixtureHttpResponse178;
public class FixtureHttpResponse178Checks {
 static InputStream bytes(String s){return new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8));}
 static void reject(String s)throws Exception {try {FixtureHttpResponse178.read(bytes(s));throw new AssertionError("Accepted invalid response");}catch(IOException expected){}}
 public static void main(String[] args)throws Exception {
  byte[] packet="HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\n{}".getBytes(StandardCharsets.UTF_8);
  InputStream delayedClose=new InputStream(){final ByteArrayInputStream data=new ByteArrayInputStream(packet);public int read(){if(data.available()==0)throw new AssertionError("Must not wait for socket close");return data.read();}};
  if(!"{}".equals(FixtureHttpResponse178.read(delayedClose)))throw new AssertionError();
  reject("HTTP/1.1 500 Error\r\nContent-Length: 2\r\n\r\n{}");
  reject("HTTP/1.1 200 OK\r\n\r\n{}");
  reject("HTTP/1.1 200 OK\r\nContent-Length: 3\r\n\r\n{}");
  reject("HTTP/1.1 200 OK\r\nContent-Length: 1048577\r\n\r\n");
  reject("HTTP/1.1 200 OK\r\nContent-Length: 2\r\nContent-Length: 2\r\n\r\n{}");
  reject("HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\nContent-Length: 2\r\n\r\n{}");
  reject("HTTP/1.1 200 OK\r\nX-Test: "+"x".repeat(16384)+"\r\nContent-Length: 2\r\n\r\n{}");
  try {FixtureHttpResponse178.read(new InputStream(){public int read()throws IOException{throw new SocketTimeoutException("sensitive message");}});throw new AssertionError();}
  catch(IOException expected){if(!expected.getMessage().equals("Fixture HTTP headers bytes=0"))throw new AssertionError(expected);}
  System.out.println("Bounded fixture HTTP status/length/truncation and no-EOF-wait checks PASS");
 }
}
