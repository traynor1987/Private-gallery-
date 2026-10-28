package uk.co.traynor.privategallery.core.browser.hidden;
import java.util.*;
public final class HiddenUploadPickerQueryHostChecks {
 private static void check(boolean value){if(!value)throw new AssertionError();}
 public static void main(String[] args) {
  ArrayList<HiddenUploadPickerQuery.Entry> source=new ArrayList<>();
  for(int i=0;i<1000;i++)source.add(new HiddenUploadPickerQuery.Entry("photo-"+i,"photo"+i,"image/png",false,true));
  source.add(new HiddenUploadPickerQuery.Entry("final-transfer","Transferred final photo","image/png",false,true));
  source.add(new HiddenUploadPickerQuery.Entry("restricted","Restricted","image/png",true,true));
  source.add(new HiddenUploadPickerQuery.Entry("wrong-type","Video","video/mp4",false,true));
  source.add(new HiddenUploadPickerQuery.Entry("incomplete","Incomplete","image/png",false,false));
  HiddenUploadPickerQuery snapshot=new HiddenUploadPickerQuery(source,mime->mime.equals("image/png"));
  check(snapshot.size()==1001 && snapshot.restrictedCount==1 && snapshot.typeCount==1);
  source.clear(); // Query pages remain stable despite backing-list mutation.
  ArrayList<String> all=new ArrayList<>();
  for(int offset=0;offset<snapshot.size();) {var page=snapshot.page(offset);check(!page.isEmpty()&&page.size()<=64);for(var entry:page)all.add(entry.id);offset+=page.size();}
  check(all.size()==1001 && new HashSet<>(all).size()==1001 && all.get(1000).equals("final-transfer"));
  check(snapshot.contains("final-transfer")&&!snapshot.contains("restricted")&&!snapshot.contains("wrong-type"));
  check(snapshot.page(snapshot.size()).isEmpty());
  for(int invalid:List.of(-1,1002)) {try {snapshot.page(invalid);throw new AssertionError();}catch(IllegalArgumentException expected){}}
  var bounded=new HiddenUploadPickerQuery.Entry("id","\"".repeat(10000),"x".repeat(10000),false,true);
  check(bounded.name.length()==200 && bounded.mime.length()==128);
  try {snapshot.page(0).clear();throw new AssertionError();}catch(UnsupportedOperationException expected){}
  System.out.println("Hidden upload picker query: >500 final item, stable pages, exclusions and metadata bounds passed");
 }
}
