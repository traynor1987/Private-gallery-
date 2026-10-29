package uk.co.traynor.privategallery.core.privateapps

import java.io.*
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.SecureRandom
import uk.co.traynor.privategallery.core.crypto.EncryptionHeader
import uk.co.traynor.privategallery.core.crypto.VaultCipher

/** Call using an operation-owned key copy and the existing Primary session commit fence. */
class PrimaryPrivateAppStore(
 private val root:File,
 private val key:ByteArray,
 private val current:()->Boolean,
 private val commit:((()->Unit)->Unit),
) : PrivateAppStore {
 private val file=File(root,"primary-private-apps-v1.enc")
 override fun owner()=PrivateAppStore.Owner.PRIMARY
 override fun read():PrivateAppCatalog=synchronized(GATE){check(current());readUnlocked().also{check(current())}}
 override fun update(action:java.util.function.UnaryOperator<PrivateAppCatalog>):PrivateAppCatalog=synchronized(GATE) {
  check(current())
  val next=PrivateAppCatalog(false,action.apply(readUnlocked()).apps)
  check(current())
  val bytes=next.encode()
  var pending:File?=null
  try {
   check(root.isDirectory || root.mkdirs())
   java.nio.channels.FileChannel.open(root.parentFile.toPath(),java.nio.file.StandardOpenOption.READ).use{it.force(true)}
   val temp=File.createTempFile("private-apps-",".ciphertext",root);pending=temp
   val nonce=SecureRandom().generateSeed(EncryptionHeader.NONCE_BYTES)
   FileOutputStream(temp).use {out->out.write(nonce);VaultCipher.encrypt(ByteArrayInputStream(bytes),out,key,AAD,nonce);out.fd.sync()}
   commit {check(current());Files.move(temp.toPath(),file.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING)}
   java.nio.channels.FileChannel.open(root.toPath(),java.nio.file.StandardOpenOption.READ).use{it.force(true)}
   check(current());next
  }finally{bytes.fill(0);pending?.delete()}
 }
 private fun readUnlocked():PrivateAppCatalog {
  if(!file.exists())return PrivateAppCatalog(false,emptyList())
  require(file.length() in 28L..(PrivateAppCatalog.MAX_BYTES+28L))
  val plain=object:ByteArrayOutputStream(){fun wipe(){buf.fill(0);reset()}}
  try {
   FileInputStream(file).use {input->
    val nonce=ByteArray(EncryptionHeader.NONCE_BYTES)
    DataInputStream(input).readFully(nonce)
    VaultCipher.decrypt(input,plain,key,AAD,EncryptionHeader(nonce))
   }
   val bytes=plain.toByteArray()
   try{return PrivateAppCatalog.decode(bytes,false)}finally{bytes.fill(0)}
  }finally{plain.wipe()}
 }
 private companion object {val GATE=Any();val AAD="private-gallery:primary:private-apps:v1".encodeToByteArray()}
}
