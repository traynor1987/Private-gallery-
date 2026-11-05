package uk.co.traynor.novaos.preview;
import java.net.URI;
/** Preview-only policy: provider restrictions and foreground media ownership. */
public final class PreviewAppPolicy {
 private PreviewAppPolicy() {}
 /** Ordinary first-party account pages are web navigation, not an OAuth grant.
  * Keep OAuth authorization (including wrapped continue URLs) out of WebView.
  * Google remains authoritative about which browsers its website accepts. */
 public static boolean googleOAuthAuthorization(String url) {return googleOAuthAuthorization(url,0);}
 private static boolean googleOAuthAuthorization(String url,int depth) {
  boolean account=false;
  try {
   URI u=new URI(url).normalize();
   if(!"https".equalsIgnoreCase(u.getScheme())||!"accounts.google.com".equalsIgnoreCase(u.getHost())||u.getRawUserInfo()!=null)return false;
   account=true;
   if(depth>=4||url.length()>16384)return true;
   String path=u.getPath().toLowerCase(java.util.Locale.ROOT);
   if(path.startsWith("/o/oauth2")||path.startsWith("/signin/oauth")||path.startsWith("/oauth2"))return true;
   String query=u.getRawQuery();if(query==null)return false;
   boolean client=false,grant=false;
   for(String pair:query.split("&")){
    String[] kv=pair.split("=",2);
    String key=java.net.URLDecoder.decode(kv[0],java.nio.charset.StandardCharsets.UTF_8).toLowerCase(java.util.Locale.ROOT);
    if(key.equals("client_id"))client=true;
    if(key.equals("redirect_uri")||key.equals("response_type")||key.equals("scope"))grant=true;
    if(kv.length==2){
     String value=kv[1];
     for(int i=0;i<=3;i++){
      if(googleOAuthAuthorization(value,depth+1))return true;
      if(i==3)break;
      String decoded=java.net.URLDecoder.decode(value,java.nio.charset.StandardCharsets.UTF_8);
      if(decoded.equals(value))break;value=decoded;
     }
    }
   }
   return client&&grant;
  }catch(Exception e){return account;}
 }
 public static boolean canPresentExternal(String tab,String profile,String active,String current,boolean foreground){return foreground&&tab.equals(active)&&profile.equals(current);}
 public static boolean keepRunning(boolean foreground,boolean selected,boolean sameProfile){return foreground&&selected&&sameProfile;}
}
