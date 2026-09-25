# Physical acceptance follow-up — Browser and protected video

Starting main: `8a2ad4fd3d166a208caa829990e55fa74f385029` (Android #320 / acceptance #30).

## Changes and evidence

- Browser chrome previously disappeared from the Column immediately, changing page height in a single frame. Top and bottom now share a 220 ms slide/height animation. Scroll callbacks caused during that transition cannot reverse its target. WebView ownership/key remains unchanged. Physical smoothness remains to be verified.
- The injected video helper paused media on every document visibility change, including fullscreen transitions. A Node regression reproduced that unconditional pause. Native Activity/background/VPN policy remains in charge. Explicit outgoing-tab pausing and a DOM-only pause message to embedded frames preserve inactive-tab silence without reading cross-origin documents or media sources.
- System Back now has a Gallery fallback from protected destinations and Browser root. Focused address editing consumes Back before page history; fullscreen and player handlers retain priority. Dedicated Media3 playback handles system Back through its existing close action.
- Protected viewing previously used an expandable plaintext output stream and copied its entire result, while CipherInputStream fed decryption in small reads. Bulk 64 KiB cipher input and an exact-size output buffer reduce I/O/provider call count and output allocation. Authentication must finish before the buffer is returned. Failure/cancellation wipes output; no plaintext disk file is introduced. Closing the video viewer now cancels its pending read, as do lock/activity cancellation.

## Limitations

Playback still authenticates the complete legacy AES-GCM payload before playing and uses an in-memory seekable source. This is not a streaming format migration and does not establish a maximum startup time or guarantee arbitrarily large videos fit in memory. Real-device failures may additionally involve codecs or memory limits; no device fatal/player report was supplied.

## Verification state

- Node helper tests: two passed (visibility transition and embedded-frame pause forwarding); original visibility regression observed failing before the fix.
- JavaScript syntax, diff whitespace, tracked no-secret and WireGuard-only checks passed locally.
- Added Android coverage: root Back, player Back, tab media pause, native fullscreen still playing, viewer-close cancellation, intermediate chrome height. Added JVM coverage: bulk crypto reads/authentication and metadata-length mismatch.
- Android compile/unit/lint/instrumentation not run: Gradle distribution download is blocked by network access. No CI result is claimed.
- Automatic approval review rejected both a direct main push and a review-branch push. No remote changes, PR, release or acceptance build were created for this follow-up. Obtain explicit owner authorization to push these fixes to `traynor1987/Private-gallery-` and continue through CI before device installation.

## Device checks after green CI

1. Slowly scroll and rapidly reverse scrolling on a long webpage on both Fold displays. Bars move together without flashing, page replacement or lost position.
2. Play the affected web videos; enter/exit webpage and helper fullscreen. Playback continues, orientation works and Back restores Browser.
3. Change tabs while video is playing. Outgoing media stops; returning does not unexpectedly autoplay. Test embedded video too.
4. System button/edge Back: address editing → page history → Gallery; panels → Browser; player → originating grid. App does not exit while an inner destination remains.
5. Open the same previously slow/failing encrypted videos, seek and reopen. Record file size, startup time and whether a read error or player error appears if still failing. Rapid open/close must not leave reads accumulating.
6. Home/screen-off obey auto-lock; VPN loss blocks Browser playback; rotate/fold does not spuriously lock; exiting video restores portrait.
