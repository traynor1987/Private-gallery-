(function () {
  'use strict';
  if (window.__privateGalleryVideoAssistant) return;
  window.__privateGalleryVideoAssistant = true;
  var video = null, button = null, timer = null;
  function remove() {
    if (timer) clearTimeout(timer);
    timer = null;
    if (button) button.remove();
    button = null;
  }
  function position() {
    if (!video || !video.isConnected || video.paused || video.ended || document.hidden) { remove(); return; }
    var r = video.getBoundingClientRect();
    if (button) {
      button.hidden = !!document.fullscreenElement || r.width < 80 || r.height < 60 || r.bottom < 0 || r.top > innerHeight;
      button.style.left = Math.max(4, Math.min(innerWidth - 48, r.right - 48)) + 'px';
      button.style.top = Math.max(4, Math.min(innerHeight - 48, r.top + 8)) + 'px';
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
    document.documentElement.appendChild(button); position();
  }
  document.addEventListener('playing', function (e) { if (e.target instanceof HTMLVideoElement) assist(e.target); }, true);
  document.addEventListener('visibilitychange', function () {
    if (document.hidden) { document.querySelectorAll('video').forEach(function(v) { v.pause(); }); remove(); }
  });
  window.addEventListener('pagehide', remove);
  function scanPlaying() {
    var v = Array.from(document.querySelectorAll('video')).find(function(v) { return !v.paused && !v.ended; });
    if (v) assist(v);
  }
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', scanPlaying, {once:true});
  else scanPlaying();
})();
