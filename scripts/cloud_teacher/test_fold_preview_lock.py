import os
from pathlib import Path
from tempfile import TemporaryDirectory
from unittest import TestCase

from fold_preview_lock import acquire


class FoldPreviewLockTest(TestCase):
    def setUp(self):
        temporary = TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.directory = Path(temporary.name)
        self.lock = self.directory / 'preview.lock'

    def test_existing_lock_is_exclusive_and_retains_contents(self):
        self.lock.write_text('retained')
        first = acquire(self.lock)
        try:
            with self.assertRaises(BlockingIOError):
                acquire(self.lock)
            self.assertEqual(self.lock.read_text(), 'retained')
        finally:
            os.close(first)
        os.close(acquire(self.lock))

    def test_symlink_preserves_the_target(self):
        target = self.directory / 'protected'
        target.write_text('retained')
        self.lock.symlink_to(target)
        with self.assertRaises(OSError):
            acquire(self.lock)
        self.assertTrue(self.lock.is_symlink())
        self.assertEqual(target.read_text(), 'retained')

    def test_hardlink_preserves_the_target(self):
        target = self.directory / 'protected'
        target.write_text('retained')
        os.link(target, self.lock)
        with self.assertRaises(ValueError):
            acquire(self.lock)
        self.assertEqual(target.read_text(), 'retained')

    def test_group_writable_lock_is_refused(self):
        self.lock.touch()
        self.lock.chmod(0o660)
        with self.assertRaises(ValueError):
            acquire(self.lock)
