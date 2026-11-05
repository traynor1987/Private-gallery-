import uk.co.traynor.novaos.preview.PreviewAppPolicy;
public class AppModeChecks {
 public static void main(String[] args){
  if(!PreviewAppPolicy.googleOAuthAuthorization("https://accounts.google.com/o/oauth2/v2/auth"))throw new AssertionError("Google OAuth not recognised");
  for(String s:new String[]{"https://accounts.google.com.evil.test/","https://youtube.com/","https://accounts.google.com@evil.test/","not a url"})if(PreviewAppPolicy.googleOAuthAuthorization(s))throw new AssertionError(s);
  for(String url:new String[]{"https://accounts.google.com/ServiceLogin?service=youtube&continue=https%3A%2F%2Fmusic.youtube.com%2Fsignin","https://accounts.google.com/v3/signin/identifier?service=youtube","https://accounts.google.com/","https://music.youtube.com/library"})if(PreviewAppPolicy.googleOAuthAuthorization(url))throw new AssertionError("Ordinary first-party browsing incorrectly intercepted: "+url);
  for(String url:new String[]{"https://accounts.google.com/ServiceLogin?continue=https://accounts.google.com/o/oauth2/v2/auth", "https://accounts.google.com/ServiceLogin?continue=HTTPS://ACCOUNTS.GOOGLE.COM/o/oauth2/v2/auth", "https://accounts.google.com/ServiceLogin?continue=%25ZZ", "https://accounts.google.com/o/oauth2/auth?client_id=fixture","https://accounts.google.com/signin/oauth/identifier","https://accounts.google.com/ServiceLogin?continue=https%3A%2F%2Faccounts.google.com%2Fo%2Foauth2%2Fv2%2Fauth%3Fclient_id%3Dfixture","https://accounts.google.com/ServiceLogin?client_id=fixture&redirect_uri=https%3A%2F%2Fexample.com%2Fcallback"})if(!PreviewAppPolicy.googleOAuthAuthorization(url))throw new AssertionError("OAuth bypass: "+url);
  if(!PreviewAppPolicy.keepRunning(true,true,true))throw new AssertionError("Home must retain foreground app");
  if(PreviewAppPolicy.keepRunning(false,true,true)||PreviewAppPolicy.keepRunning(true,false,true)||PreviewAppPolicy.keepRunning(true,true,false))throw new AssertionError("Stopped/inactive/profile-switched app must pause");
  if(!PreviewAppPolicy.canPresentExternal("tab1","primary","tab1","primary",true))throw new AssertionError("Initiator rejected");
  if(PreviewAppPolicy.canPresentExternal("tab1","primary","tab2","primary",true)||PreviewAppPolicy.canPresentExternal("tab1","hidden","tab1","primary",true)||PreviewAppPolicy.canPresentExternal("tab1","primary","tab1","primary",false))throw new AssertionError("Wrong external owner");
  System.out.println("24 App Mode OAuth/lifecycle checks passed");
 }
}
