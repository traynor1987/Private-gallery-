import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[2]

class CrossVaultPresentationTest(unittest.TestCase):
    def test_primary_authentication_uses_existing_keypad(self):
        for name in ('HiddenRestoreDialog.kt', 'PrimaryCopyDialog.kt'):
            with self.subTest(name=name):
                source = (ROOT / 'app/src/main/java/uk/co/traynor/privategallery/ui' / name).read_text()
                self.assertIn('PasscodeKeypad(', source)
                self.assertNotIn('PinPage(', source)
                self.assertIn('access.authenticate(input)', source)
                self.assertIn("input.fill('\\u0000')", source)
