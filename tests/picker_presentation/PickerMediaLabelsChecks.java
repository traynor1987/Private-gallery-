import java.lang.reflect.Method;
public final class PickerMediaLabelsChecks {
 public static void main(String[] args) throws Exception {
  Class<?> labels;
  try { labels=Class.forName("uk.co.traynor.privategallery.ui.PickerMediaLabels"); }
  catch(ClassNotFoundException missing){throw new AssertionError("Shared picker duration labels are missing",missing);}
  Method duration=labels.getMethod("duration",Long.class);
  check("Duration unavailable",duration.invoke(null,new Object[]{null}));
  check("Duration unavailable",duration.invoke(null,-1L));
  check("0:00",duration.invoke(null,0L));check("1:05",duration.invoke(null,65000L));
  check("1:01:01",duration.invoke(null,3661000L));
 }
 static void check(String expected,Object actual){if(!expected.equals(actual))throw new AssertionError(expected+" != "+actual);}
}
