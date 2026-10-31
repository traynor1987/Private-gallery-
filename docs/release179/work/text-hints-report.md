# Text hint extraction implementation report

Status: implemented, independent review pending; no Android/runtime claim.

Files EvidenceTextHints.java +EvidenceTextHints179Checks.java. Pure readonly hint extraction supports explicit-offset ISO epoch parsing, unresolved local/relative/date-only/time-only values, possibleparticipant suggestions (never identifiedparticipant), exact rawtext/provenance, bounded100ktext/256hints. No persistence/network/mutations. Rootownsfiles; foundationagentnotaffected.

RED: java com.sun.tools.javac.Main -d /tmp/evidence179-hints scripts/tests/EvidenceTextHints179Checks.java && java -cp /tmp/evidence179-hints EvidenceTextHints179Checks -> AssertionError Evidence179 visible timestamp hint feature missing/ClassNotFoundException. javac binary absent; JDKcompilerentry used.

GREEN: java com.sun.tools.javac.Main -d /tmp/evidence179-hints app/src/main/java/uk/co/traynor/privategallery/core/evidence/EvidenceTextHints.java scripts/tests/EvidenceTextHints179Checks.java && java -cp /tmp/evidence179-hints EvidenceTextHints179Checks -> all explicit-offset/provenance/ambiguous/relative/clock/unconfirmedname/hostile/bounds assertions PASS. Initial oversize case incorrectly used120kchars for256hinttest; corrected to500distinctvalidtimesbelow100klimit, retains separate100001oversizerejection.

No commit/CI bythissubtask. LocalGradleunavailablebeforecompile. Reader must not treat hints as verifiedfacts; onlyexplicitoffsettext yields epoch labelledOCR_VISIBLE_UNVERIFIED. No parkedfindings.

## Review fix round1

Independentreview specprovenancePASS/qualityImportant: participantregex multiline^\s* couldrepeatedlyscanblanknewlines. Added100knewlineboundedworkerregression; samehostcommandRED AssertionError BlankmultilineOCRmustnotscanquadraticsuffixes/TimeoutException. Changedonlyprefix tohorizontalwhitespace[ \t]*. GREEN samehostsuitePASS includingblankadversarialcase. Originalprovenance/epoch/hostile/bounds testsretained. NoAPI/runtimeclaim. Scopedre-reviewpending.
