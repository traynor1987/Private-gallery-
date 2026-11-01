"""Presentation wiring contracts; Android gestures remain covered by instrumentation."""
import pathlib
import unittest
ROOT = pathlib.Path(__file__).resolve().parents[2]
UI = ROOT / 'app/src/main/java/uk/co/traynor/privategallery/ui'
class EvidenceDashboardContract(unittest.TestCase):
    def test_dashboard_exposes_import_and_search_without_provider_egress(self):
        source = (UI / 'EvidenceDashboardPresentation.kt').read_text() if (UI / 'EvidenceDashboardPresentation.kt').exists() else ''
        self.assertIn('"Import Evidence"', source)
        self.assertIn('"evidence-dashboard-search"', source)
        self.assertNotIn('EvidenceEgress', source)
    def test_information_is_a_separate_destination(self):
        source = (UI / 'EvidenceLocker.kt').read_text()
        self.assertIn('"Information" ->', source)
        home = source.split('"Home" -> {', 1)[1].split('"Information" -> {', 1)[0]
        self.assertNotIn('A hash verifies matching bytes', home)
        self.assertIn('EvidenceDashboardOverview(', home)
