package uk.co.traynor.privategallery.core.evidence;

import java.util.HashSet;
import java.util.Set;

/** Complete strict JSON grammar validation before any Android lenient JSON parser.
 * Bounded recursion, characters and values; duplicate decoded keys and unpaired
 * surrogates are rejected so a later parser cannot reinterpret an ambiguous document.
 * No parsed private strings or source text are retained by this helper.
 */
public final class EvidenceStrictJson {
 private EvidenceStrictJson() {}
 public static void validate(String value,int maxDepth,int maxCharacters) {
  if(value==null||maxDepth<1||maxDepth>64||maxCharacters<1||maxCharacters>4*1024*1024||value.length()>maxCharacters)throw invalid();
  Parser parser=new Parser(value,maxDepth);
  parser.value(0);parser.space();
  if(parser.at!=value.length())throw invalid();
 }
 private static IllegalArgumentException invalid(){return new IllegalArgumentException("Invalid or over-limit JSON");}
 private static final class Parser {
  private final String input;
  private final int maximumDepth;
  private int at,values;
  Parser(String input,int maximumDepth){this.input=input;this.maximumDepth=maximumDepth;}
  void space(){while(at<input.length()){char c=input.charAt(at);if(c==' '||c=='\t'||c=='\r'||c=='\n')at++;else break;}}
  boolean take(char c){if(at<input.length()&&input.charAt(at)==c){at++;return true;}return false;}
  void need(char c){if(!take(c))throw invalid();}
  void value(int depth){
   if(++values>100000)throw invalid();
   space();if(at==input.length())throw invalid();
   char next=input.charAt(at);
   if(next=='{'||next=='['){
    if(depth>=maximumDepth)throw invalid();
    if(next=='{')object(depth+1);else array(depth+1);
   }else if(next=='"')string(false);
   else if(next=='t')literal("true");
   else if(next=='f')literal("false");
   else if(next=='n')literal("null");
   else number();
  }
  void object(int depth){
   need('{');space();if(take('}'))return;
   Set<String> keys=new HashSet<>();
   do {
    space();String key=string(true);if(!keys.add(key))throw invalid();space();need(':');value(depth);space();
    if(take('}'))return;
    need(',');
   }while(true);
  }
  void array(int depth){
   need('[');space();if(take(']'))return;
   do {value(depth);space();if(take(']'))return;need(',');}while(true);
  }
  void literal(String value){if(!input.startsWith(value,at))throw invalid();at+=value.length();}
  boolean digit(){return at<input.length()&&input.charAt(at)>='0'&&input.charAt(at)<='9';}
  void number(){
   take('-');if(at==input.length())throw invalid();
   if(take('0')){if(digit())throw invalid();}
   else {if(input.charAt(at)<'1'||input.charAt(at)>'9')throw invalid();do{at++;}while(digit());}
   if(take('.')){if(!digit())throw invalid();do{at++;}while(digit());}
   if(take('e')||take('E')){if(!take('+'))take('-');if(!digit())throw invalid();do{at++;}while(digit());}
  }
  int hex4(){
   if(at>input.length()-4)throw invalid();int value=0;
   for(int i=0;i<4;i++){char c=input.charAt(at++);int digit=c>='0'&&c<='9'?c-'0':c>='a'&&c<='f'?c-'a'+10:c>='A'&&c<='F'?c-'A'+10:-1;if(digit<0)throw invalid();value=(value<<4)|digit;}
   return value;
  }
  String string(boolean capture){
   need('"');StringBuilder result=capture?new StringBuilder():null;
   while(at<input.length()){
    char c=input.charAt(at++);if(c=='"')return capture?result.toString():null;if(c<0x20)throw invalid();
    if(c=='\\'){
     if(at==input.length())throw invalid();char escaped=input.charAt(at++);
     switch(escaped){
      case '"':case '\\':case '/':c=escaped;break;
      case 'b':c='\b';break;case 'f':c='\f';break;case 'n':c='\n';break;case 'r':c='\r';break;case 't':c='\t';break;
      case 'u':
       c=(char)hex4();
       if(Character.isHighSurrogate(c)){
        need('\\');need('u');char low=(char)hex4();if(!Character.isLowSurrogate(low))throw invalid();
        if(capture){result.append(c);result.append(low);}continue;
       }
       if(Character.isLowSurrogate(c))throw invalid();break;
      default:throw invalid();
     }
    }else if(Character.isHighSurrogate(c)){
     if(at==input.length()||!Character.isLowSurrogate(input.charAt(at)))throw invalid();
     if(capture){result.append(c);result.append(input.charAt(at));}at++;continue;
    }else if(Character.isLowSurrogate(c))throw invalid();
    if(capture)result.append(c);
   }
   throw invalid();
  }
 }
}
