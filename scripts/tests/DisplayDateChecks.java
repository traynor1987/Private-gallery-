import java.time.*;
import uk.co.traynor.privategallery.core.media.DisplayDateTime;
public class DisplayDateChecks {
 public static void main(String[] args) {
  long winter=DisplayDateTime.resolve(2026,1,24,12,34,56,"Europe/London");
  long summer=DisplayDateTime.resolve(2026,7,24,12,34,56,"Europe/London");
  if(winter!=Instant.parse("2026-01-24T12:34:56Z").toEpochMilli())throw new AssertionError("winter zone");
  if(summer!=Instant.parse("2026-07-24T11:34:56Z").toEpochMilli())throw new AssertionError("summer zone");
  for(int[] date:new int[][]{{2026,3,29,1,30},{2026,10,25,1,30},{2026,2,30,12,0}}) {
   try{DisplayDateTime.resolve(date[0],date[1],date[2],date[3],date[4],0,"Europe/London");throw new AssertionError("invalid/ambiguous date accepted");}catch(DateTimeException|IllegalArgumentException expected){}
  }
  System.out.println("display date timezone/DST/invalid date checks passed");
 }
}
