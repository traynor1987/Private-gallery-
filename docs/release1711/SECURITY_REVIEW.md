# NovaOS 1.7.38.8 (Build 84) production controls

Source: de8716aad373509c48cd820dd3d63aac4af13d6e
Authorization: authenticated owner push
Release scope: public stable after disposable upgrade verification
Physical Fold acceptance: pending
Upgrade fixture SHA-256: 00fa86483f88b6c2179454c8e452fd06724fc04d19131145e5325720e46930b2
Upgrade controls SHA-256: 17ea3fc306a538c60ac41befaba087dd0f4d0d8fafe8a55d44feba503772dffd
Runtime matrix SHA-256: e0c54baba756d385e021341d9571785da3aa4380d419ef0da6482ad0b308dce2
Runtime workflow run: 44079639233
Signing workflow SHA-256: 40ea043aa438e8a7f6a92489e6cb06ca064c153e63e083af195e011b336c5c04

The release job binds original Android 17 CI evidence, fixture, controls, and owner authorization to the frozen app source before allowing signer access. The Build 83 GitHub APK must match production run #30 and the original production signer. The sealed test environment seeds Build 83, installs Build 84 in place, verifies retained data and verifies it again after force-stop. Only then may the job stage the APK and checksum, verify the Git tag and draft assets, and publish stable.
