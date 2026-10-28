"""The disposable fixture permits only the two explicitly authorised code43 upgrade paths."""
import re
import unittest
from pathlib import Path

class UpgradeGuard1762Test(unittest.TestCase):
    def test_original_key_fixture_has_explicit_code43_pairs(self):
        source=Path('scripts/upgrade/UpgradeFixtureTest.kt').read_text()
        pairs={(int(a),int(b)) for a,b in re.findall(r'baselineCode == (\d+) && candidateCode == (\d+)',source)}
        self.assertIn((41,43),pairs)
        self.assertIn((42,43),pairs)
        self.assertNotIn((40,43),pairs)
        self.assertNotIn((43,43),pairs)

if __name__=='__main__':unittest.main()
