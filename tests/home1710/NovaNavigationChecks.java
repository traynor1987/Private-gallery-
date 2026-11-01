import uk.co.traynor.privategallery.core.home.*;
public class NovaNavigationChecks {
 static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 public static void main(String[] args){
  require(!NovaNavigation.route(HomeLayout.Shortcut.GALLERY).equals(NovaNavigation.route(HomeLayout.Shortcut.valueOf("VAULT"))),"Gallery and Vault must be distinct");
  require(NovaNavigation.route(HomeLayout.Shortcut.valueOf("FAVOURITES")).equals("FAVOURITE"),"reuse authorised favourites");
  require(NovaNavigation.columns(328,1f)==4,"four cover columns");
  require(NovaNavigation.columns(760,1f)>4,"inner display adaptive columns");
  require(NovaNavigation.columns(328,1.8f)<4,"large text spacing");
  require(NovaNavigation.columns(150,1f)>=1,"narrow split screen safe");
  require(NovaNavigation.ownerLabel(true,true)==null,"Hide Content must never disclose Hidden ownership");
  require(NovaNavigation.ownerLabel(false,true)==null,"Hide Content suppresses all Space labels");
  require("Hidden Space".equals(NovaNavigation.ownerLabel(true,false)),"Authenticated Hidden label remains clear");
  require("GALLERY".equals(NovaNavigation.rootDestination(true)),"Concealed Home must lead to a neutral page rather than trapping navigation");
  require("HOME".equals(NovaNavigation.rootDestination(false)),"Normal root is Nova Home");
  System.out.println("Nova destination and adaptive-column checks passed");
 }
}
