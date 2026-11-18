import pathlib
import subprocess
import tempfile
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[2]
SCAN = ROOT / 'scripts/no_secret_scan.sh'
CERT = 'docs/release1711/code80-transfer-recipient.pem'

class SecretScan86(unittest.TestCase):
    def run_scan(self, entries):
        with tempfile.TemporaryDirectory() as tmp:
            folder = pathlib.Path(tmp)
            subprocess.run(['git', 'init', '-q', tmp], check=True)
            for name, content in entries.items():
                file = folder / name
                file.parent.mkdir(parents=True, exist_ok=True)
                file.write_bytes(content)
            subprocess.run(['git', '-C', tmp, 'add', '.'], check=True)
            return subprocess.run(['bash', str(SCAN)], cwd=folder, capture_output=True, text=True)
    def test_exact_public_certificate_is_valid_but_replacement_is_rejected(self):
        self.assertEqual(0, self.run_scan({CERT: (ROOT / CERT).read_bytes()}).returncode)
        self.assertNotEqual(0, self.run_scan({CERT: b'altered certificate'}).returncode)
    def test_other_sensitive_filenames_are_always_rejected(self):
        for name in ('signing.jks', 'extra.pem', 'secrets/owner.key', 'local.properties', '.env'):
            with self.subTest(name=name):
                self.assertNotEqual(0, self.run_scan({name: b'synthetic fixture'}).returncode)
    def test_source_private_key_marker_is_rejected(self):
        marker = b'-----' + b'BEGIN ' + b'PRIVATE KEY' + b'-----'
        self.assertNotEqual(0, self.run_scan({'source.txt': marker}).returncode)
    def test_large_tracked_inventory_cannot_hide_a_sensitive_filename(self):
        entries = {f'long-path/{i:05}/ordinary-file-with-long-path.txt': b'ok' for i in range(2000)}
        entries['first.key'] = b'synthetic fixture'
        self.assertNotEqual(0, self.run_scan(entries).returncode)

if __name__ == '__main__': unittest.main()
