import uk.co.traynor.privategallery.core.privateapps.PrivateAppPolicy;
public final class PrivateAppPolicyChecks {
 static void check(boolean value) {if(!value)throw new AssertionError();}
 public static void main(String[] args) {
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
