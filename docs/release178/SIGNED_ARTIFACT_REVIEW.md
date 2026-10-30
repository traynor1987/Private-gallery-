# Private Gallery 1.7.8 signed artifact review

**Disposition: VERIFIED for owner-acceptance delivery.** The mandatory original-key archived code46 → signed code47 in-place upgrade and subsequent process-restart verification passed. Physical owner acceptance remains NOT_PERFORMED / OWNER_REQUIRED. This separate artifact review does not modify the canonical source-security review.

## Bound identities

| Item | Verified value |
|---|---|
| Frozen application source | 04b91ae01c64323aac02975672d6046dfba62e30 |
| Release-control source | 67296943557d6caf55f1ab5840f3c7eaa8b18b84 |
| Production workflow | 41081513370, attempt 1, acceptance178 job124151646414 |
| Artifact | 12990222572, private-gallery-1.7.8-code47-owner-acceptance |
| Raw artifact ZIP SHA256 | 5ea6025721ad51530249592213ec7d8a8682682e52eba84ba65587e86b68f82e |
| Delivered APK | outputs/apk/release/app-release.apk — exactly one APK in archive |
| APK size | 105454166 bytes |
| APK SHA256 | 21066bd7153520c688c5a6b6034fca549b33df391762e64a5070eecfeb342d3d |
| Package/version | uk.co.traynor.privategallery, 1.7.8 / code47 |
| Minimum/target SDK | 37 / 37 — Android17 only |
| Original certificate SHA256 | 94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17 |
| Canonical source-review SHA256 | 5a8d24aafc95a6f6276b8d4df3b94952e27bc99ebf376f355ce5fa3de85fad3e |
| Executed reviewed upgrade fixture SHA256 | 29a7618f7ab4f46d01223e4be54b86fe8ee21ad1abb87d1c2b27a86bfd9a46da |

## Independent checks

The reviewer independently fetched GitHub run/jobs/artifacts APIs. Run41081513370 is a completed successful push on release/1.7.8-private-apps using .github/workflows/acceptance1762.yml, control67296943557d6caf55f1ab5840f3c7eaa8b18b84, attempt1. The acceptance178 job and all required steps succeeded; the failure-evidence step was correctly skipped. The historical acceptance job was skipped. The sole owner-acceptance artifact is unexpired and API-bound to that control/run. The reviewer independently hashed the original downloaded ZIP and actual APK; both match the API/archive evidence and release summary.

The reviewer independently parsed the APK's embedded v2 signing block and X.509 certificate: the certificate fingerprint equals the original identity above, its public key matches the signing-block key, RSA PKCS#1 SHA512 signature over signed data verifies, and the complete APK v2 content digest verifies across103 chunks. This directly verifies the actual delivered APK, in addition to archived successful apksigner output (one signer, APK Signature Scheme v2 true). No replacement/debug certificate is accepted.

The reviewer independently decoded the actual binary AndroidManifest.xml from the APK: exact package/version/code, minimum and target37, not debuggable, and no CiAccessibilityService178 declaration. Archived release-manifest evidence is nonempty and the successful workflow enforces that debug-service absence. Debug-only accessibility instrumentation is not exposed in the delivered manifest. Archived production lint/build/package/certificate/distribution checks succeeded; Tor distribution evidence is present.

The archived final-security178.md hashes exactly to the canonical source review above. final-scope178.json binds the same application identity, original signer/baseline, all19 original scope controls, reviewed source decision with zero Critical/Important findings, physical OWNER_REQUIRED, successful focused run41074139917/artifact12987700481 and archive SHA25690e7b48fec4cbeaec3a84187cd8ade05639c817d514af170fe16c21070c4d69e. The reviewer independently reran the archived verify_final_scope178.py against archived approval/report/focused evidence/39-case manifest/final fixture: PASS. Executed fixture, validator and case-manifest bytes match their reviewed hashes. Earlier independent source review already validated39 Android cases,29 JVM cases, source-bound original exit0, strict packet/DNS/VPN/Tor/revocation evidence and healthy API37 emulator; no failed predecessor result was substituted.

## Genuine archived-baseline upgrade and restart

Original baseline provenance is pinned to run40899438953/artifact12908558722, archived source25aae6d59f79c455e47acc73a5c99f9c8f15242a, artifact SHA25616873a3b24abcb311d21ca11e93e1af1707be42946a817770e4b9d629e790869 and original signed APK SHA256696e80071497501d8da6cd8dd3e2c9728a63b0d4abc9e88c4ab46338fef97a2e. Archived package/certificate/hash evidence matches code46 and original signing identity. The fixture builds compatible instrumentation only; it installs the exact archived original APK, not a rebuilt baseline application.

The reviewer inspected the executed runner and archived seed/verify/after-restart outputs, each reporting OK(1 test), plus matching from46 duplicate evidence and PASS upgrade-result. The runner installs the archived baseline, seeds real encrypted test state, installs the same signed code47 APK with adb install -r, verifies preserved data, force-stops the application and verifies again with restartCheck=true. Candidate source, package, certificate and SHA256 in upgrade evidence match the actual delivery APK above. There is no seeded-data clear/uninstall between baseline and candidate; existing app state is upgraded in place on the disposable emulator.

Executed fixture coverage includes independent Primary/Hidden PIN/key boundaries; exact encrypted payload/file identities and media reads; collections/favourites and sort/timestamp behavior; encrypted browser metadata and actual browser preferences; persistent synthetic Primary cookie; synthetic AI credential/privacy settings; Panic Exit/Hide Content preferences; distinct nondefault encrypted camera options/audio consent in both vaults; and Recently Deleted IDs/state/deletion timestamps/retention/payload hash/nonce with no active-gallery resurrection, including restart. The strict integral JSON comparator accepts only Int/Long equivalence of the exact numeric value; floats/strings/missing fields are not coerced. Both earlier fixture compilation and representation failures remain failed historical runs; this review relies on the later complete successful run.

## Disposition and limits

No remaining Critical or Important signed-artifact discrepancy was identified. The exact APK hash above is verified for owner-acceptance delivery. Do not silently replace it with another build; any different bytes require identity/evidence review. This is synthetic emulator preservation and source/runtime verification, not evidence that a physical owner installation or Samsung Fold/OEM WebView acceptance has already occurred. Existing shared Primary browser storage, ephemeral Hidden logins and documented PWA/download/screenshot limitations remain applicable.

```json
{
  "source_sha": "04b91ae01c64323aac02975672d6046dfba62e30",
  "release_control_sha": "67296943557d6caf55f1ab5840f3c7eaa8b18b84",
  "artifact_id": 12990222572,
  "apk_sha256": "21066bd7153520c688c5a6b6034fca549b33df391762e64a5070eecfeb342d3d",
  "decision": "VERIFIED_FOR_OWNER_ACCEPTANCE_DELIVERY",
  "critical_open": 0,
  "important_open": 0,
  "original_key_code46_upgrade_restart": "PASS",
  "physical_acceptance": "OWNER_REQUIRED"
}
```
