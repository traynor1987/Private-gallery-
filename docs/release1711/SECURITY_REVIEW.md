# NovaOS 1.7.37 Code 72 signing review

Source: 7422343e73b13569a6a18501ecfe077bcca55c3d
Decision: APPROVED
Production signing: APPROVED
Upgrade fixture SHA-256: 48bbd38578fb315d490b9d8d208c310b404eb11237912bc202437a71d02a9c6a
Upgrade controls SHA-256: fbabb8251d598e967cbab8e1e2a875a93b1e61a8a1c954e20e09aaa321d9d2e3
Runtime matrix SHA-256: 789b0eef5c8a8383ffefa050bb69064f32a447201838e2e61e6a204ee2092694
Runtime archive SHA-256: ea00771eea62047d871a96dd1c6e1a4f11e8a033cf2940ff191bc27da9692bfe
Signing workflow SHA-256: f755c4aff19b8a8c6beac6ec7022f679aafe27babe8fc3ffbb541568c1586a00

Run 42633699329 must pass the legacy Android 17 regression matrix, the launcher gesture and Sanctuary cases, eight observed Backup restarts and one observed Launcher process restart before signer access.

Production uses the pinned original-key Code 71 artifact 13672198205 from run 42614242525. Delivery requires an Android 17 in-place Code 71 to Code 72 upgrade and repeat verification after a force-stop. The production fixture checks retained encrypted Vault, Evidence Locker and Backup records, both launcher layouts, Sanctuary policy and the Android Home default. Physical Galaxy Z Fold acceptance is pending.
