package uk.co.traynor.privategallery.core.domain

import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.nio.file.DirectoryStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SecureDirectoryStream
import java.util.IdentityHashMap
import org.junit.Assert.*
import org.junit.Test

class SecondaryPreparedMoveTest {
    @Test fun preparedRenameHasNoDirectoryAcquisitionVerificationOrCleanupUnderAdmissionGate() {
        val dir = Files.createTempDirectory("prepared-domain-move")
        try {
            val root = Files.createDirectory(dir.resolve("domain-store"))
            val temporary = Files.createDirectory(root.resolve("temporary"))
            val source = temporary.resolve("pending")
            val target = root.resolve("selected")
            Files.write(source, byteArrayOf(1, 2, 3))
            val gate = Any()
            val events = mutableListOf<String>()
            val actuals = IdentityHashMap<Any, Any>()
            fun tracked(actual: SecureDirectoryStream<Path>): SecureDirectoryStream<Path> {
                val proxy = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(SecureDirectoryStream::class.java)) { _, method, args ->
                    if (Thread.holdsLock(gate)) events.add(method.name)
                    val arguments = args?.copyOf()?.map { actuals[it] ?: it }?.toTypedArray() ?: emptyArray()
                    try {
                        val value = method.invoke(actual, *arguments)
                        if (value is SecureDirectoryStream<*>) {
                            @Suppress("UNCHECKED_CAST")
                            tracked(value as SecureDirectoryStream<Path>)
                        } else value
                    } catch (failure: InvocationTargetException) { throw failure.cause ?: failure }
                }
                actuals[proxy] = actual
                @Suppress("UNCHECKED_CAST")
                return proxy as SecureDirectoryStream<Path>
            }
            val expected = listOf(dir, root, temporary).associateWith { DomainInventory.stat(it).key }
            val pinned = SecondaryDirectoryHandles.PinnedDirectories(dir, expected) { path ->
                val actual: DirectoryStream<Path> = Files.newDirectoryStream(path)
                assertTrue(actual is SecureDirectoryStream<*>)
                @Suppress("UNCHECKED_CAST")
                tracked(actual as SecureDirectoryStream<Path>)
            }
            val move = try {
                pinned.open(temporary)
                pinned.open(root)
                pinned.prepareAtomicReplace(source, target).also { prepared ->
                    synchronized(gate) { prepared.commit() }
                    assertArrayEquals(byteArrayOf(1, 2, 3), Files.readAllBytes(target))
                    assertFalse(Files.exists(source))
                    assertThrows(IllegalStateException::class.java) { synchronized(gate) { prepared.commit() } }
                }
            } finally { pinned.close() }
            assertEquals(listOf("move"), events)
            assertThrows(IllegalStateException::class.java) { move.commit() }
        } finally { dir.toFile().deleteRecursively() }
    }
}
