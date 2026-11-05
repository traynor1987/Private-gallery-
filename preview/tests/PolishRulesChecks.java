import java.util.*;
import uk.co.traynor.novaos.preview.*;
public class PolishRulesChecks {
 public static void main(String[] args){
  if(PolishRules.edgePage(0,3,0.01f)!=0||PolishRules.edgePage(0,3,.99f)!=1)throw new AssertionError("page edges bounded");
  List<int[]> source=List.of(new int[]{0,0,2,2},new int[]{2,0,2,2});
  List<int[]> moved=PolishRules.relocate(source,0,new int[]{2,0,2,2});
  if(moved==null||moved.get(0)[0]!=2||WidgetRules.overlap(moved.get(0),moved.get(1))||source.get(0)[0]!=0)throw new AssertionError("transactional collision relocation");
  if(PolishRules.relocate(source,0,new int[]{3,0,2,2})!=null)throw new AssertionError("invalid move rejected");
  if(PolishRules.uniqueSource("Track",List.of("Track","Track"))!=-1||PolishRules.uniqueSource("Track",List.of("Other","Track"))!=1||PolishRules.uniqueSource("",List.of(""))!=-1)throw new AssertionError("no guessed media ownership");
  if(PolishRules.mediaOwner("Native track",List.of(""))!=0||PolishRules.mediaOwner("Native track",List.of("","Native track"))!=-1)throw new AssertionError("sole native source without JS metadata; mixed unknown sources fail closed");
  System.out.println("10 polish placement/media ownership assertions passed");
 }
}
