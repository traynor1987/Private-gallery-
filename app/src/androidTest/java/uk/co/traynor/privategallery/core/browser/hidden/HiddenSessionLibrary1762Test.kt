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
   assertFalse(vpn.usesGoogleSearch());assertFalse(tor.usesGoogleSearch())
   vpn.useGoogleSearch(true)
   assertFalse(tor.usesGoogleSearch())
   vpn.bookmark("https://example.test/saved");vpn.visit("https://example.test/visited")
   tor.visit("https://example.test/tor");assertTrue(tor.history().isEmpty());assertTrue(tor.bookmarks().isEmpty())
   session.lock();session.complete(session.beginAuthentication(),ByteArray(32){7})
   assertTrue(runCatching {vpn.bookmark("https://example.test/stale")}.isFailure)
   val restarted=HiddenBrowserLibrary(root,session,session.authority(),true)
   assertTrue(restarted.usesGoogleSearch())
   assertEquals(listOf("https://example.test/saved"),restarted.bookmarks())
   assertEquals(listOf("https://example.test/visited"),restarted.history())
   restarted.clearBrowsingData();assertTrue(restarted.history().isEmpty());assertEquals(1,restarted.bookmarks().size)
   restarted.saveHistory(false);restarted.visit("https://example.test/no-history");assertTrue(restarted.history().isEmpty())
   root.walkTopDown().filter {it.isFile}.forEach {assertFalse(it.readBytes().toString(Charsets.ISO_8859_1).contains("example.test"))}
   session.lock();session.complete(session.beginAuthentication(),ByteArray(32){8})
   assertTrue(runCatching {HiddenBrowserLibrary(root,session,session.authority(),true).bookmarks()}.isFailure)
  } finally {session.lock();root.deleteRecursively()}
 }
 @Test fun primaryAndHiddenEncryptedBookmarksAndHistoryStayIndependent() {
  val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
  val root = java.io.File(context.cacheDir, "browser-records-${java.util.UUID.randomUUID()}")
  val primaryKey = ByteArray(32) { 19 }
  val session = uk.co.traynor.privategallery.core.hidden.HiddenSession(android.os.SystemClock::elapsedRealtime)
  try {
   assertTrue(session.complete(session.beginAuthentication(), ByteArray(32) { 61 }))
   val primaryBookmarks = uk.co.traynor.privategallery.core.browser.EncryptedBookmarkStore(java.io.File(root, "primary-bookmarks"), primaryKey)
   val primaryHistory = uk.co.traynor.privategallery.core.browser.v2.EncryptedBrowserHistoryStore(java.io.File(root, "primary-history"), primaryKey)
   val hidden = HiddenBrowserLibrary(java.io.File(root, "hidden"), session, session.authority(), true)
   val primaryUrl = "https://primary-fixture.test/"
   val hiddenUrl = "https://hidden-fixture.test/"
   primaryBookmarks.add("Primary fixture", primaryUrl)
   primaryHistory.add("Primary fixture", primaryUrl)
   assertTrue(hidden.bookmarks().isEmpty())
   assertTrue(hidden.history().isEmpty())
   hidden.bookmark(hiddenUrl)
   hidden.visit(hiddenUrl)
   assertEquals(listOf(primaryUrl), primaryBookmarks.list().map { it.url })
   assertEquals(listOf(primaryUrl), primaryHistory.list().map { it.url })
   assertEquals(listOf(hiddenUrl), hidden.bookmarks())
   assertEquals(listOf(hiddenUrl), hidden.history())
   root.walkTopDown().filter { it.isFile }.forEach {
    val contents = it.readBytes().toString(Charsets.ISO_8859_1)
    assertFalse(contents.contains(primaryUrl))
    assertFalse(contents.contains(hiddenUrl))
   }
   hidden.clearBrowsingData()
   assertTrue(hidden.history().isEmpty())
   assertEquals(listOf(primaryUrl), primaryHistory.list().map { it.url })
   session.lock()
   assertTrue(runCatching { hidden.bookmarks() }.isFailure)
   assertEquals(listOf(primaryUrl), primaryBookmarks.list().map { it.url })
  } finally {
   session.lock()
   primaryKey.fill(0)
   root.deleteRecursively()
  }
 }
 @Test fun unsafeUrlsNeverEnterTheLibrary() {
  val library=HiddenSessionLibrary()
  listOf("file:///secret", "javascript:alert(1)", "https://user:pass@example.test/").forEach {
   library.visit(it, "");library.bookmark(it, "")
  }
  assertTrue(library.history().isEmpty());assertTrue(library.bookmarks().isEmpty())
 }
}
