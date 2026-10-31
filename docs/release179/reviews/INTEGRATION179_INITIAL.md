# Evidence179 Task6 initial integration review

Date: 2026-10-31. Scope: the 21 paths in `/workspace/scratch/4181ccd09b2e/review179-integration.diff`, SHA-256 `65069090b0d3bda96a698d2068a97fd4b8866bdb8e7d356e9b339c7cee293f00`.

## Verdict

**Changes requested for Task6. This is not release approval.** The source establishes substantial owner/session, secure-window, encrypted staging, and explicit-consent integration, but the required bulk/selection flows are incomplete, manual timeline ordering is incorrect when chronological and insertion order differ, and the VPN startup admission differs from the existing no-conflicting-transport boundary. Test-source defects and missing integration scenarios also need correction before the grouped Android gate.

No Critical finding was established in this scoped source review. There are four Important findings and two Minor findings below. These severity labels do not imply any Android execution or network-path proof.

Read first: `docs/release179/work/task-6-brief.md`; then the Task6 report, original owner design, full `docs/release179/CONTRACTS.md`, and Task5 brief. Reviewed all scoped product/test/script changes. Read actual Access, Controller, AI, Credentials, Network, archive/open, model, existing Main lifecycle, and existing Hidden transport APIs as dependencies. Those dependencies were not edited or independently re-approved here; Task5 was still being implemented separately during review.

**Observed verification:** source inspection only. No tests, compilation, CI, signing, commits, pushes, product edits, or subagent delegation were performed. All Kotlin/Android/API37 compile and runtime results are **UNRUN**. Previously reported host passes were not rerun and are not evidence of these UI/SAF/transport properties.

## Important findings

### I1 — VPN startup omits the browser admission/process half of the conflict fence

Location: `app/src/main/java/uk/co/traynor/privategallery/core/evidence/EvidenceRouteCoordinator.kt:45-57`; compare its Tor branch at 62 and existing `HiddenBrowserRuntime.kt:101`.

The VPN branch rejects a live Tor process/isolation service, but does not require `HiddenVpnBrowserBroker.admissionRetired()` or absence of `hasDedicatedProcess(context)`. Both conditions are checked by the Tor branch and by the existing browser startup sequence before selecting a new transport. A return to the vault while a dedicated browser process is still retiring can therefore admit VPN preparation/readiness verification and an Evidence route before that process/admission boundary is retired. `prepareEvidenceVpn` does not supply the missing check: it can return immediately when an owned network exists.

This is a source-level violation of the Task6 no-conflicting-transport startup requirement, not a claim that direct-network leakage has been observed. Apply the same complete admission fence before VPN preparation/verification, preserving fail-closed behavior while a process is alive or its death is not established. Recheck authority at every wait boundary. Add a focused case holding browser admission/process retirement outstanding and assert no preparation, DNS/HTTPS readiness probe, or Evidence route is admitted until the boundary is satisfied. Keep the ordinary owned WireGuard tunnel reuse policy distinct from a live browser-process capability.

### I2 — First manual timeline move uses a different order from the order shown

Location: `app/src/main/java/uk/co/traynor/privategallery/ui/EvidenceLocker.kt:134-145`.

Rows are rendered from `catalog.timeline(incident.id())`, which sorts by known timestamp until manual mode is selected. Both move buttons calculate their index from `incident.events()`, which remains insertion order. `EvidenceCatalog.moveEvent` moves that raw list and turns on manual mode. For example, events inserted as `[12:00, 11:00, 10:00]` display as `[10:00, 11:00, 12:00]`; choosing **Move later** on 10:00 calculates raw destination 2 and switches to `[12:00, 11:00, 10:00]`, moving it two places and reversing unrelated rows. Even a boundary arrow can unexpectedly reorder the entire timeline.

Make the first manual edit start from the currently displayed chronology, then move exactly one adjacent position in that list and persist audited manual order. Preserve the existing manual order thereafter. Disable/no-op the first/last boundary controls. Add actual component coverage with insertion order different from timestamp order, including unknown times; the existing test inserts a late event followed by an unknown event, whose chronological and stored order already match and cannot detect this defect.

### I3 — Required bulk registration and export selection are not reachable

Locations: `EvidenceLocker.kt:110`, `174`, `181-198`; `EvidenceLockerHost.kt:119,126`; `EvidenceExportDialogs.kt:29-45`.

The only existing-original chooser immediately closes and registers one ID. There is no multi-selection/confirmation or bulk registration path, despite the original design's bulk-register requirement. Export is reachable only from a single incident detail and always invokes `onExport(listOf(incident.id()))`; the library selection toolbar has no export action and the incident list has no export selection. Consequently the typed archive support for multiple selected incidents and separately selected evidence IDs is not exposed by the actual UI. Repeated single-item operations or creating temporary incidents is not the specified selection flow.

Add an owner-reviewed bulk existing-original selection and a bounded export selection that carries explicit incident/evidence IDs into the existing encrypted-default flow. Preserve captured authority, original preservation, live egress checks, generic filenames, and the separate plaintext confirmation. Add component tests for two originals registered in one owner action and export selection containing multiple incidents plus an explicitly selected standalone record. If a narrower product scope is intended, it needs an explicit owner-approved spec change rather than a completion claim.

### I4 — Critical output-picker/archive and Hidden lifecycle behavior has no Task6 integration test source

Locations: `app/src/androidTest/java/uk/co/traynor/privategallery/ui/EvidenceSaf179Test.kt`; scoped component/auth tests; `EvidenceLockerHost.kt:85-109`; `EvidenceExportDialogs.kt:96-152`.

The SAF suite supplies a read-only provider and covers two Primary import returns. It does not exercise the authored output-provider code, cipher-stage handoff, output cancellation/deletion, archive-opening UI, password re-entry, ciphertext digest binding, or Hidden authentication grace/expiry. Core archive tests cannot establish the composition/lifecycle/provider ownership of these integration paths. The authored route test also does not cover permission return or transport-startup cancellation. Merely running the current Task6 classes would leave these mandatory boundaries untested.

Add focused actual Host/dialog tests with synthetic Android providers and observable FD/cancellation/deletion behavior: encrypted prepare → picker → fresh controller → exact ciphertext; cancel/revoke during a blocked write without blocking Main; partial-destination cleanup request with original URI never deleted; default encrypted versus explicit plaintext; expected-owner rejection before report publication; plaintext archive re-export requiring password again and refusing a changed ciphertext digest; and Hidden within-grace fresh authority versus expired/reauthenticated rejection after real lifecycle reconciliation. Include Lock/Hide/Panic while provider/native report work is active. These tests remain UNRUN until the grouped API37 execution provides results.

## Minor findings

### M1 — The landscape component test selects the portrait viewport

Location: `app/src/androidTest/java/uk/co/traynor/privategallery/ui/EvidenceLocker179UiTest.kt:31,110`.

The viewport selector uses case-sensitive `name.contains("Landscape")`, but the test method is `landscapeDarkLargeTextIncidentFormAndExitRemainReachable`. `BrowserViewportRule` passes the unmodified `description.methodName`, so this test selects the default `320 × 840`, not `840 × 392`. Correct the selector or use explicit per-case parameters and assert the effective resource dimensions/orientation. The current source must not be described as landscape coverage, even if it later passes.

### M2 — The revoked-startup route test reuses an already closed coordinator

Location: `app/src/androidTest/java/uk/co/traynor/privategallery/core/evidence/EvidenceRouteCoordinator179Test.kt:23-29`; `EvidenceRouteCoordinator.kt:84`.

The test first attempts forbidden Hidden `System`, which throws inside `connect` and closes the coordinator in its catch block. It then revokes access and attempts `Tor` on that same ended coordinator. The second call fails regardless of access revocation, so its zero-side-effect assertions do not distinguish a working authority fence from one that is missing. Use independent coordinators/fixtures for forbidden-mode and revoked-startup cases, assert the latter is otherwise live immediately before revocation, and include revocation after startup has begun. A source/runtime pass of the present test would not prove its advertised revocation property.

## Security and API assessment from source

These are inspected implementation properties, **not PASS results**:

- Main supplies explicit Primary/Hidden origins. Primary tickets capture the authentication epoch; per-operation access also captures current key identity and transfer generation. Hidden tickets capture `HiddenSession.Authentication` and fresh controllers capture new operation authority. Host callback admission requires resumed state plus the original authentication. Existing Main `onStart` reconciles foreground timeouts before the resumed presentation. Ordinary pause/stop retires the controller and local forms; Lock/Hide/Panic revoke the owner through the existing flows.
- `EvidenceOwnerSession.close` makes the closed flag immediate and always posts controller/stage cleanup. UI resource publication uses Main `publishIfCurrent` with pending ownership. The inspected scoped publication blocks assign state/resources rather than performing provider/native cleanup under the publication monitor. Decoded image/report objects retain explicit pending cleanup paths; opened archives are retired asynchronously to preserve native borrowed-buffer lifetime.
- Picker tickets contain IDs/digests/URI/receipt/sealed ciphertext, not a catalogue, copied key, password, or readable preview. Encrypted staging is prepared before picker launch; password chars are wiped before handoff. Stage mode transitions are 0600 → 0400 inside a 0700 owner directory. Startup cleanup is narrowly named and avoids original-media directories and symlinks.
- `EvidenceDocumentIo` closes admission immediately and schedules provider cancellation/closure off Main. Failed destination writes request best-effort deletion. Incoming original reads do not invoke destination deletion. Reference/incident deletion calls model mutations rather than original repository delete/trash APIs. Provider behavior still needs the tests in I4; deletion cannot be guaranteed or described as secure erasure.
- Export defaults to the encrypted container. Plaintext uses a distinct warning/confirmation and a fresh live plan after return. Archive opening supplies `expectedOwner`; re-export carries the original URI plus ciphertext digest and requests password again after the destination picker. Actual dependency signatures match the Root callers at the time inspected (`open`, `write`, `withReport`, `ciphertextSha256`, `writePlaintext`). Core format/crypto/PDF correctness remains Task5's separate review and execution responsibility.
- Primary system transport is offered and admitted only while the live browser Require VPN policy is false. Hidden never offers/adopts `System`. Existing owned-network and Tor isolation primitives are reused; this diff introduces no WebView, JavaScript/cookie policy change, or explicit direct fallback. I1 prevents concluding that its startup conflict fence is complete. Real VPN/Tor route, DNS, revocation, and permission behavior remain unproved.
- AI UI defaults to text, requires preparation and a separate send button, displays exact text and explicit image previews plus model/route/cost/retention disclosures, and supplies typed group/event application through further owner actions. Applied subgroups union the stored analysis's full lineage. The existing AI UI test covers one text-only synthetic response and generic incident application; explicit images, typed subgroup/event flows, changed preview/route, and revocation require additional actual cases.
- All new full-screen dialogs explicitly request secure windows; forms/confirmation titles expose originating Lock/Hide/Panic controls through the shared composition local. Existing Main protection remains in place. Actual secure-window/Recents/panic imagery, accessibility, keyboard, fold layouts, and font-scale reachability are runtime gates, not established by source flags.

No definitive Kotlin compiler error was established by inspection of the scoped files. That statement does not imply compilation succeeded. Dependency APIs may still change while the disjoint Task5 worker finishes; recheck the finalized interface/build in Task7.

## Pending gates and handoff

1. Resolve I1–I4 and M1–M2, then perform scoped re-review of their concrete changes. Keep other workers' product paths separate.
2. Task7 grouped Kotlin/Android build and API37 actual component, authority, provider, export/archive, large-font/fold/landscape, secure-window and native-resource cancellation cases, with exact source/test/artifact identity. Do not substitute source-presence assertions for execution.
3. Real owned VPN and Tor packet/DNS/startup/revocation/permission proof, including the no-conflicting-transport fence and Primary live Require VPN policy changes; no provider POST before explicit consent and no automatic billed retry.
4. Finish separate Task5 archive/PDF/open review and integrate its final tests/API; retain the core model/store/OCR/AI review outcomes and unresolved issues as distinct evidence.
5. Independent consolidated whole-branch review, affected existing-feature regression evidence, original production-key code50 → code51 upgrade without uninstall/data clear, and signing/APK identity review. Physical Fold owner acceptance remains separately required. None is authorized as complete by this report.

Read-only dependency note sent to Root: `EvidenceCredentials` wraps backend save/read/clear in an access lease commit callback, which appears to perform credential filesystem/Keystore work under the authentication commit monitor. This lies outside the 21-path Task6 diff; route it to the responsible AI/access reviewer and reconcile the applicable monitor contract before consolidated approval. No dependency modification was made here.

## Narrow follow-up — retained AI advisory visibility (2026-10-31)

Root requested a read-only check of incident advisory rendering after reference removal. The observation is confirmed by the actual UI/model/export source. This addendum adds **I5, Important** to the initial findings; the combined count is now **five Important and two Minor**, with no Critical finding established. The initial four-Important/two-Minor review above remains the original scoped result. No product changes, tests, compile, or runtime checks were performed for this follow-up.

### I5 — Removing evidence references makes an applied advisory disappear from incident detail

Location: `app/src/main/java/uk/co/traynor/privategallery/ui/EvidenceLocker.kt:148`; supporting application code `EvidenceAiDialog.kt:69-80` and model behavior `EvidenceCatalog.java:83-87`.

The incident detail renders a stored analysis only when `analysis.evidenceIds` intersects the incident's current `evidenceIds`. Applying an advisory appends its ID/model label to incident notes and unions its complete lineage into the incident; the actual summary remains in the stored analysis. `removeEvidence` intentionally detaches the removed ID from both incident and analysis reference lists while preserving analysis text, incident notes, and both lineages. After the last overlapping reference is removed, the filter is false: the incident still shows the applied-advisory ID label, but the actual advisory/summary disappears from its detail. There is no other stored-analysis library/detail renderer in these Evidence UI files. This is loss of owner access through the local UI, **not deletion of the persisted AI text or source fingerprints**.

Minimal source-derived reproduction: analyse one evidence record; separately apply/save its summary to an incident; close the AI dialog; remove that evidence record via the reference-only action; reopen the incident. Its notes still identify the applied analysis and the catalogue still retains the analysis text/lineage, but incident detail displays no advisory body. The archive selector can still retain this analysis for the incident through its complete contributing lineage (`EvidenceArchive.kt:34-36`), subject to the existing live egress restrictions, making local review and export visibility inconsistent.

Retain a durable, explicit incident-to-analysis association or otherwise resolve the existing owner-approved association safely when rendering. Do not reconstruct that relationship merely from arbitrary shared source overlap, silently reattach removed originals, or weaken retained-lineage egress checks. Show the preserved advisory and its source fingerprints with removed/unattached references clearly labelled. Add a focused actual UI regression for apply → reference removal → reopen, asserting summary visibility, retained provenance, zero original deletion, and unchanged export-policy enforcement. Keep it **UNRUN** until the grouped API37 gate.

This warrants a separate Important functional finding, rather than folding it into I3's selection-flow work or treating it as cosmetic: the specified preserved optional AI summary becomes unreachable in its owner-approved incident even though storage correctly retains it. Include I5 in the serialized Task6 fixes and scoped re-review. The separate credential-monitor issue remains with its AI/access owner.
