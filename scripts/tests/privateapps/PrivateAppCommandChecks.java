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
 static byte[] position(int index){return java.nio.ByteBuffer.allocate(4).putInt(index).array();}
 static void moveToChecks() {
  check(PrivateAppCommands.MOVE_TO==14);
  var store=new Store();var a=app("A");var b=app("B");
  var rich=new PrivateAppCatalog.App(UUID.randomUUID().toString(),"Rich","https://example.test/start","https://example.test/","standalone","AQID",true,false,true,false,"Website compatibility retained");
  store.value=new PrivateAppCatalog(false,List.of(a,b,rich));
  var moved=PrivateAppCommands.execute(store,14,position(0),rich.id());
  check(moved.apps.equals(List.of(rich,a,b)));check(moved.apps.get(0)==rich);
  check(store.updates==1 && store.reads==0);
  check(PrivateAppCommands.execute(store,14,position(2),rich.id()).apps.equals(List.of(a,b,rich)));
  check(PrivateAppCommands.execute(store,14,position(1),rich.id()).apps.equals(List.of(a,rich,b)));
  check(PrivateAppCatalog.decode(store.value.encode(),false).apps.equals(store.value.apps));
  var before=store.value.apps;
  for(byte[] invalid:new byte[][]{null,new byte[0],new byte[3],new byte[5],position(-1),position(32),position(Integer.MAX_VALUE)})
   rejects(()->PrivateAppCommands.execute(store,14,invalid,rich.id()));
  rejects(()->PrivateAppCommands.execute(store,14,position(3),rich.id()));
  rejects(()->PrivateAppCommands.execute(store,14,position(0),UUID.randomUUID().toString()));
  for(String id:new String[]{"",rich.id().toUpperCase(java.util.Locale.ROOT),"../bad"})rejects(()->PrivateAppCommands.execute(store,14,position(0),id));
  check(store.value.apps.equals(before));
  var full=new ArrayList<PrivateAppCatalog.App>();for(int i=0;i<PrivateAppCatalog.MAX_APPS;i++)full.add(app("App "+i));
  store.value=new PrivateAppCatalog(false,full);
  var expected=new ArrayList<>(full);var selected=expected.remove(0);expected.add(31,selected);
  check(PrivateAppCommands.execute(store,14,position(31),selected.id()).apps.equals(expected));
  check(PrivateAppCommands.execute(store,14,position(0),selected.id()).apps.equals(full));
  // Resolve the target against the list supplied by the atomic store update.
  PrivateAppStore latest=new PrivateAppStore(){
   public Owner owner(){return Owner.HIDDEN_TOR;}
   public PrivateAppCatalog read(){throw new AssertionError("No separate read allowed");}
   public PrivateAppCatalog update(UnaryOperator<PrivateAppCatalog> change){return change.apply(new PrivateAppCatalog(true,List.of(rich)));}
  };
  rejects(()->PrivateAppCommands.execute(latest,14,position(1),rich.id()));
 }
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
  moveToChecks();
  System.out.println("PrivateAppCommand checks PASS");
 }
}
