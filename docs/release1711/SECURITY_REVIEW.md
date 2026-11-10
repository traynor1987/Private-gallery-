# NovaOS 1.7.38.3 signing review

Source: c818c670baa4b980708dc2effb1ab2ab0331ade1
Decision: APPROVED
Production signing: APPROVED
Upgrade fixture SHA-256: d36e071d3eebcede57af2aa95ae2974e82ad1b99b3643b281a1bc5479e09d3a2
Upgrade controls SHA-256: 50e6e6f657c05417625d5f51443eed6a886a3d86e46a197302104868de8283da
Runtime matrix SHA-256: 789b0eef5c8a8383ffefa050bb69064f32a447201838e2e61e6a204ee2092694
Runtime archive SHA-256: c11882b098122621e21e3f4380fe1dda2e7629d9528342a18c4c14203e8a5ef0
Signing workflow SHA-256: f675cd90a0ccf3eb34b4a3b254c1ab975f6c757bd4e5259eb394a7a0399674b4

Run 42813658935 passed the Android 17 runtime gate. The production upgrade uses the original signed Code 75 APK, checks retained data and layouts after the in-place Code 76 upgrade, and repeats the checks after a force-stop. Physical Fold acceptance remains pending.
