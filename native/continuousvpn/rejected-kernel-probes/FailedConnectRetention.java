import java.net.*;
import java.util.*;
import uk.co.traynor.privategallery.core.vpn.continuity.ProbeReservations;
public class FailedConnectRetention {
 public static void main(String[] args) throws Exception {
  InetAddress a=InetAddress.getByAddress(new byte[]{127,0,0,1});
  var p=new ProbeReservations(List.of(a),new InetSocketAddress(a,53),new ProbeReservations.NetworkBinder(){public void bind(Socket s){} public void bind(DatagramSocket s){}},1,80,500);
  p.sealForChildAdmission();
  Socket s=p.tcpFactory(a,tuple->()->{
   try(ServerSocket x=new ServerSocket()) { x.setReuseAddress(false); x.bind(new InetSocketAddress(a,tuple.localPort())); System.out.println("BUG: source binding free BEFORE revoke acknowledgement"); }
   catch(BindException expected) { System.out.println("binding retained at revoke"); }
   return false;
  }).createSocket();
  int local=s.getLocalPort(), remote;
  try(ServerSocket x=new ServerSocket(0,8,a)){remote=x.getLocalPort();}
  try{s.connect(new InetSocketAddress(a,remote));}catch(Exception e){System.out.println("connect: "+e.getClass().getName());}
  try(ServerSocket x=new ServerSocket()){x.setReuseAddress(false);x.bind(new InetSocketAddress(a,local));System.out.println("BUG: binding free after unknown revoke, isClosed="+s.isClosed());}
  catch(BindException expected){System.out.println("binding retained after unknown revoke");}
  p.disposeAfterRetirement(()->true);
 }
}
