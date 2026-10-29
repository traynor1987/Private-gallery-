package uk.co.traynor.privategallery.core.browser.v2;

import java.util.Locale;
import java.util.Map;

/** Recognises common HTML file-accept extensions without relaxing vault egress policy. */
public final class BrowserAcceptTypes {
 private static final Map<String, String> EXTENSIONS = Map.ofEntries(
  Map.entry(".jpg", "image/jpeg"), Map.entry(".jpeg", "image/jpeg"),
  Map.entry(".png", "image/png"), Map.entry(".webp", "image/webp"),
  Map.entry(".gif", "image/gif"), Map.entry(".heic", "image/heic"),
  Map.entry(".heif", "image/heif"), Map.entry(".mp4", "video/mp4"),
  Map.entry(".webm", "video/webm"), Map.entry(".mov", "video/quicktime"),
  Map.entry(".mkv", "video/x-matroska"), Map.entry(".pdf", "application/pdf"),
  Map.entry(".txt", "text/plain"), Map.entry(".csv", "text/csv"));
 private BrowserAcceptTypes() {}
 public static String safeUploadName(String mime) {
  return "upload." + switch(mime.toLowerCase(Locale.ROOT)) {
   case "image/jpeg" -> "jpg";
   case "image/png" -> "png";
   case "image/webp" -> "webp";
   case "image/gif" -> "gif";
   case "image/heic" -> "heic";
   case "image/heif" -> "heif";
   case "video/mp4" -> "mp4";
   case "video/webm" -> "webm";
   case "video/quicktime" -> "mov";
   case "video/x-matroska" -> "mkv";
   case "application/pdf" -> "pdf";
   case "text/plain" -> "txt";
   case "text/csv" -> "csv";
   default -> "bin";
  };
 }
 public static boolean accepts(String mime, String[] requested) {
  String actual = mime.toLowerCase(Locale.ROOT);
  boolean specified = false;
  for (String entry : requested) {
   for (String part : entry.split(",")) {
    String token = part.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
    if (token.isEmpty()) continue;
    specified = true;
    if (token.equals("*/*") || token.equals(actual) || actual.equals(EXTENSIONS.get(token)) ||
       (token.endsWith("/*") && actual.startsWith(token.substring(0, token.length() - 1)))) return true;
   }
  }
  return !specified;
 }
}
