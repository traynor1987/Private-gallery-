package uk.co.traynor.privategallery.core.browser.v2

/** DOM-only helper runs in each frame. It exposes no Java/credential/network bridge. */
internal object BrowserVideoAssistant {
    fun script(context: android.content.Context): String = context.assets.open("browser-video-assistant.js").bufferedReader().use { it.readText() }
    const val SOURCE = """(function(){var vs=Array.from(document.querySelectorAll('video')).filter(function(v){var r=v.getBoundingClientRect();return r.width>0&&r.height>0;});var v=vs.find(function(v){return !v.paused&&!v.ended;})||vs[0];return JSON.stringify(v?{url:v.currentSrc||v.src,drm:!!v.mediaKeys,video:true,playing:!v.paused&&!v.ended}:{url:'',drm:false,video:false,playing:false});})()"""
    const val PAUSE = """(function pause(w,n){try{w.document.querySelectorAll('video,audio').forEach(function(v){v.pause();});for(var j=0;j<w.frames.length;j++)w.frames[j].postMessage({type:'private-gallery-pause-media'},'*');if(n<8)for(var i=0;i<w.frames.length;i++)pause(w.frames[i],n+1);}catch(e){}})(window,0)"""
}
