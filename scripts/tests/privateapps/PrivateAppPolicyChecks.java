import uk.co.traynor.privategallery.core.privateapps.PrivateAppPolicy;
public final class PrivateAppPolicyChecks {
 static void check(boolean value) {if(!value)throw new AssertionError();}
 static void appScope(boolean expected,String url,String start,String scope) {
  if(PrivateAppPolicy.inAppScope(url,start,scope)!=expected)
   throw new AssertionError("App scope expected "+expected+": url="+url+", start="+start+", scope="+scope);
 }
 public static void main(String[] args) {
  appScope(true,"https://m.youtube.com/","https://www.youtube.com/","https://www.youtube.com/");
  // Presentation aliases must not expand generic origin or metadata/transport permission checks.
  check(!PrivateAppPolicy.sameOrigin("https://m.youtube.com/","https://www.youtube.com/"));
  check(!PrivateAppPolicy.inScope("https://m.youtube.com/","https://www.youtube.com/"));
  String[] aliases={"youtube.com","www.youtube.com","m.youtube.com"};
  for(String startHost:aliases)for(String scopeHost:aliases)for(String targetHost:aliases)
   appScope(true,"https://"+targetHost+"/watch?v=fixture#time","https://"+startHost+"/","https://"+scopeHost+"/");
  appScope(true,"https://M.YOUTUBE.COM:443/","https://WWW.YOUTUBE.COM:443/","https://YOUTUBE.COM:443/");
  appScope(true,"https://m.youtube.com:443/watch?v=fixture","https://www.youtube.com/","https://www.youtube.com/");
  appScope(true,"https://m.youtube.com/","https://www.youtube.com/","");
  appScope(true,"https://m.youtube.com/","https://www.youtube.com/"," \t");
  appScope(true,"https://m.youtube.com/","https://www.youtube.com/",null);
  appScope(true,"https://m.youtube.com/app","https://www.youtube.com/app/start","https://www.youtube.com/app");
  appScope(true,"https://m.youtube.com/app/page?x=1","https://www.youtube.com/app/start","https://youtube.com/app/");
  appScope(false,"https://m.youtube.com/app","https://www.youtube.com/app/start","https://www.youtube.com/app/");
  for(String url:new String[]{"https://m.youtube.com/application","https://m.youtube.com/outside",
   "https://m.youtube.com/app/../outside","https://m.youtube.com/app/./page","https://m.youtube.com/app/x/../page",
   "https://m.youtube.com/app/%2e%2e/outside",
   "https://m.youtube.com/app/%2Foutside","https://m.youtube.com/app/%5Coutside","https://m.youtube.com/app/%252e%252e/outside"})
   appScope(false,url,"https://www.youtube.com/app/start","https://www.youtube.com/app");
  appScope(false,"https://m.youtube.com/app/page","https://www.youtube.com/","https://www.youtube.com/app/");
  appScope(false,"https://m.youtube.com/app/page","https://www.youtube.com/app/start","https://youtube.com/app/%2e/");
  appScope(false,"https://m.youtube.com/app/page","https://www.youtube.com/app/./start","https://youtube.com/app/");
  appScope(false,"https://m.youtube.com/app/page","https://www.youtube.com/app/start","https://youtube.com/app/./");
  for(String url:new String[]{"https://music.youtube.com/","https://accounts.google.com/","https://youtu.be/",
   "https://studio.youtube.com/","https://evil.youtube.com/","https://www.youtube.com.evil.test/",
   "https://youtube.com.evil.test/","https://evil.test/","https://127.0.0.1/","https://[::1]/",
   "http://m.youtube.com/","https://m.youtube.com:444/","https://m.youtube.com:0/","https://m.youtube.com:65536/",
   "https://m.youtube.com./","https://user@m.youtube.com/","https://m.youtube.com\\@evil.test/"})
   appScope(false,url,"https://www.youtube.com/","https://www.youtube.com/");
  for(String start:new String[]{"https://music.youtube.com/","https://youtu.be/","https://evil.test/","https://127.0.0.1/",
   "http://www.youtube.com/","https://www.youtube.com:444/","https://user@www.youtube.com/","https://www.youtube.com./",
   "https://www.youtube.com/app/%2f/","https://www.youtube.com\\@evil.test/",null,""})
   appScope(false,"https://m.youtube.com/",start,"https://www.youtube.com/");
  for(String scope:new String[]{"https://music.youtube.com/","https://youtu.be/","https://evil.test/","https://127.0.0.1/",
   "http://www.youtube.com/","https://www.youtube.com:444/","https://user@www.youtube.com/","https://www.youtube.com./",
   "https://www.youtube.com/%2f/","https://www.youtube.com\\@evil.test/"})
   appScope(false,"https://m.youtube.com/","https://www.youtube.com/",scope);
  // Other installed apps retain their exact existing same-origin scope, including YouTube Music.
  appScope(true,"https://music.youtube.com/watch?v=fixture","https://music.youtube.com/","https://music.youtube.com/");
  appScope(false,"https://www.youtube.com/","https://music.youtube.com/","https://music.youtube.com/");
  appScope(false,"https://m.youtube.com/","https://music.youtube.com/","https://music.youtube.com/");
  appScope(true,"https://example.test/app/page","https://example.test/app/","https://example.test/app/");
  appScope(false,"https://example.test/application","https://example.test/app/","https://example.test/app/");
  appScope(true,"https://studio.youtube.com/page","https://studio.youtube.com/","https://studio.youtube.com/");
  appScope(false,null,"https://www.youtube.com/","https://www.youtube.com/");
  check(PrivateAppPolicy.safeUrl("https://example.test/app",false));
  for(String url:new String[]{"javascript:alert(1)","file:///a","content://vault/a","https://user:pass@example.test/a","https://example.test/\nsecret","https://example.test\\@evil.test/","https://example.test:99999/"})check(!PrivateAppPolicy.safeUrl(url,false));
  check(!PrivateAppPolicy.safeUrl("http://example.test/app",false));
  check(!PrivateAppPolicy.safeUrl("https://"+"a".repeat(56)+".onion./",false));
  check(PrivateAppPolicy.inScope("https://example.test","https://example.test/"));
  check(PrivateAppPolicy.inScope("https://example.test/app/page","https://example.test/app/"));
  check(!PrivateAppPolicy.inScope("https://example.test/application","https://example.test/app/"));
  check(!PrivateAppPolicy.inScope("https://evil.test/app/","https://example.test/app/"));
  check(!PrivateAppPolicy.inScope("https://example.test/app/%2e%2e/private","https://example.test/app/"));
  check(!PrivateAppPolicy.inScope("https://example.test/app/../private","https://example.test/app/"));
  check(PrivateAppPolicy.sameOrigin("https://EXAMPLE.test:443/a","https://example.test/b"));
  check(!PrivateAppPolicy.sameOrigin("https://example.test:444/a","https://example.test/b"));
  check(PrivateAppPolicy.validName("My private app"));
  check(!PrivateAppPolicy.validName(" "));
  check(!PrivateAppPolicy.validName("bad\nname"));
  check(!PrivateAppPolicy.validName("bad\ud800"));
  check(!PrivateAppPolicy.validName("x".repeat(121)));
  System.out.println("PrivateAppPolicy checks PASS");
 }
}
