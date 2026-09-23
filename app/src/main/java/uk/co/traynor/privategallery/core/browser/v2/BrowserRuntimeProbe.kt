package uk.co.traynor.privategallery.core.browser.v2

import android.webkit.WebView

/** Acceptance-only aggregate snapshots. No native bridge, page text, addresses or storage reads. */
internal class BrowserRuntimeProbe(
    private val view: WebView,
    private val enabled: () -> Boolean,
    private val record: (String, Map<String, String>) -> Unit,
) {
    private var transition = 0
    private var armed = false
    private var transitionStarted: Long? = null
    private var awaitingRelease = false
    private var armedAt = 0L
    private var captureTicket = 0
    private val scheduled = mutableListOf<Runnable>()
    private val pinned = linkedMapOf<String, Map<String, String>>()
    private data class Failure(val at: Long, val kind: String, val code: Int, val mainFrame: Boolean, val document: Int)
    private val failures = ArrayDeque<Failure>()
    private var failureOverflow = 0
    private var previous: Map<String, String> = emptyMap()
    private var generation = 0
    private var disposed = false
    private var completion: MutableList<() -> Unit>? = null
    private fun completePending() { val callbacks = completion; completion = null; callbacks?.forEach { it() } }

    fun newDocument() { generation++; previous = emptyMap(); completePending() }
    fun armNextInteraction(onReady: (Boolean) -> Unit) {
        if (disposed || !enabled()) { onReady(false); return }
        cancelScheduled()
        pinned.clear(); failures.clear(); failureOverflow = 0
        transition++; armed = false; transitionStarted = null
        armedAt = android.os.SystemClock.elapsedRealtime()
        val ticket = captureTicket
        fun baseline() {
            if (disposed || !enabled() || ticket != captureTicket) { onReady(false); return }
            view.evaluateJavascript("(function(){var s=window.__privateGalleryAcceptanceStructureV1;if(s){s.before=null;s.armed=false;}})()") {
                if (disposed || !enabled() || ticket != captureTicket) { onReady(false); return@evaluateJavascript }
                val document = generation
                capture("ARMED_BASELINE") {
                    if (disposed || !enabled() || ticket != captureTicket || document != generation) { onReady(false); return@capture }
                    view.evaluateJavascript("(function(){var s=window.__privateGalleryAcceptanceStructureV1;if(s){s.armed=true;return true;}return false;})()") { result ->
                        val ready = ticket == captureTicket && document == generation && !disposed && enabled() && result == "true" && pinned.containsKey("ARMED_BASELINE")
                        armed = ready
                        record("INTERACTION_ARMED", mapOf("transition" to transition.toString(), "ready" to ready.toString()))
                        onReady(ready)
                    }
                }
            }
        }
        // Wait for any in-flight sample, then collect a fresh baseline.
        if (completion != null) completion?.add { baseline() } else baseline()
    }

    /** Called before WebView processes ACTION_DOWN; never consumes or redirects the input. */
    fun inputStarted() {
        if (!armed || disposed || !enabled()) return
        armed = false
        transitionStarted = android.os.SystemClock.elapsedRealtime()
        record("INTERACTION_START", mapOf("transition" to transition.toString(), "document_generation" to generation.toString()))
        awaitingRelease = true
    }

    /** Post-release sampling observes the click-handler result rather than only pointer-down. */
    fun inputFinished() {
        if (!awaitingRelease || disposed || !enabled()) return
        awaitingRelease = false
        val ticket = captureTicket
        listOf(0L, 250L, 1000L, 2000L).forEach { delay ->
            val task = Runnable {
                if (!disposed && enabled() && ticket == captureTicket) capture("AFTER_INPUT_" + delay)
            }
            scheduled += task
            view.postDelayed(task, delay)
        }
    }

    fun inputCanceled() {
        if (!awaitingRelease) return
        cancelScheduled(); armed = false
        record("INTERACTION_CANCELED", mapOf("transition" to transition.toString()))
        pinned["CANCELED"] = mapOf("transition" to transition.toString())
        transitionStarted = null
        view.evaluateJavascript("(function(){var s=window.__privateGalleryAcceptanceStructureV1;if(s)s.armed=false;})()", null)
    }

    fun failure(kind: String, code: Int, mainFrame: Boolean) {
        if (disposed || !enabled() || pinned.isEmpty()) return
        if (kind !in setOf("HTTP", "RESOURCE", "TLS", "RENDERER")) return
        if (failures.size == 32) { failures.removeFirst(); failureOverflow++ }
        failures.addLast(Failure(android.os.SystemClock.elapsedRealtime(), kind, code, mainFrame, generation))
    }

    fun interactionReport(): String = if (pinned.isEmpty()) "No armed interaction captured." else buildString {
        appendLine("PINNED STRUCTURAL COMPARISON transition=$transition started=${transitionStarted != null}")
        appendLine("Structural/hit-test estimates, not pixel proof. Frame interiors and complex paint may be unknown.")
        appendLine("Failure times are callback times, not request start times; correlation does not establish causation.")
        pinned.forEach { (phase, values) -> appendLine(phase + " " + values.entries.joinToString(" ") { "${it.key}=${it.value}" }) }
        appendLine("FAILURE_WINDOW dropped=$failureOverflow")
        failures.forEach { failure ->
            val origin = transitionStarted ?: armedAt
            appendLine("FAILURE relative_ms=${failure.at - origin} kind=${failure.kind} code=${failure.code} main_frame=${failure.mainFrame} document_generation=${failure.document}")
        }
    }

    private fun cancelScheduled() {
        captureTicket++; awaitingRelease = false
        scheduled.forEach(view::removeCallbacks)
        scheduled.clear()
    }

    fun pause() {
        if (disposed) return
        cancelScheduled(); armed = false; transitionStarted = null; pinned.clear(); failures.clear()
        generation++; completePending()
        view.evaluateJavascript("(function(){var s=window.__privateGalleryAcceptanceStructureV1;if(s&&typeof s.stop==='function')s.stop();delete window.__privateGalleryAcceptanceStructureV1;})()", null)
    }
    fun dispose() { pause(); disposed = true; generation++; completePending() }

    fun capture(phase: String, onComplete: () -> Unit = {}) {
        if (disposed || !enabled()) { onComplete(); return }
        completion?.let {
            if (phase == "OWNER_SNAPSHOT" || phase.startsWith("AFTER_INPUT_")) {
                val ticket = captureTicket
                it += { if (!disposed && enabled() && ticket == captureTicket) capture(phase, onComplete) else onComplete() }
            } else it += onComplete
            return
        }
        val callbacks = mutableListOf(onComplete)
        completion = callbacks
        val document = generation
        view.evaluateJavascript(SCRIPT) { raw ->
            if (document != generation || disposed) return@evaluateJavascript
            if (!enabled()) { completePending(); return@evaluateJavascript }
            // Script returns an object: WebView JSON-encodes it exactly once.
            val values = BrowserRuntimeSnapshot.parse(raw)
            if (values.isEmpty()) { record("RUNTIME_SNAPSHOT_UNAVAILABLE", mapOf("phase" to phase)); completePending(); return@evaluateJavascript }
            val timing = mapOf("phase" to phase, "document_generation" to document.toString(),
                "transition" to transition.toString(), "relative_ms" to (android.os.SystemClock.elapsedRealtime() - (transitionStarted ?: armedAt)).toString())
            record("RUNTIME_SNAPSHOT", values + timing)
            if (phase == "ARMED_BASELINE" || (transitionStarted != null && (phase.startsWith("AFTER_INPUT_") || phase == "OWNER_SNAPSHOT"))) {
                val baseline = pinned["ARMED_BASELINE"]
                val comparison = buildMap {
                    if (baseline != null) {
                        put("same_document_marker", (baseline["document_marker"] == values["document_marker"] && values.containsKey("document_marker")).toString())
                        put("page_starts_since_baseline", (document - (baseline["document_generation"]?.toIntOrNull() ?: document)).toString())
                        listOf("dom_nodes", "frames", "modals", "covering_layers", "ancestor_hidden", "sample_obscured", "sample_unobscured", "nodes_added", "nodes_removed", "attribute_changes").forEach { key ->
                            val before = baseline[key]?.toIntOrNull(); val after = values[key]?.toIntOrNull()
                            if (before != null && after != null) put("change_" + key, (after - before).toString())
                        }
                    }
                }
                pinned[phase] = values + timing + comparison
            }
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
              var measureVisual=${BrowserVisualStructure.SCRIPT};
              var key='__privateGalleryAcceptanceStructureV1';
              var s=window[key];
              if(!s){
                s={runtime_errors:0,promise_rejections:0,resource_failures:0,focus_changes:0,visibility_changes:0,
                  document_marker:Math.floor(Math.random()*999999)+1,mutation_batches:0,nodes_added:0,nodes_removed:0,attribute_changes:0,root:document.documentElement,body:document.body};
                Object.defineProperty(window,key,{value:s,configurable:true});
                var hooks=[];function observe(target,kind,handler){target.addEventListener(kind,handler,true);hooks.push([target,kind,handler]);}
                s.mutations=function(records){if(records.length)s.mutation_batches++;if(records.length>1000)s.mutation_scan_truncated=true;records.slice(0,1000).forEach(function(r){if(r.type==='childList'){s.nodes_added+=r.addedNodes.length;s.nodes_removed+=r.removedNodes.length;}else if(r.type==='attributes')s.attribute_changes++;});};
                s.observer=new MutationObserver(s.mutations);s.observer.observe(document,{subtree:true,childList:true,attributes:true});
                s.stop=function(){s.armed=false;s.observer.disconnect();hooks.forEach(function(h){h[0].removeEventListener(h[1],h[2],true);});};
                observe(window,'pointerdown',function(){if(s.armed&&s.snapshot){s.armed=false;s.before=s.snapshot();}});
                observe(window,'error',function(e){if(e.target===window)s.runtime_errors++;else s.resource_failures++;});
                observe(window,'unhandledrejection',function(){s.promise_rejections++;});
                observe(window,'focus',function(){s.focus_changes++;});
                observe(window,'blur',function(){s.focus_changes++;});
                observe(document,'visibilitychange',function(){s.visibility_changes++;});
                try {var c=document.createElement('canvas');var gl=c.getContext('webgl');s.webgl=!!gl;if(gl){var lose=gl.getExtension('WEBGL_lose_context');if(lose)lose.loseContext();}}catch(e){s.webgl=false;}
              }
              s.snapshot=function(){
              s.mutations(s.observer.takeRecords());
              var nodes=document.getElementsByTagName('*'),visible=0,limit=Math.min(nodes.length,5000);
              for(var i=0;i<limit;i++){var e=nodes[i],r=e.getBoundingClientRect();if(r.width>0&&r.height>0){var css=getComputedStyle(e);if(css.display!=='none'&&css.visibility!=='hidden'&&Number(css.opacity)>0)visible++;}}
              var storage=false;try{storage=typeof window.localStorage==='object';}catch(e){}
              return Object.assign({document_marker:s.document_marker,root_replaced:s.root!==document.documentElement,body_replaced:s.body!==document.body,
                mutation_scan_truncated:!!s.mutation_scan_truncated,mutation_batches:s.mutation_batches,nodes_added:s.nodes_added,nodes_removed:s.nodes_removed,attribute_changes:s.attribute_changes,dom_nodes:nodes.length,sampled_elements:limit,scan_truncated:nodes.length>limit,visible_elements:visible,
                scripts:document.scripts.length,frames:document.querySelectorAll('iframe,frame').length,canvas:document.querySelectorAll('canvas').length,
                stylesheets:document.styleSheets.length,modals:document.querySelectorAll('dialog[open],[role="dialog"],[aria-modal="true"]').length,
                ready:document.readyState,visibility:document.visibilityState,focus:document.hasFocus(),viewport_width:window.innerWidth,viewport_height:window.innerHeight,
                local_storage:storage,indexed_db:typeof indexedDB!=='undefined',service_worker:'serviceWorker' in navigator,webgl:!!s.webgl,
                runtime_errors:s.runtime_errors,promise_rejections:s.promise_rejections,resource_failures:s.resource_failures,focus_changes:s.focus_changes,visibility_changes:s.visibility_changes},measureVisual());
              };
              return Object.assign(s.snapshot(),{pre_input_captured:!!s.before,interaction_before:s.before||null});
            })()
        """.trimIndent()
    }
}
