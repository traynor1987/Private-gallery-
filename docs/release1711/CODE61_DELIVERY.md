# NovaOS 1.7.11.5 — Black Screen and Android pinning owner acceptance

Production-signed in-place update, code61, package `uk.co.traynor.privategallery`, Android17/API37 only.
Nova Home remains component version2.0; this is NovaOS1.7.x. Existing Home appearance, Gallery,
Vaults, authentication, encrypted storage, BrowserV1 and networking architecture are preserved.

## Download

[Original-key owner-acceptance APK](https://github.com/traynor1987/Private-gallery-/releases/download/novaos-1.7.11.5-home2-code61/NovaOS-1.7.11.5-home2-code61-owner-acceptance.apk)

[Upgrade/signature evidence ZIP](https://github.com/traynor1987/Private-gallery-/releases/download/novaos-1.7.11.5-home2-code61/NovaOS-1.7.11.5-home2-code61-evidence.zip)

[Release and all verification assets](https://github.com/traynor1987/Private-gallery-/releases/tag/novaos-1.7.11.5-home2-code61)

Install over existing NovaOS. Do not uninstall or clear application data.
This is an original-key, non-debuggable owner-acceptance prerelease, not the isolated development APK.

## Exact identities

| Item | Verified value |
| --- | --- |
| Integration branch | `integration/novaos-1.7-home2` |
| Original signed60 source | `f2727d75d142c2a733c53861e1a0f74440e0b6b5` |
| Frozen signed61 application source | `2da2895e387839ec37d0590dfb713dc1bec99853` |
| Frozen source tree | `3b0353e37fcf51a078d721c89e29f0993e2af88b` |
| Signing activation | `4904c937d3773aa07b117022edbcafcbcf8c5d5a` |
| Independent Pin feature checkpoint | `ca7523e975744bcab14f21be4addf41fc05fbf08` |
| Independent Black Screen checkpoint | `2ee99042f976863757a69437cc68bdbbca1c73e6` |
| Version / code | `1.7.11.5` / `61` |
| Package / minimum and target SDK | `uk.co.traynor.privategallery` / `37` |
| APK bytes | `144991916` |
| APK SHA-256 | `90083faaeb8ebaa896451d9dc721c4d26ee170135b3a4e1aad126038bcd7ef3e` |
| Original certificate SHA-256 | `94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17` |
| Signer | One original RSA4096 signer, APKv2 verified |
| Evidence ZIP bytes / SHA-256 | `112168` / `50fc64664a648aa78c049b9e6b55cb2cbaf4918029df208d0f4c6638a46f0948` |
| Original production artifact | `13531400377`, `60738267` bytes |
| Original artifact SHA-256 | `26d0f86d8ece1b3c22d4b41c32eddca2592e9722c3b7b93f7d420fd42aeaa678` |

## Delivered behavior

- Installed Nova Store App Mode: bottom-right overflow → **Black Screen Mode**. A native opaque-black
  cover hides app/navigation content and requests supported immersive bars, using minimum per-window
  brightness. Existing WebView, UUID, session and audio are retained; no reload, navigation or pause is
  introduced by activation/wake. Ordinary touches cannot reach the page. Double tap, Android Back or
  the labelled accessibility Wake action restores the interface and prior brightness/bar state.
- Lock, timeout, concealment, Space changes, Panic, focus loss and lifecycle cleanup retain existing
  security authority. A stale view/menu callback cannot activate or wake a different authenticated
  session. No new encryption key, public file, service, privileged permission or unrestricted wake lock.
- Native Settings → Privacy & Security → **Pin NovaOS** → **Request Android pinning**. Uses Android's
  public confirmation flow; Android controls actual pinning and unpinning. A public Security Settings
  link is supplied when App pinning needs enabling. No silent pinning, kiosk/device-owner setup,
  accessibility bypass or hidden Settings key is used. Routines can launch NovaOS normally where
  supported; this does not silently pin it.

## Verification and upgrade

[Original focused runtime run42266383469](https://github.com/traynor1987/Private-gallery-/actions/runs/42266383469)
passed all196distinct selected Android cases in38classes and447JVM cases in108suites, with no
failure/error/skip. All prior172Android cases remain selected. Host policies, encrypted adapters,
Private Apps checks and BrowserV1 preservation pass. The preserved BrowserV1 session-file SHA-256 is
`8f4c8e68023c5a73a648f8cf7c4e3328d13538de3b276b0220686f6798861e7b`.
Local debug lint and production release lint pass.

Original runtime artifact13528247116:74887629bytes, SHA-256
`195a5bff528a86fe8af129b2585eb8a5df313d62fe16f1517f753db78036a502`.
Root and independent reviewer inspected original actual Main/window captures, genuinely painted
keyboard-before and pure-black-after frames, retained local HTML audio, native Android confirmation
and cancellation, and real popup three-finger Panic input/revocation. The three-size layout test
passes with real Back delivered to the focused native modal; all layout/clock/drawer checks remain.
Failed earlier reports are retained and explained in CODE61_PROGRESS.md; no failing case was waived.

The exact-source independent review SHA-256 is
`9056470dfd813d89abc4a2079d2b14445853381d4ed58ef00c40528f288e7a99`.
It approved signer readiness only; subsequent production checks were then performed.

[Original-key production run42269170059](https://github.com/traynor1987/Private-gallery-/actions/runs/42269170059),
job128068998830, passed original runtime/review/control gates, baseline validation, baseline-compatible
fixture compilation before signing credentials, original-key verification, frozen production build,
release lint, APK identity/signature/OCR/16KiB alignment, actual signed60 → signed61 installation,
encrypted-data/Home/browser preservation, process restart verification, publication and signing-material
cleanup. Upgrade seed/verify/after-restart each report `OK (1 test)` on the enforcing API37 emulator.
The original signed60 APK was used, not a rebuilt baseline; neither uninstall nor data clearing was
used for the upgrade. These are synthetic seeded-data checks, not a claim about the owner's phone.

After publication, the parent independently downloaded the original production archive, checked
its advertised bytes/SHA/CRC, extracted actual APK/evidence, reran apksigner/aapt and16KiB zipalign,
and matched all four release assets to the actual source, size and hashes. Final independent delivery
review also passed, as recorded below.

## Limits and Galaxy Z Fold acceptance

Black Screen is an overlay: the display remains on and consumes power. Existing lock/timeout and
real screen-off/background policies remain in force, as do third-party playback restrictions.
Automated audio uses real local HTML/WAV playback; live YouTube Music/accounts and incoming calls
were not tested. Fold layouts were simulated; physical folded/unfolded operation remains owner acceptance.

Android public SDK reports current NONE/PINNED/LOCKED state, not a reliable global App-pinning toggle.
Pin status refreshes at entry, resume/focus and explicit actions; a nonfocusable confirmation can leave
the cached label stale until another refresh. Reopen Settings to refresh. Native request/confirmation/
cancel is verified; successful owner pin acceptance and the physical Android unpin gesture remain untested.

Owner checklist after in-place installation:

1. Open existing Primary and Hidden media; confirm concealed Hidden entry and separate Hidden PIN.
2. Start YouTube Music, activate Black Screen, listen, then double tap/Back/accessibility-wake; confirm
   playback, login, page and previous brightness remain.
3. Fold/unfold, rotate, switch Home and interrupt with an incoming call; verify safe window restoration.
4. Test timeout/lock and three-finger Panic; require authentication before protected content returns.
5. Request Pin NovaOS, confirm through Android, then use Android's displayed unpin gesture.

VPN/Tor development and future runtimes remain deferred. No live-service restriction bypass is provided.


## Final independent delivery check

A separate reviewer independently refetched production run/job/artifact/release metadata and original
logs, rehashed the original production archive and APK, and ran native apksigner/aapt/zipalign.
All checks pass with no Critical/Important finding or pending mandatory release issue. All three
original upgrade phases report success; all14 preserved approval/control inputs match exact current
and activation bytes; the four published assets match; packaged OCR models/native libraries and
ELF16KiB alignment pass. Original signing-material cleanup step19 succeeded. Physical owner acceptance
remains unperformed as stated above. Application source stays frozen at2da2895; this delivery record
and progress/plan updates change documentation only.
