import uk.co.traynor.privategallery.core.vpn.VpnDnsMessage;
import java.io.*;
import java.util.*;

public final class VpnDnsMessageChecks {
 static void check(boolean value){if(!value)throw new AssertionError();}
 static byte[] response(byte[] query,int type,boolean cname)throws Exception {
  ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);
  out.writeShort(0x178);out.writeShort(0x8180);out.writeShort(1);out.writeShort(cname?2:1);out.writeShort(0);out.writeShort(0);out.write(query,12,query.length-12);
  if(cname){out.writeShort(0xc00c);out.writeShort(5);out.writeShort(1);out.writeInt(1);out.writeShort(8);out.writeByte(5);out.writeBytes("alias");out.writeShort(0xc00c);out.writeByte(5);out.writeBytes("alias");out.writeShort(0xc00c);}else out.writeShort(0xc00c);
  out.writeShort(type);out.writeShort(1);out.writeInt(1);out.writeShort(type==1?4:16);
  if(type==1)out.write(new byte[]{(byte)203,0,113,10});else{out.write(new byte[]{0x20,1,0x48,0x60,0,0,0,0,0,0,0,0,0,0,0,(byte)0x88});}
  return bytes.toByteArray();
 }
 interface Checked {void run()throws Exception;}
 static void rejects(Checked block)throws Exception{try{block.run();throw new AssertionError("Malformed DNS accepted");}catch(IllegalArgumentException expected){}}
 public static void main(String[] args)throws Exception{
  byte[] q=VpnDnsMessage.query("example.test",1,0x178),reply=response(q,1,false);
  check(VpnDnsMessage.answers(reply,"example.test",1,0x178).get(0).getHostAddress().equals("203.0.113.10"));
  check(VpnDnsMessage.answers(response(q,1,true),"example.test",1,0x178).size()==1);
  byte[] q6=VpnDnsMessage.query("example.test",28,0x178);check(VpnDnsMessage.answers(response(q6,28,false),"example.test",28,0x178).get(0).getAddress().length==16);
  for(int n=0;n<reply.length;n++){final byte[] shortReply=Arrays.copyOf(reply,n);rejects(()->VpnDnsMessage.answers(shortReply,"example.test",1,0x178));}
  rejects(()->VpnDnsMessage.answers(reply,"other.test",1,0x178));rejects(()->VpnDnsMessage.answers(reply,"example.test",1,0x179));rejects(()->VpnDnsMessage.answers(reply,"example.test",28,0x178));
  for(int flags:new int[]{0x0100,0x8980,0x8380,0x8182,0x81c0}){byte[] bad=reply.clone();bad[2]=(byte)(flags>>8);bad[3]=(byte)flags;rejects(()->VpnDnsMessage.answers(bad,"example.test",1,0x178));}
  byte[] loop=reply.clone();loop[q.length]=(byte)0xc0;loop[q.length+1]=(byte)q.length;rejects(()->VpnDnsMessage.answers(loop,"example.test",1,0x178));
  byte[] unrelated=reply.clone();unrelated[q.length+1]=13;rejects(()->VpnDnsMessage.answers(unrelated,"example.test",1,0x178));
  for(String host:new String[]{"", "x..test", "a".repeat(64)+".test", "x.test\n", "onion", "x.onion", "a".repeat(255)})rejects(()->VpnDnsMessage.query(host,1,0x178));
  byte[] nxdomain=Arrays.copyOf(q,q.length);nxdomain[2]=(byte)0x81;nxdomain[3]=(byte)0x83;check(VpnDnsMessage.answers(nxdomain,"example.test",1,0x178).isEmpty());
  byte[] additional=reply.clone();additional[7]=0;additional[11]=1;
  check(VpnDnsMessage.answers(additional,"example.test",1,0x178).isEmpty());
  ByteArrayOutputStream loopBytes=new ByteArrayOutputStream();DataOutputStream loopOut=new DataOutputStream(loopBytes);
  loopOut.writeShort(0x178);loopOut.writeShort(0x8180);loopOut.writeShort(1);loopOut.writeShort(1);loopOut.writeShort(0);loopOut.writeShort(0);loopOut.write(q,12,q.length-12);
  loopOut.writeShort(0xc00c);loopOut.writeShort(5);loopOut.writeShort(1);loopOut.writeInt(1);loopOut.writeShort(2);loopOut.writeShort(0xc00c);
  rejects(()->VpnDnsMessage.answers(loopBytes.toByteArray(),"example.test",1,0x178));
  check(!VpnDnsMessage.truncated(reply,"example.test",1,0x178));
  byte[] tc=Arrays.copyOf(q,q.length);tc[2]=(byte)0x83;tc[3]=(byte)0x80;
  check(VpnDnsMessage.truncated(tc,"example.test",1,0x178));
  rejects(()->VpnDnsMessage.truncated(tc,"other.test",1,0x178));rejects(()->VpnDnsMessage.truncated(tc,"example.test",1,0x179));
  rejects(()->VpnDnsMessage.query("example.test",2,0x178));
  byte[] extra=Arrays.copyOf(reply,reply.length+1);rejects(()->VpnDnsMessage.answers(extra,"example.test",1,0x178));
  System.out.println("VPN DNS wire identity, CNAME, IPv4/6, truncation and malformed-response checks PASS");
 }
}
