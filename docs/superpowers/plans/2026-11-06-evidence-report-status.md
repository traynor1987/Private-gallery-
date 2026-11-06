# Evidence Report and Home Status Implementation Plan

> Execute authorized owner requirements with disjoint core-report/status work and parent-owned
> UI integration, builds, Git, CI and release activation. Read the specification first.

**Goal:** Optional cited encrypted behaviour reports and a visibly functioning actual Home indicator.
**Architecture:** Existing EvidenceAi preview/transport/Analysis envelope; typed report helper;
existing authenticated viewer/document handoff; actual Android status provider and Home chrome.
**Tech stack:** Existing Kotlin/Compose/Java, Android17/API37, Gradle/GitHub workflows.
**Spec:** docs/superpowers/specs/2026-11-06-evidence-report-status.md.

## Constraints and review focus

- Package uk.co.traynor.privategallery, original signer, encrypted data and Home2.0 unchanged.
- Exact consent per request; unknown references/quotes/time claims fail closed; prompt injection
  in evidence never acquires tool, incident, storage or network authority.
- Duplicate/overlapping screenshots cannot inflate category counts; unknown dates remain unknown.
- Lock/timeout/Panic/Space changes retire previews, requests, reports and picker capabilities.
- Export contains only the chosen report; stale/foreign source links cannot open other originals.
- Actual Home provider/window tests must expose missing state that fake-provider tests missed.

## Task1 Existing report extension

- [x] Add failing synthetic report cases for refs/quotes, duplicates/counts, alternatives/context.
- [x] Implement EvidenceAiReport.Report with outcome/assessment/confidence/alternatives/limits,
  chronology, deduplicated instances/categories/references, counts and deterministic text().
- [x] Integrate Task.EVIDENCE_REPORT, exact preview and nullable Details.report; preserve old tasks.
- [x] Test consent/cancel/revocation/Space isolation and backwards-compatible encrypted history.

## Task2 UI and report-only export

- [x] Add report choice, exact payload/selection review, native report display and source links.
- [x] Add encrypted report history/reopen and safe report-only export with owner confirmation.
- [x] Test no upload on open/preview/cancel, no originals in export, no stale/foreign viewer access.

## Task3 Actual Home status

- [x] Reproduce/capture actual Main/Home with real provider/window state and battery broadcasts.
- [x] Apply the proven bounded integration fix; retain real-bar suppression and callback retirement.
- [x] Verify visible initial/update/charging state and compact/wide/landscape bounds; preserve UI.

## Task4 Verified release

- [x] Verify next code and pin actual signed59 baseline and unchanged preservation assertions.
- [x] Compile app/tests, run host/JVM and focused API37 original reports with screenshots.
- [x] Independently review exact source and all security/upgrade controls before signing.
- [x] Verify original-key59-to-next preservation/restart, actual APK/cert/hash and publish direct asset.
- [x] Record exact fixes/source/test evidence and physical Galaxy Fold acceptance limits.
