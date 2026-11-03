# Navigation ownership draft review

Reviewed the supplied diff/report against the working tree at `/workspace/scratch/novaos` (baseline HEAD `e772b50a`), including the new Java helper, shell runner/host checks, Android production-session tests, relevant MainActivity lifecycle callers, and Hidden popup ownership implementation. No edits, CI, device tests, or reruns of the report's passing host checks were performed.

## Verdict

One **Important** integration defect needs repair before calling the Primary ownership fix complete. No Critical defect identified in the reviewed scope. The normal Private App → Home → Browser transition now selects an ordinary ID rather than the active app URL, and app A/B focus is partitioned by explicit owner ID.

## Important: ordinary-only snapshots no longer fence asynchronous session restore

**Changed location:** `app/src/main/java/uk/co/traynor/privategallery/core/browser/v2/BrowserV2Session.kt:647` (`metadataSnapshot`, with destructive application at 648–655).

**Affected caller:** `MainActivity.kt:2563–2572`, `restoreBrowserPresentation()`.

The caller captures `metadataSnapshot()` before asynchronous encrypted reads and applies the loaded session only if a subsequent snapshot equals that capture. Previously this represented the whole tab collection and active selection. The new projection intentionally excludes app tabs and uses the remembered ordinary selection, so opening an app, switching between retained apps, or navigating an app can leave the guard value identical.

Concrete interleaving:

1. Browser already has its ordinary renderer/tab (so no new ordinary WebView handle is created in the following operations).
2. A delayed `reconcileAfterUnlock()` completion starts `restoreBrowserPresentation()` and captures the ordinary snapshot; its disk read remains pending.
3. The user opens Private App A. `openPrivateApp` creates an app-owned tab and selects it without modifying the ordinary snapshot.
4. The read returns with a non-null browser session. Generation, key, unlock, and snapshot-equality checks all still pass.
5. `restoreMetadata()` clears `privateAppTabs`, replaces all tabs with saved ordinary metadata, and rebuilds ordinary ownership. App A disappears despite still being in an authorized runtime. Its WebView remains in `webViews` without a corresponding tab, because this method also assumes restoration is happening into an unchanged session. Reopening A allocates a new tab/view and loses the retained in-page state.

This violates the same-WebView/context-retention requirement and creates orphaned renderers. The asynchronous restore is reachable following unlock because trash reconciliation completes independently of user navigation; this is not a request to weaken or postpone authentication.

**Repair:** Keep the ordinary-only persisted snapshot, but give the restore caller a separate runtime mutation/selection revision (or equivalent complete runtime guard). Opening/selecting/navigating app-owned tabs must invalidate an outstanding restore. Only restore into an unchanged, eligible session, or explicitly preserve current runtime app ownership and views if merging is intended. Do not put app URLs back into the encrypted Browser snapshot to repair the guard.

**Regression:** Hold the restore result after a live ordinary renderer exists, open/navigate A and switch to B, then deliver the old ordinary snapshot. Assert that the pending restore is rejected and A/B tab IDs, owner bindings, selected owner, and WebView identities remain intact. This is not covered by either added test: both perform restore only in a separate fresh session.

## Scope and quality notes

- Ownership is assigned before new renderer/navigation publication; ordinary picker/count and newly written encrypted snapshots exclude saved-app roots and inherited app popups.
- Explicit ordinary launch and ordinary close fallback choose IDs, with no URL/recent-site heuristic. Existing app roots remain reused within the authorized runtime.
- `canFocus` checks both ID existence and owner equality, and popup creation now requires the active parent. Existing Binder, authentication, transport/profile configuration, and process separation are unchanged in this diff.
- Hidden's local ordinary-picker validation/filtering and saved-app root lookup are appropriately scoped. Hidden Home/Exit retention remains a separate parent transport/lifecycle integration task; this review does not certify it complete.
- The host helper checks are useful owner-policy checks, but manually registering an inherited popup in that test does not exercise production `onCreateWindow`, renderer reuse, or MainActivity restoration. The two new Android tests exercise production session bookkeeping with renderers disabled; they do not demonstrate DOM/backstack/cookie preservation on a device.
- The report correctly marks Android tests/compilation as unexecuted because Gradle distribution acquisition failed before compilation. Historical ownerless snapshots and the existing bounded-tab eviction policy are acknowledged limitations, not newly proven defects in this scoped diff.

## Follow-up review: revision guard and legacy ownership (2026-11-03)

Reviewed the revised MainActivity restore callback, runtime revision implementation, guarded restore entry point, new deferred-restore Android test, and unchanged encrypted session format. No test reruns, implementation edits, or CI were performed. This addendum supersedes the earlier verdict where noted.

### Original interleaving: resolved by source inspection

The original case in which an app is opened/selected/navigated **after** a restore read begins is fenced correctly: MainActivity captures `runtimeMutationRevision()`, the relevant app operations publish `changed()` and advance that revision, and its callback uses `restoreMetadataIfUnchanged()` after the existing identity/authentication/generation checks. The added real-renderer test asserts the appropriate tab IDs, owners, active owner, and WebView identities for that case, with networking closed. Its unexecuted status is reported accurately.

### Important remaining lifecycle case: read begins after the app runtime already exists

The new guard establishes that the runtime did not mutate after capture; it does not establish that replacing that runtime is appropriate.

`reconcileAfterUnlock()` schedules trash reconciliation and invokes `restoreBrowserPresentation()` only from its eventual completion callback (`MainActivity.kt:1677`). The user can already have opened app A before that callback runs. The restore then captures A's current revision. If A remains idle while the encrypted read runs, the revision still matches, so `restoreMetadataIfUnchanged()` accepts the read and `restoreMetadata()` clears A's owner binding and tabs. Its renderer is left in `webViews`, and reopening A cannot resume that same tab/view.

A deterministic session-level example is: create the ordinary view; open A; capture `runtimeMutationRevision()`; call `restoreMetadataIfUnchanged(oldOrdinarySnapshot, capturedRevision)` without another operation. The implementation returns true and clears A. The new test captures before opening A, so it does not cover this variation.

This is an existing restoration eligibility assumption exposed by the broader ownership requirement, rather than a newly introduced revision-counter error. It remains an Important functional gap if this work is described as preserving authorized app contexts across unlock/startup timing.

**Repair:** In addition to the mutation fence, reject destructive non-null metadata restore when a saved-app runtime is already established, or use a separate initial-restore eligibility token that is invalidated permanently by user browser/app interaction. If merging is intended, preserve current app ownership and renderer mappings explicitly. Add the case in which A exists before the pending read is created; assert its owner, selection, tab ID, and WebView survive. A null disk snapshot need not block independent bookmark application solely because an app exists.

### Important existing-install gap: ownerless legacy data is automatically adopted as ordinary

The diagnosis correctly identifies that historical `browser-session-v2.enc` data has no owner marker. The current store uses that same filename, format, and AAD for both historical mixed snapshots and new filtered snapshots. `load()` returns historical entries and `selectedTabId` unchanged; `restoreMetadata()` registers every restored ID with ordinary ownership; `openOrdinaryBrowser()` therefore trusts the legacy selection; `loadColdRestoreIfNeeded()` submits its saved URL once the existing network gate permits it.

Consequently an existing installation whose selected v2 tab was a saved app can still have its first Browser launch open that app's page as an ordinary tab. New filtered writes cannot identify or repair this provenance: after restore, the imported legacy records have already been relabelled ordinary, so subsequent new snapshots can preserve that misclassification indefinitely. This is not an authentication bypass, and it does not justify URL inference, but it materially misses the requested prevention of stale saved state reopening the wrong context. Treating it only as an informational caveat would overstate the ownership repair for existing installs.

**Required migration property:** New trusted ordinary metadata must be distinguishable from unmarked legacy metadata. Preserve legacy bytes/records without automatically assigning them ordinary or app ownership, selecting them for a Browser launch, or submitting their URLs. A new owner-aware snapshot version/file plus preserved legacy quarantine and an explicit recovery/open-in-Browser action is one valid design; equivalent provenance-aware behavior is acceptable. A user may explicitly adopt a legacy entry into a new ordinary context, but the application cannot infer the original owner from its URL. A generic catalogue/URL comparison is inappropriate because the same site can legitimately occur in multiple owners.

Merely dropping legacy `selectedTabId` is insufficient: `BrowserSessionManager.restore()` falls back to the first entry. Registering every legacy tab ordinary but selecting a blank tab also leaves ambiguous saved-app entries in Browser's ordinary tab collection, so they must remain separately recoverable until explicit adoption. Preserve the historical data before any new-format save can replace it.

**Regression:** Use a genuine old-format encrypted fixture containing ordinary-looking and saved-app-looking URLs (including the same URL in more than one record), with the ambiguous app record selected. On first load, Browser must select a fresh/verified ordinary context, no ambiguous URL may be submitted automatically, and the legacy records must remain recoverable. After an explicit recovery choice, only that chosen record should become ordinary. Also verify trusted new-format ordinary selection restores normally, including when its URL matches a saved-app catalogue entry.

### Current scoped conclusion

The specific mutation-after-capture finding is resolved in source; the remaining live-before-capture restoration eligibility case and legacy-provenance migration are **Important** gaps against a complete ownership claim. No Critical defect identified. Hidden Home/Exit retention remains separate and uncertified. Android runtime tests and compilation remain unexecuted in this environment.

## Follow-up closure review: trusted v3, explicit recovery, and restore eligibility

Reviewed the current superseding diagnosis section and exact follow-up changes in `EncryptedBrowserSessionStore`, MainActivity automatic restore/recovery handles, `BrowserV2Session`, recovery UI, encrypted store JVM tests, and ownership Android tests. This section supersedes the preceding two-Important conclusion for those findings. No code edits, test reruns, or CI were performed; the parent's local compile/targeted JVM execution is separate evidence.

**Scoped verdict: both remaining Important findings are closed by source inspection. No new Critical or Important defect identified in this follow-up scope.**

- **Live-before-capture eligibility:** `restoreMetadataIfUnchanged` first checks runtime revision and now rejects any non-null replacement snapshot while `privateAppTabs` is nonempty. This protects app A even if the restore captured its current revision after A was already established, including A retained in the background behind ordinary Browser. A null disk snapshot still permits independently guarded bookmark-only handling. The new Android case captures after A exists and asserts rejection with identical view, tab ID, selection, and owner, plus acceptance of null.
- **Legacy provenance:** new saves target `browser-session-v3.enc`/`.new`, use v3-specific authenticated AAD, and include authenticated version and `ordinary-browser` marker fields. MainActivity's only automatic session read calls `loadForPresentation`, which reads v3 only. V2 ciphertext is never overwritten by this save path and is available through read-only `legacyRecoveryEntries`. The original public `load()` method and `BrowserSessionSnapshot` constructor/data shape remain intact for compatibility, while production automatic presentation no longer calls that compatibility method. Missing/rejected trusted data does not trigger a v2 fallback in MainActivity.
- **Explicit adoption:** the recovery panel describes ambiguous ownership and requires an individual record choice. Recovery rereads the chosen legacy ID, then `openRecoveredOrdinaryTab` validates the URL policy and creates a fresh ordinary tab ID. It copies metadata only, never adopts the old owner/ID/selected record, and submits navigation through the existing cold-restore network gate. Other runtime owners are retained subject to the unchanged bounded-tab policy.
- **Primary authorization and async fences:** the recovery handle captures the existing Primary controller's authentication/key identity, presentation generation, transfer generation, concealment/Hidden selection, route and lifecycle checks. The code rechecks that controller before publishing a legacy list, before accepting a choice, and after rereading on IO. A runtime revision comparison rejects context mutations during either IO interval. Copied keys are cleared in finally blocks. The UI renders legacy records only while the request's originating controller remains current. No new auth, profile, transport, or cross-space path is introduced.

The added JVM fixture uses historical v2 serialization/AAD, duplicate URLs under different IDs, and an ambiguous selected record. It checks no automatic v3 presentation, explicit legacy availability, rejection of old ciphertext relabelled as v3, trusted v3 same-URL round-trip, unchanged compatibility reads, and byte-for-byte retention of original v2 ciphertext. The added Android explicit-choice case checks exactly one new ordinary tab with a fresh ID. These tests cover the source fixes' relevant data properties; they do not by themselves prove the physical UI, upgrade ABI/device flow, or DOM/cookie/backstack retention.

Parent-reported local compilation/targeted JVM checks were underway at review time and were not independently executed here. Hidden Home/Exit retention remains explicitly outside this reviewed completion claim. Existing unrelated lifetime/transport mechanisms were not expanded into this review.
