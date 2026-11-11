# NovaOS 1.7.38.4 Code 77 production upgrade

Original production package uk.co.traynor.privategallery, Android 17 only.
The fixture uses the exact distributed Code 76 APK, original production certificate and candidate Code 77. It never rebuilds the installed baseline.
It installs the candidate in place on a fresh disposable emulator, checks retained encrypted Primary and Hidden records, Evidence Locker, browser sessions, Sanctuary, both launcher layouts and dock preferences, then force-stops and repeats checks. No uninstall or data clear.
The fixture additionally checks that upgrading does not consent to Guardian capture or Main dismissal and does not spontaneously create Guardian records.

Physical owner evidence: Main notification capture and encrypted Inbox visibility confirmed. Profile probe independently observed Main and Friends with text and zero unresolved events. Main retained count was 3 before and 3 after an observed Friends notification. This closes archive-isolation acceptance for that test.

The existing contact cards are retained; conversation detail is full screen. Authentication, independent per-message holds, protected placeholders and secure-window protection remain mandatory. Source includes the re-analysis preview-close correction and Main-dispatch Evidence publication correction.
Automatic real WhatsApp dismissal, cloud analysis and automatic Panic remain disabled. Protect All/evening schedule deferred. Samsung notification previews remain governed separately by Samsung settings.

Source: 15cdfd9e612ffc68960d1374420fe05e425a9877
Required runtime: https://github.com/traynor1987/Private-gallery-/actions/runs/42973393230
No signer access until exact-source successful runtime evidence and independent review are bound by hash.

Host verification notes: four current Code77 fail-closed checks pass. The historical scripts/tests/test_upgrade1711_controls.py still asserts Code64/65 identities and fixture hashes; it reports 10 failures and 2 errors both before and after these adaptations. It is not presented as a passing Code77 gate. Historical fixture-transformation and shell-debug-candidate tests are retained unchanged. The original Code76 archive checksum, byte size, nested provenance and exact fixture transform against c818 build configuration have been validated independently.
