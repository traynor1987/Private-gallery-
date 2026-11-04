package uk.co.traynor.novaos.preview;
import java.net.URI;
/** Preview-only policy: provider restrictions and foreground media ownership. */
public final class PreviewAppPolicy {
 private PreviewAppPolicy() {}
 public static boolean googleAccount(String url) {
  try { URI u=new URI(url); return "https".equalsIgnoreCase(u.getScheme()) && "accounts.google.com".equalsIgnoreCase(u.getHost()) && u.getRawUserInfo()==null; }
  catch(Exception e){return false;}
 }
 public static boolean canPresentExternal(String tab,String profile,String active,String current,boolean foreground){return foreground&&tab.equals(active)&&profile.equals(current);}
 public static boolean keepRunning(boolean foreground,boolean selected,boolean sameProfile){return foreground&&selected&&sameProfile;}
}
