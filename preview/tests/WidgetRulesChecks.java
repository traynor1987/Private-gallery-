import uk.co.traynor.novaos.preview.WidgetRules;
import java.util.*;
public class WidgetRulesChecks {
 static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
 public static void main(String[] args){
  int[] a={0,0,2,2},b={2,0,2,2};
  check(!WidgetRules.overlap(a,b),"touching edges must not collide");check(WidgetRules.overlap(a,new int[]{1,1,2,2}),"overlap");
  check(WidgetRules.fits(new int[]{0,0,4,4},List.of(),-1),"4x4 fits");check(!WidgetRules.fits(new int[]{3,0,2,2},List.of(),-1),"outside columns");
  check(!WidgetRules.fits(a,List.of(a),-1),"occupied slot");check(WidgetRules.fits(a,List.of(a),0),"self ignored");
  check(Arrays.equals(WidgetRules.firstFree(2,2,List.of(a)),b),"automatic placement snaps beside first");
  check(WidgetRules.compatible(2,2,2,2)&&!WidgetRules.compatible(2,2,4,1),"stacks share exact size");
  check(!WidgetRules.pipEligible(false,true,false)&&WidgetRules.pipEligible(true,true,false)&&!WidgetRules.pipEligible(true,true,true),"PiP only eligible visible native video outside lock");
  check(WidgetRules.active(8,3)==2&&WidgetRules.active(-1,3)==0,"stale stack index clamped");
  System.out.println("13 widget placement/stack/PiP policy checks passed");
 }
}
