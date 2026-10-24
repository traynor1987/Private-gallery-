# Media Experience & Privacy Shield Implementation Plan

Execution: inline, one writer. User explicitly authorises autonomous implementation and signed acceptance preparation, superseding interactive design approval.

Goal: refine existing Compose photo viewer/Media3 player and introduce opt-in neutral Panic Exit without changing vault formats or credentials.
Architecture: retain authenticated loaders, bounded in-memory rendering, revocable video sessions and camera journal finalisation. Reuse lockPrimary for revocation. No plaintext disk caches. No new dependencies.
Baseline: 03eb984b64461524df2c1ffcb70d5cfd676218f6; signed application source 0af69436147731d70dbdcac7455a4f252f199b6e, code37. Target code38/version1.7.5.

Constraints: Hidden PIN-only; Primary fingerprint retained; no1.8, no main merge, no force push, no actual owner-media operations. VPN+Tor remains unavailable. Original signer only.

Review focus: late asynchronous callbacks after lock; multi-pointer gesture ending at fit scale; process recreation after panic; capture finalisation after session revocation; screenshot-enabled Recents on pre33.

- [x] Task1: photo policy tests for tap-centred zoom, multipointer navigation suppression and fit-only swipe-down; refine FullscreenMediaViewer using existing bounded renderer, immersive bars and lifecycle preview teardown. Keep snapshot entry order. Bounded adjacent loading only if authentication/revocation and memory ownership can be proved.
- [x] Task2: player policy seeking tests, left/right double-tap via native gesture detector without stealing timeline, speed menu and feedback. Existing Media3 timeline/elapsed controls preserved. Optional session-only resume, scoped by viewer owner and cleared on lock.
- [x] Task3: Panic Exit neutral curtain, optional quick-lock button and configurable three-finger shortcut, persisted enable settings with neutral copy. Revoke both sessions, existing camera journal, browser and previews, suppress automatic biometric only for panic. Persist no sensitive navigation. Focus curtain for Recents/background while retaining configured timeout.
- [x] Task4: relevant Compose/instrumentation tests, negative lifecycle/isolation cases. Single refinement workflow for coherent checkpoints; full Android/signing/37->38 upgrade once stable candidate. concurrency cancellation for refinement only; never cancel a signing candidate.
- [x] Task5: independent read-only security review, fixes with tests, verified remote checkpoint, original-key signed APK/package/cert/hash/upgrade checks, owner Fold checklist and exact handoff. No physical acceptance claim.

Host verification: java com.sun.tools.javac.Main for shared pure Java policies, existing host security checks; ./gradlew tests require CI because no SDK/Gradle distribution and Java download network fails. Pending tests must never be reported passing.

Final automated evidence: exact app/test source69bf,349JVM/91focused/74preflight/365full Android passed; continuation40105499589 completed debug and original-key production37->38 upgrades/restarts and signed/package/hash verification. APK a5d42d3420fa639828546ca84de1ef2d1bd0971313e444af2d50b8a5d17ec114. See docs/release175/HANDOFF.md. Physical acceptance remains pending; branch retained without main merge.
