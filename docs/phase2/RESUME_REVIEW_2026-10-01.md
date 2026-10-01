# Durable checkpoint recovery and adversarial review

## SOURCE FACT

Recovered remote branch `phase2/concealed-container-foundation` at `50d42a9d6c141b002268fe438380607923d28caf`, draft PR #58. Independently verified main `f34db23445bd16fd45ae100474c19a2de8669695`, merged Phase 1 PR #57 and successful main CI `36918293917`. Checkpoint push CI `36922800684` and PR CI `36922805735` both completed SUCCESS. Those results cover only the committed core checkpoint, not recovered uncommitted storage/UI work.

The owner supplied the complete original Phase 2 specification during resumption. All 54 sections, 42 matrix cases, 34 report categories, 50 exit gates and physical-acceptance STOP remain authoritative. No work was restarted. Recovered source, transaction tests, UI edits and biometric adapters were inspected and continued.

## AUTOMATED TEST EVIDENCE — local behavioral probes

- Ordinary-directory replacement during selection promotion redirected path-based rename into a synthetic Primary canary. Pre-fix replacement regression: 1 test, 1 failure; expected 14-byte canary became 26-byte selection. Pinned directory implementation: replacement plus store suite, 8 tests passed. The replacement test remains permanent.
- Biometric cancellation after unwrap, while catalog validation was paused at a durable query-charge write, still promoted authority. Pre-fix: 1 test, 1 failure (`cancelled biometric must not create authority`). Cancellation now invalidates the sequence and original attempt and issues a fresh PIN-only attempt. Post-fix: 3 biometric controller tests passed, including usable PIN fallback, absent-slot eligibility, automatic eligible prompting and enabled-slot grace resume.
- Earlier committed regressions cover sleep-inclusive clock injection, unforgeable attempts, bounded inventory and setup selection atomic with cancellation. Each is independently inspectable in source/tests; a previous-session assertion alone is not evidence.

Local full JVM/lint/debug APK/instrumentation APK validation before the latest biometric and window changes: 473 tests, zero failures/errors/skips; build successful. These counts are not the 42-case acceptance matrix. The latest milestone subsequently passed a fresh full local run: 479 JVM tests, zero failures/errors/skips, lintDebug, assembleDebug and assembleDebugAndroidTest (4m40s). The tracked-source secret scan and merged/packaged backup exclusions passed. Remote exact-head CI is still required.

## IMPLEMENTED — filesystem confinement

Writes, directory fsyncs and selection replacement use `SecureDirectoryStream` relative operations with NOFOLLOW and directory identity binding. Streams are pinned before injectable IO/fault seams. Inventory enumeration and reads use pinned ancestor handles too. No fallback to absolute-path mutation is allowed when the provider lacks secure directory handles. A synthetic Android storage-adapter test is committed; Android execution remains a CI gate.

Java exposes no relative mkdir primitive. Empty staging directories are created beneath the Android-provided app `filesDir`, then atomically moved via pinned handles into the intended namespace. They contain no keys or plaintext. The Android-provided app-private anchor is trusted; this does not defend against a fully compromised OS replacing that anchor. Directory/file fsync and rename are required; physical storage/controller durability is a platform limitation. Unknown material, traversal failure or exhausted inventory bounds fails closed.

## IMPLEMENTED — window privacy ordering

A fresh review found that restoring Primary's screenshot preference immediately after setting Compose route CLOSED could remove FLAG_SECURE before sensitive pixels had disappeared. Protection is now latched for the current Activity window after discovery. Exit/background do not clear it, regardless of Primary's screenshot preference. A newly created Activity starts with no Secondary authority/route and applies the ordinary Primary preference. This deliberately conservative policy avoids relying on Compose redraw or timer delivery to prove old pixels are gone. Recents screenshots remain disabled. Integrated instrumentation and physical/OEM checks remain required.

## READ-ONLY REVIEW

Independent storage review confirmed the replacement race; independent production call-chain review found biometric cancellation, window ordering, biometric eligibility/resume UX and missing PIN guidance. Fixes and regression evidence are recorded above. These reviews are not final candidate acceptance: integrated MainActivity, real credential adapters, backup isolation, API36 instrumentation, signing and owner acceptance remain open. No Critical finding was confirmed; no unresolved Important finding may be admitted merely by documenting it.

## UNRESOLVED / STOP

Draft PR remains NO-GO pending complete matrix, exact-head CI, signed build and owner physical acceptance. No media import, transfer, Camera, Browser, Tor, VPS backup, Primary format migration or public release is introduced.
