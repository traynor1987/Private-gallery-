import uk.co.traynor.privategallery.home2.PreviewShellRules;
public class ShellRulesChecks {
 public static void main(String[] args){
  if(!PreviewShellRules.keepRunning(true,true,false)||!PreviewShellRules.keepRunning(true,false,true))throw new AssertionError("Foreground selected/media app must retain lifecycle");
  if(PreviewShellRules.keepRunning(false,true,true)||!PreviewShellRules.keepRunning(true,false,false))throw new AssertionError("Only leaving the foreground pauses retained pages; unknown website metadata must not pause app switching");
  if(!PreviewShellRules.mediaState("playing")||!PreviewShellRules.mediaState("paused")||PreviewShellRules.mediaState("invented"))throw new AssertionError("Unsupported media state");
  if(!PreviewShellRules.order(false).equals("recents,home,back")||!PreviewShellRules.order(true).equals("back,home,recents"))throw new AssertionError("Navigation order");
  if(!PreviewShellRules.returnRoute("browser",false).equals("home")||!PreviewShellRules.returnRoute("browser",true).equals("browser")||!PreviewShellRules.returnRoute("store",false).equals("store"))throw new AssertionError("Recents return must not produce empty browser");
  if(PreviewShellRules.mediaPriority("playing")<=PreviewShellRules.mediaPriority("paused"))throw new AssertionError("Playing session must precede newer paused session");
  System.out.println("13 shell lifecycle/media/navigation checks passed");
 }
}
