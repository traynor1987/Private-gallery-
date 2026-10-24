package uk.co.traynor.privategallery.core.ui;
import java.util.Locale;
/** Stable pure ordering; SQL expressions are constants, never interpolated user input. */
public final class MediaOrder {
 private MediaOrder() {}
 public static long valid(long value){return value>0 && value<=253402300799000L?value:0;}
 public static long taken(long capture,long added){long v=valid(capture);return v>0?v:valid(added);}
 public static int compare(int mode,long addedA,long takenA,String nameA,String idA,long addedB,long takenB,String nameB,String idB){
  int c;
  if(mode==4||mode==5)c=nameA.toLowerCase(Locale.ROOT).compareTo(nameB.toLowerCase(Locale.ROOT));
  else c=Long.compare(mode<2?valid(addedA):taken(takenA,addedA),mode<2?valid(addedB):taken(takenB,addedB));
  if(mode==0||mode==2||mode==5)c=-Integer.signum(c);
  return c!=0?c:idA.compareTo(idB);
 }
 public static String sql(int mode){
  String added="CASE WHEN date_added > 0 AND date_added <= 253402300799 THEN date_added ELSE 0 END";
  switch(mode){
   case 0:return added+" DESC, _id DESC";
   case 1:return added+" ASC, _id ASC";
   case 2:return "CASE WHEN datetaken > 0 AND datetaken <= 253402300799000 THEN datetaken ELSE ("+added+") * 1000 END DESC, _id DESC";
   case 3:return "CASE WHEN datetaken > 0 AND datetaken <= 253402300799000 THEN datetaken ELSE ("+added+") * 1000 END ASC, _id ASC";
   case 4:return "_display_name COLLATE NOCASE ASC, _id ASC";
   case 5:return "_display_name COLLATE NOCASE DESC, _id ASC";
   default:throw new IllegalArgumentException("Unsupported media order");
  }
 }
}
