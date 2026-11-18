import importlib.util
from pathlib import Path
import unittest
from unittest.mock import patch
import xml.etree.ElementTree as ET

path = Path(__file__).resolve().parents[2]/'docs/build85/verify_live_build84.py'
spec = importlib.util.spec_from_file_location('live_update_driver', path)
driver = importlib.util.module_from_spec(spec)
spec.loader.exec_module(driver)


class RealUpdateActions(unittest.TestCase):
    def test_available_version_with_long_notes_scrolls_to_actual_download_action(self):
        summary = ET.fromstring('<hierarchy><node text="Installed"/><node text="1.7.40 · Build 86 available"/><node text="Long release notes"/></hierarchy>')
        action = ET.fromstring('<hierarchy><node text="Download &amp; Install" enabled="true"/></hierarchy>')
        with patch.object(driver, 'tree', side_effect=[summary, action]), patch.object(driver, 'scroll') as scroll:
            self.assertIs(action, driver.reveal_action('Download & Install'))
            scroll.assert_called_once_with(summary)

    def test_missing_or_disabled_action_fails_instead_of_tapping_unobserved_coordinates(self):
        page = ET.fromstring('<hierarchy><node text="Download &amp; Install" enabled="false"/></hierarchy>')
        with patch.object(driver, 'tree', return_value=page), patch.object(driver, 'scroll') as scroll, patch.object(driver, 'tap') as tap:
            with self.assertRaisesRegex(RuntimeError, 'real update action'):
                driver.reveal_action('Download & Install')
            self.assertEqual(12, scroll.call_count)
            tap.assert_not_called()

    def test_returns_to_installed_summary_after_scrolling_to_controls(self):
        controls = ET.fromstring('<hierarchy><node text="Check for updates"/></hierarchy>')
        summary = ET.fromstring('<hierarchy><node text="Installed"/><node text="1.7.40 · Build 86"/><node text="1.7.40 · Up to date"/></hierarchy>')
        with patch.object(driver, 'tree', side_effect=[controls, summary]), patch.object(driver, 'scroll') as scroll:
            self.assertIs(summary, driver.updates_summary())
            scroll.assert_called_once_with(controls, reverse=True)


if __name__ == '__main__':
    unittest.main()
