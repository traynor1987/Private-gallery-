import uk.co.traynor.privategallery.core.ui.InAppVersionPresentation;
public final class Presentation1761Checks {
 private static int checks;
 private static void eq(Object a,Object b){checks++;if(!a.equals(b))throw new AssertionError(a+" != "+b);}
 public static void main(String[] args){
  var normal=new InAppVersionPresentation(false,"1.7.6.1",42);
  var decoy=new InAppVersionPresentation(true,"1.7.6.1",42);
  eq("1.7.6.1 · Build 42",normal.installed());eq("Private Gallery 1.7.6.1",normal.about());
  eq("0.0.1 · Build 4",decoy.installed());eq("Private Gallery 0.0.1",decoy.about());
  eq("Private Gallery 0.0.1 · Build 4",decoy.header());
  eq("Basic gallery functionality. More features coming soon.",decoy.description());
  eq("Media stays in encrypted private app storage until you restore it.",normal.description());
  eq("Up to date · 1.7.6.1",normal.updateStatus("Up to date · 1.7.6.1"));
  eq("Checks use the installed Android app version.",decoy.updateStatus("Up to date · 1.7.6.1"));
  eq(normal.installed(),new InAppVersionPresentation(false,"1.7.6.1",42).installed());
  System.out.println("Presentation1761: "+checks+" checks PASS");
 }
}
