import uk.co.traynor.novaos.preview.PreviewAppPolicy;
public class AppModeChecks {
 public static void main(String[] args){
  if(!PreviewAppPolicy.googleAccount("https://accounts.google.com/o/oauth2/v2/auth"))throw new AssertionError("Google OAuth not recognised");
  for(String s:new String[]{"https://accounts.google.com.evil.test/","https://youtube.com/","https://accounts.google.com@evil.test/","not a url"})if(PreviewAppPolicy.googleAccount(s))throw new AssertionError(s);
  if(!PreviewAppPolicy.keepRunning(true,true,true))throw new AssertionError("Home must retain foreground app");
  if(PreviewAppPolicy.keepRunning(false,true,true)||PreviewAppPolicy.keepRunning(true,false,true)||PreviewAppPolicy.keepRunning(true,true,false))throw new AssertionError("Stopped/inactive/profile-switched app must pause");
  if(!PreviewAppPolicy.canPresentExternal("tab1","primary","tab1","primary",true))throw new AssertionError("Initiator rejected");
  if(PreviewAppPolicy.canPresentExternal("tab1","primary","tab2","primary",true)||PreviewAppPolicy.canPresentExternal("tab1","hidden","tab1","primary",true)||PreviewAppPolicy.canPresentExternal("tab1","primary","tab1","primary",false))throw new AssertionError("Wrong external owner");
  System.out.println("13 App Mode OAuth/lifecycle checks passed");
 }
}
