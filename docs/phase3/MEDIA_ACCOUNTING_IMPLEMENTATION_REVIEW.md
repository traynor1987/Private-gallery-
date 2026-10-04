# Phase3 F1 media/accounting implementation review

**Task2 spec compliance: COMPLIANT within approved component scope. Task quality:
APPROVED. No unresolved Critical/Important finding in that scope. PHASE3 RESULT:
NO-GO.** This is a local implementation review, not complete transfer acceptance.

Original independent review inspected `6c8773d..d21f36d` and found three Important
gaps despite609 passing JVM tests. Fix round1 was independently re-reviewed on
`d21f36d..74fd791eb07c53760e3015050701377689254e50`; only its five-file diff and
original findings/new breakage were in scope. The reviewer reran no tests and
changed no app, index, HEAD or branch state. Both full reports remain in the
implementation ledger workspace; this record preserves the public disposition.

| Finding | Verified enforcement and regression | Disposition |
| --- | --- | --- |
| I1 callback revokes original operation after initial validity check | Concrete original scope/credentials/epoch check repeats after callback; real decrypted plaintext is wiped and withheld when final callback revokes | ADDRESSED |
| I2 retained producer retries after initial counter registration failure | Irreversible private registration-start before callback/create; failure terminally invalidates admission; exact retained video first/second-key and whole58/U82 retries deny before new GCM/registration | ADDRESSED |
| I3 equal-byte new inode substitutes pending canonical reservation | Registration returns original entry only after final callback+same-object reopen; private grant pins entries/ancestors at mint, claim, consume and completion; both formats and first-video-key replacement deny | ADDRESSED |

The fix's new admission-only abort does not physically close under storage
gates. Real delegated-channel tests prove the caller still owns physical close
and performs it outside exact Primary/Hidden locks. Callback-facing producer
flags repeat after binding. Read permits have no pending entries and acquire
no root/storage IO through the new pending check under a reader mutex. These
are narrow checks; existing helper outer-use close placement and lifecycle
registry/resource/native acknowledgement remain Task3.

Fresh exact restored source passed98 targeted tests/9 suites and628 unfiltered
full JVM tests/123 suites,0 failures/errors/skips. Root independently inspected
raw logs/XML and all14 source hashes. Earlier fix RED/GREEN raw logs were lost
in a workspace replacement and are qualified as unretained agent history;
they are not presented as preserved execution proof. The interrupted run has
no result. Fresh raw results are separately backed; see IMPLEMENTATION_EVIDENCE.md.

Unchanged cross-task limits: Task3 authority resource ownership/cleanup/outer
close placement; Task4 selected manifest and complete ordinary grammar; Tasks5/6
independent paired proof/P1 recovery and complete transfer/restart/receipt state
machine; Task7 actual lifecycle/provider/native UI. No DTO/counter result grants
source-deletion authority. No owner data migrated or Primary encrypted-format
changed. Inherited warning noise stays visible for final review.

No source checkpoint has published this local Task2 work yet. Complete matrix,
coherent checkpoint lint/build/packaging/backup/secrets, exact-head push/PR CI,
permanent-signer candidate and owner physical acceptance remain mandatory. PR59
stays draft/unmerged; no Phase4.
