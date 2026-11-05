package uk.co.traynor.novaos.preview;
public final class ChatRules {
 public static final int ADAPTER_VERSION=1;
 public static final boolean FILTER_VERIFIED=false;
 public static String phone(String input){
  if(input==null||input.length()>50||!input.matches("[+0-9 ()\\-]+"))throw new IllegalArgumentException("Use a full international phone number beginning with +.");
  String s=input.replaceAll("[ ()\\-]","");if(!s.matches("\\+[1-9][0-9]{6,14}"))throw new IllegalArgumentException("Use +, country code and 7–15 digits.");return s;
 }
 public static boolean canReveal(boolean origin,boolean list,boolean identity,boolean attached,boolean navigation,boolean search){return FILTER_VERIFIED&&origin&&list&&identity&&attached&&navigation&&search;}
 public static String reason(boolean connected,boolean list){return !connected?"No retained WhatsApp page is available.":!list?"A supported conversation container was not detected. This may be a login or changed interface.":"Stable conversation identification and complete filtering coverage have not been verified by this adapter.";}
}
