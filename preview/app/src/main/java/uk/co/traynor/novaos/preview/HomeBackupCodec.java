package uk.co.traynor.novaos.preview;
import org.json.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
/** Strict layout-only wire format. It never accepts URLs, paths, credentials or note contents. */
public final class HomeBackupCodec {
 public static final int MAX_BYTES=524288;
 static void require(boolean b){if(!b)throw new IllegalArgumentException("Invalid or unsupported Home layout backup");}
 static void keys(JSONObject o,String... allowed){Set<String> set=new HashSet<>(Arrays.asList(allowed));Iterator<String> k=o.keys();while(k.hasNext())require(set.contains(k.next()));}
 static String string(JSONObject o,String key,int max)throws JSONException{Object v=o.get(key);require(v instanceof String&&((String)v).length()<=max);return (String)v;}
 static int integer(JSONObject o,String key,int min,int max)throws JSONException{Object v=o.get(key);require(v instanceof Number);double d=((Number)v).doubleValue();require(Double.isFinite(d)&&d==Math.floor(d)&&d>=min&&d<=max);return (int)d;}
 static void number(JSONObject o,String key,double min,double max)throws JSONException{Object v=o.get(key);require(v instanceof Number);double d=((Number)v).doubleValue();require(Double.isFinite(d)&&d>=min&&d<=max);}
 static void ids(JSONArray a,int max)throws JSONException{require(a.length()<=max);Set<String> seen=new HashSet<>();for(int i=0;i<a.length();i++){Object v=a.get(i);require(v instanceof String&&((String)v).matches("[a-z0-9:-]{1,80}"));require(seen.add((String)v));}}
 public static JSONObject validate(String raw)throws JSONException{
  require(raw!=null&&raw.getBytes(StandardCharsets.UTF_8).length<=MAX_BYTES);
  int depth=0;boolean quoted=false,escaped=false;
  for(int i=0;i<raw.length();i++){char c=raw.charAt(i);if(quoted){if(escaped)escaped=false;else if(c=='\\')escaped=true;else if(c=='"')quoted=false;}else if(c=='"')quoted=true;else if(c=='{'||c=='['){depth++;require(depth<=16);}else if(c=='}'||c==']'){depth--;require(depth>=0);}}
  require(depth==0&&!quoted);
  JSONTokener tok=new JSONTokener(raw);Object value=tok.nextValue();require(value instanceof JSONObject);require(tok.nextClean()==0);JSONObject o=(JSONObject)value;
  keys(o,"format","version","layout","widgets","theme","reducedMotion","demoLock");require("nova-home-layout".equals(string(o,"format",40)));require(integer(o,"version",1,1)==1);
  require(Arrays.asList("dark","light","system").contains(string(o,"theme",10)));require(o.get("reducedMotion") instanceof Boolean&&o.get("demoLock") instanceof Boolean);
  JSONObject l=o.getJSONObject("layout");keys(l,"pages","dock","folders","installedIds","drawer","wallpaper","columns","iconSize");
  integer(l,"columns",3,6);integer(l,"iconSize",44,76);JSONArray pages=l.getJSONArray("pages");require(pages.length()>=1&&pages.length()<=8);for(int i=0;i<pages.length();i++)ids(pages.getJSONArray(i),100);ids(l.getJSONArray("dock"),5);ids(l.getJSONArray("drawer"),300);ids(l.getJSONArray("installedIds"),200);
  for(int i=0;i<l.getJSONArray("installedIds").length();i++)require(l.getJSONArray("installedIds").getString(i).matches("web:[a-f0-9]{24}"));
  JSONArray folders=l.getJSONArray("folders");require(folders.length()<=64);Set<String> folderIds=new HashSet<>();for(int i=0;i<folders.length();i++){JSONObject f=folders.getJSONObject(i);keys(f,"id","name","apps");String id=string(f,"id",80);require(id.matches("folder:[a-z0-9-]+")&&folderIds.add(id));require(!string(f,"name",32).trim().isEmpty());ids(f.getJSONArray("apps"),200);}
  JSONObject w=l.getJSONObject("wallpaper");keys(w,"builtin","image","zoom","x","y","blur","dim");require(Arrays.asList("Aurora","Midnight","Dusk").contains(string(w,"builtin",20)));String image=string(w,"image",40);require(image.isEmpty()||image.equals("wallpaper-primary.jpg")||image.equals("wallpaper-hidden.jpg"));number(w,"zoom",1,3);number(w,"x",-1,1);number(w,"y",-1,1);number(w,"blur",0,24);number(w,"dim",0,.8);
  JSONArray widgets=o.getJSONArray("widgets");require(widgets.length()<=64);Set<String> widgetIds=new HashSet<>(),itemIds=new HashSet<>();Map<Integer,List<int[]>> occupied=new HashMap<>();
  for(int i=0;i<widgets.length();i++){JSONObject s=widgets.getJSONObject(i);keys(s,"id","page","x","y","w","h","active","items");require(widgetIds.add(string(s,"id",80)));int page=integer(s,"page",0,pages.length()-1);int[] r={integer(s,"x",0,3),integer(s,"y",0,23),integer(s,"w",1,4),integer(s,"h",1,4)};List<int[]> onPage=occupied.computeIfAbsent(page,k->new ArrayList<>());require(WidgetRules.fits(r,onPage,-1));onPage.add(r);
   JSONArray items=s.getJSONArray("items");require(items.length()>=1&&items.length()<=8);integer(s,"active",0,items.length()-1);for(int j=0;j<items.length();j++){JSONObject it=items.getJSONObject(j);keys(it,"id","type","config");require(itemIds.add(string(it,"id",80)));require(string(it,"type",40).matches("[a-z0-9-]+"));require(string(it,"config",0).isEmpty());}
  }
  return o;
 }
}
