# Build 99 release controls

This release covers Wave A in PR #87. It advances the original production package on Android 17 directly from the published Build 97 to 2.0.0, versionCode 99. The Build 97 release remains the baseline until publication. PR #77 and the other NovaOS 2.0 workstreams remain separate.

| Identity | Value |
| --- | --- |
| Package | `uk.co.traynor.privategallery` |
| Published baseline | `v1.7.51`, code 97, source `6b7f1bbe40db3f213ff087833f37e05954e1c792` |
| Build 97 APK SHA-256 | `75760970c6dcc00f560cbb5493b67a6bbc190397948dabb32e50e4753e12c988` |
| Original certificate SHA-256 | `94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17` |
| Candidate | PR #87 on `feature/novaos2-wave-a`, 2.0.0, code 99 |

## Admission sequence

1. Independently review admission controls and confirm `main` contains only the reviewed release controls since the previous main head. Incorporate that exact control snapshot in PR #87 before freezing the candidate. Confirm the version and tag are available and the app changes remain within Wave A.
2. Run the focused Wave A API 37 job and the complete Android 17 major-release security job at the same frozen candidate commit. Record exact source and tree, successful job and attempt IDs, artifact IDs and SHA-256 digests. Preserve failed results. Review rendered light Control Centre contrast, Guardian results and actual Protected Home test evidence.
3. Create the exact receipt-only rehearsal authorization through the authenticated owner ref after both lanes pass. The marker is the only changed file in its commit and records the candidate, tree and immutable workflow inputs. Original-key signing is limited to the existing protected workflow. The rehearsal installs the authentic populated Build 97 APK, exercises the existing updater and installer, verifies data and settings through a restart, and retains receipts only.
4. Merge the exact PR #87 head with a normal GitHub merge commit after the rehearsal succeeds. Its first parent must be the authenticated rehearsal marker commit and its second parent the tested candidate. Its only tree difference from the tested head is the inert rehearsal marker.
5. The owner signing marker is the only change above the verified production merge. It binds both CI lanes and the authenticated prior rehearsal attempt and artifact digest. The protected production job signs the exact merged source with the original key, validates the release package and repeats the populated upgrade using the actual newly signed APK. Only that job retains production assets.
6. The stable publisher separately verifies the owner marker, exact merge, signing and upgrade provenance, APK identity and updater metadata. It creates the stable release through the existing GitHub release workflow and verifies discovery and installation through the unmodified Build 97 updater on a disposable Android 17 emulator.

A failed gate blocks promotion. The real Galaxy Z Fold acceptance follows publication and upgrade through **Settings → Updates & About → Check for updates**. Review [Wave A acceptance](WAVE_A_ACCEPTANCE.md) for both displays, folding, Guardian, Sanctuary and the existing encrypted vaults.

Later Nova Intelligence can keep the PR #77 branch history. Once 99 is installed, the updater requires its later release to advance both versionCode above 99 and semantic version above 2.0.0.
