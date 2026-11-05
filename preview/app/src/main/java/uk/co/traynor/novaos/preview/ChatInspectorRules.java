package uk.co.traynor.novaos.preview;
import java.net.URI;
import java.util.*;
import org.json.*;
/** Closed diagnostic vocabulary: no free-form page strings can enter a report. */
public final class ChatInspectorRules {
 private static final Set<String> INPUT=Set.of("origin","container","rows","shape","consistent","virtual","unread","shadow");
 private static final Map<String,Set<String>> SCHEMA=new LinkedHashMap<>();
 static {
  SCHEMA.put("inspectorVersion",Set.of("0.1"));
  for(String k:List.of("originVerified","conversationContainer","rowStructureRecognised","unreadIndicatorStructure"))SCHEMA.put(k,Set.of("YES","NO"));
  SCHEMA.put("renderedRowCountBucket",Set.of("NONE","ONE_TO_TEN","ELEVEN_TO_FIFTY","MORE_THAN_FIFTY"));
  SCHEMA.put("stableNonPersonalRowIdentity",Set.of("UNKNOWN"));
  SCHEMA.put("virtualisedList",Set.of("INDICATED_BY_GEOMETRY","UNKNOWN"));
  SCHEMA.put("rowStructureAcrossSamples",Set.of("AGGREGATE_SIGNATURE_UNCHANGED","AGGREGATE_SIGNATURE_CHANGED","UNKNOWN"));
  SCHEMA.put("rowStructureDuringScrolling",Set.of("UNKNOWN_NOT_OBSERVED"));
  SCHEMA.put("documentNavigationAcrossSamples",Set.of("NO_DOCUMENT_NAVIGATION_OBSERVED"));
  SCHEMA.put("singlePageAppNavigation",Set.of("UNKNOWN_NOT_OBSERVED"));
  SCHEMA.put("openShadowRoot",Set.of("DETECTED","NOT_DETECTED_IN_BOUNDED_SAMPLE"));
  SCHEMA.put("closedShadowRoot",Set.of("UNKNOWN"));
  SCHEMA.put("filteringAttachmentFeasible",Set.of("UNVERIFIED"));
  SCHEMA.put("compatibilityState",Set.of("INVESTIGATION_REQUIRED","UNSUPPORTED_STRUCTURE"));
 }
 private static Set<String> keys(JSONObject o){Set<String> keys=new HashSet<>();Iterator<String> it=o.keys();while(it.hasNext())keys.add(it.next());return keys;}
 public static boolean origin(String url){try{URI u=new URI(url);return "https".equalsIgnoreCase(u.getScheme())&&"web.whatsapp.com".equalsIgnoreCase(u.getHost())&&u.getRawUserInfo()==null&&(u.getPort()==-1||u.getPort()==443);}catch(Exception e){return false;}}
 public static boolean safeToInspect(boolean explicitAction,boolean verifiedOrigin){return explicitAction&&verifiedOrigin;}
 public static JSONObject sample(String raw)throws JSONException{if(raw==null||raw.length()>600)throw new IllegalArgumentException("Invalid sample");JSONObject o=new JSONObject(raw);validateSample(o);return o;}
 private static void validateSample(JSONObject o)throws JSONException{if(!keys(o).equals(INPUT))throw new IllegalArgumentException("Unknown sample fields");for(String k:INPUT)if(!k.equals("rows")&&!k.equals("shape")&&!(o.get(k) instanceof Boolean))throw new IllegalArgumentException("Non-boolean sample");Object n=o.get("rows");if(!(n instanceof Integer)||((Integer)n)<0||((Integer)n)>64)throw new IllegalArgumentException("Invalid aggregate count");Object shape=o.get("shape");if(!(shape instanceof Integer)||((Integer)shape)<0||((Integer)shape)>63)throw new IllegalArgumentException("Invalid structural signature");}
 public static String report(JSONObject o,boolean consistentSamples)throws JSONException{
  validateSample(o);boolean origin=o.getBoolean("origin"),container=o.getBoolean("container");int rows=o.getInt("rows");JSONObject r=new JSONObject();
  r.put("inspectorVersion","0.1");r.put("originVerified",origin?"YES":"NO");r.put("conversationContainer",container?"YES":"NO");r.put("rowStructureRecognised",container&&rows>0&&o.getBoolean("consistent")?"YES":"NO");r.put("renderedRowCountBucket",rows==0?"NONE":rows<=10?"ONE_TO_TEN":rows<=50?"ELEVEN_TO_FIFTY":"MORE_THAN_FIFTY");r.put("stableNonPersonalRowIdentity","UNKNOWN");r.put("virtualisedList",o.getBoolean("virtual")?"INDICATED_BY_GEOMETRY":"UNKNOWN");r.put("unreadIndicatorStructure",o.getBoolean("unread")?"YES":"NO");r.put("rowStructureAcrossSamples",!container?"UNKNOWN":consistentSamples?"AGGREGATE_SIGNATURE_UNCHANGED":"AGGREGATE_SIGNATURE_CHANGED");r.put("rowStructureDuringScrolling","UNKNOWN_NOT_OBSERVED");r.put("documentNavigationAcrossSamples","NO_DOCUMENT_NAVIGATION_OBSERVED");r.put("singlePageAppNavigation","UNKNOWN_NOT_OBSERVED");r.put("openShadowRoot",o.getBoolean("shadow")?"DETECTED":"NOT_DETECTED_IN_BOUNDED_SAMPLE");r.put("closedShadowRoot","UNKNOWN");r.put("filteringAttachmentFeasible","UNVERIFIED");r.put("compatibilityState",origin&&container&&rows>0?"INVESTIGATION_REQUIRED":"UNSUPPORTED_STRUCTURE");return validateReport(r.toString(2));
 }
 public static String validateReport(String raw)throws JSONException{if(raw==null||raw.length()>4096)throw new IllegalArgumentException("Invalid report");JSONObject r=new JSONObject(raw);if(!keys(r).equals(SCHEMA.keySet()))throw new IllegalArgumentException("Unknown report fields");for(String k:SCHEMA.keySet()){Object v=r.get(k);if(!(v instanceof String)||!SCHEMA.get(k).contains(v))throw new IllegalArgumentException("Disallowed report value");}JSONObject safe=new JSONObject();for(String k:SCHEMA.keySet())safe.put(k,r.getString(k));return safe.toString(2);}
}
