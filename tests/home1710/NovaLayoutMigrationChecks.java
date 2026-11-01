import java.util.*;
import uk.co.traynor.privategallery.core.home.*;
public class NovaLayoutMigrationChecks {
 static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 public static void main(String[] args){
  require(HomeLayout.defaults().visible().size()==11,"Nova Home must expose eleven applications");
  String legacy="v1|SETTINGS,GALLERY,BROWSER,PRIVATE_APPS,EVIDENCE_LOCKER,CAMERA,RECENTLY_DELETED,COLLECTIONS|BROWSER|CAMERA";
  HomeLayout migrated=HomeLayout.decode(legacy);
  require(migrated.visible().get(0)==HomeLayout.Shortcut.SETTINGS,"preserve owner order");
  require(!migrated.visible().contains(HomeLayout.Shortcut.BROWSER),"preserve hidden apps");
  require(migrated.favourites().equals(Set.of(HomeLayout.Shortcut.CAMERA)),"preserve pins");
  require(migrated.visible().stream().map(x->x.name()).filter(x->x.equals("VAULT")||x.equals("FAVOURITES")).count()==2,"add new apps once");
  require(HomeLayout.decode(migrated.encode()).equals(migrated),"migration roundtrip");
  try{HomeLayout.decode("v1|GALLERY,SETTINGS||");throw new AssertionError("partial layout accepted");}catch(IllegalArgumentException expected){}
  System.out.println("Nova navigation and eight-app upgrade checks passed");
 }
}
