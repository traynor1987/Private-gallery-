import uk.co.traynor.novaos.preview.ChatRules;
public class ChatRulesChecks {
 static int n;static void check(boolean b){n++;if(!b)throw new AssertionError("Chat privacy check "+n);}
 public static void main(String[]args){
 check(ChatRules.phone("+44 7700 900123").equals("+447700900123"));
 for(String s:new String[]{"Named contact","447700900123","+0123456789","+123","+1234567890123456","+447700900123?text=hello","+44<script>"}){boolean rejected=false;try{ChatRules.phone(s);}catch(IllegalArgumentException e){rejected=true;}check(rejected);}
 check(!ChatRules.canReveal(true,true,true,true,true,true));check(!ChatRules.canReveal(false,true,true,true,true,true));
 check(ChatRules.reason(false,false).equals("No retained WhatsApp page is available."));check(ChatRules.reason(true,true).contains("identification"));
 System.out.println(n+" chat identity/concealment assertions passed");}
}
