package uk.co.traynor.privategallery.core.home

import java.io.*
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.SecureRandom
import uk.co.traynor.privategallery.core.crypto.EncryptionHeader
import uk.co.traynor.privategallery.core.crypto.VaultCipher
import uk.co.traynor.privategallery.core.evidence.EvidenceAccess

internal data class HomePreferences(
 val layout:HomeLayout=HomeLayout.defaults(), val startupHome:Boolean=true,
 val compact:Boolean=false, val largeIcons:Boolean=false,
 val recent:List<HomeLayout.Shortcut> = emptyList(),
)

/** Same captured session leases as evidence, separate file and owner-authenticated AAD. */
internal class HomePreferencesStore(private val access:EvidenceAccess,private val existingInstall:Boolean) {
 private val root=File(access.root,"home1710")
 private val file=File(root,"preferences.enc")
 private val aad="private-gallery:home:1710:${access.owner.name}".encodeToByteArray()
 fun read():HomePreferences=synchronized(gate){access.leases.acquire().use {lease->
  access.checkCurrent();if(!file.exists())return@use HomePreferences(startupHome=!existingInstall)
  require(file.length() in 28L..8192L)
  val plain=object:ByteArrayOutputStream(){fun wipe(){buf.fill(0);reset()}}
  try {
   FileInputStream(file).use {input->val nonce=ByteArray(EncryptionHeader.NONCE_BYTES);DataInputStream(input).readFully(nonce)
    VaultCipher.decrypt(input,plain,lease.key(),aad,EncryptionHeader(nonce))}
   val bytes=plain.toByteArray()
   try{DataInputStream(ByteArrayInputStream(bytes)).use {data->
    require(data.readInt()==1);val layout=HomeLayout.decode(data.readUTF())
    val home=data.readBoolean();val compact=data.readBoolean();val large=data.readBoolean()
    val count=data.readInt();require(count in 0..8)
    val recent=List(count){HomeLayout.Shortcut.valueOf(data.readUTF())};require(data.available()==0)
    access.checkCurrent();check(lease.current());HomePreferences(layout,home,compact,large,recent)
   }}finally{bytes.fill(0)}
  }finally{plain.wipe()}
 }}
 fun write(value:HomePreferences)=synchronized(gate){access.leases.acquire().use {lease->
  access.checkCurrent();require(value.recent.size<=8)
  val buffer=object:ByteArrayOutputStream(){fun wipe(){buf.fill(0);reset()}}
  val bytes=try{DataOutputStream(buffer).use {data->data.writeInt(1);data.writeUTF(value.layout.encode());data.writeBoolean(value.startupHome);data.writeBoolean(value.compact);data.writeBoolean(value.largeIcons);data.writeInt(value.recent.size);value.recent.forEach{data.writeUTF(it.name)}};buffer.toByteArray()}finally{buffer.wipe()}
  check(root.isDirectory||root.mkdirs());val temp=File.createTempFile("home-",".enc",root)
  try{val nonce=SecureRandom().generateSeed(EncryptionHeader.NONCE_BYTES)
   FileOutputStream(temp).use{out->out.write(nonce);VaultCipher.encrypt(ByteArrayInputStream(bytes),out,lease.key(),aad,nonce);out.fd.sync()}
   lease.commit{access.checkCurrent();Files.move(temp.toPath(),file.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING)}
  }finally{bytes.fill(0);temp.delete()}
 }}
 private companion object{val gate=Any()}
}
