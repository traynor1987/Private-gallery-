import uk.co.traynor.privategallery.core.evidence.EvidenceStrictJson;

/** Actual JVM execution of the production prevalidator, without Android's lenient parser. */
public final class EvidenceStrictJson179Checks {
 private static int checks;
 private static void valid(String value) {EvidenceStrictJson.validate(value,32,2*1024*1024);checks++;}
 private static void invalid(String value) {
  try {EvidenceStrictJson.validate(value,32,2*1024*1024);throw new AssertionError("accepted invalid JSON");}
  catch(IllegalArgumentException expected){if(!"Invalid or over-limit JSON".equals(expected.getMessage()))throw new AssertionError("unsanitized failure");checks++;}
 }
 public static void main(String[] args) {
  for(String value:new String[]{"{}","[]","null","true","false","0","-0","123","-12.5e+3"," \r\n\t{\"a\":[true,false,null,-1.25e-3],\"b\":\"text \\\" \\/ \\b \\f \\n \\r \\t \\u0041 \\uD83D\\uDE00\"}","{\"ignored\":\"/*\\\"*/ [[[[\"}","[".repeat(32)+"0"+"]".repeat(32)})valid(value);
  for(String value:new String[]{""," ","/*\"*/{}","{'a':1}","{a:1}","{\"a\":/*\"*/[0]/*\"*/}","//\"\n{}","{\"a\":1}//comment","{}{}","{} trailing","[1,]","[,1]","{\"a\":1,}","{\"a\"=1}","{\"a\";1}","{\"a\":undefined}","NaN","Infinity","+1","01","-01","1.",".1","1e","1e+","--1","tru","truefalse","\"raw\ncontrol\"","\"\\x20\"","\"\\u123\"","\"\\uD800\"","\"\\uDC00\"","{\"a\":1,\"a\":2}","{\"a\":1,\"\\u0061\":2}","[".repeat(33)+"0"+"]".repeat(33),"{\"ignored\":/*\"*/"+"[".repeat(12000)+"0"+"]".repeat(12000)+"/*\"*/}"})invalid(value);
  try {EvidenceStrictJson.validate("{}",32,1);throw new AssertionError("character quota bypass");}catch(IllegalArgumentException expected){checks++;}
  try {EvidenceStrictJson.validate("[]",65,100);throw new AssertionError("unsafe configured depth");}catch(IllegalArgumentException expected){checks++;}
  // Large flat input exercises a bound independent of recursion depth.
  invalid("["+"0,".repeat(100001)+"0]");
  System.out.println("EvidenceStrictJson179Checks: "+checks+" passed");
 }
}
