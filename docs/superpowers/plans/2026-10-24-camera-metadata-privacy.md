# Private Gallery 1.7.4 Implementation Plan

> Execute inline with superpowers:executing-plans. Owner's detailed 1.7.4 mission is the binding specification and authorises execution.

Goal: refine the foreground camera and introduce authenticated media information, editable display dates, size sorting, storage tools and verified export privacy.
Architecture: reuse CameraX capture/journal, existing vault sessions and additive authenticated presentation store. Never migrate payload/index formats. Export transformations act on copies and fail closed for unverified containers.
Tech stack: existing Kotlin/Compose/CameraX, Java host policy checks, Android instrumentation and GitHub Actions original-key signing.

Constraints: uk.co.traynor.privategallery; original certificate 94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17; Hidden PIN only; no main merge, no 1.8; no real user device operations.
Review focus: revoked sessions during metadata writes; missing dates/sizes; timezone ambiguity; retained XMP location; temporary plaintext lifecycle.

1. Recover exact baseline f1e056b05347a8c9a1edc83cd0a1366267fffbce and create release branch. Commit recovery checkpoint and verify remote.
2. Add Largest/Smallest ordering using existing plaintextSize and MediaStore SIZE. Test unknown sizes last, deterministic equal-size ordering and allowlisted SQL. Preserve six existing sorts and per-vault preferences.
3. Add adjusted capture timestamp/zone to encrypted supplemental metadata, preserving original date/hash/payload. Expose repository mutation under existing Primary metadata lock / Hidden session commit. Test save/reopen/restore/missing original and revoked publication. Add Details UI and date/time picker using authenticated callbacks.
4. Refine camera chrome with compact overlay and circular shutter, supported zoom ratios, settings entry and timer/grid. Remove camera headings/instructions from reading surface while preserving accessible controls and honest recording feedback. Run camera presentation/hardware/revocation tests.
5. Implement format-specific sanitisation and review/staging/share lifecycle, with output reinspection and safe unsupported failures. Add real EXIF/XMP/video fixtures before enabling each container. Never imply visual anonymity.
6. Add authenticated storage breakdown/largest and duplicate candidates from active repository only. Disposable cleanup must exclude encrypted originals, require confirmation and be tested.
7. Configure fresh 1.7.4 regression and exact 36->37 synthetic upgrade, increment to code37 only for candidate release. Push before CI. Independent security review, fix blockers, original-key signing after passing gates; verify APK SHA/package/certificate. Owner checklist is separate from emulator acceptance.

Each implementation task: failing behavioural test -> implementation -> focused verification -> commit and push -> remote SHA check. Record partial/blocked features rather than presenting unverified work as a release.
