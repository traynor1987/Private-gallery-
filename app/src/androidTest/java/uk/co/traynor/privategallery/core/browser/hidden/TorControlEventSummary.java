package uk.co.traynor.privategallery.core.browser.hidden;
import java.util.*;
/** Test-only strict allow-list: never emit raw destinations, paths, relay IDs or credentials. */
public final class TorControlEventSummary {
 private static final Set<String> STREAM=Set.of("NEW","NEWRESOLVE","REMAP","SENTCONNECT","SENTRESOLVE","SUCCEEDED","FAILED","CLOSED","DETACHED","CONTROLLER_WAIT","XOFF_SENT","XOFF_RECV","XON_SENT","XON_RECV");
 private static final Set<String> CIRC=Set.of("LAUNCHED","BUILT","GUARD_WAIT","EXTENDED","FAILED","CLOSED");
 private static final Set<String> HS=Set.of("REQUESTED","UPLOAD","RECEIVED","UPLOADED","IGNORE","FAILED","CREATED");
 private static final Map<String,Set<String>> FIELDS=Map.of(
  "PURPOSE",Set.of("GENERAL","HS_CLIENT_INTRO","HS_CLIENT_REND","HS_SERVICE_INTRO","HS_SERVICE_REND","HS_VANGUARDS","TESTING","CONTROLLER","MEASURE_TIMEOUT","PATH_BIAS_TESTING","CIRCUIT_PADDING","USER","DIR_FETCH","DIR_UPLOAD","DNS_REQUEST"),
  "HS_STATE",Set.of("HSCI_CONNECTING","HSCI_INTRO_SENT","HSCI_DONE","HSCR_CONNECTING","HSCR_ESTABLISHED_IDLE","HSCR_ESTABLISHED_WAITING","HSCR_JOINED","HSSI_CONNECTING","HSSI_ESTABLISHED","HSSR_CONNECTING","HSSR_JOINED"),
  "REASON",Set.of("NONE","TORPROTOCOL","INTERNAL","REQUESTED","HIBERNATING","RESOURCELIMIT","CONNECTFAILED","OR_IDENTITY","OR_CONN_CLOSED","TIMEOUT","FINISHED","DESTROYED","NOPATH","NOSUCHSERVICE","MEASUREMENT_EXPIRED","IP_NOW_REDUNDANT","MISC","RESOLVEFAILED","EXITPOLICY","DONE","NOROUTE","NOTDIRECTORY","END","PRIVATE_ADDR","NOT_FOUND","QUERY_REJECTED","UPLOAD_REJECTED","UNEXPECTED","BAD_DESC","CANT_ATTACH"));
 public static String summarize(String raw) {
  if(raw==null || raw.length()>8192 || raw.indexOf('\n')>=0 || raw.indexOf('\r')>=0)return "";
  if(raw.startsWith("650-"))return summarize("650 "+raw.substring(4));
  String[] p=raw.split(" +");
  if(p.length<4 || !p[0].equals("650"))return "";
  String base;int start;
  if(p[1].equals("STREAM") && p.length>=6 && digits(p[2]) && STREAM.contains(p[3]) && digits(p[4])) {
   base="STREAM id="+p[2]+" status="+p[3]+" circuit="+p[4];start=6;
  } else if(p[1].equals("CIRC") && digits(p[2]) && CIRC.contains(p[3])) {
   base="CIRC id="+p[2]+" status="+p[3];start=4;
  } else if(p[1].equals("HS_DESC") && HS.contains(p[2])) {
   base="HS_DESC status="+p[2];start=4;
  } else return "";
  StringBuilder result=new StringBuilder(base);
  for(int i=start;i<p.length;i++) {
   int eq=p[i].indexOf('=');if(eq<1)continue;
   String key=p[i].substring(0,eq),value=p[i].substring(eq+1);
   Set<String> allowed=FIELDS.get(key.equals("REMOTE_REASON")?"REASON":key);
   if(allowed!=null)result.append(' ').append(key).append('=').append(allowed.contains(value)?value:"OTHER");
  }
  return result.toString();
 }
 private static boolean digits(String value){return value.matches("[0-9]{1,10}");}
}
