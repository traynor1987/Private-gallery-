package uk.co.traynor.privategallery.core.calculator;
import java.math.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
public final class CalculatorEngineTest {
 private void equal(String expression,String expected){assertEquals(expected,CalculatorEngine.canonical(CalculatorEngine.evaluate(expression,false)));}
 @Test public void decimalArithmeticAndPrecedenceArePredictable(){
  equal("0.1+0.2","0.3");equal("2+3*4","14");equal("(2+3)*4","20");equal("-2^2","-4");equal("(-2)^2","4");equal("2^-3","0.125");equal("2^3^2","512");equal("200+10%","200.1");equal("200*(1+10%)","220");equal("50%%","0.005");equal("5!","120");equal("0!","1");equal("1/3","0.3333333333333333333333333333333333");
  for(int i=1;i<200;i++){String n=BigDecimal.valueOf(i,2).toPlainString();equal(n+"+"+n,BigDecimal.valueOf(i*2,2).stripTrailingZeros().toPlainString());}
 }
 @Test public void scientificModesAndFormattingHaveBoundedPrecision(){
  assertEquals(BigDecimal.valueOf(StrictMath.sin(4.4)),CalculatorEngine.evaluate("sin(4.4)",true));
  assertEquals(.5,CalculatorEngine.evaluate("sin(30)",false).doubleValue(),1e-14);assertEquals(1,CalculatorEngine.evaluate("sin(pi/2)",true).doubleValue(),1e-14);equal("cos(180)","-1");equal("log(1000)","3");equal("ln(e)","1");equal("sqrt(4)","2");equal("9^0.5","3");
  assertTrue(CalculatorEngine.evaluate("sqrt(2)^2",false).subtract(new BigDecimal("2")).abs().doubleValue()<1e-32);
  assertEquals("0.000001234567890123456789012345678901234",CalculatorEngine.format(new BigDecimal("0.000001234567890123456789012345678901234")));
  assertEquals("1,234,567.5",CalculatorEngine.format(CalculatorEngine.evaluate("1234567.5",false)));
  assertTrue(CalculatorEngine.canonical(CalculatorEngine.evaluate("1000!",false)).length()<48);
  assertNotNull(CalculatorEngine.storedNumber(CalculatorEngine.canonical(CalculatorEngine.evaluate("1E-10000",false))));
 }
 @Test public void scientificUnderflowIsNeverPresentedAsAnExactZero(){
  for(String expression:List.of("(1E-300)^1.5","(1E-300)^999.5","(1E-1000)^0.5","sin(1E-323)","cos(1E-323)","tan(-1E-323)")){
   IllegalArgumentException failure=assertThrows(expression,IllegalArgumentException.class,()->CalculatorEngine.evaluate(expression,false));
   assertEquals("Scientific range exceeded",failure.getMessage());
  }
  equal("sin(0)","0");equal("cos(0)","1");equal("tan(0)","0");assertEquals(.5,CalculatorEngine.evaluate("sin(30)",false).doubleValue(),1e-14);
  assertTrue(CalculatorEngine.evaluate("sin(1E-323)",true).signum()>0);assertTrue(CalculatorEngine.evaluate("tan(1E-323)",true).signum()>0);
  equal("0^0.5","0");equal("0^1.5","0");equal("9^0.5","3");equal("(1E-300)^2","1E-600");
  assertThrows(IllegalArgumentException.class,()->CalculatorEngine.evaluate("0^-0.5",false));
 }
 @Test public void importedPreviouslyUnderflowedZeroResultsFailClosed(){
  // Canonical v1 streams produced by the old encoder: expressions were incorrectly accepted as zero.
  for(String encoded:List.of("AAAAAQAAAAEADCgxRS0zMDApXjEuNQABMAA=","AAAAAQAAAAEADigxRS0zMDApXjk5OS41AAEwAA==","AAAAAQAAAAEADSgxRS0xMDAwKV4wLjUAATAA","AAAAAQAAAAEAC3NpbigxRS0zMjMpAAEwAA==")){
   assertThrows(IllegalArgumentException.class,()->CalculatorPreferences.decode(encoded));
   assertThrows(IllegalArgumentException.class,()->CalculatorPreferences.validate(Map.of("history",encoded)));
  }
 }
 @Test public void invalidDomainsAndResourceBoundsFailNormally(){
  for(String bad:List.of("","1/0","sqrt(-1)","ln(0)","tan(90)","(-1)^0.5","1001!","1.2!","2^1001","1e10001","1e-10001","sin(1e500)","2(3)",".","1+","()","NaN","1".repeat(513),"(".repeat(33)+"1"+")".repeat(33),"-".repeat(100)+"2","1+".repeat(130)+"1"))assertThrows(bad,IllegalArgumentException.class,()->CalculatorEngine.evaluate(bad,false));
 }
 @Test public void actualBackupDecoderRejectsWrongTypesForgedResultsAndDisabledHistory(){
  var entries=List.of(new CalculatorPreferences.Entry("1+2","3",false),new CalculatorPreferences.Entry("sin(pi/2)","1",true));
  String encoded=CalculatorPreferences.encode(entries);assertEquals(entries,CalculatorPreferences.decode(encoded));
  CalculatorPreferences.validate(Map.of("history",encoded,"angle","RAD","history-enabled",true,"scientific",false,"memory","1E+100"));
  for(Map<String,?> bad:List.of(Map.of("angle","degrees"),Map.of("history-enabled","false"),Map.of("scientific",1),Map.of("memory","NaN"),Map.of("memory","1E+10001"),Map.of("history",encoded+"="),Map.of("history-enabled",false,"history",encoded),Map.of("secret",true)))assertThrows(IllegalArgumentException.class,()->CalculatorPreferences.validate(bad));
  assertThrows(IllegalArgumentException.class,()->CalculatorPreferences.encode(List.of(new CalculatorPreferences.Entry("1+1","3",false))));
  assertThrows(IllegalArgumentException.class,()->CalculatorPreferences.encode(Collections.nCopies(51,entries.get(0))));
  String wide="1"+"\u2003".repeat(511);var longest=Collections.nCopies(50,new CalculatorPreferences.Entry(wide,"1",false));
  assertEquals(longest,CalculatorPreferences.decode(CalculatorPreferences.encode(longest)));
  byte[] altered=Base64.getDecoder().decode(encoded);altered[7]=51;assertThrows(IllegalArgumentException.class,()->CalculatorPreferences.decode(Base64.getEncoder().encodeToString(altered)));
 }
 @Test public void gestureIsOneUseBoundedAndResettableWithoutAnyExpression(){
  long[] time={1};var sequence=new CalculatorGesture(()->time[0]);for(int i=0;i<4;i++)assertFalse(sequence.hold());assertTrue(sequence.hold());assertTrue(sequence.consume());assertFalse(sequence.consume());
  for(int i=0;i<4;i++)sequence.hold();time[0]+=15000;assertFalse(sequence.hold());sequence.reset();
  for(int i=0;i<5;i++)sequence.hold();time[0]+=1000;assertFalse(sequence.consume());
  for(int i=0;i<4;i++)sequence.hold();sequence.reset();assertFalse(sequence.hold());time[0]--;assertFalse(sequence.hold());
 }
}
