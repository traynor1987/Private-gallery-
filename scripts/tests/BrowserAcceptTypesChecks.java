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
  check(BrowserAcceptTypes.safeUploadName("image/heic").equals("upload.heic"));
  check(BrowserAcceptTypes.safeUploadName("image/heif").equals("upload.heif"));
  check(BrowserAcceptTypes.safeUploadName("IMAGE/JPEG").equals("upload.jpg"));
  check(BrowserAcceptTypes.safeUploadName("unknown/type").equals("upload.bin"));
  check(BrowserAcceptTypes.accepts("application/pdf",new String[]{".pdf"}));
  check(BrowserAcceptTypes.accepts("text/csv",new String[]{".csv"}));
  check(BrowserAcceptTypes.accepts("text/plain",new String[]{".txt"}));
  check(!BrowserAcceptTypes.accepts("text/html",new String[]{".pdf,.txt,.csv"}));
  check(!BrowserAcceptTypes.accepts("application/pdf",new String[]{"image/*"}));
  check(BrowserAcceptTypes.safeUploadName("application/pdf").equals("upload.pdf"));
  check(BrowserAcceptTypes.safeUploadName("text/plain").equals("upload.txt"));
  check(BrowserAcceptTypes.safeUploadName("text/csv").equals("upload.csv"));
 }
 private static void check(boolean value) { if (!value) throw new AssertionError(); }
}
