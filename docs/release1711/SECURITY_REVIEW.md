# NovaOS 1.7.38 Code 73 signing review

Source: c0db80efc8d1342896984bd937abb01dc786a0f4
Decision: APPROVED
Production signing: APPROVED
Upgrade fixture SHA-256: a2ccc12bb32a7075079bdadf6a364ba320401aa267cb3873a31c18459c0d0735
Upgrade controls SHA-256: e6fe0bd7fb34ce9eba43863962d366be12268c6d0e1da3788fe99c584e6c87bd
Runtime matrix SHA-256: 789b0eef5c8a8383ffefa050bb69064f32a447201838e2e61e6a204ee2092694
Runtime archive SHA-256: 6965db59287132caff55d7f771d1e638ba884a80f95c1c8ba1d94b73edc20267
Signing workflow SHA-256: 40536cf6ea8c24c9f5bbfc66c1e2c3dacc7c58c31703012643ace3dac92f635b

Run 42658049110 must pass the legacy Android 17 regression matrix, the launcher gesture, App Library, smart folder, tracking, default Home, profile safety and Sanctuary cases, eight observed Backup restarts and one observed Launcher process restart before signer access.

Production uses the pinned original-key Code 72 artifact 13687062546 from run 42641862359. Delivery requires an Android 17 in-place Code 72 to Code 73 upgrade and repeat verification after a force-stop. The production fixture checks retained encrypted Vault, Evidence Locker and Backup records, both launcher layouts, Sanctuary policy and the Android Home default. Physical Galaxy Z Fold acceptance is pending.
