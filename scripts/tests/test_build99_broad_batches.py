import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from build99_broad_batches import batches


class BatchSelectionTest(unittest.TestCase):
    def test_exact_coverage_with_separate_install_for_fresh_case(self):
        cases = {"A#one", "A#two", "B#one", "B#two", "C#one", "D#one"}
        selected = list(batches(cases, {"C"}, {"B#two"}, {"A#two"}, limit=2))
        self.assertEqual(selected[0], "A#two")
        expanded = []
        for group in selected:
            for selector in group.split(","):
                expanded.extend([selector] if "#" in selector else
                                sorted(c for c in cases if c.startswith(selector + "#")))
        self.assertCountEqual(expanded, cases - {"C#one", "B#two"})

    def test_missing_or_overlapping_special_case_fails(self):
        for dedicated, fresh in [({"X#one"}, {"A#two"}), ({"A#one"}, {"A#one"})]:
            with self.subTest(dedicated=dedicated), self.assertRaises(ValueError):
                list(batches({"A#one", "A#two"}, set(), dedicated, fresh))
