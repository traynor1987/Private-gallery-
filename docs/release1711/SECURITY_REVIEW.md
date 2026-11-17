# NovaOS 1.7.38.8 (Build 84) production controls

Source: de8716aad373509c48cd820dd3d63aac4af13d6e
Authorization: authenticated owner push
Release scope: public stable after disposable upgrade verification
Physical Fold acceptance: pending
Upgrade fixture SHA-256: 00fa86483f88b6c2179454c8e452fd06724fc04d19131145e5325720e46930b2
Upgrade controls SHA-256: 862789b5567155b4b854f20e964cb4f4184ea710600ecb52feb9c7e4f404a5dc
Runtime matrix SHA-256: 6a4d1f73ea1e5e296333f378650a1b241b9cd15194413035bd4d094c71ea60f6
Runtime workflow run: 44079639233
Signing workflow SHA-256: f264845adfc738ab6e48caeb940d1e09be2c4d3cc503a150bd55c972049a0782

The release job binds original Android 17 CI evidence, fixture, controls, and owner authorization to the frozen app source before allowing signer access. The Build 83 GitHub APK must match production run #30 and the original production signer. The sealed test environment seeds Build 83, installs Build 84 in place, verifies retained data and verifies it again after force-stop. Only then may the job stage and retain the signed APK and its receipts. A separate publication workflow has no signing access and checks provenance, the Git tag, and draft assets before marking a release stable.
