# NovaOS 1.7.38.2 signing review

Source: dc4f0b19be8391b899364fb62d890ad322350e2e
Decision: APPROVED
Production signing: APPROVED
Upgrade fixture SHA-256: c490a0f74c3045b83dd90ce11d0a18a0f7c2c423c8c97540f0a3a06beac7f836
Upgrade controls SHA-256: 10552f51472b08acc7b5965ff97bff1dcb6905adc95425f7c35d2edb73bccd40
Runtime matrix SHA-256: 789b0eef5c8a8383ffefa050bb69064f32a447201838e2e61e6a204ee2092694
Runtime archive SHA-256: 3ba37f29ee2b94c2c476990d6f66dcfb31e506101d90c77d34eb274873c26546
Signing workflow SHA-256: 32086fb6babfd45868082d75eda60e9096ab8aef002fc19ef5b8fe7a41b114bd

Run 42725757718 passed the Android 17 runtime gate. The production upgrade uses the original signed Code 74 APK, checks retained data and layouts after the in-place Code 75 upgrade, and repeats the checks after a force-stop. Physical Fold acceptance remains pending.
