# Preview 0.4 navigation and multitasking

Build on e17a7d18, same package, same development key, code4. Production integration is separately authorized later, never part of this change.

- Add a private shell settings/event store without changing existing layout or cookie schema. Buttons default Recents/Home/Back; reversible order and optional Nova gesture strip. Root consumes Android navigation/IME insets once, app content remains above Nova's own bar.
- Recents is a horizontal adaptive pager of profile-local grouped retained app tabs, safe icon/title placeholders, resume, upward swipe-close, accessible close and confirmed Close All. No screenshot capture of account pages. Retain WebViews attached in a common host; pause inactive tabs unless they report supported active media through the public Media Session API. Stop/pause on Activity stop and profile changes as before.
- Read only bounded public navigator.mediaSession title/state while foregrounded and outside Google account pages; no DOM media scraping, credentials, JS bridge, media extraction, handler replacement or autoplay. Mini surface shows provided metadata/state and Return. No transport buttons without a supported host action interface. If unavailable, disclose this in the panel.
- Optional inset left/right Edge handle, configurable height and favourite apps; sheet links to profile-local recent apps, Store, Home, settings. No system gesture exclusion.
- Persist real preview installation and wallpaper-change events per demo profile, capped at40. Clear/delete UI; no fake downloads/update notifications, no Android notification interception.
- Test pure shell/media policy, private settings/events round-trip, Recents/Home/Back/app retention, gesture/order, edge panel, events and Fold/inset layouts. Host checks first; one focused Android17 attempt, publish experimental with exact runtime outcome. No new engine, OAuth change, VPN or Tor.


Review: two important findings fixed and covered: empty Recents Back falls back to Home rather than blank browser; playing sessions outrank newer paused sessions. Host rules RED→GREEN. Media callbacks bounded before JSON parse and discarded after navigation; lifecycle calls only on transitions. Navigation/Edge/mini/event UI and tests compile. Recents uses safe placeholders; no webpage screenshots or unsupported transport controls.
