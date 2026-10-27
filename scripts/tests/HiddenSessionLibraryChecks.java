import uk.co.traynor.privategallery.core.browser.hidden.HiddenSessionLibrary;
public final class HiddenSessionLibraryChecks {
 public static void main(String[] args) {
  HiddenSessionLibrary library = new HiddenSessionLibrary();
  library.visit("https://example.com/a", "Example");
  check(library.history().size() == 1);
  library.visit("https://example.com/a", "Updated");
  check(library.history().size() == 1);
  library.bookmark("https://example.com/a", "Example");
  library.bookmark("https://example.com/a", "Example");
  check(library.bookmarks().size() == 1);
  library.visit("file:///secret", "invalid");
  library.bookmark("https://user:pass@example.com/", "invalid");
  check(library.history().size() == 1 && library.bookmarks().size() == 1);
  HiddenSessionLibrary other = new HiddenSessionLibrary();
  check(other.history().isEmpty() && other.bookmarks().isEmpty());
  library.removeBookmark("https://example.com/a");
  check(library.bookmarks().isEmpty());
  for (int i = 0; i < 110; i++) library.visit("https://example.com/" + i, "");
  check(library.history().size() == 100);
  library.clear();
  check(library.history().isEmpty() && library.bookmarks().isEmpty());
 }
 private static void check(boolean value) { if (!value) throw new AssertionError(); }
}
