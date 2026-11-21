# NovaOS 1.7.49 (Build 95) release review

Source: b003dafbad69a46d7f83896738030200a0dd0df4
Authorization: authenticated owner push
Release scope: public stable after disposable upgrade verification
Physical Fold acceptance: pending
Upgrade fixture SHA-256: 0fd1161956a4ae8fbd7b193e40c3574f5131b3fbe1e7bc421cf2a2610c55ebf8
Upgrade controls SHA-256: f7c8134397aade44203932437fe67846f4ce71dea923e2eb386a7ff1a46087d4
Runtime matrix SHA-256: b3a221684edbd0e3462614ff8dbb402fb74d257823a6e9d5b7ea658ebf6242fa
Runtime workflow run: 44933923184
Runtime branch: main
Signing workflow SHA-256: f44463f0c6165d065471df0923d2acda7bf48a467b7cbcad554fb1b79abf08a0

The Android 17 integration and Hidden Space suites passed at this source. The Hidden Space suite passed all 776 JVM tests and 134 Android cases. Live WireGuard and Tor routing passed with independent packet evidence. The Tor connection closed in 189 ms after Hidden Space locked, within the 20-second requirement. Tor remains enabled.

The previous integration attempt reached the CI runner time limit. The current source differs from the prior candidate only in the main-branch integration job time allowance. Protected-core, app storage and backup exclusion checks passed at source. The signed APK and populated Build 94 to 95 upgrade still require verification before publication.

The Galaxy Z Fold Guardian replacement-notification issue documented for Build 94 remains open. Physical Fold acceptance is pending.
