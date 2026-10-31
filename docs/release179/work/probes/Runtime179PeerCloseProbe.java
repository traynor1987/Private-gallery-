import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.security.*;
import java.security.cert.*;
import java.security.spec.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.regex.*;
import javax.net.ssl.*;

public class Runtime179PeerCloseProbe {
 static class PeerEof extends EOFException {}
 static int readPeer(InputStream input)throws IOException {int n=input.read();if(n<0)throw new PeerEof();return n;}
 static boolean peerReset(Throwable failure){Throwable cause=failure;for(int i=0;i<8&&cause!=null;i++,cause=cause.getCause())if(cause instanceof SocketException&&Set.of("Connection reset","Connection reset by peer").contains(cause.getMessage()))return true;return false;}
 static void check(boolean condition,String reason){if(!condition)throw new AssertionError(reason);}
 static String fixture(String source,String name){var matcher=Pattern.compile("private const val "+name+"=\"([^\"]+)\"").matcher(source);check(matcher.find(),"fixture missing");return matcher.group(1);}
 static SSLContext[] contexts(String source)throws Exception {
  var cert=CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(Base64.getDecoder().decode(fixture(source,"CERT"))));
  var key=KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(fixture(source,"KEY"))));
  var store=KeyStore.getInstance(KeyStore.getDefaultType());store.load(null);store.setKeyEntry("fixture",key,new char[0],new java.security.cert.Certificate[]{cert});
  var keys=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());keys.init(store,new char[0]);
  var trust=KeyStore.getInstance(KeyStore.getDefaultType());trust.load(null);trust.setCertificateEntry("fixture",cert);
  var manager=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());manager.init(trust);
  var server=SSLContext.getInstance("TLS");server.init(keys.getKeyManagers(),null,null);
  var client=SSLContext.getInstance("TLS");client.init(null,manager.getTrustManagers(),null);
  return new SSLContext[]{server,client};
 }
 static void run(SSLContext[] tls,String mode,boolean old)throws Exception {
  var observed=new CountDownLatch(1);var completed=new CountDownLatch(1);var accepted=new AtomicReference<SSLSocket>();var failures=new CopyOnWriteArrayList<Throwable>();
  try(var server=(SSLServerSocket)tls[0].getServerSocketFactory().createServerSocket(0,1,InetAddress.getLoopbackAddress())){
   server.setSoTimeout(3000);
   var worker=new Thread(()->{
    try(var socket=(SSLSocket)server.accept()){
     accepted.set(socket);socket.setSoTimeout(400);socket.startHandshake();socket.getOutputStream().write('x');socket.getOutputStream().flush();
     readPeer(socket.getInputStream());throw new IOException("Unexpected inbound data while response is held");
    }catch(PeerEof eof){for(var suppressed:eof.getSuppressed())failures.add(suppressed);observed.countDown();}
    catch(Exception failure){if(peerReset(failure)){for(var suppressed:failure.getSuppressed())failures.add(suppressed);observed.countDown();}else failures.add(failure);}
    finally{if(old)observed.countDown();completed.countDown();}
   },"peer-close-host-probe");worker.setDaemon(true);worker.start();
   try(var client=(SSLSocket)tls[1].getSocketFactory().createSocket(InetAddress.getLoopbackAddress(),server.getLocalPort())){
    client.setSoTimeout(3000);client.startHandshake();check(client.getInputStream().read()=='x',"held response prefix missing");
    if(mode.equals("unexpected")){client.getOutputStream().write('!');client.getOutputStream().flush();}
    else if(mode.equals("local-close"))accepted.get().close();
    else if(mode.equals("eof"))client.close();
    check(completed.await(3,TimeUnit.SECONDS),"handler incomplete");boolean positive=observed.getCount()==0;
    if(old){check(positive&&failures.stream().anyMatch(x->x instanceof SocketTimeoutException),"old timeout counterexample missing");}
    else if(mode.equals("eof")){check(positive&&failures.isEmpty(),"genuine EOF was not distinguished");}
    else{check(!positive&&!failures.isEmpty(),"non-peer closure accepted");if(mode.equals("idle"))check(failures.get(0) instanceof SocketTimeoutException,"idle peer did not time out");}
    System.out.println((old?"OLD":"NEW")+" "+mode+" peerClose="+positive+" failures="+failures.stream().map(x->x.getClass().getSimpleName()).toList());
   }finally{worker.join(3000);check(!worker.isAlive(),"probe worker survived");}
  }
 }
 public static void main(String[] args)throws Exception {
  var tls=contexts(Files.readString(Path.of(args[0])));
  run(tls,"idle",true);run(tls,"idle",false);run(tls,"unexpected",false);run(tls,"local-close",false);run(tls,"eof",false);
 }
}
