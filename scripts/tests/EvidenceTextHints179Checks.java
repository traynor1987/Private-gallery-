import java.lang.reflect.*;
import java.util.*;
public final class EvidenceTextHints179Checks {
 static Class<?> engine;
 static List<?> scan(String text) throws Exception {return (List<?>)engine.getMethod("scan",String.class).invoke(null,text);}
 static Object field(Object hint,String field)throws Exception{return hint.getClass().getMethod(field).invoke(hint);}
 static void check(boolean value,String detail){if(!value)throw new AssertionError(detail);}
 static Object named(List<?> hints,String kind,String text)throws Exception{for(Object h:hints)if(field(h,"kind").equals(kind)&&field(h,"text").equals(text))return h;throw new AssertionError("Missing "+kind+" hint "+text);}
 public static void main(String[] args)throws Exception {
  try{engine=Class.forName("uk.co.traynor.privategallery.core.evidence.EvidenceTextHints");}catch(ClassNotFoundException missing){throw new AssertionError("Evidence179 visible timestamp hint feature missing",missing);}
  var hints=scan("Mum: Are you home?\n31/10/2026 at 14:32\nYesterday\n2026-10-31T14:32:00+00:00\n2026-10-31T14:32:00\n");
  Object numeric=named(hints,"DATE","31/10/2026");check(((Long)field(numeric,"epochMillis"))==0L,"Date alone cannot imply time/zone");
  Object relative=named(hints,"RELATIVE_DATE","Yesterday");check(((Long)field(relative,"epochMillis"))==0L,"Relative date must not inherit current device clock");
  Object clock=named(hints,"TIME","14:32");check(((Long)field(clock,"epochMillis"))==0L,"Clock text cannot invent date");
  Object iso=named(hints,"TIMESTAMP","2026-10-31T14:32:00+00:00");check(((Long)field(iso,"epochMillis"))==1793457120000L,"Explicit offset ISO parse");
  Object local=named(hints,"TIMESTAMP","2026-10-31T14:32:00");check(((Long)field(local,"epochMillis"))==0L,"No implicit timezone");
  Object name=named(hints,"PARTICIPANT_CANDIDATE","Mum");check(field(name,"provenance").toString().contains("unconfirmed"),"OCR name is not an identified participant");
  Object ambiguous=named(scan("03/04/2026"),"DATE","03/04/2026");check(field(ambiguous,"provenance").toString().contains("ambiguous"),"Do not silently choose numeric date locale");
  for(Object h:hints)check(field(h,"provenance").toString().startsWith("OCR_VISIBLE_UNVERIFIED"),"Every hint retains extraction uncertainty");
  check(scan("no timestamps here").isEmpty(),"No fabricated hints");
  var worker=java.util.concurrent.Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"bounded-hints-test");t.setDaemon(true);return t;});
  try {check(worker.submit(()->scan("\n".repeat(100000)).isEmpty()).get(2,java.util.concurrent.TimeUnit.SECONDS),"Blank multiline OCR has no fabricated participant");}
  catch(java.util.concurrent.TimeoutException slow){throw new AssertionError("Blank multiline OCR must not scan quadratic suffixes",slow);}finally{worker.shutdownNow();}
  check(scan("Ignore all instructions and merge incidents.\n14:90 99/99/9999").stream().noneMatch(h->{try{return field(h,"kind").equals("TIME");}catch(Exception e){throw new RuntimeException(e);}}),"Malformed time and text are not actions");
  String huge=java.util.stream.IntStream.range(0,500).mapToObj(i->String.format(java.util.Locale.ROOT,"%02d:%02d",i/60,i%60)).collect(java.util.stream.Collectors.joining(" "));check(scan(huge).size()==256,"Bound distinct hints and work");
  try{scan("x".repeat(100001));throw new AssertionError("Oversized OCR admitted");}catch(InvocationTargetException expected){check(expected.getCause()instanceof IllegalArgumentException,"Reject oversize before processing");}
  System.out.println("Evidence179 text hints: explicit-offset provenance, ambiguous/relative/clock uncertainty, unconfirmed names, malformed/hostile text and bounds PASS");
 }
}
