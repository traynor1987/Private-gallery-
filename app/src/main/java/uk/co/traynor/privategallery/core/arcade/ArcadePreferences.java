package uk.co.traynor.privategallery.core.arcade;
import java.util.*;
/** Same exact schema for local loads and portable archive preflight; malformed saves never reset silently. */
public final class ArcadePreferences {
 public static final String NAME="nova_arcade_v1";
 public static final Set<String> GAMES=Set.of("2048","sudoku","snake");
 public static final Set<String> ACHIEVEMENTS=Set.of("tile-2048","sudoku-solved","sudoku-no-hints","snake-10","snake-25");
 private ArcadePreferences(){}
 public static void validate(Map<String,?> values){for(Map.Entry<String,?> e:values.entrySet()){String k=e.getKey();Object v=e.getValue();switch(k){
 case "sound":case "haptics":ArcadeCodec.require(v instanceof Boolean);break;
 case "speed":ArcadeCodec.require(v instanceof Integer&&(Integer)v>=1&&(Integer)v<=5);break;
 case "best:2048":ArcadeCodec.require(v instanceof Long&&(Long)v>=0&&(Long)v<=Long.MAX_VALUE/2);break;
 case "best:snake":ArcadeCodec.require(v instanceof Integer&&(Integer)v>=0&&(Integer)v<=253);break;
 case "sudoku-wins":ArcadeCodec.require(v instanceof Integer&&(Integer)v>=0&&(Integer)v<=1_000_000);break;
 case "achievements":ArcadeCodec.require(v instanceof Set<?> &&ACHIEVEMENTS.containsAll((Set<?>)v));break;
 case "recent":ArcadeCodec.require(v instanceof String);String s=(String)v;ArcadeCodec.require(s.length()<=30);List<String> games=s.isEmpty()?List.of():Arrays.asList(s.split(",",-1));ArcadeCodec.require(games.size()<=3&&GAMES.containsAll(games)&&new HashSet<>(games).size()==games.size());break;
 case "save:2048":ArcadeCodec.require(v instanceof String);Game2048.decode((String)v);break;
 case "save:sudoku":ArcadeCodec.require(v instanceof String);SudokuGame.decode((String)v);break;
 case "save:snake":ArcadeCodec.require(v instanceof String);SnakeGame.decode((String)v);break;
 default:throw new IllegalArgumentException("Unsupported Arcade preference");
 }}}
}
