# NovaOS 1.7.50 (Build 96) release review

Source: 6d91450aac55c805df5d6f0bac20164b09697ab2
Authorization: authenticated owner push
Release scope: public stable after disposable upgrade verification
Physical Fold acceptance: pending
Upgrade fixture SHA-256: 0d1c2207aeab2ef68fe29f10b0a868914521c0d49aaea79a9e79d9b3996c6e7e
Upgrade controls SHA-256: ed353777c909100c9df1e341f23d60f9e40108ec444b602ab79d8ad705065ea3
Runtime matrix SHA-256: b3a221684edbd0e3462614ff8dbb402fb74d257823a6e9d5b7ea658ebf6242fa
Runtime workflow run: 45012948591
Runtime branch: main
Signing workflow SHA-256: b74ea12110ca0255e1586f9a1fb86a65906af67f9fb0b3442082ff52c4e2d58b

Build 96 includes the Hidden Workspace and shared Labs changes from PR #75 and the Tor relay repair from PR #76. The final merge tree matches PR #76 head `7a7fb6bf972f654a0bbdd32ca432c5d006d36ea0`, verified by Hidden Space Android 17 run 45008824556. Nova Home Intelligence PR #73 is separate.

Hidden Space Android 17 run 45012948628 passed on the final main commit with 776 JVM tests, 136 Android test executions and all three isolated upgrade phases. Live WireGuard routing and revocation passed. The independent Tor origin observed closure after 399 ms on lock and 2,560 ms after abrupt process loss, within the 20-second limit. Source backup exclusions and the protected-core checks passed. The exact-source integration suite covers Guardian, Sanctuary and backup process restart.

Production publication requires the original signing certificate, a populated Build 95 to Build 96 in-app upgrade and verified restart. The existing Guardian replacement-notification issue on Galaxy Z Fold remains open. Owner acceptance is pending on both physical Fold displays.
