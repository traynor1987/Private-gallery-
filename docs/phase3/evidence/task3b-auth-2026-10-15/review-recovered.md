# Recovered independent review summary

The read-only Task3B reviewer found three Important defects: reservation validation
and result consumption were not atomic; cancellation delayed controllable-key wiping
until a cleanup worker ran; PIN wiping missed attempt-creation and route failures.
A single fix pass made typed consumption atomic under the original reservation gate,
added bounded synchronous result invalidation without physical acknowledgement,
and protected complete synchronous PIN entrypoints with finally. Actual failing
regressions preceded the fixes; the final98-test focused selection passed.
This is a recovered summary of the completed review, not its missing original bytes
or an additional post-fix independent review.

Remaining findings were not dismissed: SecondaryController still closes requests
under its gate and starts unfunded workers; worker raw-key promotion precedes full
retirement; SecondaryStore and Main biometric/recovery/restore/sensitive paths lack
complete exact-attempt manifests. Pending Cipher work outside its own monitor is
not proof of cleanup outside every calling ranked gate. The three-child Primary
helper covers a synchronous result/input/invocation leaf, not implicit Native/KDF/
provider/IO children or queued workers.

The supported backend has no exact-operation Cipher abort/actual-return
acknowledgement. Prompt cancellation, completed futures, alias deletion, GC,
dummy initialization and hidden APIs are not accepted substitutes. A supported
provider/platform adapter with proved abort/return/failure semantics is required.
Android/device observations alone cannot establish that missing contract.
Task3B remains PARTIAL/BLOCKED; Task3C cannot begin. Phase3 NO-GO, PR59 draft.
