"""The disposable fixture permits only the three explicitly authorised code44 upgrade paths."""
import re
import unittest
from pathlib import Path

class UpgradeGuard1762Test(unittest.TestCase):
    def test_original_key_fixture_has_explicit_code44_pairs(self):
        source=Path('scripts/upgrade/UpgradeFixtureTest.kt').read_text()
        pairs={(int(a),int(b)) for a,b in re.findall(r'baselineCode == (\d+) && candidateCode == (\d+)',source)}
        self.assertIn((41,44),pairs)
        self.assertIn((42,44),pairs)
        self.assertNotIn((40,44),pairs)
        self.assertIn((43,44),pairs)
        self.assertNotIn((44,44),pairs)

if __name__=='__main__':unittest.main()
