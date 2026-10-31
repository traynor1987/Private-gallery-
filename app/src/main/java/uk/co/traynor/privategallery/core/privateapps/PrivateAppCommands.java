package uk.co.traynor.privategallery.core.privateapps;

/** Bounded operation protocol, applied atomically by the vault-owning process. */
public final class PrivateAppCommands {
 private PrivateAppCommands(){}
 public static final int MAX_COMMAND_BYTES=16384;
 public static final int READ=0, UPSERT=1, REMOVE=2, PIN=3, UNPIN=4, MOVE_UP=5, MOVE_DOWN=6, SHOW_ADDRESS=7, HIDE_ADDRESS=8, REQUIRE_AUTH=9, NO_AUTH=10, RESET_SETTINGS=11, APP_STYLE=12, BROWSER_STYLE=13, MOVE_TO=14;
 public static PrivateAppCatalog execute(PrivateAppStore store,int action,byte[] payload,String id) {
  if(id==null || id.length()>36 || (payload!=null && payload.length>MAX_COMMAND_BYTES))throw new IllegalArgumentException("Invalid private app request");
  switch(action) {
   case READ:
    if(payload!=null || !id.isEmpty())throw new IllegalArgumentException("Invalid read");
    return store.read();
   case UPSERT:
    if(payload==null || !id.isEmpty())throw new IllegalArgumentException("Invalid save");
    PrivateAppCatalog value=PrivateAppCatalog.decode(payload,store.owner()==PrivateAppStore.Owner.HIDDEN_TOR);
    if(value.apps.size()!=1)throw new IllegalArgumentException("One app required");
    return store.update(c->c.upsert(value.apps.get(0)));
   case REMOVE:
    requireEntryRequest(payload,id);
    return store.update(c->c.remove(id));
   case PIN:
   case UNPIN:
   case SHOW_ADDRESS:
   case HIDE_ADDRESS:
   case REQUIRE_AUTH:
   case NO_AUTH:
   case RESET_SETTINGS:
   case APP_STYLE:
   case BROWSER_STYLE:
    requireEntryRequest(payload,id);
    // Resolve inside the store's atomic update, preserving newer metadata and order.
    return store.update(c->{
     for(PrivateAppCatalog.App app:c.apps)if(app.id().equals(id))return c.upsert(new PrivateAppCatalog.App(
      app.id(),app.name(),app.startUrl(),app.scope(),app.display(),action==RESET_SETTINGS?"":app.icon(),
      action!=RESET_SETTINGS && (action==PIN || (action!=UNPIN && app.pinned())),action==RESET_SETTINGS || action==SHOW_ADDRESS || (action!=HIDE_ADDRESS && app.addressBar()),
      action==REQUIRE_AUTH || (action!=NO_AUTH && app.requireAuthentication()),
      action==RESET_SETTINGS || action==APP_STYLE || (action!=BROWSER_STYLE && app.appStyle()),app.discoveryReport()));
     return c;
    });
   case MOVE_TO:
    requireEntryRequest(null,id);
    if(payload==null || payload.length!=4)throw new IllegalArgumentException("Invalid move payload");
    int index=java.nio.ByteBuffer.wrap(payload).getInt();
    if(index<0 || index>=PrivateAppCatalog.MAX_APPS)throw new IllegalArgumentException("Invalid move destination");
    return store.update(c->c.moveTo(id,index));
   case MOVE_UP:
   case MOVE_DOWN:
    requireEntryRequest(payload,id);
    return store.update(c->c.move(id,action==MOVE_UP?-1:1));
   default:throw new IllegalArgumentException("Unknown private app request");
  }
 }
 private static void requireEntryRequest(byte[] payload,String id) {
  if(payload!=null || !id.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))throw new IllegalArgumentException("Invalid private app entry request");
 }
}
