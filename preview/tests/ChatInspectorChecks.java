import uk.co.traynor.novaos.preview.ChatInspectorRules;
import org.json.JSONObject;
public class ChatInspectorChecks{
 static int n;static void check(boolean value){n++;if(!value)throw new AssertionError("Inspector privacy check "+n);}
 public static void main(String[]args){
 check(ChatInspectorRules.origin("https://web.whatsapp.com/"));
 for(String url:new String[]{"http://web.whatsapp.com/","https://web.whatsapp.com.evil.test/","https://evil.test/?https://web.whatsapp.com","https://user@web.whatsapp.com/","https://web.whatsapp.com:444/","javascript:alert(1)"})check(!ChatInspectorRules.origin(url));
 JSONObject input=new JSONObject("{\"origin\":true,\"container\":true,\"rows\":3,\"shape\":3,\"consistent\":true,\"virtual\":true,\"unread\":true,\"shadow\":false}");
 String report=ChatInspectorRules.report(input,true);check(report.contains("UNKNOWN"));check(report.contains("INVESTIGATION_REQUIRED"));check(!report.contains("447700"));check(ChatInspectorRules.validateReport(report).equals(report));
 for(Object secret:new Object[]{"+447700900123","friend@example.com","SYNTHETIC CONTACT","SYNTHETIC MESSAGE","session-token","cookie=secret","<div>private</div>","data:image/png;base64,private"}){
  JSONObject bad=new JSONObject(input.toString()).put("container",secret);boolean rejected=false;try{ChatInspectorRules.report(bad,true);}catch(Exception e){rejected=true;}check(rejected);
  rejected=false;try{ChatInspectorRules.validateReport(new JSONObject(report).put("extra",secret).toString());}catch(Exception e){rejected=true;}check(rejected);
 }
 check(!ChatInspectorRules.safeToInspect(false,true));check(!ChatInspectorRules.safeToInspect(true,false));check(ChatInspectorRules.safeToInspect(true,true));
 System.out.println(n+" inspector schema/origin/privacy assertions passed");}
}
