# Runtime179 peer-close correction — round 1 scoped review

2026-10-31. **Specification verdict: R179-1 ADDRESSED in the scoped source correction. Implementation-quality verdict: no newly introduced defect found in this correction.** Changed-source Android compilation and runtime remain pending. This is neither runtime nor release approval.

## Scope and evidence

Reviewed only R179-1 from `RUNTIME179_FIX1.md`, the runtime correction brief, the appended round-1 report, `work/runtime179-peerclose-review.diff`, the current network Android test fixture, its saved preimage, and the recorded JDK probe source/results. No broader review, source edit, Git/CI operation, subagent, signing access, Android execution, or passing-probe/suite rerun was performed. This report is this reviewer's only repository write.

Read-only Python verification reconstructed the exact supplied two-file diff from the saved preimages, confirmed that the report is append-only, all eight test declarations/names are unchanged, and the `clientRead`/`EventListener.responseBodyStart` block is byte-identical. No trailing source whitespace was found.

## Finding disposition

1. **R179-1 — ADDRESSED.** `peerClosed` is no longer lowered in `finally`; that path now lowers only `handlerCompleted`. The dedicated `PeerEof` can originate only after an actual input read returns a negative value. The alternative positive path explicitly classifies a reset through Android `ECONNRESET` or the two exact `SocketException` reset messages, with a bounded cause walk. Unknown forms fail closed.

   An idle read timeout, unexpected held-response input, local `Socket closed`, and ordinary parser/handler failures are recorded rather than accepted as peer-close evidence. Suppressed socket-cleanup exceptions accompanying EOF or a classified reset are retained in `handlerFailures`; both consumers await handler completion before requiring that collection to be empty. Thus a valid close observation accompanied by a cleanup error cannot make either test pass. A normal handler return or local cleanup alone supplies no positive observation.

   The response-read case retains the server-prefix and actual client `responseBodyStart` preconditions, bounded cancellation/join, `CancellationException`, exactly one fully received request, and the explicit no-publication assertion. It now additionally requires positive peer closure and completed error-free handling. The upload case retains the source writer's actual write/flush/entered precondition and keeps its release latch held until peer closure, handler completion/error checks, and the assertion that the writer has not returned. It then releases the writer, requires `CancellationException`, and retains the empty completed-request assertion for the interrupted upload. No server-release latch creates the socket-closure observation.

## Verification limits and identities

The supplied probe records the old idle false positive, then `peerClose=false` for new idle, unexpected-input and local-close cases, and `peerClose=true` with no errors for genuine EOF. Its source uses real JDK loopback TLS IO and mirrors this observation/error distinction. It does not execute the Kotlin fixture, Android TLS reset handling, or production route cancellation. The passing probe was inspected, not rerun. Exact Android behavior remains to be established by actual compilation and corrected-source execution.

Verified SHA-256 identities:

| Artifact | SHA-256 |
|---|---|
| `EvidenceNetwork179Test.kt` | `8d8d10fdceeb958304313760114874e86382281ee3ca233aaf3947b23bed5af9` |
| `work/runtime179-peerclose-review.diff` | `589e533e08d14f6ca6a0d0877bbb37d53997ca3c5d7e1a8297e5eeaf3b1371c0` |
| `/tmp/Runtime179PeerCloseProbe.java` | `3770b18214deda08f8b5cfd6b1aebb3981555db2f04962a5083e151aa5350aac` |

The grouped actual main/test compilation and corrected-source mandatory 153-case run remain required. All prior bulk/hang evidentiary limits and separate security, owned-route, signing/upgrade, physical-device and release gates remain open.
