package uk.co.traynor.privategallery.core.browser.hidden;

import uk.co.traynor.privategallery.core.hidden.HiddenSession;
import uk.co.traynor.privategallery.core.hidden.HiddenRecordStore;
import java.nio.file.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Executable with only JDK17; no Android runtime or owner data. */
public final class HiddenBrowserLibraryHostChecks {
 private static void check(boolean value) {if(!value)throw new AssertionError();}
 private static void rejects(Runnable action) {try {action.run();}catch(RuntimeException expected){return;}throw new AssertionError("Expected rejection");}
 private static void unlock(HiddenSession session,int key) {byte[] bytes=new byte[32];Arrays.fill(bytes,(byte)key);check(session.complete(session.beginAuthentication(),bytes));}
 public static void main(String[] args)throws Exception {
  Path root=Files.createTempDirectory("hidden-library-checks");
  AtomicLong clock=new AtomicLong(100);
  HiddenSession session=new HiddenSession(clock::get);
  try {
   unlock(session,7);
   HiddenBrowserLibrary vpn=new HiddenBrowserLibrary(root.toFile(),session,session.authority(),true);
   HiddenBrowserLibrary tor=new HiddenBrowserLibrary(root.toFile(),session,session.authority(),false);
   check(vpn.savesHistory() && !tor.savesHistory());
   vpn.bookmark("https://example.test/secret-bookmark");vpn.visit("https://example.test/secret-history");tor.visit("https://example.test/tor-disabled");
   check(tor.history().isEmpty() && tor.bookmarks().isEmpty());
   tor.bookmark("http://example.test/tor-bookmark");tor.saveHistory(true);tor.visit("http://example.test/tor-history");
   session.lock();unlock(session,7);
   rejects(()->vpn.bookmark("https://example.test/stale"));
   HiddenBrowserLibrary restarted=new HiddenBrowserLibrary(root.toFile(),session,session.authority(),true);
   check(restarted.bookmarks().equals(List.of("https://example.test/secret-bookmark")));
   check(restarted.history().equals(List.of("https://example.test/secret-history")));
   HiddenBrowserLibrary torRestart=new HiddenBrowserLibrary(root.toFile(),session,session.authority(),false);
   check(torRestart.savesHistory() && torRestart.history().size()==1 && torRestart.bookmarks().size()==1);
   restarted.clearBrowsingData();check(restarted.history().isEmpty() && restarted.bookmarks().size()==1 && torRestart.history().size()==1);
   restarted.saveHistory(false);restarted.visit("https://example.test/not-saved");check(restarted.history().isEmpty());
   rejects(()->restarted.bookmark("https://user:password@example.test/"));
   rejects(()->restarted.bookmark("file:///secret"));
   rejects(()->restarted.bookmark("https://example.test/"+"x".repeat(2048)));
   for(int i=0;i<55;i++)restarted.bookmark("https://example.test/b"+i);
   check(restarted.bookmarks().size()==50);
   restarted.saveHistory(true);for(int i=0;i<105;i++)restarted.visit("https://example.test/h"+i);
   check(restarted.history().size()==100);
   try(var files=Files.walk(root)) {for(Path file:files.filter(Files::isRegularFile).toList()) {String disk=new String(Files.readAllBytes(file),java.nio.charset.StandardCharsets.ISO_8859_1);check(!disk.contains("example.test") && !disk.contains("secret-bookmark"));}}
   session.lock();unlock(session,8);
   HiddenBrowserLibrary wrong=new HiddenBrowserLibrary(root.toFile(),session,session.authority(),true);
   rejects(wrong::bookmarks);rejects(()->wrong.bookmark("https://example.test/wrong-key"));
   session.lock();unlock(session,7);
   HiddenBrowserLibrary good=new HiddenBrowserLibrary(root.toFile(),session,session.authority(),true);check(good.bookmarks().size()==50);
   // A write staged by old authority must not promote after lock/re-authentication.
   CountDownLatch staged=new CountDownLatch(1),resume=new CountDownLatch(1);
   AtomicBoolean rejected=new AtomicBoolean();
   HiddenRecordStore controlled=new HiddenRecordStore(root.resolve("hidden-browser-library-v1").toFile(),dir->{staged.countDown();try {if(!resume.await(5,TimeUnit.SECONDS))throw new IOException("Timed out");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException(e);}});
   HiddenBrowserLibrary old=new HiddenBrowserLibrary(session,session.authority(),true,controlled);
   Thread worker=new Thread(()->{try {old.bookmark("https://example.test/late-write");}catch(RuntimeException expected){rejected.set(true);}});worker.start();
   check(staged.await(5,TimeUnit.SECONDS));session.lock();unlock(session,7);resume.countDown();worker.join(5000);check(!worker.isAlive() && rejected.get());
   HiddenBrowserLibrary after=new HiddenBrowserLibrary(root.toFile(),session,session.authority(),true);check(!after.bookmarks().contains("https://example.test/late-write"));
   // Separate wrappers share one mutation gate: read/modify/promote cannot lose an update.
   after.clearHistory();
   AtomicReference<Throwable> error=new AtomicReference<>();
   ArrayList<Thread> writers=new ArrayList<>();
   for(int i=0;i<8;i++) {final int number=i;Thread writer=new Thread(()->{try {new HiddenBrowserLibrary(root.toFile(),session,session.authority(),true).visit("https://example.test/concurrent"+number);}catch(Throwable failure){error.set(failure);}});writers.add(writer);writer.start();}
   for(Thread writer:writers)writer.join(5000);
   check(error.get()==null && writers.stream().noneMatch(Thread::isAlive) && after.history().size()==8);
   try(var files=Files.walk(root)) {check(files.noneMatch(path->path.getFileName().toString().startsWith("staged-")));}
   session.onBackground(30_000);clock.addAndGet(30_000);session.onForeground();rejects(after::history);
   System.out.println("HiddenBrowserLibrary host checks passed: encryption, bounds, mode/credential isolation, restart, clear, expiry and stale promotion");
  } finally {session.lock();try(var paths=Files.walk(root)){for(Path path:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);}}
 }
}
