import unittest,check_avd_target
class AvdTargetChecks(unittest.TestCase):
 def test_correct_dotted_target(self):self.assertEqual('android-37.0',check_avd_target.validate('path=/tmp/test.avd\ntarget=android-37.0\n'))
 def test_old_tools_target_rejected(self):
  with self.assertRaises(ValueError):check_avd_target.validate('target=android-0\n')
 def test_wrong_api_rejected(self):
  with self.assertRaises(ValueError):check_avd_target.validate('target=android-36\n')
