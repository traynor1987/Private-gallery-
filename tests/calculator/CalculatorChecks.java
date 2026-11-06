import uk.co.traynor.privategallery.core.calculator.*;
import uk.co.traynor.privategallery.core.hidden.HiddenPinThrottle;
import java.util.*;
public final class CalculatorChecks {
 static int checks;
 static void check(boolean v){checks++;if(!v)throw new AssertionError("Check "+checks);}
 static void eq(String expr,String result){check(CalculatorEngine.canonical(CalculatorEngine.evaluate(expr,false)).equals(result));}
 static void bad(Runnable r){try{r.run();throw new AssertionError("Accepted invalid input");}catch(IllegalArgumentException expected){checks++;}}
 public static void main(String[] args){
  eq("0.1+0.2","0.3");eq("2+3*4","14");eq("(2+3)*4","20");eq("-2^2","-4");eq("2^-3","0.125");eq("2^3^2","512");eq("200+10%","200.1");eq("200*(1+10%)","220");eq("50%%","0.005");eq("5!","120");eq("0!","1");check(CalculatorEngine.evaluate("sqrt(2)^2",false).subtract(new java.math.BigDecimal("2")).abs().doubleValue()<1e-32);eq("sin(30)","0.49999999999999994");eq("cos(180)","-1");eq("log(1000)","3");eq("ln(e)","1");
  check(CalculatorEngine.canonical(CalculatorEngine.evaluate("sin(4.4)",true)).equals(java.math.BigDecimal.valueOf(StrictMath.sin(4.4)).toString()));
  check(Math.abs(CalculatorEngine.evaluate("sin(pi/2)",true).doubleValue()-1)<1e-14);
  check(CalculatorEngine.format(CalculatorEngine.evaluate("1234567.5",false)).equals("1,234,567.5"));
  check(CalculatorEngine.format(new java.math.BigDecimal("0.000001234567890123456789012345678901234")).equals("0.000001234567890123456789012345678901234"));
  check(CalculatorEngine.canonical(CalculatorEngine.evaluate("1000!",false)).length()<50);
  eq("sin(0)","0");eq("cos(0)","1");eq("tan(0)","0");check(CalculatorEngine.evaluate("sin(1E-323)",true).signum()>0);check(CalculatorEngine.evaluate("tan(1E-323)",true).signum()>0);
  bad(()->CalculatorEngine.evaluate("sin(1E-323)",false));bad(()->CalculatorEngine.evaluate("cos(1E-323)",false));bad(()->CalculatorEngine.evaluate("tan(-1E-323)",false));bad(()->CalculatorPreferences.decode("AAAAAQAAAAEAC3NpbigxRS0zMjMpAAEwAA=="));
  eq("0^0.5","0");eq("0^1.5","0");eq("9^0.5","3");eq("(1E-300)^2","1E-600");
  for(String s:List.of("(1E-300)^1.5","(1E-300)^999.5","(1E-1000)^0.5","0^-0.5","1/0","sqrt(-1)","ln(0)","tan(90)","(-1)^0.5","1001!","1.2!","2^1001","1e10001","1e-10001","sin(1e500)","2(3)",".","1+","()","NaN","1".repeat(513),"(".repeat(33)+"1"+")".repeat(33)))bad(()->CalculatorEngine.evaluate(s,false));
  var widest=Collections.nCopies(50,new CalculatorPreferences.Entry("1"+"\u2003".repeat(511),"1",false));check(CalculatorPreferences.decode(CalculatorPreferences.encode(widest)).equals(widest));
  bad(()->CalculatorPreferences.decode("AAAAAQAAAAEADCgxRS0zMDApXjEuNQABMAA="));
  bad(()->CalculatorPreferences.decode("AAAAAQAAAAEADigxRS0zMDApXjk5OS41AAEwAA=="));
  var item=new CalculatorPreferences.Entry("0.1+0.2","0.3",false);
  String encoded=CalculatorPreferences.encode(List.of(item));check(CalculatorPreferences.decode(encoded).equals(List.of(item)));
  CalculatorPreferences.validate(Map.of("history",encoded,"memory","1E+100","angle","DEG","history-enabled",true,"scientific",true));
  bad(()->CalculatorPreferences.validate(Map.of("angle","bad")));bad(()->CalculatorPreferences.validate(Map.of("memory",Double.NaN)));bad(()->CalculatorPreferences.validate(Map.of("memory","1e10001")));bad(()->CalculatorPreferences.validate(Map.of("secret","hello")));bad(()->CalculatorPreferences.decode(encoded+"="));bad(()->CalculatorPreferences.validate(Map.of("history-enabled",false,"history",encoded)));
  bad(()->CalculatorPreferences.encode(List.of(new CalculatorPreferences.Entry("1+1","3",false))));
  long[] clock={10};var gesture=new CalculatorGesture(()->clock[0]);for(int i=0;i<4;i++)check(!gesture.hold());check(gesture.hold());check(gesture.consume());check(!gesture.consume());for(int i=0;i<4;i++)gesture.hold();clock[0]+=15000;check(!gesture.hold());gesture.reset();for(int i=0;i<4;i++)gesture.hold();gesture.reset();check(!gesture.hold());
  var state=HiddenPinThrottle.empty();for(int i=0;i<4;i++){state=state.failed(100+i,1000+i,1);check(state.remaining(100+i)==0);}state=state.failed(104,1004,1);check(state.remaining(104)==30000);check(state.remaining(30104)==0);state=state.failed(30104,31004,1);check(state.remaining(30104)==60000);check(HiddenPinThrottle.decode(state.encode()).remaining(30104)==60000);
  var reboot=state.rebase(20,31004,2);check(reboot.remaining(20)==60000);check(reboot.rebase(20,Long.MIN_VALUE,3).remaining(20)<=300000);check(HiddenPinThrottle.recovery(10,20,3).remaining(10)==300000);
  for(int i=0;i<20;i++)state=state.failed(1000000+i,1000000+i,1);check(state.remaining(1000020)<=300000);bad(()->HiddenPinThrottle.decode(new byte[]{1}));
  System.out.println("Calculator/throttle host checks passed: "+checks);
 }
}
