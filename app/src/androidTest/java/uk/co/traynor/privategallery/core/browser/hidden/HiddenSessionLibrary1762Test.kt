package uk.co.traynor.privategallery.core.browser.hidden

import org.junit.Assert.*
import org.junit.Test

class HiddenSessionLibrary1762Test {
 @Test fun entriesAreSessionScopedBoundedAndCleared() {
  val first=HiddenSessionLibrary();val second=HiddenSessionLibrary()
  first.bookmark("https://example.test/page", "Example")
  repeat(110) { first.visit("https://example.test/$it", "") }
  assertEquals(100,first.history().size)
  assertEquals(1,first.bookmarks().size)
  assertTrue(second.history().isEmpty());assertTrue(second.bookmarks().isEmpty())
  first.clear();assertTrue(first.history().isEmpty());assertTrue(first.bookmarks().isEmpty())
 }
 @Test fun unsafeUrlsNeverEnterTheLibrary() {
  val library=HiddenSessionLibrary()
  listOf("file:///secret", "javascript:alert(1)", "https://user:pass@example.test/").forEach {
   library.visit(it, "");library.bookmark(it, "")
  }
  assertTrue(library.history().isEmpty());assertTrue(library.bookmarks().isEmpty())
 }
}
