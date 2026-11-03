package uk.co.traynor.privategallery;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.view.accessibility.AccessibilityEvent;

/** Disposable CI debug APK only. No event content, actions, storage or network operations. */
public final class CiAccessibilityService178 extends AccessibilityService {
 public static volatile boolean connected;
 private static volatile CiAccessibilityService178 instance;
 public static void requestTouchExploration(boolean enabled) {
  CiAccessibilityService178 service=instance;if(service==null)throw new IllegalStateException("Fixture service not connected");
  new android.os.Handler(android.os.Looper.getMainLooper()).post(()->{
   android.accessibilityservice.AccessibilityServiceInfo info=service.getServiceInfo();
   int flag=android.accessibilityservice.AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE;
   info.flags=enabled ? info.flags|flag : info.flags & ~flag;service.setServiceInfo(info);
  });
 }
 @Override protected void onServiceConnected(){instance=this;connected=true;android.util.Log.i("CiAccessibility178","connected");}
 @Override public void onAccessibilityEvent(AccessibilityEvent event){}
 @Override public void onInterrupt(){}
 @Override public boolean onUnbind(Intent intent){connected=false;android.util.Log.i("CiAccessibility178","unbound");return super.onUnbind(intent);}
 @Override public void onDestroy(){instance=null;connected=false;super.onDestroy();}
}
