import uk.co.traynor.novaos.preview.FavouritesRules;
public class FavouritesRulesChecks{
 static int n;static void check(boolean v){n++;if(!v)throw new AssertionError("Favourites check "+n);}
 public static void main(String[]a){for(String s:new String[]{"LOGIN_REQUIRED","CHECKING","FAVOURITES_ACTIVE","FILTER_UNAVAILABLE","COMPATIBILITY_ERROR"})check(FavouritesRules.state(s));for(String s:new String[]{"WORKING","All","SYNTHETIC CONTACT","+447700900123"})check(!FavouritesRules.state(s));check(FavouritesRules.reveal("FAVOURITES_ACTIVE","SELECTED",true));check(!FavouritesRules.reveal("CHECKING","SELECTED",true));check(!FavouritesRules.reveal("FAVOURITES_ACTIVE","ACTIVATION_PENDING",true));check(!FavouritesRules.reveal("FAVOURITES_ACTIVE","SELECTED",false));System.out.println(n+" Favourites state/reveal assertions passed");}
}
