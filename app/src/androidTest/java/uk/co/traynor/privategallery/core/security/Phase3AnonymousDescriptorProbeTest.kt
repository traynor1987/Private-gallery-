package uk.co.traynor.privategallery.core.security

import android.os.Build
import android.os.Process
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.FileDescriptor
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Public ciphertext/OS feasibility on API30+ x86_64 only. No production staging
 * is enabled, no owner data is read, and descriptor reclamation is not disk wiping. */
@RunWith(AndroidJUnit4::class)
class Phase3AnonymousDescriptorProbeTest {
    @Test fun unnamedDescriptorIsPrivateRegularCloseOnExecAndRetiresItsExactOriginal() {
        val flags=probeFlags()
        val directory=InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
        val fixture=Phase3BoundedCipherProbeTest.SEALED
        val authority=authority();val operation=authority.operationOrNull(setOf(PrimaryScope.WRITE))!!
        val guard=ScopedIoGuard(operation,PrimaryScope.WRITE);val closed=CountDownLatch(1)
        var ownedForCleanup:OwnedResource<ReservedValue<Any>>?=null
        lateinit var descriptor:OwnedNativeUse<FileDescriptor>
        try {
            val owned=guard.createOwned(OwnedResourceManifest.io("fixture","descriptor")) {
                val root=create("fixture",{_:Any->}) {Any()}
                descriptor=guardedNative("descriptor",guard,{fd:FileDescriptor->Os.close(fd);closed.countDown()}) {
                    Os.open(directory.absolutePath,flags,OsConstants.S_IRUSR or OsConstants.S_IWUSR)
                }
                root
            }
            ownedForCleanup=owned
            assertEquals(fixture.size,descriptor.useInt {fd->
                val before=Os.fstat(fd)
                assertTrue(OsConstants.S_ISREG(before.st_mode));assertEquals(0L,before.st_nlink)
                assertEquals(Process.myUid(),before.st_uid);assertEquals(0L,before.st_size)
                assertEquals(0x180,before.st_mode and 0x1ff)
                assertEquals(OsConstants.FD_CLOEXEC,Os.fcntlInt(fd,OsConstants.F_GETFD,0) and OsConstants.FD_CLOEXEC)
                var offset=0
                while(offset<fixture.size) {
                    val count=Os.pwrite(fd,fixture,offset,fixture.size-offset,offset.toLong())
                    check(count in 1..(fixture.size-offset));offset+=count
                }
                val after=Os.fstat(fd);assertEquals(0L,after.st_nlink);assertEquals(fixture.size.toLong(),after.st_size)
                val restored=ByteArray(fixture.size);offset=0
                while(offset<restored.size) {
                    val count=Os.pread(fd,restored,offset,restored.size-offset,offset.toLong())
                    check(count in 1..(restored.size-offset));offset+=count
                }
                assertArrayEquals(fixture,restored);offset
            })
            guard.check();assertEquals(1L,closed.count);assertFalse(owned.retirement.isComplete)
            owned.close();assertTrue(owned.retirement.await(5,TimeUnit.SECONDS));assertEquals(0L,closed.count)
            guard.check()
            var lateInvoked=false
            assertThrows(IllegalStateException::class.java){descriptor.useInt {lateInvoked=true;0}}
            assertFalse(lateInvoked)
        }finally{operation.close();ownedForCleanup?.close();authority.revoke()}
    }

    @Test fun missingDirectoryDeniesWithoutNamedFallbackAndCompletesOnlyNoResourceRetirement() {
        val flags=probeFlags()
        val directory=File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
            "phase3-missing-"+UUID.randomUUID())
        assertFalse(directory.exists())
        val authority=authority();val operation=authority.operationOrNull(setOf(PrimaryScope.WRITE))!!
        val guard=ScopedIoGuard(operation,PrimaryScope.WRITE)
        var original:ReleaseReservation?=null;var nativeDisposalInvoked=false;var resultReturned=false
        try {
            val failure=assertThrows(ErrnoException::class.java) {
                guard.createOwned(OwnedResourceManifest.io("fixture","descriptor")) {
                    original=this.original
                    val root=create("fixture",{_:Any->}) {Any()}
                    guardedNative("descriptor",guard,{fd:FileDescriptor->nativeDisposalInvoked=true;Os.close(fd)}) {
                        Os.open(directory.absolutePath,flags,OsConstants.S_IRUSR or OsConstants.S_IWUSR)
                    }
                    resultReturned=true;root
                }
            }
            assertEquals(OsConstants.ENOENT,failure.errno)
            assertFalse(resultReturned);assertFalse(nativeDisposalInvoked);assertFalse(directory.exists())
            assertTrue(checkNotNull(original).terminalRetirement.await(5,TimeUnit.SECONDS))
            assertFalse(checkNotNull(original).failed);guard.check()
        }finally{operation.close();authority.revoke()}
    }

    private fun authority():PrimarySessionAuthority {
        val result=PrimarySessionAuthority {0}
        for(name in listOf("ioReleasePool","presentationReleasePool")) {
            result.javaClass.getDeclaredField(name).apply{isAccessible=true}.set(result,ReleasePool(16))
        }
        result.open(ByteArray(32){11});return result
    }
    private fun probeFlags():Int {
        // SDK36 exposes neither O_TMPFILE nor O_DIRECTORY. No hidden-API lookup.
        // Linux v6.12 asm-generic fcntl: 020000000 | 00200000 = 0x410000.
        // This fixture deliberately refuses other ABIs; their constants/support
        // and API26-29 public descriptor operations require separate proof.
        check(Build.VERSION.SDK_INT>=30)
        check(Process.is64Bit()&&System.getProperty("os.arch")=="x86_64")
        return 0x410000 or OsConstants.O_RDWR or OsConstants.O_EXCL or OsConstants.O_CLOEXEC
    }
}
