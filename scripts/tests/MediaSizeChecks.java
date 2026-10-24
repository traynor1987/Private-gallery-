import uk.co.traynor.privategallery.core.ui.MediaOrder;
public final class MediaSizeChecks {
 public static void main(String[] args){
  if(MediaOrder.sizeCompare(6,900,"b",100,"a")>=0)throw new AssertionError("largest first");
  if(MediaOrder.sizeCompare(7,900,"b",100,"a")<=0)throw new AssertionError("smallest first");
  for(int mode:new int[]{6,7}) {
   if(MediaOrder.sizeCompare(mode,-1,"a",100,"b")<=0)throw new AssertionError("unknown last");
   if(MediaOrder.sizeCompare(mode,0,"a",100,"b")>=0 && mode==7)throw new AssertionError("zero is known");
   if(MediaOrder.sizeCompare(mode,100,"a",100,"b")>=0)throw new AssertionError("stable ties");
   if(!MediaOrder.sql(mode).contains("_size"))throw new AssertionError("actual MediaStore size");
  }
  System.out.println("size ordering checks passed");
 }
}
