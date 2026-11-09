# NovaOS 1.7.38.1 Code 73 repair signing review

Source: 90e84e9e477a551c9f8985daca230bbf543059a0
Decision: APPROVED
Production signing: APPROVED
Upgrade fixture SHA-256: 49ecd50568d978793ec22f9c41c94cc5219744965ba0c85338c00f4edf0c875b
Upgrade controls SHA-256: c042b4333b294b15e2ef696d7f33ba9df1f0d0bd35c0508b065e16a8cf85bfaa
Runtime matrix SHA-256: 789b0eef5c8a8383ffefa050bb69064f32a447201838e2e61e6a204ee2092694
Runtime archive SHA-256: e4ba7478a6105feff06498135a1005d94ea59835b9b7dd60c0bd145577620e3d
Signing workflow SHA-256: 944912a2099dbfe5cb0e420502a1742ac9084ca523086839f220fd43ec1020e0

Run 42673353877 must pass the legacy Android 17 regression matrix, the launcher gesture, native Library pager, smart folder presentation, default Home entry, profile safety and Sanctuary cases, eight observed Backup restarts and one observed Launcher process restart before signer access.

Production uses the pinned original-key Code 73 artifact 13698305559 from run 42666431368. Delivery requires an Android 17 in-place Code 73 to Android version code 74 upgrade and repeat verification after a force-stop. The production fixture checks retained encrypted Vault, Evidence Locker and Backup records, both launcher layouts, Sanctuary policy and the Android Home default. Physical Galaxy Z Fold acceptance is pending.
