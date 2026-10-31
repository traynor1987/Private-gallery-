# Task1: Encrypted Evidence domain/store foundation

Read this first: implement the following task and shared exact interface definitions in docs/release179/CONTRACTS.md, including binding resolved-review addendum. Do not read the whole plan. Owner requests complete1.7.9; this is foundation only, no completion claim.

 Owner-bound model, store, grouping and search
Files core/evidence/EvidenceCatalog.java, EvidenceEncryptedStore.java; scripts/tests/Evidence179Checks.java; scripts/check_evidence179.sh.
- [ ] Writefailinghosttests forcodec/owners/tamper/atomicrevokedcommit/rawOCRcorrection/audit/groupacceptreject/merge/split/manualtime/search/quotas.
- [ ] ObserveRED; implementCONTRACTS Java immutablemodels andexistingrecordcipher domainadapter.
- [ ] Runjavachostsuite; reviewcommitcoherentfoundation withoutAndroidCI.



Ownership: create only core/evidence/EvidenceCatalog.java, EvidenceEncryptedStore.java (may split codec/group/search pure helpers within samecore/evidence Java package iffileunwieldy), scripts/tests/Evidence179Checks.java, scripts/check_evidence179.sh. NoKotlin/Main/Gradle/workflow/legacycipher edits. Read existingHiddenRecordStore.java/PrivateAppCatalog.java/hostscripts forpatterns. Java17publicnestedrecords useexactCONTRACTS signatures. Fillin minorvalidation choices consistently, reportthem. Buildsource testsfirst, observe RED featuremissing throughreflection ifneeded thenimplement. Hostjava realcrypto/currentlease test notmocks ofcipher. Testowner/domainswap/tamper/truncation/duplicate/quotas; originalfingerprintimmutability; owneraudit; rawOCRcorrection; incidentassignment/merge/split/archive/manualtimeline; search+heuristicsacceptreject noauto; failed/revokedcommit andcrossinstance update concurrency. NeedCONTRACTS SourceFingerprintlineage addendum andpage-indexed OCRmodel EXACT.

No subagents. No CI/signing/push orcommit (rootintegrates/reviews/commits sharedworkspace). No broadunrelated regressions. Run focusedhostsuite plus existing8groupsonce anddiffcheck; reportnotcompiledAndroid. Statusreportfull atdocs/release179/work/task-1-report.md: files/tests exactRED/GREENcommands+outputs, decisions, concerns. Returnunder15linesstatus/filepath/testsummary; originalcoresecuritymustnotbeweakened.
