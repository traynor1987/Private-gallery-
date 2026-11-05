package uk.co.traynor.novaos.preview;
import java.util.Set;
public final class FavouritesRules{
 private static final Set<String> STATES=Set.of("LOGIN_REQUIRED","CHECKING","FAVOURITES_ACTIVE","FILTER_UNAVAILABLE","COMPATIBILITY_ERROR");
 public static boolean state(String s){return STATES.contains(s);}
 public static boolean reveal(String state,String reason,boolean currentDocument){return currentDocument&&"FAVOURITES_ACTIVE".equals(state)&&"SELECTED".equals(reason);}
}
