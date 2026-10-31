# Independent route execution after complete failed UI selection

Root-owned existing runner/fast-workflow correction. User authorises groupedfocusedsecurityverification, continuingindependentworkwhenonetaskblocked; no newworkflowfile, sourceversion or securityscope reduction.

FirstthreeAndroidruns stoppedbeforeallfouractualownedroutecaseswhennonrouteUIcasesfailed. Thirdrun has147PASS/2FAILof149normalcompletedcases. CurrentrunnerfailfastpreventsgatheringrequiredindependentVPN/Torobservations. The revised runner preservesactualnonrouteexitstatus in nonroute-exit-status.txt. Onlyexit1 with exactuniquecomplete149nonroutecaseidentityset, no skips and anactualfailure/error cancontinue; missing/truncated/duplicate/foreign/skipped/unexplained/infrastructure/timeout resultsstopbefore routecases. Existingonionpreflightandfourorderedroutebarriers/individualerrorchecksremain.

Afterrouteobservationsarearchived, anexplicitfailurebarrierexitswithoriginalnonroutefailureBEFOREstrict153-case/rawroutevalidationandbeforefocused/finalgates. A run withanynonroutefailure thereforecannotapproveproduction,regardlessrouteoutcome. Existingfullstrictcase/packetvalidatorandGitHubsource/run/conclusionchecksunchanged. Thiscollectsdiagnostics,notpartialreleaseapproval. SourceArchive helper alwayspreservesoriginalframeworkXML/status; no synthesizedAndroidXML.

Focusedcontroltests exerciseactualrunnerblocks inisolatedtemporarysynthetic149-case XML, mockonlyrun_selectionstatus androute/strict-validatorreachabilitymarkers. They arehostcontrolchecks,norealAndroidorpacketproof. Negativecasesmissing/partial/duplicate/foreign/skippedXML, unexplainedexit1 andexit2/124/137 cannotreachroutephase; normalcompleteexit1 canreachroutesbutmustexit1beforestrictvalidator; exit0retainsstrictvalidatorpath. No sourceCreds/signing/devicecalls.

Preimplementationcommand PYTHONDONTWRITEBYTECODE=1 python3 -m unittest scripts.tests.test_evidence179_runner -q: Ran6tests, FAILED(errors=6), requiredcontinuationblocksabsent. Postimplementation SAMEcommand: Ran6tests in0.201s, OK,exit0. bash-n scripts/run_evidence179.sh exit0. ExistingFocused evidencehostcheckcommand addsONLYthisnew6methodmodule; no unchangedpassingbroadsuitewasrunlocally.

Files: scripts/run_evidence179.sh, scripts/tests/test_evidence179_runner.py, .github/workflows/fast-development.yml. Freshindependentscopereviewwithtwo-test runtimefix3 mandatorybeforeCI. ActualLinux/Androidcontinuationunrun; all153/security/original-keyupgrade/signinggatesstillmandatory.


## R179-F3-1 correction: execution status and collection status

Scoped review finding: `run_selection` returned an artifact-copy exception status before its original Gradle/timeout status. Because XML copying precedes additional-output copying, complete failed 149-case XML could already exist when a later copy failed. The caller then interpreted collection exit 1 as ordinary Android exit 1 and permitted diagnostic route continuation. The prior whole-function stub controls did not exercise this boundary. This correction supersedes the earlier status-preservation claim for that boundary.

Only `scripts/run_evidence179.sh`, `scripts/tests/test_evidence179_runner.py` and this appended report changed. Preimages were saved before editing in `/tmp/runtime179-runner-fix1-before/`. Android fix3 files, manifest, workflows, validators, versions and product source remain outside this correction.

`run_selection` now resets and exposes independent original invocation and collection statuses on every call. Its process result preserves a nonzero original status; a successful invocation with failed collection returns the collection failure. Nonroute receipts independently record the original status in `nonroute-exit-status.txt` and collection status in `nonroute-collection-exit-status.txt`. Collection must succeed before even considering the existing exact failed-149 diagnostic continuation check. Original 124/137 remain 124/137 after collection failure. Original 0 plus collection failure exits 1 while retaining original receipt 0. Route end phases similarly preserve true original status and add `collection_exit_status`; failed collection prevents the independent peer-close check and the next route. The existing final nonroute failure barrier still precedes strict 153-case/raw-route validation and gates.

The focused controls now extract the actual `run_selection`, continuation/failure-barrier blocks and actual route-loop body. Synthetic timeout/device emission is mocked; the real archive Python, XML copy and additional-output copy run in isolated temporary workspaces. A real existing-destination collision raises `FileExistsError` after the complete XML was copied. Controls cover original 124/137, ordinary 1 and successful 0 with this collection failure; 1 plus successful collection reaches routes but exits 1 before strict validation; 0 plus successful collection retains the strict-validation path. Actual route-body controls cover 0/1/124/137 plus collection failure and true end-phase original/collection fields, and successful collection still reaching the independent-close command. Missing/partial/duplicate/foreign/skipped XML, unexplained 1 and infrastructure statuses retain their negative controls. Reachability markers stand in for route/strict/peer execution only; they are not Android or packet evidence.

Observed commands from repository root:

- Before the production correction, `PYTHONDONTWRITEBYTECODE=1 python3 -m unittest scripts.tests.test_evidence179_runner -q`: exit 1, `Ran 9 tests in 0.734s`, `FAILED (failures=4, errors=5)`. Real-boundary failures included ordinary 1 plus archive failure reaching routes, original 0 recorded as 1, and original 124/137 replaced by 1; new independent collection receipt assertions also failed before implementation.
- After the correction, same command: exit 0, `Ran 9 tests in 0.687s`, `OK`.
- After adding real route-body controls, same command: exit 0, `Ran 11 tests in 1.048s`, `OK`. Final unchanged-source confirmation: exit 0, `Ran 11 tests in 0.974s`, `OK`.
- `bash -n scripts/run_evidence179.sh`: exit 0, no output, including final confirmation.
- Source/dependency audit of `scripts/verify_evidence179_routes.py` lines 209–215 and 246–256: host phase validation accesses required original/peer statuses without an exact phase fieldset; the new collection field is compatible. Host phases and the runner are raw-hash bound. `scripts/revalidate_runtime179.py` `route_comparison` and `main` replay the frozen raw validator, compare proof claims and all canonical raw/control hashes, and include the host-phase hash; they do not reject an extra host-phase field. No validator or replay edit was made.

Frozen source SHA256:

- `scripts/run_evidence179.sh`: `fbc67c80135ea7a7f2074acfc59385109c0a188e075bc4233472d818492eb94b`
- `scripts/tests/test_evidence179_runner.py`: `2b99705e47c775d0d73fda3e0216c3594a770240cb161dfd8c0d8ed3a97edb43`
- Audited unchanged raw validator: `9e08c0b5ba6ee478132e2a4fbb5c560b1dfa3a3ceb85dc0ed6a558ad911d8775`
- Audited unchanged release replay: `d0b4817e1d55c0aad7a9c89cb82b60c6e5ac38e61acb074b5d15646242389ef2`

Limitations: local host controls only. No actual Android, VPN/Tor, raw packet/release replay, CI, signing or unchanged broad-suite run occurred in this correction. The strict 153-case roster, original failure barrier and all mandatory release gates remain required. Frozen for fresh narrow independent review.
