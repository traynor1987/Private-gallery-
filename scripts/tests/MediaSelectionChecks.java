import java.time.*;
import java.util.*;
import uk.co.traynor.privategallery.core.ui.MediaSelection;
public class MediaSelectionChecks {
 static int checks;
 static void check(boolean value){checks++;if(!value)throw new AssertionError("check "+checks);}
 public static void main(String[] args){
  ZoneId zone=ZoneId.of("Europe/London");LocalDate day=LocalDate.of(2026,10,25);
  long begin=day.atStartOfDay(zone).toInstant().toEpochMilli();
  long next=day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();
  check(next-begin==25*3600_000L);
  check(MediaSelection.date(begin,day,day,zone));check(MediaSelection.date(next-1,day,day,zone));
  check(!MediaSelection.date(next,day,day,zone));check(!MediaSelection.date(begin-1,day,day,zone));
  check(!MediaSelection.date(0,day,day,zone));check(!MediaSelection.date(-1,day,day,zone));
  boolean rejected=false;try{MediaSelection.date(begin,day.plusDays(1),day,zone);}catch(IllegalArgumentException e){rejected=true;}check(rejected);
  check(MediaSelection.size(101,0,100,0));check(!MediaSelection.size(100,0,100,0));
  check(MediaSelection.size(99,1,100,0));check(!MediaSelection.size(100,1,100,0));
  check(MediaSelection.size(100,2,100,200));check(MediaSelection.size(200,2,100,200));
  check(!MediaSelection.size(-1,1,100,0));check(MediaSelection.size(Long.MAX_VALUE,0,100,0));
  rejected=false;try{MediaSelection.size(10,2,20,10);}catch(IllegalArgumentException e){rejected=true;}check(rejected);
  check(MediaSelection.drag(Arrays.asList("a","b","c","d"),3,1).equals(new LinkedHashSet<>(Arrays.asList("b","c","d"))));
  check(MediaSelection.drag(Arrays.asList("a","b"),-1,1).isEmpty());
  check(MediaSelection.retain(new LinkedHashSet<>(Arrays.asList("b","hidden")),Arrays.asList("a","b")).equals(Set.of("b")));
  System.out.println("Media selection: "+checks+" checks PASS");
 }
}
