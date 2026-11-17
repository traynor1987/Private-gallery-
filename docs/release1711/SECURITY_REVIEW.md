# NovaOS 1.7.38.8 (Build 84) production controls

Source: de8716aad373509c48cd820dd3d63aac4af13d6e
Authorization: authenticated owner push
Release scope: public stable after disposable upgrade verification
Physical Fold acceptance: pending
Upgrade fixture SHA-256: 00fa86483f88b6c2179454c8e452fd06724fc04d19131145e5325720e46930b2
Upgrade controls SHA-256: 17ea3fc306a538c60ac41befaba087dd0f4d0d8fafe8a55d44feba503772dffd
Runtime matrix SHA-256: e0c54baba756d385e021341d9571785da3aa4380d419ef0da6482ad0b308dce2
Runtime workflow run: 44079639233
Signing workflow SHA-256: a7dbe2034c5bb7cb67bb30a587b4a92a0a83178cc5dfa18b1640d64a1a0229fe

The release job binds original Android 17 CI evidence, fixture, controls, and owner authorization to the frozen app source before allowing signer access. The Build 83 GitHub APK must match production run #30 and the original production signer. The sealed test environment seeds Build 83, installs Build 84 in place, verifies retained data and verifies it again after force-stop. Only then may the job stage and retain the signed APK and its receipts. A separate publication workflow has no signing access and checks provenance, the Git tag, and draft assets before marking a release stable.
