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
  session.lock();rejects(vpn::read);rejects(()->vpn.update(c->c.remove(app.id())));
  unlock(session);rejects(vpn::read);
  var resumed=new HiddenPrivateAppStore(root.toFile(),session,session.authority(),false);
  check(resumed.read().apps.equals(List.of(app)));
  rejects(()->resumed.update(c->{session.lock();return c.remove(app.id());}));
  unlock(session);check(new HiddenPrivateAppStore(root.toFile(),session,session.authority(),false).read().apps.equals(List.of(app)));
  session.lock();try(var files=Files.walk(root)){for(Path p:files.sorted(Comparator.reverseOrder()).toList())Files.delete(p);}
  System.out.println("HiddenPrivateAppStore checks PASS");
 }
}
