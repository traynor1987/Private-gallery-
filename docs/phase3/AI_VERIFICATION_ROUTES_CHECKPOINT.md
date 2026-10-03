# Exact account/model verification routes

**PR59 remains DRAFT/unmerged; Phase3 NO-GO.** This scoped production correction builds on remote477bda68df00e12c9866377364c728125b079941 and narrows owned read-only verification before any reservation or connection acquisition.

A new two-case regression failed against the exact preceding implementation: a `/v1/models/../images/edits` request reached the connection factory. The old decoded path prefix also admitted unregistered, encoded and query-bearing variants. The saved parent source, failed test XML and raw RED log remain evidence; this is a real behavioral failure, not an inferred runtime exploit or actual provider request.

The entry point now admits only the four exact literal URLs used by current callers: the two compiled OpenAI model enum IDs and the fixed Replicate account/model constants. No user/server data expands the set, and no decoding, normalization or fallback grants another route. Scope, GET/no-body, global HTTPS checks and existing response limits remain. Legacy image requests retain their separate transitional path.

The new regression rejects nine ambiguous/unregistered variants before Native factory or consumer invocation, and positively exercises all four supported literal routes with synthetic transport and exact terminal cleanup. Existing ownership/ART fixtures now use a supported model URL; lifecycle assertions and all negative limits remain intact. The focused one-IO-thread ownership/caller/route/API suite passed35/35. Required full candidate checks and exact-head CI are recorded below only after actual completion.

Independent read-only review reports no scoped Critical/Important/Minor findings. This closes route narrowing only. Upstream credential/producer/immutable-copy ownership, full factory/capability audit, image handoff, actual TLS/provider/OEM acceptance and combined capacity remain pending. Task3, Tasks4–8 and all63 product exit gates remain incomplete. No owner data migration or later phase occurs.

Original487 attempt1 remains an unexplained packageDebug failure. Same-source retry and independent488 successes remain preserved; later511's explicit heap failure does not establish487's cause.

## Fresh candidate verification

All483 frozen source/app/workflow inputs matched this candidate and remained unchanged throughout the sequential build verifier. The one-IO-thread component suite passed35, broader targeted suite175, complete debug JVM901 and complete Phase0 JVM908, each with0 failures/errors/skips. Debug and Phase0 lint had0 Error/Fatal, with53+4 and70+4 Warning/Information respectively. Both app and both instrumentation APKs assembled. All14 configured backup mutations and source/merged/actual-APK backup/model policies, immutable corpus/fixture comparisons, five format-vector tests,30 design-contract tests, three Browser tests and secret/VPN checks passed. Raw logs, full-suite XML attributes/hashes, relevant XML, lint reports, development artifact hashes and independent review are retained in evidence/ai-verification-routes. No local device execution is claimed.

Exact preceding477 push541 and PR542 independently completed all27 steps successfully with261 decoded PASSED Android cases each, including13 AI fake-transport cases. PR merge4928139a40b036d3871aaaf5bcd4f4c81e384690 has the exact source tree5fe31f89c65379a2e6d5ec5c125d20d98cac0768 and main/source parents93ed56f/477bda6. These completed observations supplement the earlier pending publication record. New route checkpoint exact-head CI remains PENDING at publication, expected261 complete Android cases; predecessor success does not substitute for it.
