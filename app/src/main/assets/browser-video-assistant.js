(function () {
  'use strict';
  if (window.__privateGalleryVideoAssistant) return;
  window.__privateGalleryVideoAssistant = true;
  var video = null, button = null, download = null, timer = null, saveAvailable = false;
  function remove() {
    if (button) console.info('PG_VIDEO_STOPPED');
    if (timer) clearTimeout(timer);
    timer = null;
    if (button) button.remove();
    button = null;
    if (download) download.remove();
    download = null;
  }
  function position() {
    if (timer) clearTimeout(timer);
    timer = null;
    if (!video || !video.isConnected || video.paused || video.ended || document.hidden) { remove(); return; }
    var r = video.getBoundingClientRect();
    if (button) {
      var hidden = !!document.fullscreenElement || r.width < 80 || r.height < 60 || r.bottom < 0 || r.top > innerHeight;
      button.hidden = hidden;
      button.style.left = Math.max(4, Math.min(innerWidth - 48, r.right - 48)) + 'px';
      button.style.top = Math.max(4, Math.min(innerHeight - 48, r.top + 8)) + 'px';
      if (download) {
        download.hidden = hidden || !saveAvailable;
        download.style.left = Math.max(4, Math.min(innerWidth - 92, r.right - 92)) + 'px';
        download.style.top = button.style.top;
      }
    }
    timer = setTimeout(position, 350);
  }
  function assist(v) {
    remove(); video = v;
    if (!v || v.paused || v.ended || !document.documentElement) return;
    button = document.createElement('button');
    button.type = 'button';
    button.setAttribute('data-pg-video-view', '');
    button.setAttribute('aria-label', 'View video fullscreen');
    button.title = 'View video fullscreen';
    button.textContent = '⛶';
    button.style.cssText = 'position:fixed!important;z-index:2147483647!important;width:40px!important;height:40px!important;border:1px solid #ffffff66!important;border-radius:12px!important;background:#202630cc!important;color:white!important;font:26px sans-serif!important;line-height:32px!important;padding:0!important;';
    button.addEventListener('click', function (event) {
      event.preventDefault(); event.stopPropagation();
      // A real DOM click preserves transient user activation, including in embedded frames.
      // No source extraction, DRM/session migration, native bridge or permission-policy bypass.
      var request = video.requestFullscreen || video.webkitRequestFullscreen;
      if (!request) { button.title = 'Use Browser menu → Video view'; return; }
      try {
        var result = request.call(video);
        if (result && result.catch) result.catch(function () { if (button) button.title = 'Use the page fullscreen control or Browser menu → Video view'; });
      } catch (_) { if (button) button.title = 'Use Browser menu → Video view'; }
    });
    document.documentElement.appendChild(button);
    download = document.createElement('button');
    download.type = 'button';
    download.setAttribute('data-pg-video-save', '');
    download.setAttribute('aria-label', 'Save video to Vault');
    download.title = 'Save video to Vault';
    download.textContent = '↓';
    download.style.cssText = button.style.cssText;
    download.addEventListener('click', function (event) {
      event.preventDefault(); event.stopPropagation();
      if (saveAvailable && video && !video.paused && !video.ended) window.prompt('private-gallery-save-video', '');
    });
    document.documentElement.appendChild(download); position();
    console.info('PG_VIDEO_PLAYING');
  }
  document.addEventListener('playing', function (e) { if (e.target instanceof HTMLVideoElement) assist(e.target); }, true);
  document.addEventListener('visibilitychange', function () {
    // Chromium may hide the document while moving video to a native custom view.
    // Native Activity/tab/VPN lifecycle code owns background pausing.
    if (document.hidden) remove();
    else scanPlaying();
  });
  window.addEventListener('message', function(event) {
    if (event.data && event.data.type === 'private-gallery-video-save-available') {
      saveAvailable = event.data.available === true;
      for (var j = 0; j < window.frames.length; j++) window.frames[j].postMessage(event.data, '*');
      position(); return;
    }
    if (!event.data || event.data.type !== 'private-gallery-pause-media') return;
    document.querySelectorAll('video,audio').forEach(function(v) { v.pause(); });
    for (var i = 0; i < window.frames.length; i++) {
      window.frames[i].postMessage({type: 'private-gallery-pause-media'}, '*');
    }
  });
  window.addEventListener('pagehide', remove);
  function scanPlaying() {
    var v = Array.from(document.querySelectorAll('video')).find(function(v) { return !v.paused && !v.ended; });
    if (v) assist(v);
  }
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', scanPlaying, {once:true});
  else scanPlaying();
})();
