package uk.co.traynor.privategallery.ui

import android.app.Activity
import android.os.Build
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowManager
import java.util.IdentityHashMap

/** Process-owned window focus, never window titles, media, credentials or another app's views. */
internal class PrivacyWindowTracker(private val activity:Activity, private val focusChanged:(Boolean)->Unit) {
    private data class Watch(val observer:ViewTreeObserver,val listener:ViewTreeObserver.OnWindowFocusChangeListener)
    private val watches=IdentityHashMap<View,Watch>()
    private val priorSecure=IdentityHashMap<View,Boolean>()
    private data class Curtain(val drawable:android.graphics.drawable.ColorDrawable,
        val layout:View.OnLayoutChangeListener,val accessibility:Int)
    private val curtains=IdentityHashMap<View,Curtain>()
    private var concealed=false
    private fun cover(view:View) {
        if(view===decor || curtains.containsKey(view))return
        val drawable=android.graphics.drawable.ColorDrawable(android.graphics.Color.BLACK)
        drawable.setBounds(0,0,view.width,view.height)
        val layout=View.OnLayoutChangeListener {_,_,_,_,_,_,_,_,_->
            drawable.setBounds(0,0,view.width,view.height)
        }
        curtains[view]=Curtain(drawable,layout,view.importantForAccessibility)
        view.importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        view.addOnLayoutChangeListener(layout)
        view.overlay.add(drawable)
        view.invalidate()
    }
    private fun uncover(view:View) {
        curtains.remove(view)?.let {
            view.overlay.remove(it.drawable)
            view.removeOnLayoutChangeListener(it.layout)
            view.importantForAccessibility=it.accessibility
            view.invalidate()
        }
    }
    /** Secondary Dialog/Popup pixels must not sit above the Activity's native curtain. */
    fun coverSecondaryWindows() {
        concealed=true
        refresh()
        roots().forEach(::cover)
    }
    fun uncoverSecondaryWindows() {
        concealed=false
        curtains.keys.toList().forEach(::uncover)
    }
    private val decor get()=activity.window.decorView
    val supportsOwnedFocus get()=Build.VERSION.SDK_INT>=29
    private fun roots():List<View> {
        if(Build.VERSION.SDK_INT<29)return listOf(decor)
        val main=decor
        val token=(main.layoutParams as? WindowManager.LayoutParams)?.token
        return android.view.inspector.WindowInspector.getGlobalWindowViews().filter {view ->
            val params=view.layoutParams as? WindowManager.LayoutParams
            view===main || view.context.playerActivity()===activity ||
                (token!=null && params?.token===token) ||
                (main.windowToken!=null && view.applicationWindowToken===main.windowToken)
        }.filter {it.isAttachedToWindow}
    }
    /** Returns whether a new secondary window was attached; allow its first focus dispatch. */
    fun refresh():Boolean {
        val current=roots()
        watches.keys.toList().filter {old->current.none {it===old}}.forEach {old->
            watches.remove(old)?.let {if(it.observer.isAlive)it.observer.removeOnWindowFocusChangeListener(it.listener)}
            priorSecure.remove(old)
            uncover(old)
        }
        var added=false
        current.forEach {view ->if(!watches.containsKey(view)) {
            val listener=ViewTreeObserver.OnWindowFocusChangeListener {focused->focusChanged(focused)}
            val observer=view.viewTreeObserver
            observer.addOnWindowFocusChangeListener(listener);watches[view]=Watch(observer,listener)
            if(view!==decor)added=true
        }}
        if(concealed)current.forEach(::cover)
        return added
    }
    fun hasFocus():Boolean = if(!supportsOwnedFocus)true else roots().any {
        it.windowVisibility==View.VISIBLE && it.hasWindowFocus()
    }
    private fun secure(view:View,enabled:Boolean):Boolean {
        val params=view.layoutParams as? WindowManager.LayoutParams ?: return true
        val flag=WindowManager.LayoutParams.FLAG_SECURE
        val flags=if(enabled)params.flags or flag else params.flags and flag.inv()
        if(flags==params.flags)return true
        params.flags=flags
        return runCatching {activity.windowManager.updateViewLayout(view,params);true}.getOrElse {!view.isAttachedToWindow}
    }
    /** Every live owned window is protected, including a dialog already focused over the Activity. */
    fun protect():Boolean {
        refresh()
        var applied=true
        roots().forEach {view ->
            if(view!==decor) {
                val params=view.layoutParams as? WindowManager.LayoutParams
                if(params!=null && !priorSecure.containsKey(view))priorSecure[view]=params.flags and WindowManager.LayoutParams.FLAG_SECURE!=0
                if(!secure(view,true))applied=false
            }
        }
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        return applied
    }
    /** Never clear an originally secure credential/provider window. Main policy is resolved separately. */
    fun restore(allowed:Boolean):Boolean {
        refresh()
        if(!allowed)return protect()
        var applied=true
        priorSecure.entries.toList().forEach {(view,wasSecure)->
            if(!secure(view,wasSecure))applied=false
            priorSecure.remove(view)
        }
        return applied
    }
    fun close() {
        uncoverSecondaryWindows()
        watches.values.forEach {if(it.observer.isAlive)it.observer.removeOnWindowFocusChangeListener(it.listener)}
        watches.clear();priorSecure.clear()
    }
}
