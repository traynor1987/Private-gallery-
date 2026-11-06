package uk.co.traynor.privategallery.core.privateapps;

import java.net.URI;
import java.util.Locale;

/** Metadata validation only. Passing this policy never grants transport or vault authority. */
public final class PrivateAppPolicy {
 private PrivateAppPolicy() {}
 public static final int MAX_URL=2048, MAX_NAME=120;
 public static boolean safeUrl(String value,boolean tor) {
  if(value==null || value.length()>MAX_URL || value.indexOf('\\')>=0 || value.chars().anyMatch(c->c<=32 || c==127))return false;
  try {
   URI uri=URI.create(value);
   String host=uri.getHost();
   if(host==null || host.endsWith(".") || uri.getRawUserInfo()!=null || uri.getPort()>65535 || uri.getPort()==0)return false;
   boolean onion=host.toLowerCase(Locale.ROOT).endsWith(".onion");
   if(onion && (!tor || !host.toLowerCase(Locale.ROOT).matches("[a-z2-7]{56}\\.onion")))return false;
   return "https".equalsIgnoreCase(uri.getScheme()) || (onion && "http".equalsIgnoreCase(uri.getScheme()));
  }catch(IllegalArgumentException ignored){return false;}
 }
 public static boolean validName(String name) {
  if(name==null || name.isBlank() || name.length()>MAX_NAME)return false;
  for(int i=0;i<name.length();i++) {
   char c=name.charAt(i);
   if(Character.isISOControl(c) || Character.getType(c)==Character.FORMAT)return false;
   if(Character.isHighSurrogate(c)) {if(++i>=name.length() || !Character.isLowSurrogate(name.charAt(i)))return false;}
   else if(Character.isLowSurrogate(c))return false;
  }
  return true;
 }
 private static int port(URI uri) {return uri.getPort()<0?("https".equalsIgnoreCase(uri.getScheme())?443:80):uri.getPort();}
 public static boolean sameOrigin(String first,String second) {
  if(!safeUrl(first,true) || !safeUrl(second,true))return false;
  URI a=URI.create(first),b=URI.create(second);
  return a.getScheme().equalsIgnoreCase(b.getScheme()) && a.getHost().equalsIgnoreCase(b.getHost()) && port(a)==port(b);
 }
 public static boolean inScope(String url,String scope) {
  if(!sameOrigin(url,scope))return false;
  return pathInScope(URI.create(url),URI.create(scope));
 }
 /** App presentation only: known YouTube mobile redirects do not change transport authority. */
 public static boolean inAppScope(String url,String startUrl,String savedScope) {
  String scope=savedScope==null || savedScope.isBlank()?startUrl:savedScope;
  if(inScope(url,scope))return true;
  if(!youtubeWebAlias(url) || !youtubeWebAlias(startUrl) || !youtubeWebAlias(scope))return false;
  URI value=URI.create(url),start=URI.create(startUrl),base=URI.create(scope);
  // A saved scope must also contain this app's start path before it can classify aliases.
  return pathInScope(start,base) && pathInScope(value,base);
 }
 private static boolean youtubeWebAlias(String url) {
  if(!safeUrl(url,false))return false;
  URI uri=URI.create(url);
  if(!"https".equalsIgnoreCase(uri.getScheme()) || port(uri)!=443)return false;
  String host=uri.getHost().toLowerCase(Locale.ROOT);
  if(!host.equals("youtube.com") && !host.equals("www.youtube.com") && !host.equals("m.youtube.com"))return false;
  // Do not extend scope across hosts for paths whose browser canonicalisation is ambiguous.
  for(String segment:uri.getRawPath().split("/"))if(segment.equals(".") || segment.equals(".."))return false;
  return true;
 }
 private static boolean pathInScope(URI value,URI base) {
  // Reject ambiguous encoded separators/dot segments rather than trusting renderer canonicalisation.
  for(String path:new String[]{value.getRawPath(),base.getRawPath()}) {
   String lower=path.toLowerCase(Locale.ROOT);
   if(lower.contains("%2e") || lower.contains("%2f") || lower.contains("%5c") || lower.contains("%25"))return false;
  }
  String path=value.normalize().getPath(),prefix=base.normalize().getPath();
  if(prefix.isEmpty())prefix="/";
  if(path.isEmpty())path="/";
  return path.equals(prefix) || path.startsWith(prefix.endsWith("/")?prefix:prefix+"/");
 }
}
