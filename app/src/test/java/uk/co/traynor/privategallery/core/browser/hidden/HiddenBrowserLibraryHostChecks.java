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
   long visited=System.currentTimeMillis()-1000;
   String unicodeTitle="Private \uD83D\uDD12 日本語 \"title\" \\";
   vpn.bookmark("https://example.test/secret-bookmark",unicodeTitle,visited);vpn.visit("https://example.test/secret-history","History title",visited);tor.visit("https://example.test/tor-disabled");
   check(tor.history().isEmpty() && tor.bookmarks().isEmpty());
   tor.bookmark("http://example.test/tor-bookmark");tor.saveHistory(true);tor.visit("http://example.test/tor-history");
   session.lock();unlock(session,7);
   rejects(()->vpn.bookmark("https://example.test/stale"));
   HiddenBrowserLibrary restarted=new HiddenBrowserLibrary(root.toFile(),session,session.authority(),true);
   check(restarted.bookmarks().equals(List.of("https://example.test/secret-bookmark")));
   check(restarted.history().equals(List.of("https://example.test/secret-history")));
   check(restarted.snapshot().bookmarks.get(0).title.equals(unicodeTitle));
   check(restarted.snapshot().bookmarks.get(0).timestamp==visited);
   check(restarted.snapshot().history.get(0).title.equals("History title"));
   rejects(()->restarted.bookmark("https://example.test/bad","x".repeat(257),visited));
   rejects(()->restarted.bookmark("https://example.test/bad","line\nfeed",visited));
   rejects(()->restarted.bookmark("https://example.test/bad","\uD800",visited));
   rejects(()->restarted.bookmark("https://example.test/bad","title",-1));
   rejects(()->restarted.bookmark("https://example.test/bad","title",Long.MAX_VALUE));
   restarted.visit("https://example.test/secret-history","Updated title",visited+1);
   check(restarted.history().size()==1 && restarted.snapshot().history.get(0).title.equals("Updated title"));
   check(restarted.snapshot().history.get(0).timestamp==visited+1);
   // Read v1 under the same AAD/record identity without inventing title/date or rewriting on read.
   File legacyRoot=root.resolve("legacy").toFile();
   HiddenRecordStore legacyRecords=new HiddenRecordStore(new File(legacyRoot,"hidden-browser-library-v1"));
   String record="d39228ad-dc03-4875-9493-ea64192a3111";
   byte[] key=new byte[32];Arrays.fill(key,(byte)7);
   ByteArrayOutputStream plain=new ByteArrayOutputStream();
   try(DataOutputStream output=new DataOutputStream(plain)) {output.writeInt(1);output.writeBoolean(true);output.writeInt(1);output.writeUTF("https://legacy.test/bookmark");output.writeInt(1);output.writeUTF("https://legacy.test/history");}
   File stage=legacyRecords.stage("metadata",record,plain.toByteArray(),key);legacyRecords.promote(stage,"metadata",record);legacyRecords.syncPromotions();
   byte[] legacyCipher=Files.readAllBytes(legacyRecords.file("metadata",record).toPath());
   HiddenBrowserLibrary legacy=new HiddenBrowserLibrary(legacyRoot,session,session.authority(),true);
   check(legacy.snapshot().bookmarks.get(0).title.isEmpty() && legacy.snapshot().bookmarks.get(0).timestamp==0);
   check(legacy.snapshot().history.get(0).url.equals("https://legacy.test/history"));
   check(Arrays.equals(legacyCipher,Files.readAllBytes(legacyRecords.file("metadata",record).toPath())));
   legacy.bookmark("https://legacy.test/new",unicodeTitle,visited);
   HiddenBrowserLibrary migrated=new HiddenBrowserLibrary(legacyRoot,session,session.authority(),true);
   check(migrated.snapshot().bookmarks.get(0).title.equals(unicodeTitle));
   check(migrated.snapshot().bookmarks.get(1).title.isEmpty() && migrated.snapshot().bookmarks.get(1).timestamp==0);
   byte[] migratedBytes=legacyRecords.read("metadata",record,key);
   check(new DataInputStream(new ByteArrayInputStream(migratedBytes)).readInt()==2);Arrays.fill(migratedBytes,(byte)0);Arrays.fill(key,(byte)0);
   HiddenBrowserLibrary torRestart=new HiddenBrowserLibrary(root.toFile(),session,session.authority(),false);
   check(torRestart.savesHistory() && torRestart.history().size()==1 && torRestart.bookmarks().size()==1);
   restarted.clearBrowsingData();check(restarted.history().isEmpty() && restarted.bookmarks().size()==1 && torRestart.history().size()==1);
   restarted.saveHistory(false);restarted.visit("https://example.test/not-saved");check(restarted.history().isEmpty());
   rejects(()->restarted.bookmark("https://user:password@example.test/"));
   rejects(()->restarted.bookmark("file:///secret"));
   rejects(()->restarted.bookmark("https://example.test/"+"x".repeat(2048)));
   for(int i=0;i<55;i++)restarted.bookmark("https://example.test/b"+i);
   check(restarted.bookmarks().size()==50);
   restarted.bookmark("https://example.test/b54","z".repeat(256),visited);
   check(restarted.snapshot().bookmarks.size()==50 && restarted.snapshot().bookmarks.get(0).title.length()==256);
   restarted.saveHistory(true);for(int i=0;i<105;i++)restarted.visit("https://example.test/h"+i);
   check(restarted.history().size()==100);
   restarted.visit("https://example.test/h104",unicodeTitle,visited);
   check(restarted.snapshot().history.size()==100 && restarted.snapshot().history.get(0).title.equals(unicodeTitle));
   try(var files=Files.walk(root)) {for(Path file:files.filter(Files::isRegularFile).toList()) {String disk=new String(Files.readAllBytes(file),java.nio.charset.StandardCharsets.ISO_8859_1);check(!disk.contains("example.test") && !disk.contains("secret-bookmark") && !disk.contains("Private"));}}
   Map<Path,byte[]> beforeWrong=new HashMap<>();try(var files=Files.walk(root)){for(Path file:files.filter(Files::isRegularFile).toList())beforeWrong.put(file,Files.readAllBytes(file));}
   session.lock();unlock(session,8);
   HiddenBrowserLibrary wrong=new HiddenBrowserLibrary(root.toFile(),session,session.authority(),true);
   rejects(wrong::bookmarks);rejects(()->wrong.bookmark("https://example.test/wrong-key","Wrong key",visited));
   for(var item:beforeWrong.entrySet())check(Arrays.equals(item.getValue(),Files.readAllBytes(item.getKey())));
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
   System.out.println("HiddenBrowserLibrary host checks passed: encryption, metadata roundtrip/unicode/bounds, legacy migration, mode/credential isolation, wrong-key nonoverwrite, restart, clear, expiry and stale promotion");
  } finally {session.lock();try(var paths=Files.walk(root)){for(Path path:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);}}
 }
}
