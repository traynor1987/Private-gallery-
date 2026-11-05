import uk.co.traynor.novaos.preview.*;
import java.util.*;
public class CorrectionRulesChecks {
 public static void main(String[] args){
  if(!CorrectionRules.site("https://WEB.WHATSAPP.COM/mobile/?x=1").equals("web.whatsapp.com"))throw new AssertionError("canonical host key");
  if(CorrectionRules.desktopAllowed("https://accounts.google.com/o/oauth2/auth"))throw new AssertionError("auth provider restriction");
  String ua=CorrectionRules.desktopUa("Mozilla/5.0 (Linux; Android 17; wv) AppleWebKit/537.36 Version/4.0 Chrome/150.2.3.4 Mobile Safari/537.36");
  if(ua.contains("Android")||ua.contains("Mobile")||!ua.contains("Chrome/150.2.3.4"))throw new AssertionError("desktop request uses real engine version");
  List<int[]> saved=List.of(new int[]{0,0,2,2,0},new int[]{1,0,2,2,0},new int[]{-3,100,2,1,8},new int[]{2,2,2,2,0});
  List<int[]> repaired=CorrectionRules.repair(saved,2);
  if(repaired==null||!Arrays.equals(saved.get(0),repaired.get(0))||!Arrays.equals(saved.get(3),repaired.get(3)))throw new AssertionError("preserve valid customisations first");
  for(int i=0;i<repaired.size();i++)for(int j=i+1;j<repaired.size();j++)if(repaired.get(i)[4]==repaired.get(j)[4]&&WidgetRules.overlap(repaired.get(i),repaired.get(j)))throw new AssertionError("repair collision");
  System.out.println("7 desktop/recovery assertions passed");
 }
}
