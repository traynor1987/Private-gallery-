import uk.co.traynor.privategallery.core.ui.MediaOrder;
public final class MediaOrderChecks {
 public static void main(String[] args){
  if(MediaOrder.compare(0,2000,100,"old.jpg","a",1000,500,"new.jpg","b")>=0)throw new AssertionError("Added must beat capture date");
  if(MediaOrder.compare(2,2000,100,"old.jpg","a",1000,500,"new.jpg","b")<=0)throw new AssertionError("Taken must use original capture");
  if(MediaOrder.compare(3,2000,0,"a","a",1000,-1,"b","b")<=0)throw new AssertionError("Missing capture falls back added");
  if(MediaOrder.compare(4,0,0,"B","b",0,0,"a","a")<=0)throw new AssertionError("A-Z");
  if(MediaOrder.compare(5,0,0,"B","b",0,0,"a","a")>=0)throw new AssertionError("Z-A");
  if(MediaOrder.compare(0,1,1,"a","a",1,1,"a","b")>=0)throw new AssertionError("Stable ID ties");
  if(MediaOrder.valid(Long.MAX_VALUE)!=0||MediaOrder.valid(-1)!=0)throw new AssertionError("Invalid date");
  try{MediaOrder.sql(99);throw new AssertionError("Unknown order accepted");}catch(IllegalArgumentException expected){}
  System.out.println("8 media order checks passed");
 }
}
