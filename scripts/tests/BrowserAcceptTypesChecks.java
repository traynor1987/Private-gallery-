import uk.co.traynor.privategallery.core.browser.v2.BrowserAcceptTypes;
public final class BrowserAcceptTypesChecks {
 public static void main(String[] args) {
  check(BrowserAcceptTypes.accepts("image/jpeg",new String[]{".jpg,.png"}));
  check(BrowserAcceptTypes.accepts("image/jpeg",new String[]{".JPEG"}));
  check(BrowserAcceptTypes.accepts("image/png",new String[]{"image/*"}));
  check(BrowserAcceptTypes.accepts("video/mp4",new String[]{}));
  check(!BrowserAcceptTypes.accepts("image/png",new String[]{".jpg"}));
  check(!BrowserAcceptTypes.accepts("video/mp4",new String[]{".unknown"}));
  check(!BrowserAcceptTypes.accepts("video/mp4",new String[]{"image/*"}));
 }
 private static void check(boolean value) { if (!value) throw new AssertionError(); }
}
