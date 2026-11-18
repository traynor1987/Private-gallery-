import subprocess
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import rehearse_in_app_update85 as rehearsal


class InstallerHandoff(unittest.TestCase):
    def test_null_root_without_dump_file_retries_with_fresh_hierarchy(self):
        xml = '<hierarchy><node package="com.android.packageinstaller" text="Update" enabled="true" bounds="[10,20][80,60]" /></hierarchy>'
        with tempfile.TemporaryDirectory() as directory, \
             patch.object(rehearsal, 'adb', side_effect=[
                 '', 'ERROR: null root node returned by UiTestAutomationBridge.',
                 subprocess.CalledProcessError(1, ['adb', 'shell', 'cat']),
                 '', 'UI hierarchy dumped', xml,
             ]) as adb, patch.object(rehearsal.time, 'sleep'):
            self.assertEqual(xml, rehearsal.installer_tree(Path(directory)))
            calls = [call.args for call in adb.call_args_list]
            self.assertEqual(2, calls.count(('shell', 'rm', '-f', '/sdcard/nova85-installer.xml')))
            self.assertIn('CalledProcessError', (Path(directory)/'in-app-installer-wait.txt').read_text())
            self.assertFalse(any('tap' in call for call in calls))

    def test_malformed_dump_is_rejected_before_fresh_observation(self):
        with tempfile.TemporaryDirectory() as directory, \
             patch.object(rehearsal, 'adb', side_effect=['', '', 'incomplete xml', '', '', '<hierarchy />']) as adb, \
             patch.object(rehearsal.time, 'sleep'):
            self.assertEqual('<hierarchy />', rehearsal.installer_tree(Path(directory)))
            self.assertEqual(6, adb.call_count)

    def test_unavailable_root_fails_closed_and_preserves_diagnostics(self):
        with tempfile.TemporaryDirectory() as directory, \
             patch.object(rehearsal.time, 'monotonic', side_effect=[0, 0, 61]), \
             patch.object(rehearsal.time, 'sleep'), \
             patch.object(rehearsal, 'adb', side_effect=['', 'ERROR: null root', subprocess.TimeoutExpired('cat', 35), 'window', 'activity']) as adb:
            output = Path(directory)
            with self.assertRaisesRegex(RuntimeError, 'fresh installer hierarchy'):
                rehearsal.installer_tree(output)
            self.assertEqual('window', (output/'in-app-installer-window.txt').read_text())
            self.assertFalse(any('tap' in call.args for call in adb.call_args_list))


if __name__ == '__main__':
    unittest.main()
