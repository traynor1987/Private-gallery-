# Private Gallery 1.7.2 recovery

## Verified starting point
- Source branch: release/1.7.1-professional-ui.
- Independently verified full remote SHA: 397c3b480c60f5ea494020804dc0117705099d62.
- New branch: release/1.7.2-camera-ai-refinements, created from that exact commit.
- Clean baseline; existing metallic-blue icon preserved. No earlier branch changed.
- Read V1_7_1_RECOVERY.md current candidate record. 1.7.1 signed code34 and automated regression/synthetic original-key upgrade gates passed; owner physical acceptance remains pending. Preserve that release and its evidence separately.
- Baseline inspection found existing CameraX controller with immutable VaultCaptureAccess, capture journal, input gate, revoke/stop lifecycle and authenticated editor thumbnail loaders.

## Status
Design recorded in docs/superpowers/specs/2026-10-23-camera-ai-refinements-design.md at commit 9d1b30cf35a6fb08fc3b5afd0e245d4839e1e254.
No application implementation yet. No new tests, signing or APK claimed. Design approval is pending under the active brainstorming workflow.

## Next steps
1. Approve/revise the concrete design, then write the implementation plan.
2. Add effective-prompt composition and balanced independent image cards with focused tests.
3. Refine full-screen camera and add Reading presentation over the existing capture pipeline.
4. Verify ownership, immediate authority invalidation, lock handling, encrypted-import-before-cleanup and recovery.
5. Run fresh full regression/security/browser/process tests and production-package original-key code34 to code35 upgrade/restart checks.
6. Verify signing certificate, APK identity/hash and provide one acceptance APK. Physical Fold acceptance remains owner-only.

## Preservation
No owner-device/media operations, uninstall/clear-data, key generation, encryption migration, network routing change, main merge, V2 or face recognition. VPN+Tor remains unavailable. Each coherent implementation must be pushed and independently verified before lengthy CI.
