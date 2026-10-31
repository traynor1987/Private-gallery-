import uk.co.traynor.privategallery.core.privateapps.*;
import java.util.*;
public final class PrivateAppCatalogChecks {
 static void check(boolean v){if(!v)throw new AssertionError();}
 static void fails(Runnable r){try{r.run();}catch(IllegalArgumentException e){return;}throw new AssertionError("accepted invalid catalogue");}
 public static void main(String[] args)throws Exception {
  var first=new PrivateAppCatalog.App(UUID.randomUUID().toString(),"Example","https://example.test/app/","https://example.test/app/","standalone","",false,false,true);
  var second=new PrivateAppCatalog.App(UUID.randomUUID().toString(),"Other","https://other.test/","","browser","",true,true,false);
  var catalog=new PrivateAppCatalog(false,List.of(first,second));
  var decoded=PrivateAppCatalog.decode(catalog.encode(),false);
  check(decoded.apps.equals(catalog.apps));
  // Code47's v1 bytes must survive migration with standalone presentation enabled.
  var oldBytes=new java.io.ByteArrayOutputStream();
  try(var out=new java.io.DataOutputStream(oldBytes)) {
   out.writeInt(1);out.writeInt(1);
   for(String value:List.of(first.id(),first.name(),first.startUrl(),first.scope(),first.display(),first.icon()))out.writeUTF(value);
   out.writeBoolean(first.pinned());out.writeBoolean(first.addressBar());out.writeBoolean(first.requireAuthentication());
  }
  var migrated=PrivateAppCatalog.decode(oldBytes.toByteArray(),false).apps.get(0);
  check((Boolean)migrated.getClass().getMethod("appStyle").invoke(migrated));
  check(migrated.id().equals(first.id()) && migrated.requireAuthentication());
  check(decoded.upsert(first.withName("Renamed")).apps.get(0).name().equals("Renamed"));
  check(decoded.move(second.id(),-1).apps.get(0).id().equals(second.id()));
  check(decoded.remove(first.id()).apps.equals(List.of(second)));
  fails(()->new PrivateAppCatalog(false,List.of(first,first)));
  fails(()->new PrivateAppCatalog(false,List.of(first.withName("bad\nname"))));
  fails(()->new PrivateAppCatalog(false,List.of(new PrivateAppCatalog.App(first.id(),"Bad","https://example.test/","https://evil.test/","standalone","",false,false,true))));
  fails(()->new PrivateAppCatalog(false,List.of(new PrivateAppCatalog.App(first.id(),"Bad icon","https://example.test/","","browser","===",false,false,true))));
  byte[] encoded=catalog.encode();byte[] tail=Arrays.copyOf(encoded,encoded.length+1);
  fails(()->PrivateAppCatalog.decode(tail,false));
  fails(()->PrivateAppCatalog.decode(new byte[500_000],false));
  System.out.println("PrivateAppCatalog checks PASS");
 }
}
