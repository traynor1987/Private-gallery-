# Independent original-code50 binary verification

2026-10-31. Executed on retained exact original APK, not a rebuilt application:

`python3 /workspace/scratch/4181ccd09b2e/verify_apk48.py /tmp/evidence179-original50-baseline.apk 50`

Exit0. Parsed APK signing block and one certificate; verified RSA PKCS1v1.5/SHA512 signature and complete103-chunk APK v2 digest with cryptography, independently decoded compiled AndroidManifest.xml. APK105601622bytes, SHA256 `df958e447ce1c5a9bc9b515d16de03c0d11a961ae6efb030b305b2dfe733ccf3`; certificate `94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17`; package `uk.co.traynor.privategallery`,1.7.8/code50,min/target37,nondebug,CiAccessibilityService178 absent.

This verifies the archived baseline binary/signature only. It is not candidate51 verification, actual upgrade/restart, or physical acceptance. Original APK bytes were not modified. Local candidate verification helper differs only in the expected version-name assertion; it remains unused until a real production1.7.9 APK exists.
