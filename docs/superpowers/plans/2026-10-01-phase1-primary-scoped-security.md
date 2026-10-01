# Phase 1 Primary scoped security implementation plan

Goal: finish the original Primary-only architecture from merged main without changing authoritative formats, then stop for exact signed-device acceptance.

Architecture: extend the existing `PrimarySessionAuthority` rather than replace Phase 0 protections. A fixed Primary compatibility adapter binds storage items to immutable handles; capabilities constrain operation purposes. Scoped cache and network ownership enforce original-session admission. Existing crypto/index/archive implementations remain compatibility primitives behind scoped production boundaries.

Tech: Kotlin/Android, Compose, JUnit, instrumentation, existing Gradle/CI and permanent-signer manual workflow.

Specification: [recovery and requirements](../../phase1/RECOVERY_AND_REQUIREMENTS.md). Global constraints: PRIMARY only, no migration, no secrets in evidence, no Phase 2, no merge before acceptance. Execution is inline. Baseline read-only security review identified dormant handle overloads, early preview cache admission, AI I/O admission, Browser chooser ABA/network ownership, partial setup admission and implicit credential/backup scope.

## Tasks

1. Authority and fixed compatibility scope: RED tests for synthetic foreign identity, attenuated capability, old epoch and deadline; implement typed identity, operation scopes, guarded handle revision resolution and cache identity. Keep existing linearized commit/revocation and cleanup semantics. Verify targeted JVM tests, then commit.
2. Repository/UI integration: bind every exposed media item to its original handle without serializing authority; validate before lookup/read/write/egress; replace bare media-ID mutation boundaries with handles. Remove ambient key aliases and put credential/backup production access behind fixed Primary abstractions. RED stale/foreign/revision tests; verify JVM plus adapter instrumentation; commit.
3. Async and network ownership: capture request/destination and selected handles; guard AI connect/body/read and own connections before blocking; reject chooser ABA; bind Browser download resources and original destination. RED tests for delayed I/O/callbacks, deadline and revocation; implement and verify; commit.
4. Corruption and compatibility: reject missing-index partial roots and partial credential state; test synthetic foreign roots/slots exclusion, legacy corpus and fault behavior. Preserve index/envelope/archive bytes. Verify full JVM/evidence/instrumentation/lint/APK and all existing CI policy scripts. Commit with evidence matrix.
5. Whole-branch adversarial review: fresh reviewer against original contract; fix substantive findings and rerun affected tests. No repeated implementation of inherited Phase 0 protections.
6. Exact candidate: update Phase 1 report and workflow branch allowlists, push dedicated branch, obtain required CI on exact head, prepare/run signed acceptance workflow. Record actual results only; stop with small physical checklist or exact manual workflow action. Do not merge or start Phase 2.

Validation: local JDK17 `/tmp/pg-phase0-jdk17`, SDK `/tmp/pg-phase0-sdk/sdk`, cached Gradle; `:app:testDebugUnitTest`, `:app:testPhase0EvidenceUnitTest`, `:app:lintDebug`, `:app:assembleDebug`, existing policy/corpus/vector scripts and CI instrumentation. Tests must prove side effects are denied, not merely inspect implementation text.
