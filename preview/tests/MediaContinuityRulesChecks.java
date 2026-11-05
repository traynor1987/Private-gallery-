import uk.co.traynor.novaos.preview.PreviewShellRules;
public class MediaContinuityRulesChecks {
 public static void main(String[] args){
  if(!PreviewShellRules.keepRunning(true,false,false))throw new AssertionError("Foreground retained page must not pause merely because Media Session state is unavailable and another app is selected");
  if(PreviewShellRules.keepRunning(false,true,true))throw new AssertionError("Android Home remains outside the foreground continuity scope");
  System.out.println("2 foreground media-continuity policy checks passed");
 }
}
