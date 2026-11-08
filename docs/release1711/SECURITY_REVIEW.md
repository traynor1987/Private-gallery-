# NovaOS 1.7.32 Code 67 signing review

Source: 5ee1a5458bb5d62daeae2369ee9dde05952a0faa
Decision: APPROVED
Production signing: APPROVED
Upgrade fixture SHA-256: 1fb15979880a1a0f4273866595a5dfb4d68f011bfb4e44efb69b648d79374c02
Upgrade controls SHA-256: 18050878a2ef2480ab03a22603685fe02d5d52a9836cfb25f5564d58c6fba234
Runtime matrix SHA-256: 789b0eef5c8a8383ffefa050bb69064f32a447201838e2e61e6a204ee2092694
Runtime archive SHA-256: 9472d566e349f926d74ee94f5e57d599b76dcdeca24b7014ad3916956bfc63ca
Signing workflow SHA-256: 0184458138f4a723745f94644b2d5e255bf8efc6ac47bd844024a134edb6bc7c

The owner requested a production-signed in-place update after mandatory tests. Exact-source Android 17 verification run 42484407367 passed the runtime matrix, focused navigation and Sanctuary checks and original Backup restart evidence. Physical Galaxy Z Fold validation of Code 67 remains pending.

The production package and original signing certificate are required. The distributed Code 66 archive is pinned to production job 128590919516 of run 42434691690. Delivery requires the signed Code 66 to 67 upgrade and after-restart data verification.
