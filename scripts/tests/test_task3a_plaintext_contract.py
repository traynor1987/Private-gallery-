from pathlib import Path
import re
import unittest
ROOT = Path(__file__).resolve().parents[2]
BASE = ROOT / 'app/src/main/java/uk/co/traynor/privategallery'
class Task3aPlaintextContract(unittest.TestCase):
    def test_repository_returns_ownership_for_every_decrypted_read(self):
        source = (BASE/'core/vault/AndroidVaultRepository.kt').read_text()
        signatures = re.findall(r'fun (?:readForViewing|readVideoForViewing|readForEditingPreview|readForEditing)\([^\n]+', source)
        self.assertEqual(6, len(signatures))
        self.assertTrue(all(': OwnedPlaintext' in s for s in signatures), signatures)
        self.assertNotIn('class SessionPlaintext', source)
        self.assertIn('operation.createOwnedPlaintext(read)', source)
    def test_consumers_never_receive_unowned_repository_arrays(self):
        for path in ['MainActivity.kt','ui/PhotoEditor.kt','ui/FullscreenMediaViewer.kt']:
            self.assertFalse('Result<ByteArray>' in (BASE/path).read_text(), path)
    def test_video_fallback_does_not_retain_raw_source(self):
        source = (BASE/'ui/FullscreenMediaViewer.kt').read_text()
        self.assertFalse('ByteArrayDataSource(it)' in source)
    def test_retriever_does_not_retain_decrypted_array(self):
        source = (BASE/'MainActivity.kt').read_text()
        self.assertFalse('class ByteArrayMediaDataSource(private val bytes: ByteArray)' in source)
    def test_scoped_decrypt_uses_funded_format_sniff(self):
        source = (BASE/'core/vault/EncryptedPayloadStore.kt').read_text().split('private fun decryptExactly',1)[1]
        self.assertIn('ChunkedVaultVideoStore.isChunked(stored.file, it)', source)
        source = (BASE/'core/vault/ChunkedVaultVideoStore.kt').read_text()
        self.assertIn('fun isChunked(file: File, guard: ScopedIoGuard)', source)
