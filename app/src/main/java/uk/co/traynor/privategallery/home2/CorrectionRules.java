package uk.co.traynor.privategallery.home2;
import java.net.URI;
import java.util.*;
import java.util.regex.*;
public final class CorrectionRules {
 public static String site(String url){try{return new URI(PreviewRules.https(url)).getHost().toLowerCase(Locale.ROOT);}catch(Exception e){return "";}}
 public static boolean desktopAllowed(String url){String host=site(url);return !host.isEmpty()&&!host.equals("accounts.google.com")&&!PreviewAppPolicy.googleOAuthAuthorization(url);}
 public static String desktopUa(String mobile){Matcher m=Pattern.compile("Chrome/([0-9.]+)").matcher(mobile);if(!m.find())return mobile;return "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/"+m.group(1)+" Safari/537.36";}
 /** Keep all valid placements before relocating invalid/overlapping records. Original order/data are not mutated. */
 public static List<int[]> repair(List<int[]> source,int pages){
  if(pages<1||pages>8)return null;
  List<int[]> out=new ArrayList<>();for(int[] r:source)out.add(r.clone());List<Integer> bad=new ArrayList<>();List<List<int[]>> occupied=new ArrayList<>();for(int i=0;i<pages;i++)occupied.add(new ArrayList<>());
  for(int i=0;i<source.size();i++){int[] r=source.get(i);if(r.length==5&&r[4]>=0&&r[4]<pages&&WidgetRules.fits(Arrays.copyOf(r,4),occupied.get(r[4]),-1))occupied.get(r[4]).add(Arrays.copyOf(r,4));else bad.add(i);}
  for(int i:bad){int[] old=source.get(i);if(old.length!=5)return null;int w=Math.max(1,Math.min(4,old[2])),h=Math.max(1,Math.min(4,old[3]));int preferred=Math.max(0,Math.min(pages-1,old[4]));boolean found=false;
   for(int n=0;n<pages;n++){int p=(preferred+n)%pages;int[] r=WidgetRules.firstFree(w,h,occupied.get(p));if(r!=null){occupied.get(p).add(r);out.set(i,new int[]{r[0],r[1],w,h,p});found=true;break;}}
   if(!found)return null;
  }
  return out;
 }
}
