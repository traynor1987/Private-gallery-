import uk.co.traynor.privategallery.core.privateapps.*;
import java.util.*;
import java.util.function.UnaryOperator;
public final class PrivateAppCommandChecks {
 static final class Store implements PrivateAppStore {
  PrivateAppCatalog value=new PrivateAppCatalog(false,List.of());
  public Owner owner(){return Owner.PRIMARY;}
  public PrivateAppCatalog read(){return value;}
  public PrivateAppCatalog update(UnaryOperator<PrivateAppCatalog> f){return value=f.apply(value);}
 }
 static void rejects(Runnable r){try{r.run();}catch(IllegalArgumentException e){return;}throw new AssertionError();}
 public static void main(String[] args) {
  var store=new Store();var app=new PrivateAppCatalog.App(UUID.randomUUID().toString(),"Fixture","https://example.test/","","browser","",false,true,false);
  byte[] payload=new PrivateAppCatalog(false,List.of(app)).encode();
  if(PrivateAppCommands.execute(store,1,payload,"").apps.size()!=1)throw new AssertionError();
  rejects(()->PrivateAppCommands.execute(store,1,new byte[PrivateAppCommands.MAX_COMMAND_BYTES+1],""));
  rejects(()->PrivateAppCommands.execute(store,0,payload,""));
  rejects(()->PrivateAppCommands.execute(store,2,null,"../bad"));
  if(!PrivateAppCommands.execute(store,2,null,app.id()).apps.isEmpty())throw new AssertionError();
  System.out.println("PrivateAppCommand checks PASS");
 }
}
