package uk.co.traynor.privategallery.phase0

import android.net.Uri
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream

/** Only immutable local bytes; every miss is a hard denial, never null/network fallback. */
internal object SyntheticSite {
  const val ORIGIN = "https://phase0.invalid"
  fun response(uri: Uri, marker: String): WebResourceResponse {
    if (!Phase0BrowserPolicy.allowsResource(uri.toString())) return denied()
    return when (uri.path) {
      "/" -> bytes("text/html", """<!doctype html><meta charset="utf-8"><title>synthetic phase0</title>
        <meta http-equiv="Content-Security-Policy" content="default-src 'none'; script-src 'self' 'unsafe-inline'; connect-src 'self'; worker-src 'self'; img-src 'none'; form-action 'none'; frame-src 'none'; base-uri 'none'">
        <p>Synthetic test fixture. No production Vault or website data.</p>""")
      "/sw.js" -> bytes("application/javascript", """
        self.addEventListener('install', e => e.waitUntil(self.skipWaiting()));
        self.addEventListener('activate', e => e.waitUntil(self.clients.claim()));
        self.addEventListener('message', e => { if(e.ports[0]) e.ports[0].postMessage('$marker'); });
      """)
      else -> denied()
    }
  }
  private fun bytes(type: String, text: String) = WebResourceResponse(type, "UTF-8", 200, "OK",
    mapOf("Cache-Control" to "no-store", "X-Content-Type-Options" to "nosniff"),
    ByteArrayInputStream(text.toByteArray(Charsets.UTF_8)))
  fun denied() = WebResourceResponse("text/plain", "UTF-8", 403, "Forbidden",
    mapOf("Cache-Control" to "no-store"), ByteArrayInputStream(ByteArray(0)))

  /** Poll via evaluateJavascript; no JS bridge or external fixture dependency. */
  fun script(marker: String, write: Boolean, serviceWorker: Boolean): String = """
    (function(){
      window.phase0Snapshot = null;
      const marker = '$marker', write = $write;
      const result = {};
      const measured = value => ({status:'MEASURED',value:value});
      const missing = () => ({status:'NOT_SUPPORTED',value:null});
      const failed = () => ({status:'UNMEASURED_FIXTURE_OR_PROVIDER',value:null});
      const bounded = promise => Promise.race([promise,new Promise((_,reject)=>setTimeout(reject,5000))]);
      (async function(){
        try { if(write) document.cookie='phase0_js='+marker+'; Secure; SameSite=Strict; Path=/'; result.domCookie=measured(document.cookie); }
        catch(_) { result.domCookie=failed(); }
        try { if(write) localStorage.setItem('phase0',marker); result.localStorage=measured(localStorage.getItem('phase0')); }
        catch(_) { result.localStorage=failed(); }
        try { if(write) sessionStorage.setItem('phase0',marker); result.sessionStorage=measured(sessionStorage.getItem('phase0')); }
        catch(_) { result.sessionStorage=failed(); }
        if(!window.indexedDB) result.indexedDB=missing();
        else try { result.indexedDB=measured(await bounded(new Promise((resolve,reject)=>{
          const request=indexedDB.open('phase0-synthetic',1);
          request.onupgradeneeded=()=>request.result.createObjectStore('markers');
          request.onerror=reject;
          request.onsuccess=()=>{
            const db=request.result, tx=db.transaction('markers',write?'readwrite':'readonly'), store=tx.objectStore('markers');
            const item=write?store.put(marker,'phase0'):store.get('phase0');
            let value=null; item.onsuccess=()=>value=write?marker:item.result;
            tx.oncomplete=()=>{ db.close(); resolve(value === undefined ? null : value); };
            tx.onerror=()=>{ db.close(); reject(); };
          };
        }))); } catch(_) { result.indexedDB=failed(); }
        if(!window.caches) result.cacheStorage=missing();
        else try {
          const cache=await bounded(caches.open('phase0-synthetic'));
          if(write) await bounded(cache.put('/phase0-cache-marker',new Response(marker)));
          const entry=await bounded(cache.match('/phase0-cache-marker'));
          result.cacheStorage=measured(entry?await bounded(entry.text()):null);
        } catch(_) { result.cacheStorage=failed(); }
        if(!navigator.serviceWorker || !$serviceWorker) result.serviceWorker=missing();
        else try {
          let registration;
          if(write) {
            for(const old of await bounded(navigator.serviceWorker.getRegistrations())) await bounded(old.unregister());
            registration=await bounded(navigator.serviceWorker.register('/sw.js'));
            await bounded(new Promise((resolve,reject)=>{
              if(registration.active) { resolve(); return; }
              const worker=registration.installing || registration.waiting;
              if(!worker) { reject(); return; }
              worker.addEventListener('statechange',()=>{
                if(worker.state==='activated') resolve();
                if(worker.state==='redundant') reject();
              });
            }));
          } else registration=await bounded(navigator.serviceWorker.getRegistration('/'));
          if(!registration || !registration.active) result.serviceWorker=measured(null);
          else result.serviceWorker=measured(await bounded(new Promise(resolve=>{
            const channel=new MessageChannel(); channel.port1.onmessage=e=>resolve(e.data);
            registration.active.postMessage('marker',[channel.port2]);
          })));
        } catch(_) { result.serviceWorker=failed(); }
        result.httpCache={status:'UNMEASURED_SYNTHETIC_INTERCEPTION',value:null};
        result.history={status:'UNMEASURED_PROVIDER_HISTORY',value:null};
        window.phase0Snapshot = result;
      })().catch(()=>window.phase0Snapshot={fixtureStatus:'UNMEASURED'});
    })();
  """.trimIndent()
}
