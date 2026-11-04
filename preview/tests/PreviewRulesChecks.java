import uk.co.traynor.novaos.preview.PreviewRules;
import java.util.*;
public class PreviewRulesChecks {
 static int count;
 static void check(boolean b){count++;if(!b)throw new AssertionError("check "+count);}
 static void reject(String s){count++;try{PreviewRules.https(s);throw new AssertionError(s);}catch(IllegalArgumentException ok){}}
 public static void main(String[]args){
  check(PreviewRules.https("https://example.com/a").equals("https://example.com/a"));
  check(PreviewRules.https("https://EXAMPLE.com:443/#part").equals("https://example.com/#part"));
  for(String s:List.of("javascript:alert(1)","http://example.com","file:///data/data/x","intent://example.com","https://user:pass@example.com","https://127.0.0.1","https://localhost","https://192.168.1.2","https://[::1]","https://example.com:8443/","https://example.com\\evil","https://example.com\n.evil"))reject(s);
  check(PreviewRules.key("https://example.com/").equals(PreviewRules.key("https://EXAMPLE.com:443/")));
  check(PreviewRules.columns(304,1f,4)==4);check(PreviewRules.columns(380,1f,4)==4);check(PreviewRules.columns(840,1f,4)==7);check(PreviewRules.columns(380,2f,4)<4);
  var ids=List.of("store","photos","settings");
  check(PreviewRules.move(ids,"photos",-1).equals(List.of("photos","store","settings")));
  check(PreviewRules.move(ids,"store",-1).equals(ids));check(ids.get(0).equals("store"));
  System.out.println(count+" preview URL/layout checks passed");
 }
}
