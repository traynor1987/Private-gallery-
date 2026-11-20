import importlib.util
from pathlib import Path
import unittest
from unittest.mock import Mock, call, patch
import xml.etree.ElementTree as ET

path = Path(__file__).resolve().parents[2]/'docs/build85/verify_live_build84.py'
spec = importlib.util.spec_from_file_location('live_update_driver', path)
driver = importlib.util.module_from_spec(spec)
spec.loader.exec_module(driver)


class RealUpdateActions(unittest.TestCase):
    def test_available_version_with_long_notes_scrolls_to_actual_download_action(self):
        summary = ET.fromstring('<hierarchy><node text="Installed"/><node text="1.7.41 · Build 87 available"/><node text="Long release notes"/></hierarchy>')
        action = ET.fromstring('<hierarchy><node text="Download update" enabled="true"/></hierarchy>')
        with patch.object(driver, 'tree', side_effect=[summary, action]), patch.object(driver, 'scroll') as scroll:
            self.assertIs(action, driver.reveal_action('Download update'))
            scroll.assert_called_once_with(summary)

    def test_missing_or_disabled_action_fails_instead_of_tapping_unobserved_coordinates(self):
        page = ET.fromstring('<hierarchy><node text="Download update" enabled="false"/></hierarchy>')
        with patch.object(driver, 'tree', return_value=page), patch.object(driver, 'scroll') as scroll, patch.object(driver, 'tap') as tap:
            with self.assertRaisesRegex(RuntimeError, 'real update action'):
                driver.reveal_action('Download update')
            self.assertEqual(12, scroll.call_count)
            tap.assert_not_called()

    def test_returns_to_installed_summary_after_scrolling_to_controls(self):
        controls = ET.fromstring('<hierarchy><node text="Check for updates"/></hierarchy>')
        summary = ET.fromstring('<hierarchy><node text="Installed"/><node text="1.7.41 · Build 87"/><node text="1.7.41 · Up to date"/></hierarchy>')
        with patch.object(driver, 'tree', side_effect=[controls, summary]), patch.object(driver, 'scroll') as scroll:
            self.assertIs(summary, driver.updates_summary())
            scroll.assert_called_once_with(controls, reverse=True)

    def test_recovery_waits_for_acknowledgement_and_the_enabled_compose_button(self):
        def page(checked, enabled):
            return ET.fromstring(f'<hierarchy><node text="{synthetic_key}"/><node checkable="true" checked="{checked}"/>'
                                 '<node class="android.widget.EditText"/><node text="Re-enter your saved recovery key"/>'
                                 f'<node clickable="true" enabled="{enabled}"><node text="Continue" enabled="true"/></node>'
                                 '</hierarchy>')

        synthetic_key = 'A' * 43
        before, checked, ready = page('false', 'false'), page('true', 'false'), page('true', 'true')
        destination = ET.fromstring('<hierarchy><node text="Enter NovaOS"/></hierarchy>')
        self.assertIsNone(driver.recovery_continue(checked))
        expected = [(checked, 'checked recovery acknowledgement'), (ready, 'enabled recovery Continue'),
                    (destination, 'completed recovery confirmation')]

        def observe(predicate, label):
            next_page, expected_label = expected.pop(0)
            self.assertEqual(expected_label, label)
            self.assertTrue(predicate(next_page))
            return next_page

        actions = Mock()
        with patch.object(driver, 'wait_for', side_effect=observe), patch.object(driver, 'tap', actions.tap), \
                patch.object(driver, 'enter_recovery_key', actions.enter):
            driver.complete_recovery_setup(before)
        self.assertEqual([], expected)
        self.assertEqual(actions.mock_calls, [
            call.tap(driver.recovery_checkbox(before)),
            call.enter(driver.fields(checked)[0], synthetic_key),
            call.tap(driver.recovery_continue(ready)),
        ])

    def test_recovery_key_uses_ordered_key_events_on_the_focused_field(self):
        field = ET.fromstring('<node class="android.widget.EditText" focused="true" />')
        page = ET.fromstring('<hierarchy><node class="android.widget.EditText" focused="true" /></hierarchy>')

        def observe(predicate, label):
            self.assertEqual('focused recovery confirmation field', label)
            self.assertTrue(predicate(page))
            return page

        with patch.object(driver, 'wait_for', side_effect=observe), patch.object(driver, 'tap') as tap, \
                patch.object(driver, 'adb') as adb, patch.object(driver.time, 'sleep'):
            driver.enter_recovery_key(field, 'aZ-_0')
        tap.assert_called_once_with(field)
        expected = [call('shell', 'input', '-d', '0', 'text', character) for character in 'aZ-_0']
        expected.append(call('shell', 'input', 'keyevent', 'KEYCODE_BACK'))
        self.assertEqual(expected, adb.call_args_list)


if __name__ == '__main__':
    unittest.main()
