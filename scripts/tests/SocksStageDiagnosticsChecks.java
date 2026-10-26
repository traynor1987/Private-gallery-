import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import uk.co.traynor.privategallery.core.browser.hidden.SocksStageDiagnostics;
public class SocksStageDiagnosticsChecks {
 static int checks;
 static void check(boolean b){if(!b)throw new AssertionError();checks++;}
 static List<String> probe(int mode,AtomicReference<byte[]> request) throws Exception {
  try(ServerSocket server=new ServerSocket(0,1,InetAddress.getByAddress(new byte[]{127,0,0,1}))) {
   var thread=new Thread(()->{try(Socket socket=server.accept()) {
    var in=new DataInputStream(socket.getInputStream());var out=socket.getOutputStream();
    check(Arrays.equals(in.readNBytes(3),new byte[]{5,1,0}));
    if(mode==3){Thread.sleep(500);return;}
    out.write(new byte[]{5,(byte)(mode==2?255:0)});out.flush();if(mode==2)return;
    var head=in.readNBytes(5);int len=head[4]&255;var host=in.readNBytes(len);var port=in.readNBytes(2);
    request.set(host);check(head[3]==3 && Arrays.equals(port,new byte[]{0,80}));
    out.write(new byte[]{5,(byte)(mode==1?4:0),0,1,0,0,0,0,0,0});out.flush();if(mode==1)return;
    var reader=new BufferedReader(new InputStreamReader(in));while(!reader.readLine().isEmpty()){}
    out.write("HTTP/1.0 200 OK\r\nContent-Length: 7\r\n\r\nfixture".getBytes());out.flush();
   }catch(Exception e){if(mode!=3)throw new RuntimeException(e);}},"fake-tor-fixture");thread.setDaemon(true);thread.start();
   var result=SocksStageDiagnostics.inspect(server.getLocalPort(),"fixture.onion","fixture",()->true,mode==3?100:2000);
   thread.join(1000);return result;
  }
 }
 public static void main(String[] args) throws Exception {
  var host=new AtomicReference<byte[]>();var ok=probe(0,host);check(new String(host.get()).equals("fixture.onion"));
  check(ok.stream().anyMatch(x->x.contains("socks_connect code=0")));check(ok.stream().anyMatch(x->x.contains("http_status=200")&&x.contains("exactBody=true")));
  check(ok.stream().noneMatch(x->x.contains("fixture.onion")||x.contains("127.0.0.1")));
  var refused=probe(1,new AtomicReference<>());check(refused.stream().anyMatch(x->x.contains("socks_connect code=4")));check(refused.stream().noneMatch(x->x.contains("http_status")));
  var auth=probe(2,new AtomicReference<>());check(auth.stream().anyMatch(x->x.contains("socks_auth code=255")));check(auth.stream().noneMatch(x->x.contains("socks_connect")));
  long start=System.nanoTime();var timed=probe(3,new AtomicReference<>());check(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start)<1500);check(timed.stream().anyMatch(x->x.contains("failure=")));
  var closed=SocksStageDiagnostics.inspect(1,"fixture.onion","fixture",()->false,100);check(closed.stream().anyMatch(x->x.contains("authority_lost")));check(closed.stream().noneMatch(x->x.contains("tcp_connected")));
  try{SocksStageDiagnostics.inspect(1,"ordinary.example","fixture",()->true,100);throw new AssertionError();}catch(IllegalArgumentException e){checks++;}
  try{SocksStageDiagnostics.inspect(1,"fixture.onion","fixture",()->true,30001);throw new AssertionError();}catch(IllegalArgumentException e){checks++;}
  System.out.println(checks+" SOCKS stage diagnostic checks PASS");
 }
}
