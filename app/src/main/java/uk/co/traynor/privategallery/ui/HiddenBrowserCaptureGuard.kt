package uk.co.traynor.privategallery.ui

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/** Authenticated Hidden policy only; secure while paused, unfocused, or awaiting a fresh frame. */
internal class HiddenBrowserCaptureGuard(
    private val activity:Activity,
    private val authorised:()->Boolean,
    private val failed:()->Unit,
    private val lock:(()->Unit)?,
    private val panic:(()->Unit)?,
) {
    constructor(activity:Activity,authorised:()->Boolean,failed:()->Unit):this(activity,authorised,failed,null,null)
    private val decor get()=activity.window.decorView
    private var resumed=false
    private var closed=false
    private var allowed=false
    private var scheduled=false
    private var frameScheduled=false
    private var previousAccessibility:Int?=null
    private var curtain:View?=null
    private var controls:View?=null
    private var deniedForeground=false
    private val concealedChildren=java.util.IdentityHashMap<View,Int>()
    var revision by mutableIntStateOf(0);private set
    val covered get()=curtain?.visibility==View.VISIBLE
    private val windows=PrivacyWindowTracker(activity) {focused ->
        if(!focused)protect()
        requestFrame()
    }
    fun start() {activity.setRecentsScreenshotEnabled(false);conceal()}
    fun updatePolicy(value:Boolean) {
        if(allowed==value)return
        allowed=value
        if(!value)protect()
        requestFrame()
    }
    private fun protect() {
        activity.setRecentsScreenshotEnabled(false)
        if(!windows.protect())failed()
    }
    private fun conceal() {
        protect();windows.coverSecondaryWindows()
        if(previousAccessibility==null)previousAccessibility=decor.importantForAccessibility
        decor.importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        if(curtain==null)curtain=android.widget.FrameLayout(activity).apply {
            setBackgroundColor(android.graphics.Color.BLACK);isClickable=true
            importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            if(lock!=null && panic!=null) {
                controls=android.widget.LinearLayout(activity).apply {
                    orientation=android.widget.LinearLayout.VERTICAL
                    gravity=android.view.Gravity.CENTER
                    importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_YES
                    addView(android.widget.TextView(activity).apply {text="Private connection paused";setTextColor(android.graphics.Color.WHITE);textSize=20f})
                    addView(android.widget.Button(activity).apply {text="Lock Hidden Space";setOnClickListener {lock.invoke()}})
                    addView(android.widget.Button(activity).apply {text="Panic Exit";setOnClickListener {panic.invoke()}})
                }
                addView(controls,android.widget.FrameLayout.LayoutParams(-1,-2,android.view.Gravity.CENTER))
            }
            (decor as ViewGroup).addView(this,ViewGroup.LayoutParams(-1,-1))
        }
        curtain?.visibility=View.VISIBLE;curtain?.bringToFront()
        updateControls()
    }
    /** Only neutral controls are exposed; every underlying content subtree stays hidden. */
    fun deniedForeground(value:Boolean) {deniedForeground=value;if(covered)updateControls()}
    private fun updateControls() {
        val show=deniedForeground && !closed && controls!=null
        val group=decor as ViewGroup
        for(index in 0 until group.childCount) {
            val child=group.getChildAt(index)
            if(child!==curtain) {
                if(!concealedChildren.containsKey(child))concealedChildren[child]=child.importantForAccessibility
                child.importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            }
        }
        decor.importantForAccessibility=if(show)View.IMPORTANT_FOR_ACCESSIBILITY_AUTO else View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        curtain?.importantForAccessibility=if(show)View.IMPORTANT_FOR_ACCESSIBILITY_YES else View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        controls?.visibility=if(show)View.VISIBLE else View.GONE
    }
    private fun restoreContentAccessibility() {
        concealedChildren.forEach {(view,value)->view.importantForAccessibility=value};concealedChildren.clear()
    }
    private fun requestFrame() {
        if(closed)return
        revision++
        if(scheduled)return
        scheduled=true
        decor.postOnAnimation {
            scheduled=false
            if(closed)return@postOnAnimation
            windows.refresh()
            if(!resumed)return@postOnAnimation
            if(windows.hasFocus()){windows.focusAcquired();decor.invalidate()}
            else {
                protect()
                val handoff=windows.pendingFocusHandoffMillis()
                if(handoff>0)decor.postDelayed({requestFrame()},minOf(32,handoff)) else conceal()
            }
        }
    }
    fun resume() {if(closed)return;resumed=true;requestFrame()}
    fun pause() {resumed=false;deniedForeground=false;revision++;conceal()}
    fun focusChanged(focused:Boolean) {if(closed)return;if(!focused)protect();requestFrame()}
    fun userLeaving(){if(!closed)protect()}
    /** Composition's current epoch must reach pre-draw after authenticated owned focus. */
    fun compositionCommitted(epoch:Int) {
        if(closed || !resumed || epoch!=revision || frameScheduled)return
        frameScheduled=true
        decor.viewTreeObserver.addOnPreDrawListener(object:ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw():Boolean {
                if(decor.viewTreeObserver.isAlive)decor.viewTreeObserver.removeOnPreDrawListener(this)
                frameScheduled=false
                if(closed || !resumed)return true
                if(epoch!=revision){requestFrame();return true}
                if(!windows.hasFocus()){protect();return true}
                if(!authorised()){conceal();return true}
                // The authority check can refresh the authoritative policy/revision.
                if(epoch!=revision)return true
                previousAccessibility?.let {decor.importantForAccessibility=it};previousAccessibility=null
                restoreContentAccessibility();deniedForeground=false
                windows.uncoverSecondaryWindows();curtain?.visibility=View.GONE
                if(allowed) {
                    if(!windows.restore(true)){failed();return true}
                    activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                } else protect()
                activity.setRecentsScreenshotEnabled(false)
                return true
            }
        })
        decor.invalidate()
    }
    fun close(){
        if(closed)return
        resumed=false;deniedForeground=false;conceal();closed=true
        // This single-use browser process is about to die. Keep every curtain/secure flag
        // until its windows detach; closing a tracker here would uncover a still-live Dialog.
    }
}
