# NovaOS 1.7.38.8 (Build 84) production controls

Source: de8716aad373509c48cd820dd3d63aac4af13d6e
Authorization: authenticated owner push
Release scope: public stable after disposable upgrade verification
Physical Fold acceptance: pending
Upgrade fixture SHA-256: 4fa962f1c198232ccb324b7493c216be452defa0a88620286d503c960429d727
Upgrade controls SHA-256: 80f0cd26d50443000d0c6a8685a7decb8bb18854149d0bd7f00fbff52b8018ee
Runtime matrix SHA-256: 62b209459a34f97e06eb73a0ccb88d7f503b7260696b3607812120ac3e2d3967
Runtime workflow run: 44079639233
Signing workflow SHA-256: 40ea043aa438e8a7f6a92489e6cb06ca064c153e63e083af195e011b336c5c04

The release job binds original Android 17 CI evidence, fixture, controls, and owner authorization to the frozen app source before allowing signer access. The Build 83 GitHub APK must match production run #30 and the original production signer. The sealed test environment seeds Build 83, installs Build 84 in place, verifies retained data and verifies it again after force-stop. Only then may the job stage the APK and checksum, verify the Git tag and draft assets, and publish stable.
