# Independent review — 1.5A partial checkpoint

Historical checkpoint. Current permanent Hidden integration and its exact-source verification are documented in [PERMANENT_HIDDEN_STATUS.md](PERMANENT_HIDDEN_STATUS.md). Statements below describe only the older checkpoint.

Reviewed implementation source: release/1.5-hidden-vault source tree329016bc13988d57c56aeccd4d6347a8ce5378d6, commit944c6b5227e1236eda5c3633c3ce131f1bcbbbfc. Scope: three Java primitives, Android credential adapter and synthetic tests. No activation/UI/media or full-product review is claimed.

A separate read-only reviewer inspected the code and independently compiled/executed the Java checks. Findings: no scoped security defects identified. This is a review result, not proof that no defects exist.

Reviewed properties: exact bounded versioned envelope parsing; Hidden-domain AEAD; PIN/derived-key buffer wiping; one-shot authentication attempt identity; lock/supersession/deadline rejection; separate Keystore aliases; bounded outer-record reads; AtomicFile persistence with ciphertext read-back; retention of corruption/missing-key records; no automatic replacement.

The reviewer requested host boundary cases for salt/nonce tamper, malformed lengths/PINs, superseded/foreign attempts, candidate wiping, clock rollback and deadline edges. The expanded host runner passes372 assertions, including100 unlock/lock cycles. This is one behavioral runner, not372 JUnit test methods.

Additional Android cases were authored for record length bounds, .bak/.new behavior, foreign namespace substitution, unavailable storage/retry and independent random keys. They have not executed locally. Actual AndroidKeyStore behavior, file-operation fault boundaries, power loss, OEM lifecycle and screenshot policy remain unverified.

Activation blockers: setup/unlock UI; different-PIN enforcement at all Primary credential writes; concealed-route binding; background/disposal/stale callback handling; complete backup exclusion policy; supported Android and Primary regression tests. No public route currently provisions Hidden credentials. No owner media was accessed.

V2 preservation: remote backup branch preservation/v2-task3b-2026-10-17-d6c9ab4 atd6c9ab454a9f0ea90bcfe12783eb68e51acdd368. Exact local-only histories additionally stored on preservation/v2-local-history-2026-10-17 at24c92a6ee9019069de48671cd29576418e0de8f1. File docs/preservation/v2-local-history-2026-10-17.bundle:170593bytes, SHA256a78808ff3021046b837ac8a2c30545b619c7854f808589ad20d87fcdf43c5eaa. Downloaded bytes matched, git bundle verify passed, and a fresh empty repository fetched only the public recovery prerequisites then successfully unbundled all three exact original commits52599b07893e98c3608cd87d8a9fb0dde675c22f,05f0c452703921f7d77a52a8d174ad6e44af739c,bde432a384093066ded04ac89b82cea9835437cc. Existing V2 branches and PR59 were not changed. Inaccessible other Work state is not covered by that assertion.

## Follow-up integration review

The same independent reviewer inspected the later uncommitted integration source (SettingsHome, HiddenFoundationDialog, shared credential-change gate, typed mismatch exception, Primary PIN collision rejection and backup exclusions). No concrete security/compile blocker was identified by inspection. This does not substitute for exact-head Android compilation and instrumentation.

The review identified that the UI background test had not submitted authentication; it was renamed to state the behavior it actually exercises. In-flight IO lifecycle instrumentation and actual candidate-buffer retirement in that Android path remain unmeasured. Existing core tests exercise completing a held attempt after session revocation. The UI now rechecks its alive flag after the first blocking credential-state read before beginning crypto.

The follow-up adds source functionality beyond the initial reviewed checkpoint; previous source SHA and test counts remain historical rather than claimed as its final Android result.
