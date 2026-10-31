package uk.co.traynor.privategallery.core.evidence

import android.content.Context
import java.io.File
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/** Run once before Main exposes any evidence UI. Only orphan ciphertext names are eligible. */
internal object EvidenceStagingCleanup {
 private val started=AtomicBoolean()
 fun cleanOnce(context:Context){if(started.compareAndSet(false,true))runCatching{cleanOwned(context)}}
 internal fun cleanOwned(context:Context){
  val root=context.noBackupFilesDir
  fun safeDirectory(file:File)=file.isDirectory&&!Files.isSymbolicLink(file.toPath())&&file.canonicalFile.parentFile==root.canonicalFile
  for(name in listOf("evidence-open-primary","evidence-open-hidden")){
   val directory=File(root,name);if(!safeDirectory(directory))continue
   directory.listFiles()?.take(4096)?.forEach{file->if(file.isFile&&!Files.isSymbolicLink(file.toPath())&&file.name.matches(Regex("encrypted-[A-Za-z0-9_-]+\\.stage")))file.delete()}
   directory.delete()
  }
  val stages=File(root,"evidence179-ciphertext-staging");if(!safeDirectory(stages))return
  stages.listFiles()?.take(4096)?.forEach{directory ->
   if(directory.isDirectory&&!Files.isSymbolicLink(directory.toPath())&&runCatching{UUID.fromString(directory.name).toString()==directory.name}.getOrDefault(false)){
    directory.listFiles()?.take(16)?.forEach{file->if(file.isFile&&!Files.isSymbolicLink(file.toPath())&&file.name.endsWith(".cipher")&&runCatching{UUID.fromString(file.name.removeSuffix(".cipher")).toString()==file.name.removeSuffix(".cipher")}.getOrDefault(false))file.delete()}
    directory.delete()
   }
  }
  stages.delete()
 }
}
