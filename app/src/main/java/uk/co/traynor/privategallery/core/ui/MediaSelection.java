package uk.co.traynor.privategallery.core.ui;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/** Metadata-only selection. Callers supply only their currently authenticated scope. */
public final class MediaSelection {
 private MediaSelection() {}
 public static boolean date(long timestamp, LocalDate start, LocalDate end, ZoneId zone) {
  Objects.requireNonNull(start);Objects.requireNonNull(end);Objects.requireNonNull(zone);
  if(end.isBefore(start))throw new IllegalArgumentException("End date precedes start date");
  if(timestamp<=0)return false;
  long first=start.atStartOfDay(zone).toInstant().toEpochMilli();
  long after=end.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();
  return timestamp>=first && timestamp<after;
 }
 /** Larger/smaller are strict; between includes both bounds. Negative means unknown. */
 public static boolean size(long bytes,int mode,long first,long second) {
  if(mode<0||mode>2||first<0||(mode==2&&second<first))throw new IllegalArgumentException("Invalid size rule");
  if(bytes<0)return false;
  return mode==0?bytes>first:mode==1?bytes<first:bytes>=first&&bytes<=second;
 }
 public static Set<String> drag(List<String> ordered,int anchor,int current) {
  if(anchor<0||current<0||anchor>=ordered.size()||current>=ordered.size())return Collections.emptySet();
  return new LinkedHashSet<>(ordered.subList(Math.min(anchor,current),Math.max(anchor,current)+1));
 }
 public static Set<String> retain(Set<String> selected,Collection<String> authenticatedScope) {
  Set<String> result=new LinkedHashSet<>(selected);result.retainAll(new HashSet<>(authenticatedScope));return result;
 }
}
