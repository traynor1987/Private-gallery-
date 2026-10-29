"""The disposable fixture permits only the four explicitly authorised code45 upgrade paths."""
import re
import unittest
from pathlib import Path

class UpgradeGuard1762Test(unittest.TestCase):
    def test_original_key_fixture_has_explicit_code45_pairs(self):
        source=Path('scripts/upgrade/UpgradeFixtureTest.kt').read_text()
        pairs={(int(a),int(b)) for a,b in re.findall(r'baselineCode == (\d+) && candidateCode == (\d+)',source)}
        self.assertIn((41,45),pairs)
        self.assertIn((42,45),pairs)
        self.assertNotIn((40,45),pairs)
        self.assertIn((43,45),pairs)
        self.assertIn((44,45),pairs)
        self.assertNotIn((45,45),pairs)

if __name__=='__main__':unittest.main()
