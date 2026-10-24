# Private Gallery 1.7.5 / code38 — acceptance handoff

Status: **GO for owner physical acceptance only.** Automated app checks, independent review, original-key signing and both synthetic in-place upgrades/restarts are verified. **NO-GO for declaring physical acceptance or general release until the owner completes the Galaxy Z Fold checklist.** No main merge is authorised.

## Source and recovery

- Authoritative initial1.7.4 branch SHA: `03eb984b64461524df2c1ffcb70d5cfd676218f6`.
- Original1.7.4/code37 tested and signed source: `0af69436147731d70dbdcac7455a4f252f199b6e`.
- Frozen1.7.5/code38 app/test source: `69bfade789df4a49d911091bb8cf8205c88a3a39`.
- Frozen complete tree: `be12b504ae9b92c7e69d54f3a41434999a783924`; main source subtree: `76ef397ebddd2bf85e1debe564ce4cff583812b0`.
- Continuation harness checkpoint: `d6f88a2360f140425474065684d108572c613394`, independently verified remote ref and matching local/API tree `ce9d9d3498d1e5c27999dfe5986836fef35973db`.
- Development/recovery branch: `release/1.7.5-media-experience-privacy`. Earlier release/V2 branches preserved; no force pushes.
- Complete chronological continuation instructions and checkpoint history: [V1_7_5_RECOVERY.md](../../V1_7_5_RECOVERY.md).

## Delivered behaviour

Photo viewing supports pinch, tap-centred double-tap, midpoint pan without unintended paging, ordered horizontal navigation, fit-scale swipe-down close, immersive/toggleable controls and correctly oriented bounded high-resolution rendering. Only immediate neighbours are prefetched, at a smaller bound, without plaintext public caching. Decoded image ownership is cleared on disposal/lock.

Video supports left/right10second double-tap,0.5/1/1.5/2× playback speed, native play/pause and responsive scrubbing, elapsed/remaining time, full-screen rotation and fit/fill, buffering/error handling and memory-only position resume within the same open viewer. Vault lock stops playback and clears positions.

Panic Exit is opt-in with discreet three-finger touch and optional visible Quick Lock, setup guidance and accessibility touch-exploration avoidance. It immediately covers content, revokes the authenticated vault, stops playback and safely invokes existing camera recording finalisation/recovery, clears sensitive viewer/navigation state and returns to neutral Primary-style locked entry. It never auto-reopens Hidden or automatically prompts biometric after Panic; independent authentication is required. No system gesture/button interception or media deletion.

Privacy Shield covers sensitive UI on focus loss/background, suppresses underlying accessibility descendants and temporarily secures the window, restoring screenshot preference on authorised focus. Neutral Panic cover removal waits for committed locked composition. Details, Favourite and existing collection/share actions are available in the viewer, scoped to the current vault. Existing encryption, credentials, camera, browser, metadata and AI systems remain in place; no1.8 subsystem was introduced.

## Automated evidence and independent review

Original full source run [40101632822](https://github.com/traynor1987/Private-gallery-/actions/runs/40101632822):349JVM,91focused Android,74security/browser preflight and365unfiltered Android tests passed with zero failures/errors/skips in their raw XML. Camera/browser process-boundary proofs and host security policies also passed. Native centre/translation pixel assertions, late-player cover, neutral Panic and real-surface video seeks passed. These are emulator/host results, not owner physical acceptance.

The full run stopped at the correct signing gate because the regression helper omitted the1.7.5 debug-upgrade dispatch. Both whitelists were corrected with a regression test;26host Python tests passed. Continuation [40105499589](https://github.com/traynor1987/Private-gallery-/actions/runs/40105499589) pins original run/head/artifact and ZIP SHA256 `278bf2a642a98640e0397ce778670b115d94adc5e9ad8653be888d652fc50cfb`, validates all raw XML and original tested debug APK SHA256 `7414701b157128a2c32020ca8a2c2b51557117c415a5704de9771fc0bc9b888b`, then checks out and signs the exact frozen69bf source. The runner's separate harness SHA is not misrepresented as runtime-tested source.

The continuation rebuilds the same debug source so its baseline and candidate use one fresh disposable CI debug signer, reruns JVM/lint, and requires synthetic debug37->38 upgrade/restart before touching production signing. Original-key production signing/package/hash and production37->38 upgrade/restart remain mandatory. No assertion was weakened to obtain green CI.

[Independent review](REVIEW.md) found and corrected late-player autoplay under cover, pause/resume recovery, neutral-entry Panic observation and duplicate-pointer pinch drift. Final review found no demonstrated Critical/cross-vault issue or concrete continuation blocker. Review is not a guarantee against every device-specific or coercion scenario.

## Signing and upgrade evidence

Verified acceptance artifact12564976077 from successful continuation40105499589, ZIP SHA256 `4ceacaaf11d4c63aec81e3858ea06cb98299f7c3165e9cd9dd2d721a177787cf`. Downloaded bytes and ZIP CRC verified. APK filename: `Private-Gallery-1.7.5-code38-Acceptance.apk`; SHA256 **`a5d42d3420fa639828546ca84de1ef2d1bd0971313e444af2d50b8a5d17ec114`**.

Verified package is `uk.co.traynor.privategallery`, version1.7.5/code38, non-debuggable, original production certificate SHA256 `94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17`. CI apksigner cryptographically verified APK Signature Scheme v2, one original signer. Independent local APKv2 certificate extraction confirms the same leaf certificate and APK hash/ZIP integrity; that local parser is an identity check, not a substitute for apksigner. Source proof is exact69bf; entire app subtree21096ac066518e16ca86e659eef2135ee502d9c9 (distinct from app/src/main subtree above).

Fresh continuation349JVM tests passed with zero failures/errors/skips; debug/release lint and builds passed. Both debug and original-key production37->38 seed, verify and after-restart phases returned `OK (1 test)`, baseline source0af69436 and candidate69bf recorded. Both upgrade-result files explicitly confirm independent PINs, Primary/Hidden encrypted media, collections, settings and VPN preservation. Production candidate hash matches the delivered APK. Tor distribution inventory passed. Diagnostic artifact12564577796 ZIP SHA256 `988eb15a67c920e8e0cecbb895f4ef54ed4ac321fb54ca3e0c46cda6d053fdd4` was also independently downloaded and verified.

Never substitute a key, uninstall the owner's application, clear data or reset credentials. Synthetic fixtures use disposable emulators and independent PINs, encrypted media, collection/settings/VPN and ciphertext-preservation/restart assertions; they do not inspect the owner's real media.

## CI strategy

One developer/writer; independent reviewer read-only. Coherent checkpoints use focused path-filtered compilation/tests with cancellation of superseded refinement jobs. Stable marker changes run the full retained suite once; genuine failures receive affected corrections and fresh validation. `[release-candidate]` avoids duplicate refinement execution. The final harness-only continuation reuses strictly pinned raw runtime evidence for unchanged exact app/test source, runs missing upgrade/signing gates and records both source and harness SHAs. Docs-only recovery updates skip CI. Final workflow permissions are read-only; the earlier one-use cancellation permission was removed. No mandatory signing, isolation, upgrade or security gate disabled.

## Limitations and physical acceptance

- Video resume is scoped to the open viewer only and is cleared by close/lock/recreation.
- Selected images are bounded to16Mpx/48MiB; adjacent previews1.2Mpx. Loading is bounded rather than unlimited original-resolution allocation.
- Focus-taking dialogs/popups may pause video; manual Play may be needed afterward. Shortcut handling is in the main application window; native/system windows retain their own controls.
- An explicit Primary collection metadata operation already queued before Panic may finish in its original domain. Panic does not roll back an already authorised write, delete media or guarantee protection against coercion.
- Existing1.7.4 export limits remain JPEG/nonanimated PNG; no new video/WebP/GIF sanitisation claim. VPN+Tor remains unavailable. Device-specific camera/transport limitations remain subject to physical testing.
- No owner device was accessed, no real media modified and no physical acceptance claimed.

Use the complete [Galaxy Z Fold checklist](OWNER_ACCEPTANCE.md). Cover and inner screen gestures, Recents/privacy flashes, independent PIN/fingerprint, Tor/VPN, camera/AI, metadata/GPS export and installation over1.7.4 must all be checked by the owner before release acceptance.
