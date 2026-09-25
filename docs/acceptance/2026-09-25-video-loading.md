# Video playback and loading follow-up

Starting device candidate: main `78be3f3`, acceptance build #31.

The owner reports videos no longer play and asks for a percentage loading modal.
No device exception, codec details or failing media file was supplied. Do not claim
that an emulator test establishes the exact physical failure cause.

## Implementation

- Protected loading is owned by one active effect generation. Disposal cancels its
  callback, wipes its buffer and drops its UI reference. Reentry starts a new load;
  late completions cannot replace a newer generation.
- ExoPlayer release belongs to its player lifetime, not the lifecycle-observer
  registration. Replacing the lifecycle owner must not release a player retained
  by Compose. Listeners attach before preparation starts.
- Decryption dialog shows measured ciphertext-read progress, monotonically from
  0 to 99. 100 is reported only after authentication and output-length validation.
  At the end it reports verification, then a separate Preparing video modal while
  the decoder opens. No fabricated time-based percentage is used for codec work.
- Cancel and system Back close the viewer and cancel its pending decrypt.
- Read failure has Retry/Close and a fixed error category. Decoder errors show
  only the Media3 numeric code, not exception text, media paths or URLs.
- Original ciphertext, key storage, VPN policy and AI restrictions are unchanged.

## Regression coverage

Baseline-only tests were submitted before production changes for page reuse and
lifecycle-owner replacement. Check their completed CI logs for causal evidence.
Candidate tests additionally cover actual encrypted import -> decrypt -> viewer
playback, measured 42% modal and Cancel, monotonic authenticated progress, and no
100% report for a corrupt payload. Existing Gallery/Browser/adaptive tests remain.
Local Gradle distribution download remains unavailable; Android CI is required.

## Physical acceptance

1. Open an affected Vault video: modal appears immediately, decryption percentage
   advances, verification completes, then playback starts. If it fails, capture
   the displayed fixed read category or numeric playback code.
2. Cancel during loading, reopen, and move between two videos repeatedly. No
   permanent loading state, stale bytes, duplicate audio or lock bypass.
3. Play a device Gallery video and a supported Browser video, then enter/exit
   fullscreen. Check both Fold displays and rotation.
4. Home/screen-off still obey auto-lock. Required VPN loss blocks Browser media.

Legacy encrypted payloads still require complete GCM authentication and an
in-memory seekable buffer. This change does not promise instant startup or support
for arbitrarily large files; device-specific decoder/memory failures need the new
safe error category/code to distinguish them. No production release is requested.
