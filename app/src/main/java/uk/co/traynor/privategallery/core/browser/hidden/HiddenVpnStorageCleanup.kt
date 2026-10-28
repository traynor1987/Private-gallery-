package uk.co.traynor.privategallery.core.browser.hidden

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.file.Files
import java.nio.file.SimpleFileVisitor
import java.nio.file.FileVisitResult
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.util.UUID
import java.util.concurrent.Executors

/** Owns whole dedicated roots, never guesses WebView/provider/Primary directory names. */
internal object HiddenVpnStorageCleanup {
    private const val ROOT="hidden-vpn-webview-v1"
    private const val MARKER="private-gallery-hidden-vpn-v1"
    private val executor=Executors.newSingleThreadExecutor()
    private val gate=Any()
    class Lease internal constructor(val id:String,val data:File,val cache:File,private val channel:FileChannel,private val lock:FileLock):Closeable {
        override fun close() {runCatching {lock.release()};runCatching {channel.close()}}
    }
    private fun root(context:Context)=File(context.noBackupFilesDir.canonicalFile,ROOT)
    private fun cacheRoot(context:Context)=File(context.cacheDir.canonicalFile,ROOT)
    private fun id(value:String) {require(UUID.fromString(value).toString()==value)}
    fun prepare(context:Context,value:String):Lease = synchronized(gate) {
        id(value)
        val parent=root(context);check(parent.mkdirs() || parent.isDirectory);check(parent.canonicalFile==parent.absoluteFile)
        val folder=File(parent,value);check(folder.mkdir())
        val cache=File(cacheRoot(context),value)
        check(cache.mkdirs() || cache.isDirectory);check(cache.canonicalFile==cache.absoluteFile)
        val channel=RandomAccessFile(File(folder,"parent.lock"),"rw").channel
        val lock=checkNotNull(channel.tryLock())
        try {
            File(folder,"child.lock").createNewFile()
            java.io.FileOutputStream(File(folder,"lease")).use {it.write(MARKER.toByteArray());it.fd.sync()}
            val fd=android.system.Os.open(folder.absolutePath,android.system.OsConstants.O_RDONLY or android.system.OsConstants.O_DIRECTORY,0)
            try {android.system.Os.fsync(fd)} finally {android.system.Os.close(fd)}
            val parentFd=android.system.Os.open(parent.absolutePath,android.system.OsConstants.O_RDONLY or android.system.OsConstants.O_DIRECTORY,0)
            try {android.system.Os.fsync(parentFd)} finally {android.system.Os.close(parentFd)}
            schedule(context)
            Lease(value,File(folder,"data"),cache,channel,lock)
        } catch(failure:Throwable) {lock.release();channel.close();throw failure}
    }
    /** The lock exists before the child starts; never recreate a root already retired by cleanup. */
    fun holdChild(context:Context,value:String):Lease {
        id(value);val folder=File(root(context),value)
        check(folder.canonicalFile==folder.absoluteFile && File(folder,"lease").readText()==MARKER)
        val file=File(folder,"child.lock");check(file.isFile && file.canonicalFile==file.absoluteFile)
        val channel=FileChannel.open(file.toPath(),java.nio.file.StandardOpenOption.WRITE)
        return try {Lease(value,File(folder,"data"),File(cacheRoot(context),value),channel,checkNotNull(channel.tryLock()))}
        catch(failure:Throwable) {channel.close();throw failure}
    }
    fun cleanAsync(context:Context) {executor.execute {runCatching {clean(context)}}}
    /** Returns true when active locks or an IO failure require another scheduled pass. */
    fun clean(context:Context):Boolean = synchronized(gate) {
        val root=root(context)
        if(!root.exists())return@synchronized false
        if(root.canonicalFile!=root.absoluteFile)return@synchronized true
        var pending=false
        root.listFiles().orEmpty().forEach {folder ->
            if(runCatching {id(folder.name);folder.isDirectory && folder.canonicalFile==folder.absoluteFile && File(folder,"lease").readText()==MARKER}.getOrDefault(false)) {
                try {
                    FileChannel.open(File(folder,"parent.lock").toPath(),java.nio.file.StandardOpenOption.WRITE).use parentChannel@ {parent ->
                        val parentLock=try {parent.tryLock()} catch(_:java.nio.channels.OverlappingFileLockException){null}
                        if(parentLock==null){pending=true;return@parentChannel}
                        parentLock.use {
                            FileChannel.open(File(folder,"child.lock").toPath(),java.nio.file.StandardOpenOption.WRITE).use childChannel@ {child ->
                                val childLock=try {child.tryLock()} catch(_:java.nio.channels.OverlappingFileLockException){null}
                                if(childLock==null){pending=true;return@childChannel}
                                childLock.use {
                                    check(cacheRoot(context).canonicalFile==cacheRoot(context).absoluteFile)
                                    erase(File(folder,"data"));erase(File(cacheRoot(context),folder.name))
                                    // Journal survives partial subtree failure; no browsing data remains on success.
                                    check(File(folder,"lease").delete())
                                }
                            }
                        }
                    }
                    if(!File(folder,"lease").exists()) {File(folder,"parent.lock").delete();File(folder,"child.lock").delete();folder.delete()}
                } catch(_:Exception) {pending=true}
            }
        }
        pending
    }
    private fun erase(file:File) {
        if(!Files.exists(file.toPath(),java.nio.file.LinkOption.NOFOLLOW_LINKS))return
        // walkFileTree defaults to NOFOLLOW_LINKS: malicious/retired symlinks cannot escape roots.
        Files.walkFileTree(file.toPath(),object:SimpleFileVisitor<Path>() {
            override fun visitFile(path:Path,attrs:BasicFileAttributes):FileVisitResult {Files.delete(path);return FileVisitResult.CONTINUE}
            override fun postVisitDirectory(path:Path,error:java.io.IOException?):FileVisitResult {if(error!=null)throw error;Files.delete(path);return FileVisitResult.CONTINUE}
        })
    }
    fun schedule(context:Context) {
        context.getSystemService(JobScheduler::class.java).schedule(JobInfo.Builder(176244,ComponentName(context,HiddenVpnStorageCleanupJob::class.java))
            .setMinimumLatency(1_000).setOverrideDeadline(10_000).setPersisted(true).build())
    }
}

/** No WebView/provider initialization and no network. Jobs survive ordinary parent process death. */
class HiddenVpnStorageCleanupJob:JobService() {
    private val worker=Executors.newSingleThreadExecutor()
    override fun onStartJob(params:JobParameters):Boolean {
        worker.execute {val retry=runCatching {HiddenVpnStorageCleanup.clean(applicationContext)}.getOrDefault(true);jobFinished(params,retry)}
        return true
    }
    override fun onStopJob(params:JobParameters)=true
    override fun onDestroy() {worker.shutdownNow();super.onDestroy()}
}
