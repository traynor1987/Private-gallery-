package uk.co.traynor.privategallery.core.browser.hidden;

import android.app.Instrumentation;
import android.app.UiAutomation;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityManager;
import android.accessibilityservice.AccessibilityServiceInfo;
import uk.co.traynor.privategallery.BuildConfig;
import uk.co.traynor.privategallery.CiAccessibilityService178;

/** Keep the real debug-only service enabled while UiAutomation retrieves native AX nodes. */
public final class FixtureAccessibility178 {
 private FixtureAccessibility178(){}
 public static UiAutomation automation(Instrumentation instrumentation) {
  if(!BuildConfig.DEBUG)throw new AssertionError("Disposable debug fixture only");
  UiAutomation automation=instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
  long deadline=SystemClock.elapsedRealtime()+5000;
  AccessibilityManager manager=instrumentation.getTargetContext().getSystemService(AccessibilityManager.class);
  boolean ready;
  do {
   boolean listed=manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK).stream()
       .anyMatch(info->info.getResolveInfo().serviceInfo.name.equals(CiAccessibilityService178.class.getName()));
   ready=CiAccessibilityService178.connected && listed;
   if(!ready)SystemClock.sleep(25);
  }while(!ready && SystemClock.elapsedRealtime()<deadline);
  if(!ready)throw new AssertionError("Real CI accessibility service not bound and enabled");
  return automation;
 }
}
