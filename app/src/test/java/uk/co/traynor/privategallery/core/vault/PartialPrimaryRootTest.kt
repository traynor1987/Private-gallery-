package uk.co.traynor.privategallery.core.vault

import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class PartialPrimaryRootTest {
    @Test fun `unrecognized partial root cannot manufacture empty index`() {
        for (path in listOf("vault-index.new", "payloads/unknown.partial", "unexpected-ledger")) {
            val root = Files.createTempDirectory("partial-primary").toFile()
            try {
                val material = root.resolve(path).apply { parentFile!!.mkdirs(); writeBytes(byteArrayOf(1,2,3)) }
                assertThrows(IllegalStateException::class.java) { EncryptedIndexStore(root).loadSnapshot(ByteArray(32)) }
                assertArrayEquals(byteArrayOf(1,2,3), material.readBytes())
                assertFalse(root.resolve("vault-index.enc").exists())
            } finally { root.deleteRecursively() }
        }
    }
}
