from pathlib import Path
from subprocess import CompletedProcess
from tempfile import TemporaryDirectory
import unittest
from unittest.mock import patch

from scripts import verify_code80_apk_identity as identity


class ApkIdentityTest(unittest.TestCase):
    def test_only_original_release_identity_is_accepted(self):
        fingerprint = identity.RECEIPT["original_certificate_sha256"]
        badging = ("package: name='uk.co.traynor.privategallery' versionCode='80' versionName='1.7.38.7' "
                   "platformBuildVersionName='17'\nsdkVersion:'37'\ntargetSdkVersion:'37'\n")
        signature = ("Number of signers: 1\nVerified using v2 scheme (APK Signature Scheme v2): true\n"
                     f"Signer #1 certificate SHA-256 digest: {fingerprint}\n")
        cases = (
            (badging, signature, True),
            (badging.replace("versionCode='80'", "versionCode='79'"), signature, False),
            (badging.replace("privategallery", "privategallery.debug"), signature, False),
            (badging.replace("sdkVersion:'37'", "sdkVersion:'36'"), signature, False),
            (badging + "application-debuggable\n", signature, False),
            (badging, signature.replace(fingerprint, "0" * 64), False),
            (badging, signature.replace("Number of signers: 1", "Number of signers: 2"), False),
            (badging, signature.replace(": true", ": false"), False),
        )
        with TemporaryDirectory() as directory:
            apk = Path(directory) / "fixture.apk"
            apk.write_bytes(b"synthetic test bytes")
            for manifest, signing, expected in cases:
                with self.subTest(manifest=manifest, signing=signing), patch.object(
                    identity.subprocess, "run", side_effect=[
                        CompletedProcess([], 0, manifest), CompletedProcess([], 0, signing),
                    ],
                ):
                    if expected:
                        self.assertEqual(len(identity.inspect(apk, Path(directory), 80, "1.7.38.7")), 64)
                    else:
                        with self.assertRaises(ValueError):
                            identity.inspect(apk, Path(directory), 80, "1.7.38.7")
