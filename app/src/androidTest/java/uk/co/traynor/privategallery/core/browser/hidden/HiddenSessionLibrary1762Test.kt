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
 @Test fun encryptedLibrarySurvivesReauthenticationAndKeepsModesIndependent() {
  val root=java.io.File(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.noBackupFilesDir,"library-test-${java.util.UUID.randomUUID()}")
  val session=uk.co.traynor.privategallery.core.hidden.HiddenSession { android.os.SystemClock.elapsedRealtime() }
  try {
   session.complete(session.beginAuthentication(),ByteArray(32){7})
   val vpn=HiddenBrowserLibrary(root,session,session.authority(),true)
   val tor=HiddenBrowserLibrary(root,session,session.authority(),false)
   assertTrue(vpn.savesHistory());assertFalse(tor.savesHistory())
   vpn.bookmark("https://example.test/saved");vpn.visit("https://example.test/visited")
   tor.visit("https://example.test/tor");assertTrue(tor.history().isEmpty());assertTrue(tor.bookmarks().isEmpty())
   session.lock();session.complete(session.beginAuthentication(),ByteArray(32){7})
   assertTrue(runCatching {vpn.bookmark("https://example.test/stale")}.isFailure)
   val restarted=HiddenBrowserLibrary(root,session,session.authority(),true)
   assertEquals(listOf("https://example.test/saved"),restarted.bookmarks())
   assertEquals(listOf("https://example.test/visited"),restarted.history())
   restarted.clearBrowsingData();assertTrue(restarted.history().isEmpty());assertEquals(1,restarted.bookmarks().size)
   restarted.saveHistory(false);restarted.visit("https://example.test/no-history");assertTrue(restarted.history().isEmpty())
   root.walkTopDown().filter {it.isFile}.forEach {assertFalse(it.readBytes().toString(Charsets.ISO_8859_1).contains("example.test"))}
   session.lock();session.complete(session.beginAuthentication(),ByteArray(32){8})
   assertTrue(runCatching {HiddenBrowserLibrary(root,session,session.authority(),true).bookmarks()}.isFailure)
  } finally {session.lock();root.deleteRecursively()}
 }
 @Test fun unsafeUrlsNeverEnterTheLibrary() {
  val library=HiddenSessionLibrary()
  listOf("file:///secret", "javascript:alert(1)", "https://user:pass@example.test/").forEach {
   library.visit(it, "");library.bookmark(it, "")
  }
  assertTrue(library.history().isEmpty());assertTrue(library.bookmarks().isEmpty())
 }
}
