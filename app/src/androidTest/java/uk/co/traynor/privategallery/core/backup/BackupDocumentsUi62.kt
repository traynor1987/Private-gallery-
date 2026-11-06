package uk.co.traynor.privategallery.core.backup
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry

/** Interacts with the actual Android DocumentsUI accessibility tree, outside the app process. */
internal object BackupDocumentsUi62 {
 private val automation get()=InstrumentationRegistry.getInstrumentation().uiAutomation
 private fun nodes():List<AccessibilityNodeInfo>{val result=mutableListOf<AccessibilityNodeInfo>();fun visit(node:AccessibilityNodeInfo?){if(node==null)return;result+=node;for(i in 0 until node.childCount)visit(node.getChild(i))};automation.windows.forEach{visit(it.root)};if(result.isEmpty())visit(automation.rootInActiveWindow);return result}
 private fun find(predicate:(AccessibilityNodeInfo)->Boolean)=nodes().firstOrNull{it.packageName?.toString()!=InstrumentationRegistry.getInstrumentation().targetContext.packageName&&predicate(it)}
 private fun click(node:AccessibilityNodeInfo):Boolean{var current:AccessibilityNodeInfo?=node;repeat(5){val n=current?:return false;if(n.isClickable)return n.performAction(AccessibilityNodeInfo.ACTION_CLICK);current=n.parent};return false}
 private fun waitFor(predicate:(AccessibilityNodeInfo)->Boolean):AccessibilityNodeInfo{val until=SystemClock.uptimeMillis()+10000;while(SystemClock.uptimeMillis()<until){find(predicate)?.let{return it};Thread.sleep(50)};throw AssertionError("DocumentsUI control missing; visible labels: "+nodes().mapNotNull{it.text?:it.contentDescription}.joinToString(" | "))}
 fun root(){
  val label="Nova62 synthetic documents"
  // Expanded DocumentsUI shows roots permanently; narrow layouts use a drawer.
  // A root toolbar title has no clickable ancestor: it already denotes this root.
  val visible=nodes().filter{it.text?.toString()==label}
  if(visible.isNotEmpty()){
   for(node in visible)if(click(node))return
   return
  }
  val drawer=waitFor{it.contentDescription?.toString() in listOf("Show roots","Show navigation menu","Open navigation drawer")}
  check(click(drawer))
  val until=SystemClock.uptimeMillis()+10000
  while(SystemClock.uptimeMillis()<until){
   for(node in nodes().filter{it.text?.toString()==label})if(click(node))return
   Thread.sleep(50)
  }
  throw AssertionError("DocumentsUI root row is not clickable")
 }
 fun open(id:String){
  root()
  // DocumentsUI RecyclerView rows expose their label without a clickable ancestor.
  // Tap the real visible row; the caller still requires the genuine picker result.
  val node=waitFor{it.isVisibleToUser&&it.isEnabled&&it.text?.toString()=="Synthetic backup $id"}
  val bounds=android.graphics.Rect();node.getBoundsInScreen(bounds);check(!bounds.isEmpty)
  val down=SystemClock.uptimeMillis()
  fun event(action:Int)=android.view.MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,bounds.exactCenterX(),bounds.exactCenterY(),0).apply{source=android.view.InputDevice.SOURCE_TOUCHSCREEN}
  val press=event(android.view.MotionEvent.ACTION_DOWN);val release=event(android.view.MotionEvent.ACTION_UP)
  try{check(automation.injectInputEvent(press,true));check(automation.injectInputEvent(release,true))}finally{press.recycle();release.recycle()}
 }
 private fun confirmFolder(){check(click(waitFor{it.text?.toString()?.uppercase()=="USE THIS FOLDER"}));check(click(waitFor{it.text?.toString()?.uppercase()=="ALLOW"}))}
 fun folder(){root();confirmFolder()}
 fun folderAtInitialRoot(){
  // OpenDocumentTree receives EXTRA_INITIAL_URI; verify the actual system picker destination.
  waitFor{it.text?.toString()=="Nova62 synthetic documents"}
  confirmFolder()
 }
 fun create(){root();check(click(waitFor{it.text?.toString()?.uppercase()=="SAVE"||it.viewIdResourceName?.endsWith(":id/button1")==true}))}
}
