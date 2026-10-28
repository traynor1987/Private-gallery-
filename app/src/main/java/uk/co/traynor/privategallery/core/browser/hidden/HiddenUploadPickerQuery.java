package uk.co.traynor.privategallery.core.browser.hidden;

import java.util.*;
import java.util.function.Predicate;

/** Immutable metadata snapshot. Pages are bounded; the authenticated result set is not truncated. */
public final class HiddenUploadPickerQuery {
 public static final int PAGE_SIZE=64;
 public static final class Entry {
  public final String id,name,mime;
  public final boolean restricted,complete;
  public Entry(String id,String name,String mime,boolean restricted,boolean complete) {
   if(id==null || id.length()>64)throw new IllegalArgumentException("Invalid media identity");
   this.id=id;this.name=bound(name,200);this.mime=bound(mime,128);this.restricted=restricted;this.complete=complete;
  }
 }
 private final List<Entry> entries;
 private final Set<String> ids;
 public final int restrictedCount,typeCount;
 public HiddenUploadPickerQuery(List<Entry> source,Predicate<String> accepts) {
  ArrayList<Entry> accepted=new ArrayList<>();HashSet<String> identities=new HashSet<>();int restricted=0,types=0;
  for(Entry entry:source) {
   if(!entry.complete)continue;
   if(entry.restricted){restricted++;continue;}
   if(!accepts.test(entry.mime)){types++;continue;}
   if(identities.add(entry.id))accepted.add(entry);
  }
  entries=List.copyOf(accepted);ids=Set.copyOf(identities);restrictedCount=restricted;typeCount=types;
 }
 private static String bound(String text,int max) {if(text==null)return "";return text.substring(0,Math.min(text.length(),max));}
 public int size(){return entries.size();}
 public boolean contains(String id){return ids.contains(id);}
 public List<Entry> page(int offset) {if(offset<0 || offset>entries.size())throw new IllegalArgumentException("Invalid page");return entries.subList(offset,offset+Math.min(PAGE_SIZE,entries.size()-offset));}
}
