import java.net.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import uk.co.traynor.privategallery.core.vpn.continuity.ProbeReservations;
public class InheritedSocketPaths {
 static final InetAddress A;
 static { try{A=InetAddress.getByAddress(new byte[]{127,0,0,1});}catch(Exception e){throw new AssertionError(e);} }
 static ProbeReservations pool() throws Exception {var p=new ProbeReservations(List.of(A),new InetSocketAddress(A,53),new ProbeReservations.NetworkBinder(){public void bind(Socket s){}public void bind(DatagramSocket s){}},1,80,500);p.sealForChildAdmission();return p;}
 static void proxy() throws Exception {
  ProxySelector previous=ProxySelector.getDefault();
  try(ServerSocket proxy=new ServerSocket(0,8,A);ServerSocket destination=new ServerSocket(0,8,A)){
   proxy.setSoTimeout(1500);
   ProxySelector.setDefault(new ProxySelector(){public List<Proxy> select(URI u){return List.of(new Proxy(Proxy.Type.SOCKS,new InetSocketAddress(A,proxy.getLocalPort())));}public void connectFailed(URI u,SocketAddress a,IOException e){}});
   var p=pool();var s=p.tcpFactory(A,t->()->true).createSocket();
   Thread connect=new Thread(()->{try{s.connect(new InetSocketAddress(A,destination.getLocalPort()));}catch(Exception e){}});connect.start();
   try(Socket received=proxy.accept()){received.setSoTimeout(1000);System.out.println("BUG: unregistered proxy endpoint received SOCKS greeting="+received.getInputStream().read());}
   connect.join(2000);p.disposeAfterRetirement(()->true);
  }finally{ProxySelector.setDefault(previous);}
 }
 static void race() throws Exception {
  ProxySelector previous=ProxySelector.getDefault();CountDownLatch selected=new CountDownLatch(1),resume=new CountDownLatch(1);AtomicReference<Throwable> error=new AtomicReference<>();
  try(ServerSocket server=new ServerSocket(0,8,A)){
   server.setSoTimeout(1500);
   ProxySelector.setDefault(new ProxySelector(){public List<Proxy> select(URI u){selected.countDown();try{resume.await();}catch(InterruptedException e){throw new RuntimeException(e);}return List.of(Proxy.NO_PROXY);}public void connectFailed(URI u,SocketAddress a,IOException e){}});
   var p=pool();var s=p.tcpFactory(A,t->()->false).createSocket();
   Thread connect=new Thread(()->{try{s.connect(new InetSocketAddress(A,server.getLocalPort()));}catch(Throwable e){error.set(e);}});connect.start();
   if(!selected.await(2,TimeUnit.SECONDS))throw new AssertionError("no select");
   s.close();resume.countDown();
   try(Socket peer=server.accept()){System.out.println("BUG: new connection established AFTER logical close and unknown revoke");}
   connect.join(2000);System.out.println("connect returned successfully="+(error.get()==null));p.disposeAfterRetirement(()->true);
  }finally{resume.countDown();ProxySelector.setDefault(previous);}
 }
 static void skip() throws Exception {
  try(ServerSocket server=new ServerSocket(0,8,A)){
   var p=pool();var s=p.tcpFactory(A,t->()->false).createSocket(A,server.getLocalPort());
   try(Socket peer=server.accept()){InputStream input=s.getInputStream();s.close();peer.getOutputStream().write(123);System.out.println("BUG: parked stream skip consumed bytes="+input.skip(1));}
   p.disposeAfterRetirement(()->true);
  }
 }
 public static void main(String[] args)throws Exception{proxy();race();skip();}
}
