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

## Persistent physical-device failure: diagnostic follow-up

The physical failure still has no supplied stage/error or affected fixture. Code
inspection did not establish that the loading dialog is the cause. Activity lock
uses `onStop`/screen-off, not window-focus loss; the video manifest already handles
orientation/screen-size changes. No lock or lifecycle bypass was added.

The import/read trace uses the same AES-GCM payload with stored nonce and item-id
AAD, authenticated at import and again before publishing the viewing buffer. No
legacy/chunk-format dispatch exists here. MIME preserves video hints and otherwise
uses conservative signature/extension detection. Large payloads still require a
plaintext-sized allocation and provider-internal GCM memory; this remains a
possible device-specific limit, not a demonstrated cause of this report.

`VaultPlaybackDiagnostics.summary()` now exposes a bounded, process-memory-only
trace of the last Vault video attempt for the diagnostic panel. Its closed enum
vocabulary covers read/progress/authentication, preparation, player state, video
track support, first rendered frame, position advancement, lifecycle, playback
suppression, numeric player errors and release. It accepts no media identifiers,
paths, names, URI values, exception messages or media bytes; it writes no logs or
files. Browser/Gallery players do not record to this Vault trace.

The encrypted import -> Fullscreen Vault instrumentation test now waits for the
initial decryption modal, uses a background reader with main-thread callbacks,
and requires a rendered first frame plus at least 500 ms of playback progress.
The old READY/ENDED-only assertion could pass without those playback outcomes.
This closes a coverage gap; it does not reproduce the unknown physical failure.
New unit coverage checks bounded retention, deduplication and per-attempt reset.

Local execution was attempted with `./gradlew testDebugUnitTest --tests
'*VaultPlaybackDiagnosticsTest'`; Gradle distribution download failed with
`Network is unreachable` before compilation/testing. CI must validate these
changes. No observed test failure on the old player or physical playback success
is claimed. Next physical run should attempt one affected Vault video, then read
the diagnostic panel: the last completed stage and fixed error code determine
whether investigation belongs in reading/authentication, extraction/decoding,
surface rendering or lifecycle/audio suppression.
