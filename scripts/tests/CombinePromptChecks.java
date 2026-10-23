import uk.co.traynor.privategallery.core.editor.CombinePrompt;
public final class CombinePromptChecks {
 public static void main(String[] args) {
  String[] prompts={"  Put us on a beach.  ","Make me a giant and the other person tiny.","Surreal proportions: 👩🏽‍🚀 on Mars", ""};
  for(String p:prompts){
   if(!CombinePrompt.compose(p,false).equals(p))throw new AssertionError("Off must preserve exact prompt");
   String effective=CombinePrompt.compose(p,true);
   if(!effective.startsWith(p+"\n\n"))throw new AssertionError("User text changed");
   if(!effective.contains("unusual scaling or surreal proportions take priority"))throw new AssertionError("User priority absent");
   if(!effective.endsWith(CombinePrompt.GUIDANCE))throw new AssertionError("Guidance absent");
  }
  System.out.println("12 Combine prompt assertions passed");
 }
}
