import uk.co.traynor.privategallery.core.privateapps.*;
import java.util.*;
import java.util.function.UnaryOperator;
public final class PrivateAppCommandChecks {
 static final class Store implements PrivateAppStore {
  PrivateAppCatalog value=new PrivateAppCatalog(false,List.of());
  int reads,updates;
  public Owner owner(){return Owner.PRIMARY;}
  public PrivateAppCatalog read(){reads++;return value;}
  public PrivateAppCatalog update(UnaryOperator<PrivateAppCatalog> f){updates++;return value=f.apply(value);}
 }
 static void rejects(Runnable r){try{r.run();}catch(IllegalArgumentException e){return;}throw new AssertionError("Expected invalid command");}
 static void check(boolean condition){if(!condition)throw new AssertionError();}
 static PrivateAppCatalog.App app(String name){return new PrivateAppCatalog.App(UUID.randomUUID().toString(),name,"https://example.test/","","browser","",false,true,false);}
 public static void main(String[] args) {
  var store=new Store();var first=app("First");var second=app("Second");var third=app("Third");
  byte[] payload=new PrivateAppCatalog(false,List.of(first)).encode();
  check(PrivateAppCommands.execute(store,PrivateAppCommands.UPSERT,payload,"").apps.size()==1);
  store.value=store.value.upsert(second).upsert(third);
  // Pinning operates on the current record, never an old client-side copy.
  store.value=store.value.upsert(new PrivateAppCatalog.App(second.id(),"Updated second",second.startUrl(),second.scope(),second.display(),second.icon(),false,false,true));
  var pinned=PrivateAppCommands.execute(store,PrivateAppCommands.PIN,null,second.id());
  check(pinned.apps.get(1).pinned());check(pinned.apps.get(1).name().equals("Updated second"));
  check(!pinned.apps.get(1).addressBar());check(pinned.apps.get(1).requireAuthentication());
  check(pinned.apps.stream().map(PrivateAppCatalog.App::id).toList().equals(List.of(first.id(),second.id(),third.id())));
  check(PrivateAppCommands.execute(store,PrivateAppCommands.PIN,null,second.id()).apps.get(1).pinned());
  check(!PrivateAppCommands.execute(store,PrivateAppCommands.UNPIN,null,second.id()).apps.get(1).pinned());
  check(PrivateAppCommands.execute(store,PrivateAppCommands.MOVE_UP,null,second.id()).apps.get(0).id().equals(second.id()));
  check(PrivateAppCommands.execute(store,PrivateAppCommands.MOVE_UP,null,second.id()).apps.get(0).id().equals(second.id()));
  check(PrivateAppCommands.execute(store,PrivateAppCommands.MOVE_DOWN,null,second.id()).apps.get(1).id().equals(second.id()));
  check(PrivateAppCommands.execute(store,PrivateAppCommands.MOVE_DOWN,null,third.id()).apps.get(2).id().equals(third.id()));
  check(store.reads==0);check(store.updates==8);
  var hiddenAddress=PrivateAppCommands.execute(store,PrivateAppCommands.HIDE_ADDRESS,null,first.id()).apps.get(0);
  check(!hiddenAddress.addressBar());check(!hiddenAddress.pinned());
  PrivateAppCommands.execute(store,PrivateAppCommands.PIN,null,first.id());
  var shownAddress=PrivateAppCommands.execute(store,PrivateAppCommands.SHOW_ADDRESS,null,first.id()).apps.get(0);
  check(shownAddress.addressBar());check(shownAddress.pinned());
  check(PrivateAppCommands.execute(store,PrivateAppCommands.REQUIRE_AUTH,null,first.id()).apps.get(0).requireAuthentication());
  var reset=PrivateAppCommands.execute(store,PrivateAppCommands.RESET_SETTINGS,null,first.id()).apps.get(0);
  check(reset.requireAuthentication());check(!reset.pinned());check(reset.addressBar());check(reset.startUrl().equals(first.startUrl()));
  check(!PrivateAppCommands.execute(store,PrivateAppCommands.BROWSER_STYLE,null,first.id()).apps.get(0).appStyle());
  check(PrivateAppCommands.execute(store,PrivateAppCommands.APP_STYLE,null,first.id()).apps.get(0).appStyle());
  check(PrivateAppCatalog.decode(store.read().encode(),false).apps.get(0).appStyle());
  check(!PrivateAppCommands.execute(store,PrivateAppCommands.NO_AUTH,null,first.id()).apps.get(0).requireAuthentication());
  var barrier=new PrivateAppClearBarrier();var old=barrier.begin();var current=barrier.begin();
  check(!barrier.complete(old,()->{throw new AssertionError();}));check(barrier.pending());
  check(barrier.complete(current,()->{}));check(!barrier.pending());check(!barrier.complete(current,()->{throw new AssertionError();}));
  var removed=PrivateAppCommands.execute(store,PrivateAppCommands.REMOVE,null,second.id());
  for(int action:new int[]{PrivateAppCommands.PIN,PrivateAppCommands.UNPIN,PrivateAppCommands.MOVE_UP,PrivateAppCommands.MOVE_DOWN,PrivateAppCommands.SHOW_ADDRESS,PrivateAppCommands.HIDE_ADDRESS,PrivateAppCommands.REQUIRE_AUTH,PrivateAppCommands.NO_AUTH,PrivateAppCommands.RESET_SETTINGS,PrivateAppCommands.APP_STYLE,PrivateAppCommands.BROWSER_STYLE}) {
   check(PrivateAppCommands.execute(store,action,null,second.id()).apps.equals(removed.apps));
   rejects(()->PrivateAppCommands.execute(store,action,payload,first.id()));
   rejects(()->PrivateAppCommands.execute(store,action,null,"../bad"));
   rejects(()->PrivateAppCommands.execute(store,action,null,""));
  }
  int writes=store.updates;
  rejects(()->PrivateAppCommands.execute(store,1,new byte[PrivateAppCommands.MAX_COMMAND_BYTES+1],""));
  rejects(()->PrivateAppCommands.execute(store,0,payload,""));
  rejects(()->PrivateAppCommands.execute(store,2,null,"../bad"));
  rejects(()->PrivateAppCommands.execute(store,99,null,first.id()));
  rejects(()->PrivateAppCommands.execute(store,PrivateAppCommands.PIN,null,null));
  check(store.updates==writes);
  System.out.println("PrivateAppCommand checks PASS");
 }
}
