package uk.co.traynor.privategallery;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.view.accessibility.AccessibilityEvent;

/** Disposable CI debug APK only. No event content, actions, storage or network operations. */
public final class CiAccessibilityService178 extends AccessibilityService {
 public static volatile boolean connected;
 @Override protected void onServiceConnected(){connected=true;}
 @Override public void onAccessibilityEvent(AccessibilityEvent event){}
 @Override public void onInterrupt(){}
 @Override public boolean onUnbind(Intent intent){connected=false;return super.onUnbind(intent);}
 @Override public void onDestroy(){connected=false;super.onDestroy();}
}
