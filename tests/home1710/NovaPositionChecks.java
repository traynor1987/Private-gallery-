import uk.co.traynor.privategallery.core.home.NovaNavigation;
public class NovaPositionChecks {
 static void require(boolean ok,String label){if(!ok)throw new AssertionError(label);}
 public static void main(String[] args){
  Object primary=new Object(),hidden=new Object();var state=new NovaNavigation.Position();
  state.record(primary,42,"collection-a");require("collection-a".equals(state.restore(primary,42)),"Fold must restore same-session collection position");
  require(state.restore(hidden,42)==null,"no cross-Space position");require(state.restore(primary,43)==null,"no stale position after reauthentication");
  state.clear();require(state.restore(primary,42)==null,"Home/Lock clears position");
  state.record(primary,42,"collection-a");state.record(null,0,null);require(state.restore(primary,42)==null,"missing authority clears position");
  System.out.println("Nova transient navigation authority checks passed");
 }
}
