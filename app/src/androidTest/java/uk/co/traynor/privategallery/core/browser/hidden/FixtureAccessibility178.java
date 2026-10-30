package uk.co.traynor.privategallery.core.browser.hidden;

import android.app.Instrumentation;
import android.app.UiAutomation;
import android.os.SystemClock;
import android.os.ParcelFileDescriptor;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import android.view.accessibility.AccessibilityManager;
import android.accessibilityservice.AccessibilityServiceInfo;
import uk.co.traynor.privategallery.BuildConfig;
import uk.co.traynor.privategallery.CiAccessibilityService178;

/** Keep the real debug-only service enabled while UiAutomation retrieves native AX nodes. */
public final class FixtureAccessibility178 {
 private FixtureAccessibility178(){}
 private static boolean configured;
 private static String shell(UiAutomation automation,String command) {
  try(ParcelFileDescriptor.AutoCloseInputStream input=new ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command))) {
   byte[] bytes=input.readNBytes(4097);
   if(bytes.length>4096)throw new AssertionError("CI accessibility setup output exceeded bound");
   return new String(bytes,StandardCharsets.UTF_8).trim();
  }catch(IOException failure){throw new AssertionError("CI accessibility shell setup failed");}
 }
 public static synchronized UiAutomation automation(Instrumentation instrumentation) {
  if(!BuildConfig.DEBUG)throw new AssertionError("Disposable debug fixture only");
  UiAutomation automation=instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
  if(!configured) {
   if(!instrumentation.getTargetContext().getPackageName().equals("uk.co.traynor.privategallery.hiddenprototype") ||
       !shell(automation,"getprop ro.kernel.qemu").equals("1"))throw new AssertionError("Disposable emulator debug package required");
   String component="uk.co.traynor.privategallery.hiddenprototype/uk.co.traynor.privategallery.CiAccessibilityService178";
   boolean previouslyEnabled=shell(automation,"settings get secure enabled_accessibility_services").contains(component);
   // Configure only after Gradle installed the APK and instrumentation started.
   // Earlier installation/replacement or UiAutomation suppression may retire setup.
   shell(automation,"settings put secure enabled_accessibility_services "+component);
   shell(automation,"settings put secure accessibility_enabled 1");
   configured=true;
   android.util.Log.i("CiAccessibility178","configured previousEnabled="+previouslyEnabled);
  }
  long deadline=SystemClock.elapsedRealtime()+5000;
  AccessibilityManager manager=instrumentation.getTargetContext().getSystemService(AccessibilityManager.class);
  boolean ready;
  do {
   boolean listed=manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK).stream()
       .anyMatch(info->info.getResolveInfo().serviceInfo.name.equals(CiAccessibilityService178.class.getName()));
   ready=CiAccessibilityService178.connected && listed;
   if(!ready)SystemClock.sleep(25);
  }while(!ready && SystemClock.elapsedRealtime()<deadline);
  if(!ready) {
   boolean listed=manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK).stream()
       .anyMatch(info->info.getResolveInfo().serviceInfo.name.equals(CiAccessibilityService178.class.getName()));
   throw new AssertionError("Real CI accessibility service not bound and enabled: connected="+CiAccessibilityService178.connected+" listed="+listed);
  }
  return automation;
 }
}
