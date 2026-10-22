# Private Gallery 1.6 — code31 owner acceptance

Status: awaiting Android gates and an identified signed candidate. Do not install an unverified artifact. No production approval is implied.

## Upgrade protection

- Recommended artifact must be production package `uk.co.traynor.privategallery`, versionCode31, signed by certificate SHA256 `94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17`.
- Install over code30. Never uninstall, clear data, reset credentials or perform destructive migrations.
- Confirm existing Primary/Hidden PINs, encrypted media, collection names/counts, favourites and representative photos/videos remain accessible. Investigation uses disposable media only.

## 1. Independent discreet authentication

- Cold start shows only neutral Private Gallery PIN presentation.
- Ordinary PIN submission checks Primary only and opens Primary. No remembered Hidden label, return button or content.
- Long-press the Private Gallery title, then enter the independent Hidden PIN: Hidden opens directly without Primary PIN.
- The gesture applies to one attempt. A failed attempt and backgrounding reset it; neither reveals Hidden.
- Repeat after process death, auto-lock expiry, device locking and vault switching. No unauthenticated Hidden browser/tab/media restoration.

## 2. Batch transfer

- In selected Hidden context, open Gallery → Primary Vault and explicitly authenticate Primary.
- Select more than100 disposable photos. Both Copy and Move identify source, destination, action and total before starting.
- Observe real completed/total counts and progress. Repeated taps cannot start another transfer.
- Cancel part way; check verified copies, failed/unprocessed count and retained originals. Move deletes no source before destination verification; moved originals remain in Recently Deleted under existing policy.
- Retry remaining items. An interrupted batch must not be called complete.

## 3. Combine Photos

- Open an image from a vault viewer → AI Edit → Seedream4.5 → Combine Photos.
- Choose the second photo from the same viewer/vault. Both references appear before submission. The numbered reference list is limited to the current viewer's eligible photos.
- Enter a custom prompt. Confirm the explicit disclosure that both selected photos and the typed prompt leave the device for external AI processing. This may incur the provider's charge.
- Observe generation state/errors; preview and test Save, Regenerate and Discard. Originals remain unchanged; saved copy belongs only to the currently authorized vault.
- Switching from an approved single-image edit to Combine must still require the both-photo disclosure.

## 4. Complete video download

- Primary: use the existing download icon beside fullscreen on an authorized unprotected finite video. Compare saved duration with the full source (e.g.13min), not a10s segment.
- Test direct MP4 and supported finite HLS/DASH, interrupted response/retry and a standalone segment. Unsupported/live/DRM or incomplete sources must fail clearly, never report a segment as the full movie.
- Hidden: only supported complete direct media is currently accepted. HLS/DASH/standalone segments are rejected safely; do not claim Hidden segmented export support.
- Byte counts are acquisition progress; success follows encryption and validation. Video sample traversal is container/sample completeness checking, not full frame decoding.

## 5. Hidden browser and routes

- Record Fold/Android/System WebView versions and network. Test folded/unfolded, keyboard shown/hidden, portrait/landscape. One shared bottom navigation; no clipped toolbar or blank empty-tab page.
- Tor Only: start without a required WireGuard tunnel, observe numeric bootstrap and HTTPS check, load supported HTTPS sites. Connected requires the actual selected transport check.
- VPN Only: use existing approved WireGuard profile, allow Android VPN permission, return to app and load supported HTTPS sites. Repeat after Primary timeout during permission.
- A cold direct-Hidden entry cannot decrypt a Primary-encrypted VPN profile without Primary authority. If no approved configuration is already loaded, it remains blocked with setup guidance; never bypass it or use direct internet.
- Background closes the browser. Within Hidden grace, return creates a fresh isolated runtime/profile with no old tabs; expired authentication returns neutral lock. Retry creates a fresh session.
- Drop the route/network, revoke permission and switch modes. Requests must stop and no direct-network fallback is allowed. Verify gateway/WireGuard packet and DNS evidence on the physical Fold; a VPN icon or exit-IP page alone is insufficient.
- VPN+Tor is explicitly unavailable and starts no alternative transport.
- JavaScript/forms/frames/redirects remain restricted. Test supported static HTTPS pages; this is not Tor Browser fingerprint protection or a full unrestricted WebView.
- Primary WireGuard remains functional. Confirm Primary/Hidden cookies, tabs and encrypted downloads remain isolated.

Record PASS/FAIL per section with precise reproduction steps. Physical network/DNS verification and owner acceptance are mandatory before production GO. No main merge without approval.
