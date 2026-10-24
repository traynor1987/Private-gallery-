# Private Gallery 1.7.4 recovery

Status: RECOVERED BASELINE; not an acceptance release.

Verified 1.7.3 remote SHA: f1e056b05347a8c9a1edc83cd0a1366267fffbce, independently checked using git ls-remote and GitHub refs API. Clean fresh checkout; no source work to overwrite. Package uk.co.traynor.privategallery; baseline version1.7.3/code36. Original signing certificate recorded in V1_7_3_RECOVERY.md: 94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17. Actual signer remains a release verification gate, never generate another key.

Owner reports installed baseline. Previous automated evidence in V1_7_3_RECOVERY.md reports signed code36 and synthetic35->36; this session does not infer physical acceptance. No device data, credentials, keys or encrypted originals accessed or changed. New branch release/1.7.4-camera-metadata-privacy; prior releases/main/V2 unchanged.

Plan: docs/superpowers/plans/2026-10-24-camera-metadata-privacy.md. Existing presentation-v1/state.enc can hold additive authenticated display date overrides without index/payload migration. CameraX existing foreground pipeline/journal retained. Host has Java17 but no Android SDK or Gradle cache; Android verification will need CI. Process enumeration failed with host fatal library error; no evidence of an existing implementation in this empty workspace.

Completed: baseline recovery only. Pending: implementation, tests, security review, original-key signing and exact36->37 synthetic upgrade. NO-GO for installation until all release gates pass. No main merge; no1.8 work.

## Initial implementation checkpoint
Size sorting implemented for vault/collections and MediaStore using actual recorded plaintext size / SIZE column; missing sizes last and deterministic ID ties. Host compiler works via java com.sun.tools.javac.Main despite absent javac launcher. New size ordering checks and eight previous ordering checks pass. JVM enum test limited explicitly to original six modes and separate new size coverage added.

Additive adjusted timestamp + IANA timezone implemented in authenticated presentation metadata; originals/index serialization unchanged. Primary mutation uses metadata lock plus caller publication authority; Hidden uses existing operation/session commit. Instrumentation tests cover reopen/original preservation/restore/missing original/invalid zone/revoked publication; NOT YET RUN: Gradle download blocked by host networking. UI and regression verification still pending. Direct git push lacks credentials; connected GitHub tree/commit/ref updates used instead with expected head and no force.
