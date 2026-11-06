package uk.co.traynor.privategallery.core.backup

import java.io.File
import java.io.FileOutputStream
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardOpenOption.READ

/** Ciphertext-only directory transaction. A durable receipt is the commit decision, not directory
 * existence. Keep the old tree until recovery sees that decision; never delete it on failure.
 * The new tree may subsequently change (for example the backup history), so its hash is not a
 * recovery decision. Callers serialize all preference IO and validate/sync every staged record. */
internal class SettingsDirectoryTransaction(private val root:File,private val sync:(File)->Unit=::forceDirectory) {
 private val parent=checkNotNull(root.parentFile)
 private val old=File(parent,"home2-backup-old")
 private val receipt=File(parent,"home2-backup-commit")
 private val pending=File(parent,"home2-backup-commit-next")
 fun install(next:File,current:()->Boolean,commit:((()->Unit)->Unit)) {
  check(!old.exists()&&!receipt.exists());check(current());check(root.isDirectory||root.mkdir())
  sync(next);var moved=false
  try {
   commit {check(current());move(root,old);moved=true;move(next,root)}
   sync(parent);check(current())
   FileOutputStream(pending).use{it.write(COMMITTED);it.fd.sync()}
   // This short final authority fence linearizes successful replacement. Retirement before or
   // during durability is a failure and rolls back. Retirement after it is a completed commit.
   commit {check(current());move(pending,receipt);sync(parent);check(current())}
  } catch(failure:Throwable) {
   try {
    if(receipt.exists())check(receipt.delete())
    if(moved)rollback()
   }catch(recovery:Throwable){failure.addSuppressed(recovery)}
   throw failure
  }finally {pending.delete()}
 }
 fun recover() {
  if(old.exists()) {
   val committed=root.isDirectory&&receipt.isFile&&receipt.length()==COMMITTED.size.toLong()&&receipt.readBytes().contentEquals(COMMITTED)
   if(committed) {
    // Rename before deleting: a crash during recursive cleanup must never leave a partial old
    // tree at the rollback path. It is already a durable committed transaction.
    val garbage=File(parent,"home2-backup-retired-${java.util.UUID.randomUUID()}")
    move(old,garbage);sync(parent);garbage.deleteRecursively()
   } else rollback()
  }
  if(receipt.exists())check(receipt.delete());pending.delete();sync(parent)
  parent.listFiles().orEmpty().filter{it.name.startsWith("home2-backup-retired-")}.forEach{it.deleteRecursively()}
 }
 private fun rollback() {
  check(old.isDirectory){"Prior settings require recovery"}
  val failed=File(parent,"home2-backup-retired-${java.util.UUID.randomUUID()}")
  if(root.exists())move(root,failed)
  try {move(old,root)}catch(failure:Throwable){if(!root.exists()&&failed.exists())runCatching{move(failed,root)};throw failure}
  // Even a repeated sync failure leaves the original bytes at root; do not destroy that tree.
  sync(parent);failed.deleteRecursively()
 }
 private fun move(from:File,to:File){Files.move(from.toPath(),to.toPath(),ATOMIC_MOVE)}
 companion object {
  private val COMMITTED="NovaOS settings committed v1\n".encodeToByteArray()
  private fun forceDirectory(file:File){if(file.isDirectory)FileChannel.open(file.toPath(),READ).use{it.force(true)}}
 }
}
