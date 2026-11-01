import java.util.*;
import uk.co.traynor.privategallery.core.home.*;
public class NovaBrowserAppsChecks {
 static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 public static void main(String[] args) {
  require(HomeLayout.defaults().visible().size()==11,"Separate Tor app missing from launcher model");
  String legacy="v1|SETTINGS,GALLERY,VAULT,BROWSER,PRIVATE_APPS,EVIDENCE_LOCKER,CAMERA,RECENTLY_DELETED,COLLECTIONS,FAVOURITES|BROWSER|CAMERA";
  HomeLayout upgraded=HomeLayout.decode(legacy);
  require(upgraded.visible().get(0)==HomeLayout.Shortcut.SETTINGS,"existing order lost");
  require(!upgraded.visible().contains(HomeLayout.Shortcut.BROWSER),"hidden choice lost");
  require(upgraded.favourites().equals(Set.of(HomeLayout.Shortcut.CAMERA)),"pins lost");
  require(HomeLayout.decode(upgraded.encode()).equals(upgraded),"roundtrip");
  var tor=HomeLayout.Shortcut.valueOf("TOR_BROWSER");
  require(!NovaNavigation.available(tor,false)&&NovaNavigation.available(tor,true),"Tor must be Hidden-only");
  require(NovaNavigation.browserMode(tor).equals("TOR_ONLY"),"Tor launch transport");
  require(NovaNavigation.browserMode(HomeLayout.Shortcut.BROWSER).equals("VPN_ONLY"),"Browser launch transport");
  System.out.println("Nova browser launcher upgrade checks passed");
 }
}
