# Evidence Locker179 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans. Steps use checkbox syntax.

**Goal:** Functional encrypted1.7.9/code51 EvidenceLocker, optionalconsentedAI andencryptedexport, original-keyupgrade andoneAPK.
**Architecture:** Immutable owner-bound catalogue + existing original references/crypto/leases; offlineOCR; advisoryAI throughownedroute; GCMcontainerPDF/JSON/originals; currentCompose design.
**Tech Stack:** Java17/Kotlin/Compose/API37/Tesseract4Android4.9.0/BouncyCastle/existingVPN-Tor/Responses.
**Spec:** docs/superpowers/specs/2026-10-31-evidence-locker-design.md
**Interfaces:** docs/release179/CONTRACTS.md defines exactsharedrecords/functions and limits.

## Global Constraints

- Version1.7.9 only, nextunused51 rechecked, packageuk.co.traynor.privategallery, min/target37.
- Originalproductioncertificate only; actualarchivedcode50baseline; nouninstall/clear/newkeys.
- Owner/currentlease checks atread/commit/Mainpublish/AI/export; Primary/Hidden separate.
- vaultOnly restrictions inherit intoOCR/AI/export; originals/reference deletion separate.
- No unrelated camera/browser/media fullsuite; batchCI; noforcepush.

## Review Focus

- Source deleted/trashed/modified afterselection: failflag/reverify, don'tsilentlyreplacehash.
- Revocation midOCR/nativecall, exportGCM orAI: cancel/wipe/cleanstaging, no latepublish.
- OCR untrusted names/dates/instructions: provenance labels, no automaticfact/grouping/actions.
- Changed payload/model/route/catalogue afterconsent: retireticket; no repeatedbilledPOST.
- Corrupt/truncated/wrongowner/oversizedmetadata/export: boundedfailclosed, priorciphertextsurvives.

### Task1 — Owner-bound model, store, grouping and search
Files core/evidence/EvidenceCatalog.java, EvidenceEncryptedStore.java; scripts/tests/Evidence179Checks.java; scripts/check_evidence179.sh.
- [ ] Writefailinghosttests forcodec/owners/tamper/atomicrevokedcommit/rawOCRcorrection/audit/groupacceptreject/merge/split/manualtime/search/quotas.
- [ ] ObserveRED; implementCONTRACTS Java immutablemodels andexistingrecordcipher domainadapter.
- [ ] Runjavachostsuite; reviewcommitcoherentfoundation withoutAndroidCI.

### Task2 — Original adapters and lease controller
Files core/evidence/EvidenceAccess.kt, EvidenceController.kt; MainActivity.kt integration; targetedJVM/Android tests.
- [ ] Testsamevaultreference/nooriginaldeletion/vaultOnly/integrityhash/missingoriginal/keyepochstale andinterruptedregistration.
- [ ] ImplementPrimarykeyidentity/generation andHiddenSessionoperation leases; existingverifiedsource streams/SAFimport. PublishMainonly andcancelonlock/Hide/Panic.
- [ ] Verifyboundedmetadata/hash/availability andalllegacyfilesuntouched.

### Task3 — OfflineOCR and provenance
Files core/evidence/EvidenceOcr.kt, EvidenceMetadata.kt; pinnedassets/model+notices; scopeddependencyconfiguration; EvidenceOcr179Test.kt.
- [ ] Testsyntheticrenderedmessage text/names/date/time/rotation/confidence/rawcorrection andrevocation/nooutputfiles.
- [ ] ImplementpinnedofflineTesseract, cancellablehOCR-first withnativeexitcleanup, boundedbitmaps, document/PDFtextorpageOCR, timestampclaimlabels.
- [ ] Verifyassetchecksum/licenses/nativeABIs/16KBcompatibility andrealAPI37OCR.

### Task4 — OptionalAI and owned routes
Files core/evidence/EvidenceAi.kt, EvidenceNetwork.kt; owner-scopedcredentials; Mainpermission/coordinator hooks; targetedconsent/transporttests.
- [ ] Testexactpayloadpreview/one-shotdigest/model/route/revisionbinding/noautoPOST/keyredaction/responseadvisory/foreignIDs/revocation/VaultOnly.
- [ ] ImplementdocumentedResponsesmodels/pricing/costlimits, exacttextandexplicitimages, credentialsonsendonly; nofiles/tools/background/cache/redirects.
- [ ] ReuseexistingPrimaryAItransport; HiddenownedVPN+OwnedVpnDns orTorprivateUnix+isolationproxy; packet/revocationtestsmandatory, nofallback.
- [ ] Separateowneracceptanceofsummary/group/order suggestions, auditallapply/reject.

### Task5 — Password export and report
Files core/evidence/EvidenceArchive.kt, EvidencePdf.kt; exportformat.md/decryptverification; focusedexporttests.
- [ ] Testwrongpassword/tag/truncate/headerbounds/originalhashes/exactbytes/labelledAI+dates/SAFcancelcleanup/plaintextconsent/vaultOnly.
- [ ] Implementversionedfixedscrypt+AESGCM streamcontainerwithPDF/JSON/transcript/index/hashes/originals; ciphertextstagingonly, EOFauthbeforepublish.
- [ ] ImplementOpenencryptedexport/password/reportview/explicitplaintextZIPre-export forportability; cleanallownerstagingonretire/restart.

### Task6 — Complete existing-themed UI
Files ui/EvidenceLocker.kt andsmallfocusedpartials; MainActivityvaultmenuentry; sharedprotectedDialogs.
- [ ] Testactualimport→OCRreview/correct→incidentassign/suggestacceptreject→timeline/order/search→AIpreview/cancel/faketransport→encryptedexport/open→delete-reference→lock/reopen.
- [ ] Implementhome/library/incidentdetail/timeline/OCR/search/AI/export/bulk/merge/split/archive/restore screens, sourceview, integritystatus.
- [ ] Coverlight/dark/Foldwidth/largefont/accessibility, no genericwhitepopups/noextrascreensredesign.

### Task7 — Focused verification, review, signing and delivery
Files existingfastworkflow179job exclusion; establishedsigning179activation; scripts/evidence179manifest/validators; docs/release179/UpgradeFixtureTest.kt; V1_7_9_RECOVERY.md.
- [ ] Recheckmaxsignedcode/assign51; preventbroadfastjob179 beforepushappchanges; compile/hostfirst thenonegroupedexactAndroid suite, diagnosebefore reruns.
- [ ] Independentfullsecurityreview0Critical/Important; freezeexactpassingsource/artifact/controlhashes.
- [ ] Actualarchivedoriginal-key50→51seed/install-r/verify/restart preservingalllegacy+newadditivestate; signoriginalcertificateaftergates.
- [ ] IndependentlyverifyoneAPKbinaryidentity/v2signature/cert/digest, saveAPK+filteredbundle, docs-onlybackupremoteSHAverify, physicalFoldchecklisthandoff.

Ownerexplicitautonomy replacesroutineapprovalpauses. Independentagentshave disjointownedfiles; integrationandwhole-change review remain mandatory. No releasecompletionclaimuntilallgates+delivery.
