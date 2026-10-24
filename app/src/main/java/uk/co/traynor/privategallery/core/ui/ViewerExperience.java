package uk.co.traynor.privategallery.core.ui;

/** Shared gesture arithmetic. No identifiers, files, sessions or process-wide media state. */
public final class ViewerExperience {
 private ViewerExperience() {}
 public static float zoomOffset(float tap,float extent,float oldOffset,float oldScale,float newScale) {
  if(newScale<=1.01f || extent<=0 || oldScale<=0)return 0;
  float centered=tap-extent/2;
  return centered-(centered-oldOffset)*newScale/oldScale;
 }
 public static float pinchOffset(float previousFocus,float currentFocus,float extent,float oldOffset,float oldScale,float newScale) {
  if(newScale<=1.01f)return 0;
  return zoomOffset(previousFocus,extent,oldOffset,oldScale,newScale)+(currentFocus-previousFocus);
 }
 public static int swipe(float dx,float dy,float scale,boolean multiplePointers,float threshold) {
  if(multiplePointers || scale>1.01f || Math.abs(dx)<=threshold || Math.abs(dx)<=Math.abs(dy)*1.4f)return 0;
  return dx<0?1:-1;
 }
 public static boolean closeSwipe(float dx,float dy,float scale,boolean multiplePointers,float threshold) {
  return !multiplePointers && scale<=1.01f && dy>threshold && dy>Math.abs(dx)*1.4f;
 }
 public static long seek(long position,long delta,long duration) {
  if(duration<=0)return 0;
  long start=Math.max(0,Math.min(position,duration));
  if(delta>0)return start+Math.min(delta,duration-start);
  return start+Math.max(delta,-start);
 }
}
