package uk.co.traynor.privategallery.core.domain

import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SecureDirectoryStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Test

/** Real directory descriptors, with controlled Native close boundaries. */
class SecondaryDirectoryRetirementTest {
    private class Fixture(private val beforeCall: (Path, String) -> Unit = { _, _ -> }, private val beforeClose: (Path) -> Unit = {}) : AutoCloseable {
        val anchor = Files.createTempDirectory("directory-retirement")
        val root = Files.createDirectory(anchor.resolve("domain-store"))
        val child = Files.createDirectory(root.resolve("temporary"))
        val late = Files.createDirectory(root.resolve("late"))
        val closes = java.util.concurrent.ConcurrentHashMap<Path, Int>()
        private val bindings = java.util.Collections.synchronizedMap(java.util.IdentityHashMap<Any, Any>())
        private val actuals = java.util.concurrent.CopyOnWriteArrayList<SecureDirectoryStream<Path>>()
        private fun track(path: Path, actual: SecureDirectoryStream<Path>): SecureDirectoryStream<Path> {
            actuals.add(actual)
            @Suppress("UNCHECKED_CAST")
            val proxy = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(SecureDirectoryStream::class.java)) { _, method, args ->
                beforeCall(path, method.name)
                if (method.name == "close") { closes.merge(path, 1, Int::plus); beforeClose(path) }
                try {
                    val arguments = args?.map { bindings[it] ?: it }?.toTypedArray() ?: emptyArray()
                    val result = method.invoke(actual, *arguments)
                    if (method.name == "newDirectoryStream") {
                        @Suppress("UNCHECKED_CAST")
                        track(path.resolve(args!![0] as Path), result as SecureDirectoryStream<Path>)
                    } else result
                } catch (failure: InvocationTargetException) { throw failure.cause ?: failure }
            } as SecureDirectoryStream<Path>
            bindings[proxy] = actual
            return proxy
        }
        val pinned = SecondaryDirectoryHandles.PinnedDirectories(anchor,
            listOf(anchor, root, child, late).associateWith { DomainInventory.stat(it).key }) {
                @Suppress("UNCHECKED_CAST")
                track(it, Files.newDirectoryStream(it) as SecureDirectoryStream<Path>)
            }.also { it.open(child) }
        override fun close() {
            // Test retains every real descriptor even when the simulated Native close fails.
            actuals.asReversed().forEach { it.close() }
            anchor.toFile().deleteRecursively()
        }
    }

    @Test fun failedChildCannotSkipIndependentClosesOrBecomeSuccessfulOnRetry() {
        val failed = java.io.IOException("held Native cleanup failure")
        Fixture { if (it.fileName.toString() == "temporary") throw failed }.use { fixture ->
            assertSame(failed, assertThrows(java.io.IOException::class.java) { fixture.pinned.close() })
            assertEquals("every independently opened directory must receive its close", 3, fixture.closes.size)
            assertSame(failed, assertThrows(java.io.IOException::class.java) { fixture.pinned.close() })
            assertTrue("failed cleanup outcome is sticky, not a retry acknowledgement", fixture.closes.values.all { it == 1 })
        }
    }

    @Test fun duplicateCloseCannotReturnBeforeTheActualOriginalNativeClose() {
        val entered = CountDownLatch(1); val release = CountDownLatch(1)
        val firstDone = CountDownLatch(1); val secondDone = CountDownLatch(1)
        val failure = AtomicReference<Throwable>()
        Fixture { if (it.fileName.toString() == "temporary") { entered.countDown(); check(release.await(10, TimeUnit.SECONDS)) } }.use { fixture ->
            val first = Thread { try { fixture.pinned.close() } catch (e: Throwable) { failure.compareAndSet(null, e) } finally { firstDone.countDown() } }
            val second = Thread { try { fixture.pinned.close() } catch (e: Throwable) { failure.compareAndSet(null, e) } finally { secondDone.countDown() } }
            first.start()
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS)); second.start()
                assertFalse("a duplicate is not retirement of the held original", secondDone.await(300, TimeUnit.MILLISECONDS))
                assertEquals(1L, firstDone.count)
                assertThrows(IllegalStateException::class.java) { fixture.pinned.open(fixture.child) }
            } finally { release.countDown(); first.join(5000); if (second.state != Thread.State.NEW) second.join(5000) }
            assertFalse(first.isAlive); assertFalse(second.isAlive); assertNull(failure.get())
            assertEquals(0L, firstDone.count); assertEquals(0L, secondDone.count)
            assertEquals(3, fixture.closes.size); assertTrue(fixture.closes.values.all { it == 1 })
        }
    }

    @Test fun retiredScopeRejectsCachedAndNewNativeAccess() {
        Fixture().use { fixture ->
            fixture.pinned.close()
            assertThrows(IllegalStateException::class.java) { fixture.pinned.open(fixture.child) }
            assertThrows(IllegalStateException::class.java) { fixture.pinned.open(fixture.late) }
            assertThrows(IllegalStateException::class.java) { fixture.pinned.parent(fixture.child.resolve("file")) }
            assertThrows(IllegalStateException::class.java) { fixture.pinned.writeNew(fixture.child.resolve("file"), byteArrayOf(1)) }
            assertThrows(IllegalStateException::class.java) { fixture.pinned.checkBindings() }
            assertThrows(IllegalStateException::class.java) { fixture.pinned.syncDirectory(fixture.child) }
            assertThrows(IllegalStateException::class.java) { fixture.pinned.remove(fixture.child, true) }
            assertThrows(IllegalStateException::class.java) { fixture.pinned.mkdir(fixture.root.resolve("new")) }
            assertThrows(IllegalStateException::class.java) { fixture.pinned.prepareAtomicReplace(fixture.child.resolve("file"), fixture.root.resolve("selected")) }
            assertThrows(IllegalStateException::class.java) { fixture.pinned.atomicReplace(fixture.child.resolve("file"), fixture.root.resolve("selected")) }
            assertFalse(Files.exists(fixture.child.resolve("file"))); assertFalse(Files.exists(fixture.root.resolve("new")))
            assertTrue(Files.isDirectory(fixture.child)); assertTrue(Files.isDirectory(fixture.late))
        }
    }
    @Test fun closeWaitsForTheAdmittedNativeOpenAndRetiresItsLateDescriptor() {
        val held = java.util.concurrent.atomic.AtomicBoolean(false)
        val entered = CountDownLatch(1); val release = CountDownLatch(1); val done = CountDownLatch(1)
        val failure = AtomicReference<Throwable>()
        Fixture(beforeCall = { _, method ->
            if (held.get() && method == "newDirectoryStream") { entered.countDown(); check(release.await(10, TimeUnit.SECONDS)) }
        }).use { fixture ->
            held.set(true)
            val reader = Thread { try { fixture.pinned.open(fixture.late) } catch (e: Throwable) { failure.compareAndSet(null, e) } }
            val closer = Thread { try { fixture.pinned.close() } catch (e: Throwable) { failure.compareAndSet(null, e) } finally { done.countDown() } }
            reader.start()
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS)); closer.start()
                assertFalse("Native acquisition has not returned", done.await(300, TimeUnit.MILLISECONDS))
                assertTrue("no descriptor may close while the actual borrower is held", fixture.closes.isEmpty())
            } finally { release.countDown(); reader.join(5000); if (closer.state != Thread.State.NEW) closer.join(5000) }
            assertFalse(reader.isAlive); assertFalse(closer.isAlive); assertNull(failure.get())
            assertEquals(4, fixture.closes.size); assertTrue(fixture.closes.values.all { it == 1 })
        }
    }

    @Test fun nativeCloseSelfReentryCannotAcknowledgeOrDeadlockItsOriginal() {
        lateinit var fixture: Fixture
        var rejected = false
        var entered = false
        fixture = Fixture { path ->
            if (path.fileName.toString() == "temporary" && !entered) {
                entered = true
                assertThrows(IllegalStateException::class.java) { fixture.pinned.close() }; rejected = true
            }
        }
        fixture.use {
            fixture.pinned.close(); fixture.pinned.close()
            assertTrue(rejected); assertEquals(3, fixture.closes.size)
            assertTrue(fixture.closes.values.all { it == 1 })
        }
    }

    @Test fun interruptedFirstCloserStillWaitsForBorrowerAndClosesEveryDescriptor() {
        val held = java.util.concurrent.atomic.AtomicBoolean(false)
        val entered = CountDownLatch(1); val release = CountDownLatch(1); val done = CountDownLatch(1)
        val failure = AtomicReference<Throwable>(); val restored = java.util.concurrent.atomic.AtomicBoolean(false)
        Fixture(beforeCall = { _, method ->
            if (held.get() && method == "newDirectoryStream") { entered.countDown(); check(release.await(10, TimeUnit.SECONDS)) }
        }).use { fixture ->
            held.set(true)
            val reader = Thread { try { fixture.pinned.open(fixture.late) } catch (e: Throwable) { failure.compareAndSet(null, e) } }
            val closer = Thread { try { fixture.pinned.close() } catch (e: Throwable) { failure.compareAndSet(null, e) } finally { restored.set(Thread.currentThread().isInterrupted); done.countDown() } }
            reader.start()
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS)); closer.start()
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
                while (closer.state != Thread.State.WAITING && done.count != 0L && System.nanoTime() < deadline) Thread.yield()
                assertEquals(Thread.State.WAITING, closer.state)
                closer.interrupt()
                assertFalse("interruption cannot abandon admitted Native cleanup", done.await(300, TimeUnit.MILLISECONDS))
                assertTrue(fixture.closes.isEmpty())
            } finally { release.countDown(); reader.join(5000); if (closer.state != Thread.State.NEW) closer.join(5000) }
            assertFalse(reader.isAlive); assertFalse(closer.isAlive); assertNull(failure.get()); assertTrue(restored.get())
            assertEquals(4, fixture.closes.size); assertTrue(fixture.closes.values.all { it == 1 })
            fixture.pinned.close()
        }
    }

    @Test fun multipleNativeCloseFailuresPreserveTheOriginalAndRetireOtherSiblings() {
        val first = java.io.IOException("first close failure"); val second = java.io.IOException("second close failure")
        Fixture { path ->
            when (path.fileName.toString()) { "temporary" -> throw first; "domain-store" -> throw second }
        }.use { fixture ->
            assertSame(first, assertThrows(java.io.IOException::class.java) { fixture.pinned.close() })
            assertEquals(3, fixture.closes.size); assertArrayEquals(arrayOf(second), first.suppressed)
            assertSame(first, assertThrows(java.io.IOException::class.java) { fixture.pinned.close() })
            assertTrue(fixture.closes.values.all { it == 1 })
        }
    }

    @Test fun preparedPublicationDoesNotAcquireTheLowerRankedLifetimeGate() {
        for (throughFacade in listOf(false, true)) Fixture().use { fixture ->
            val source = fixture.child.resolve("pending"); val target = fixture.root.resolve("selected")
            Files.write(source, byteArrayOf(1, 2, 3))
            val authorization = Any()
            // Test-only access to an app class monitor, never Android/private Native APIs.
            val field = fixture.pinned.javaClass.getDeclaredField("lifetimeGate").apply { isAccessible = true }
            val metadata = field.get(fixture.pinned)
            val prepared = CountDownLatch(1); val publish = CountDownLatch(1); val published = CountDownLatch(1)
            val held = CountDownLatch(1); val release = CountDownLatch(1)
            val failure = AtomicReference<Throwable>()
            val worker = Thread {
                try { fixture.pinned.withAccess {
                    val move = fixture.pinned.prepareAtomicReplace(source, target)
                    prepared.countDown(); check(publish.await(5, TimeUnit.SECONDS))
                    synchronized(authorization) {
                        if (throughFacade) fixture.pinned.atomicReplace(source, target) else move.commit()
                        published.countDown()
                    }
                } } catch (e: Throwable) { failure.compareAndSet(null, e) }
            }
            val holder = Thread { synchronized(metadata) { held.countDown(); check(release.await(10, TimeUnit.SECONDS)) } }
            worker.start()
            try {
                assertTrue(prepared.await(5, TimeUnit.SECONDS)); holder.start()
                assertTrue(held.await(5, TimeUnit.SECONDS)); publish.countDown()
                assertTrue("publication must not wait for a lower ranked reader monitor", published.await(2, TimeUnit.SECONDS))
            } finally { release.countDown(); publish.countDown(); worker.join(5000); if (holder.state != Thread.State.NEW) holder.join(5000) }
            assertFalse(worker.isAlive); assertFalse(holder.isAlive); assertNull(failure.get())
            assertArrayEquals(byteArrayOf(1, 2, 3), Files.readAllBytes(target)); assertFalse(Files.exists(source))
            fixture.pinned.close()
        }
    }

}
