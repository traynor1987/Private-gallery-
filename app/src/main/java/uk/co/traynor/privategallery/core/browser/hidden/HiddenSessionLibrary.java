package uk.co.traynor.privategallery.core.browser.hidden;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/** Ephemeral URLs for one Hidden browser generation; never persisted or shared with Primary. */
public final class HiddenSessionLibrary {
 private final ArrayList<String> history = new ArrayList<>();
 private final ArrayList<String> bookmarks = new ArrayList<>();
 private static boolean allowed(String url) {
  if (url == null || url.length() > 4096) return false;
  try {
   URI uri = URI.create(url);
   return ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
       && uri.getHost() != null && uri.getRawUserInfo() == null;
  } catch (IllegalArgumentException ignored) { return false; }
 }
 private static void add(ArrayList<String> target, String url, int max) {
  if (!allowed(url)) return;
  target.remove(url); target.add(0, url);
  if (target.size() > max) target.remove(target.size() - 1);
 }
 public synchronized void visit(String url, String title) { add(history, url, 100); }
 public synchronized void bookmark(String url, String title) { add(bookmarks, url, 50); }
 public synchronized void removeBookmark(String url) { bookmarks.remove(url); }
 public synchronized List<String> history() { return List.copyOf(history); }
 public synchronized List<String> bookmarks() { return List.copyOf(bookmarks); }
 public synchronized void clear() { history.clear(); bookmarks.clear(); }
}
