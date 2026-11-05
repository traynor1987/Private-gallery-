package uk.co.traynor.privategallery.home2;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Preview-only adaptation of HomeDesktop's immutable ordering and bounded layout rules. */
public final class PreviewRules {
 private PreviewRules() {}
 public static String https(String value) {
  try {
   if(value==null||value.length()>2048||value.codePoints().anyMatch(c->Character.isWhitespace(c)||Character.isISOControl(c))||value.contains("\\"))throw new IllegalArgumentException();
   URI uri=new URI(value);String host=uri.getHost();
   if(!"https".equalsIgnoreCase(uri.getScheme())||host==null||uri.getRawUserInfo()!=null||(uri.getPort()!=-1&&uri.getPort()!=443))throw new IllegalArgumentException();
   host=host.toLowerCase(Locale.ROOT);
   if(host.length()>253||!host.matches("[a-z0-9](?:[a-z0-9.-]*[a-z0-9])?")||!host.contains(".")||host.endsWith(".local")||host.endsWith(".localhost")||host.matches("[0-9.]+"))throw new IllegalArgumentException();
   for(String part:host.split("\\.",-1))if(part.length()<1||part.length()>63||part.startsWith("-")||part.endsWith("-"))throw new IllegalArgumentException();
   String path=uri.getRawPath();if(path==null||path.isEmpty())path="/";
   return "https://"+host+path+(uri.getRawQuery()==null?"":"?"+uri.getRawQuery())+(uri.getRawFragment()==null?"":"#"+uri.getRawFragment());
  }catch(Exception e){throw new IllegalArgumentException("Enter an HTTPS website with a public domain and no username, password or custom port.");}
 }
 public static String key(String url) {
  try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(https(url).getBytes(StandardCharsets.UTF_8))).substring(0,24);}catch(Exception e){throw new IllegalArgumentException("Invalid website");}
 }
 public static int columns(int width,float fontScale,int requested) {
  float scale=Math.max(1f,fontScale);
  int safe=Math.max(1,Math.min(8,(int)(width/((width>=600?112f:74f)*scale))));
  return Math.max(1,Math.min(safe,(width>=600?safe:4)+(requested-4)));
 }
 public static List<String> move(List<String> source,String id,int delta) {
  List<String> result=new ArrayList<>(source);int index=result.indexOf(id);
  if(index<0)return List.copyOf(result);
  int target=Math.max(0,Math.min(result.size()-1,index+delta));Collections.swap(result,index,target);return List.copyOf(result);
 }
}
