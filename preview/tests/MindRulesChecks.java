import uk.co.traynor.novaos.preview.MindRules;
public class MindRulesChecks {
 public static void main(String[] args) {
  if(!MindRules.inhale(0,4,6)||!MindRules.inhale(3999,4,6)||MindRules.inhale(4000,4,6)||!MindRules.inhale(10000,4,6))throw new AssertionError("4/6 phase boundaries");
  if(Math.abs(MindRules.expansion(2000,4,6)-.5)>0.001||Math.abs(MindRules.expansion(7000,4,6)-.5)>0.001)throw new AssertionError("circle follows timing");
  if(MindRules.elapsed(5000,1000,2000,false)!=5000||MindRules.elapsed(5000,1000,2000,true)!=6000)throw new AssertionError("paused time excluded");
  if(MindRules.remaining(10000,0)!=10000||MindRules.remaining(10000,11000)!=0)throw new AssertionError("sleep timer clamped");
  if(MindRules.groundCount(0)!=5||MindRules.groundCount(4)!=1||MindRules.groundCount(5)!=0)throw new AssertionError("grounding steps skippable");
  System.out.println("12 Mind timing and grounding checks passed");
 }
}
