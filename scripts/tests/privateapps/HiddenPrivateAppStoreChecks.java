import uk.co.traynor.privategallery.core.privateapps.*;
import uk.co.traynor.privategallery.core.hidden.HiddenSession;
import java.nio.file.*;
import java.util.*;
public final class HiddenPrivateAppStoreChecks {
 static void check(boolean v){if(!v)throw new AssertionError();}
 static void rejects(Runnable r){try{r.run();}catch(RuntimeException e){return;}throw new AssertionError("stale operation accepted");}
 static void unlock(HiddenSession s){check(s.complete(s.beginAuthentication(),new byte[32]));}
 public static void main(String[] args)throws Exception {
  Path root=Files.createTempDirectory("privateapps-store");
  var session=new HiddenSession(()->100L);unlock(session);
  var vpn=new HiddenPrivateAppStore(root.toFile(),session,session.authority(),false);
  var tor=new HiddenPrivateAppStore(root.toFile(),session,session.authority(),true);
  var app=new PrivateAppCatalog.App(UUID.randomUUID().toString(),"Secret title","https://example.test/private","","browser","",true,false,true);
  vpn.update(c->c.upsert(app));check(vpn.read().apps.equals(List.of(app)));check(tor.read().apps.isEmpty());
  try(var files=Files.walk(root)){for(Path p:files.filter(Files::isRegularFile).toList())check(!new String(Files.readAllBytes(p),java.nio.charset.StandardCharsets.ISO_8859_1).contains("Secret title"));}
  // Swapping encrypted VPN metadata into Tor must fail even with the same vault key.
  Path vpnFile=root.resolve("hidden-private-apps-v1/metadata-8925faed-2c5f-4975-a05e-c58fbbfe1702.enc");
  Path torFile=root.resolve("hidden-private-apps-v1/metadata-8925faed-2c5f-4975-a05e-c58fbbfe1701.enc");
  Files.copy(vpnFile,torFile);rejects(tor::read);rejects(()->tor.update(c->c.upsert(app)));Files.delete(torFile);
  byte[] original=Files.readAllBytes(vpnFile),corrupt=original.clone();corrupt[corrupt.length-1]^=1;Files.write(vpnFile,corrupt);
  rejects(vpn::read);rejects(()->vpn.update(c->c.remove(app.id())));check(Arrays.equals(corrupt,Files.readAllBytes(vpnFile)));
  Files.write(vpnFile,original);
  session.lock();rejects(vpn::read);rejects(()->vpn.update(c->c.remove(app.id())));
  unlock(session);rejects(vpn::read);
  var resumed=new HiddenPrivateAppStore(root.toFile(),session,session.authority(),false);
  check(resumed.read().apps.equals(List.of(app)));
  rejects(()->resumed.update(c->{session.lock();return c.remove(app.id());}));
  unlock(session);check(new HiddenPrivateAppStore(root.toFile(),session,session.authority(),false).read().apps.equals(List.of(app)));
  var active=new java.util.concurrent.atomic.AtomicBoolean(true);
  var fenced=new HiddenPrivateAppStore(root.toFile(),session,session.authority(),false,active::get,Runnable::run);
  rejects(()->fenced.update(c->{active.set(false);return c.remove(app.id());}));
  check(new HiddenPrivateAppStore(root.toFile(),session,session.authority(),false).read().apps.equals(List.of(app)));
  // Move-to is one encrypted publish under the original authority in each Hidden owner.
  for(boolean torMode:new boolean[]{false,true}) {
   var live=new java.util.concurrent.atomic.AtomicBoolean(true);
   var commits=new java.util.concurrent.atomic.AtomicInteger();
   var store=new HiddenPrivateAppStore(root.toFile(),session,session.authority(),torMode,live::get,commit->{commits.incrementAndGet();commit.run();});
   var first=new PrivateAppCatalog.App(UUID.randomUUID().toString(),"Move first","https://example.test/","","browser","",false,true,false);
   var rich=new PrivateAppCatalog.App(UUID.randomUUID().toString(),"Move rich","https://example.test/app","https://example.test/","standalone","AQID",true,false,true,false,"Retained report");
   store.update(c->new PrivateAppCatalog(torMode,List.of(first,rich)));
   int before=commits.get();
   var moved=PrivateAppCommands.execute(store,14,java.nio.ByteBuffer.allocate(4).putInt(0).array(),rich.id());
   check(commits.get()==before+1);check(moved.apps.equals(List.of(rich,first)));
   check(new HiddenPrivateAppStore(root.toFile(),session,session.authority(),torMode).read().apps.equals(moved.apps));
   live.set(false);
   rejects(()->PrivateAppCommands.execute(store,14,new byte[4],first.id()));check(commits.get()==before+1);
   live.set(true);session.lock();
   rejects(()->PrivateAppCommands.execute(store,14,new byte[4],first.id()));
   unlock(session);
   rejects(()->PrivateAppCommands.execute(store,14,new byte[4],first.id()));
   check(new HiddenPrivateAppStore(root.toFile(),session,session.authority(),torMode).read().apps.equals(moved.apps));
  }
  session.lock();try(var files=Files.walk(root)){for(Path p:files.sorted(Comparator.reverseOrder()).toList())Files.delete(p);}
  System.out.println("HiddenPrivateAppStore checks PASS");
 }
}
