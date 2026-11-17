# NovaOS 1.7.38.8 (Build 84) production controls

Source: de8716aad373509c48cd820dd3d63aac4af13d6e
Authorization: authenticated owner push
Release scope: public stable after disposable upgrade verification
Physical Fold acceptance: pending
Upgrade fixture SHA-256: 4fa962f1c198232ccb324b7493c216be452defa0a88620286d503c960429d727
Upgrade controls SHA-256: 4ee15e29a18113ba87cf2fe10ad5d6d0b059d28327b3678efe214d768ae804ce
Runtime matrix SHA-256: e0c54baba756d385e021341d9571785da3aa4380d419ef0da6482ad0b308dce2
Runtime workflow run: 44079639233
Signing workflow SHA-256: 40ea043aa438e8a7f6a92489e6cb06ca064c153e63e083af195e011b336c5c04

The release job binds original Android 17 CI evidence, fixture, controls, and owner authorization to the frozen app source before allowing signer access. The Build 83 GitHub APK must match production run #30 and the original production signer. The sealed test environment seeds Build 83, installs Build 84 in place, verifies retained data and verifies it again after force-stop. Only then may the job stage the APK and checksum, verify the Git tag and draft assets, and publish stable.
