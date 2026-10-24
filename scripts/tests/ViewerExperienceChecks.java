import uk.co.traynor.privategallery.core.ui.ViewerExperience;
public class ViewerExperienceChecks {
 static void eq(long a,long b){if(a!=b)throw new AssertionError(a+" != "+b);}
 static void near(float a,float b){if(Math.abs(a-b)>.01f)throw new AssertionError(a+" != "+b);}
 public static void main(String[] args){
  near(-300,ViewerExperience.zoomOffset(700,1000,0,1,2.5f));
  near(150,ViewerExperience.zoomOffset(400,1000,0,1,2.5f));
  near(0,ViewerExperience.zoomOffset(700,1000,100,2.5f,1));
  eq(1,ViewerExperience.swipe(-120,10,1,false,72));
  eq(0,ViewerExperience.swipe(-120,10,1,true,72));
  eq(0,ViewerExperience.swipe(-120,10,2,false,72));
  if(!ViewerExperience.closeSwipe(12,150,1,false,100))throw new AssertionError();
  if(ViewerExperience.closeSwipe(12,150,1,true,100))throw new AssertionError();
  eq(0,ViewerExperience.seek(5000,-10000,60000));
  eq(60000,ViewerExperience.seek(58000,10000,60000));
  eq(0,ViewerExperience.seek(5000,10000,-1));
  System.out.println("Viewer experience: 11 checks passed");
 }
}
