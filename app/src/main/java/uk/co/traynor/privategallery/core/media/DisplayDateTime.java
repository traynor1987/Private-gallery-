package uk.co.traynor.privategallery.core.media;
import java.time.*;
/** Never silently normalise a DST gap or choose one side of an ambiguous local time. */
public final class DisplayDateTime {
 private DisplayDateTime(){}
 public static long resolve(int year,int month,int day,int hour,int minute,int second,String zoneName) {
  LocalDateTime local=LocalDateTime.of(year,month,day,hour,minute,second);
  ZoneId zone=ZoneId.of(zoneName);var offsets=zone.getRules().getValidOffsets(local);
  if(offsets.size()!=1)throw new IllegalArgumentException("Missing or ambiguous local time");
  long epoch=local.toInstant(offsets.get(0)).toEpochMilli();
  if(epoch<=0||epoch>253402300799000L)throw new IllegalArgumentException("Date out of range");
  return epoch;
 }
}
