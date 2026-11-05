import uk.co.traynor.novaos.preview.HomeBackupCodec;
import org.json.*;
public class BackupCodecChecks {
 static String valid="{\"format\":\"nova-home-layout\",\"version\":1,\"theme\":\"dark\",\"reducedMotion\":false,\"demoLock\":false,\"widgets\":[],\"layout\":{\"pages\":[[]],\"dock\":[],\"folders\":[],\"installedIds\":[],\"drawer\":[],\"columns\":4,\"iconSize\":60,\"wallpaper\":{\"builtin\":\"Aurora\",\"image\":\"\",\"zoom\":1,\"x\":0,\"y\":0,\"blur\":0,\"dim\":0.12}}}";
 static void reject(String s)throws Exception{try{HomeBackupCodec.validate(s);}catch(Exception expected){return;}throw new AssertionError("Unsafe backup accepted");}
 public static void main(String[] a)throws Exception{
  HomeBackupCodec.validate(valid);reject("[".repeat(2000)+"]".repeat(2000));reject(valid.replace("\"version\":1","\"version\":2"));reject(valid.replace("\"image\":\"\"","\"image\":\"../../secret\""));reject(valid.replace("\"columns\":4","\"columns\":4.2"));reject(valid+" trailing");reject(" ".repeat(524289));
  JSONObject o=new JSONObject(valid);o.put("cookies","sensitive");reject(o.toString());o=new JSONObject(valid);o.getJSONObject("layout").put("journal","private");reject(o.toString());
  JSONObject s=new JSONObject("{\"id\":\"one\",\"page\":0,\"x\":0,\"y\":0,\"w\":2,\"h\":2,\"active\":0,\"items\":[{\"id\":\"a\",\"type\":\"note\",\"config\":\"private note\"}]}");o=new JSONObject(valid);o.getJSONArray("widgets").put(s);reject(o.toString());s.getJSONArray("items").getJSONObject(0).put("config","");HomeBackupCodec.validate(o.toString());s.put("w",5);reject(o.toString());
  System.out.println("12 layout backup schema/privacy/malformed checks passed");
 }
}
