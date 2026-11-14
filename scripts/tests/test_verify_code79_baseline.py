import hashlib
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile

from scripts import verify_code79_baseline as baseline


class Code79BaselineTest(unittest.TestCase):
    def test_accepts_only_the_exact_archive_and_original_release_receipt(self):
        apk = b"synthetic APK payload"
        expected = baseline.RECEIPT | {"apk_sha256": hashlib.sha256(apk).hexdigest()}
        for mutation in (None, "archive", "member", "apk", "receipt"):
            with self.subTest(mutation=mutation), tempfile.TemporaryDirectory() as directory:
                archive = Path(directory) / "code79.zip"
                receipt = expected.copy()
                if mutation == "receipt":
                    receipt["version_code"] = 80
                evidence = io.BytesIO()
                with zipfile.ZipFile(evidence, "w") as nested:
                    nested.writestr("release-summary.json", json.dumps(receipt))
                with zipfile.ZipFile(archive, "w") as outer:
                    outer.writestr(baseline.APK_NAME, apk if mutation != "apk" else b"other")
                    outer.writestr(baseline.EVIDENCE_NAME, evidence.getvalue())
                    if mutation == "member":
                        outer.writestr("replacement.apk", apk)
                archive_hash = hashlib.sha256(archive.read_bytes()).hexdigest()
                with patch.multiple(
                    baseline,
                    ARCHIVE_SHA256="0" * 64 if mutation == "archive" else archive_hash,
                    APK_SHA256=expected["apk_sha256"],
                    RECEIPT=expected,
                ):
                    if mutation is None:
                        self.assertEqual(expected["apk_sha256"], baseline.verify(archive))
                    else:
                        with self.assertRaises(ValueError):
                            baseline.verify(archive)
