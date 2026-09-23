package uk.co.traynor.privategallery.core.browser.v2

import android.webkit.WebView

/** Acceptance-only aggregate snapshots. No native bridge, page text, addresses or storage reads. */
internal class BrowserRuntimeProbe(
    private val view: WebView,
    private val enabled: () -> Boolean,
    private val record: (String, Map<String, String>) -> Unit,
) {
    private var previous: Map<String, String> = emptyMap()
    private var generation = 0
    private var disposed = false
    private var completion: MutableList<() -> Unit>? = null
    private fun completePending() { val callbacks = completion; completion = null; callbacks?.forEach { it() } }

    fun newDocument() { generation++; previous = emptyMap(); completePending() }
    fun pause() {
        if (disposed) return
        generation++; completePending()
        view.evaluateJavascript("(function(){var s=window.__privateGalleryAcceptanceStructureV1;if(s&&typeof s.stop==='function')s.stop();delete window.__privateGalleryAcceptanceStructureV1;})()", null)
    }
    fun dispose() { disposed = true; generation++; completePending() }

    fun capture(phase: String, onComplete: () -> Unit = {}) {
        if (disposed || !enabled()) { onComplete(); return }
        completion?.let { it += onComplete; return }
        val callbacks = mutableListOf(onComplete)
        completion = callbacks
        val document = generation
        view.evaluateJavascript(SCRIPT) { raw ->
            if (document != generation || disposed) return@evaluateJavascript
            if (!enabled()) { completePending(); return@evaluateJavascript }
            // Script returns an object: WebView JSON-encodes it exactly once.
            val values = BrowserRuntimeSnapshot.parse(raw)
            if (values.isEmpty()) { record("RUNTIME_SNAPSHOT_UNAVAILABLE", mapOf("phase" to phase)); completePending(); return@evaluateJavascript }
            record("RUNTIME_SNAPSHOT", values + ("phase" to phase))
            listOf("frames" to "IFRAME_COUNT_CHANGE", "modals" to "DOM_MODAL_COUNT_CHANGE").forEach { (key, event) ->
                values[key]?.let { value -> if (previous[key] != value) record(event, mapOf("count" to value, "phase" to phase)) }
            }
            mapOf("ready" to "DOCUMENT_READY_STATE", "visibility" to "DOCUMENT_VISIBILITY", "focus" to "DOCUMENT_FOCUS").forEach { (key, event) ->
                values[key]?.let { record(event, mapOf("value" to it, "phase" to phase)) }
            }
            previous = values
            completePending()
        }
    }

    companion object {
        // Counters begin at first snapshot (commit-visible), not before application startup.
        // No console/function monkey-patching, DOM mutation or request interception.
        internal val SCRIPT = """
            (function(){
              var key='__privateGalleryAcceptanceStructureV1';
              var s=window[key];
              if(!s){
                s={runtime_errors:0,promise_rejections:0,resource_failures:0,focus_changes:0,visibility_changes:0};
                Object.defineProperty(window,key,{value:s,configurable:true});
                var hooks=[];function observe(target,kind,handler){target.addEventListener(kind,handler,true);hooks.push([target,kind,handler]);}
                s.stop=function(){hooks.forEach(function(h){h[0].removeEventListener(h[1],h[2],true);});};
                observe(window,'error',function(e){if(e.target===window)s.runtime_errors++;else s.resource_failures++;});
                observe(window,'unhandledrejection',function(){s.promise_rejections++;});
                observe(window,'focus',function(){s.focus_changes++;});
                observe(window,'blur',function(){s.focus_changes++;});
                observe(document,'visibilitychange',function(){s.visibility_changes++;});
                try {var c=document.createElement('canvas');var gl=c.getContext('webgl');s.webgl=!!gl;if(gl){var lose=gl.getExtension('WEBGL_lose_context');if(lose)lose.loseContext();}}catch(e){s.webgl=false;}
              }
              var nodes=document.getElementsByTagName('*'),visible=0,limit=Math.min(nodes.length,5000);
              for(var i=0;i<limit;i++){var e=nodes[i],r=e.getBoundingClientRect();if(r.width>0&&r.height>0){var css=getComputedStyle(e);if(css.display!=='none'&&css.visibility!=='hidden'&&Number(css.opacity)>0)visible++;}}
              var storage=false;try{storage=typeof window.localStorage==='object';}catch(e){}
              return {dom_nodes:nodes.length,sampled_elements:limit,scan_truncated:nodes.length>limit,visible_elements:visible,
                scripts:document.scripts.length,frames:document.querySelectorAll('iframe,frame').length,canvas:document.querySelectorAll('canvas').length,
                stylesheets:document.styleSheets.length,modals:document.querySelectorAll('dialog[open],[role="dialog"],[aria-modal="true"]').length,
                ready:document.readyState,visibility:document.visibilityState,focus:document.hasFocus(),viewport_width:window.innerWidth,viewport_height:window.innerHeight,
                local_storage:storage,indexed_db:typeof indexedDB!=='undefined',service_worker:'serviceWorker' in navigator,webgl:!!s.webgl,
                runtime_errors:s.runtime_errors,promise_rejections:s.promise_rejections,resource_failures:s.resource_failures,focus_changes:s.focus_changes,visibility_changes:s.visibility_changes};
            })()
        """.trimIndent()
    }
}
