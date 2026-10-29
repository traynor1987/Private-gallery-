package uk.co.traynor.privategallery.core.privateapps;

/** Bounded operation protocol, applied atomically by the vault-owning process. */
public final class PrivateAppCommands {
 private PrivateAppCommands(){}
 public static final int MAX_COMMAND_BYTES=16384;
 public static PrivateAppCatalog execute(PrivateAppStore store,int action,byte[] payload,String id) {
  if(id==null || id.length()>36 || (payload!=null && payload.length>MAX_COMMAND_BYTES))throw new IllegalArgumentException("Invalid private app request");
  switch(action) {
   case 0:
    if(payload!=null || !id.isEmpty())throw new IllegalArgumentException("Invalid read");
    return store.read();
   case 1:
    if(payload==null || !id.isEmpty())throw new IllegalArgumentException("Invalid save");
    PrivateAppCatalog value=PrivateAppCatalog.decode(payload,store.owner()==PrivateAppStore.Owner.HIDDEN_TOR);
    if(value.apps.size()!=1)throw new IllegalArgumentException("One app required");
    return store.update(c->c.upsert(value.apps.get(0)));
   case 2:
    if(payload!=null || !id.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))throw new IllegalArgumentException("Invalid remove");
    return store.update(c->c.remove(id));
   default:throw new IllegalArgumentException("Unknown private app request");
  }
 }
}
