"""Reject tool-rendered truncation accidentally saved as application source."""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[2]


class SourceIntegrityTest(unittest.TestCase):
    def test_application_sources_are_not_truncated_tool_output(self):
        patterns = (
            re.compile(r"^Warning: truncated output", re.M),
            re.compile(r"^Total output lines: \d+", re.M),
            re.compile(r"[.…]\d+ tokens truncated[.…]"),
        )
        failures = []
        for path in sorted((ROOT / "app/src").rglob("*")):
            if path.suffix not in {".kt", ".java", ".c", ".h", ".xml"}:
                continue
            content = path.read_text(encoding="utf-8")
            if any(pattern.search(content) for pattern in patterns):
                failures.append(str(path.relative_to(ROOT)))
        self.assertEqual([], failures, "Application source contains truncated tool output")


if __name__ == "__main__":
    unittest.main()
