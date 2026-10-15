"""Admission guards supplement actual lifecycle/ownership JVM regressions."""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[2]

class AuthenticationContractTest(unittest.TestCase):
    def test_primary_pin_entrypoints_keep_owned_key_handoff(self):
        source = (ROOT / 'app/src/main/java/uk/co/traynor/privategallery/MainActivity.kt').read_text()
        self.assertFalse(bool(re.search(r'installAuthenticatedKey\(keys\.(create|unlock)\(', source)),
                         'Primary PIN entrypoints must keep the original result ownership through admission')
