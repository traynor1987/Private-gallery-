package uk.co.traynor.privategallery.core.vpn;

import java.io.ByteArrayOutputStream;
import java.net.IDN;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Bounded DNS wire codec. Never resolves names or accepts unrelated additional addresses. */
public final class VpnDnsMessage {
 private VpnDnsMessage(){}
 private static void require(boolean ok){if(!ok)throw new IllegalArgumentException("Invalid VPN DNS response");}
 public static String hostname(String host){
  require(host!=null&&!host.isEmpty()&&host.length()<=254&&host.equals(host.trim()));
  String name=IDN.toASCII(host,IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
  if(name.endsWith("."))name=name.substring(0,name.length()-1);
  require(!name.isEmpty()&&name.length()<=253&&!name.equals("onion")&&!name.endsWith(".onion"));
  for(String label:name.split("\\.",-1))require(!label.isEmpty()&&label.length()<=63);
  return name;
 }
 private static void word(ByteArrayOutputStream out,int n){out.write(n>>>8);out.write(n);}
 public static byte[] query(String host,int type,int id){
  require((type==1||type==28)&&id>=0&&id<=65535);String name=hostname(host);
  ByteArrayOutputStream out=new ByteArrayOutputStream();word(out,id);word(out,0x100);word(out,1);word(out,0);word(out,0);word(out,0);
  for(String label:name.split("\\.")){byte[] b=label.getBytes(StandardCharsets.US_ASCII);out.write(b.length);out.write(b,0,b.length);}out.write(0);word(out,type);word(out,1);return out.toByteArray();
 }
 private static int u16(byte[] b,int p){require(p>=0&&p+2<=b.length);return ((b[p]&255)<<8)|(b[p+1]&255);}
 private static String name(byte[] b,int[] cursor){
  int p=cursor[0],end=-1,hops=0;StringBuilder out=new StringBuilder();
  while(true){require(p>=0&&p<b.length&&++hops<=128);int n=b[p++]&255;
   if(n==0){cursor[0]=end<0?p:end;return out.toString().toLowerCase(Locale.ROOT);}
   if((n&0xc0)==0xc0){require(p<b.length);int target=((n&63)<<8)|(b[p++]&255);require(target<p-2);if(end<0)end=p;p=target;continue;}
   require(n<=63&&p+n<=b.length);if(out.length()>0)out.append('.');
   for(int i=0;i<n;i++){char c=(char)(b[p++]&255);require(c>32&&c<127&&c!='.');out.append(c);}require(out.length()<=253);
  }
 }
 public static boolean truncated(byte[] b,String host,int type,int id){
  require((type==1||type==28)&&b.length>=12&&b.length<=4096&&u16(b,0)==id);int flags=u16(b,2);
  require((flags&0xf840)==0x8000&&((flags&15)==0||(flags&15)==3)&&u16(b,4)==1);
  int[] p={12};require(name(b,p).equals(hostname(host))&&u16(b,p[0])==type&&u16(b,p[0]+2)==1);
  return (flags&0x200)!=0;
 }
 public static List<InetAddress> answers(byte[] b,String host,int type,int id){
  require((type==1||type==28)&&b.length>=12&&b.length<=4096&&u16(b,0)==id);int flags=u16(b,2),rcode=flags&15;
  require((flags&0xf840)==0x8000&&(flags&0x200)==0&&(rcode==0||rcode==3)&&u16(b,4)==1);
  String wanted=hostname(host);int[] p={12};require(name(b,p).equals(wanted));require(u16(b,p[0])==type&&u16(b,p[0]+2)==1);p[0]+=4;
  int answers=u16(b,6),other=u16(b,8)+u16(b,10);require(answers<=64&&other<=64);
  Map<String,String> aliases=new HashMap<>();Map<String,List<InetAddress>> addresses=new HashMap<>();
  for(int i=0;i<answers+other;i++){
   String owner=name(b,p);int record=u16(b,p[0]),cls=u16(b,p[0]+2),size=u16(b,p[0]+8);p[0]+=10;int end=p[0]+size;require(end>=p[0]&&end<=b.length);
   if(i<answers&&cls==1){
    if(record==5){int[] at={p[0]};String alias=name(b,at);require(at[0]==end&&!alias.isEmpty()&&!aliases.containsKey(owner));aliases.put(owner,alias);}
    else if(record==type){require(size==(type==1?4:16));try{addresses.computeIfAbsent(owner,k->new ArrayList<>()).add(InetAddress.getByAddress(Arrays.copyOfRange(b,p[0],end)));}catch(UnknownHostException impossible){throw new IllegalArgumentException(impossible);}}
   }
   p[0]=end;
  }
  require(p[0]==b.length);if(rcode==3){require(answers==0);return List.of();}
  Set<String> seen=new HashSet<>();String current=wanted;
  for(int hop=0;hop<16;hop++){
   require(seen.add(current));List<InetAddress> found=addresses.get(current);
   if(found!=null){require(!aliases.containsKey(current));return List.copyOf(found);}
   String next=aliases.get(current);if(next==null)return List.of();current=next;
  }
  throw new IllegalArgumentException("VPN DNS alias chain too long");
 }
}
