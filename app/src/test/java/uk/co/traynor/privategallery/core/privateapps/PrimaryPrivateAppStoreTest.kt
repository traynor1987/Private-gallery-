package uk.co.traynor.privategallery.core.privateapps

import org.junit.Assert.*
import org.junit.Test
import java.util.UUID
import kotlin.io.path.createTempDirectory

class PrimaryPrivateAppStoreTest {
 @Test fun encrypted_round_trip_rejects_revocation_and_wrong_key() {
  val root=createTempDirectory("privateapps-primary").toFile()
  val key=ByteArray(32){7};var live=true
  val store=PrimaryPrivateAppStore(root,key,{live},{commit->check(live);commit()})
  val app=PrivateAppCatalog.App(UUID.randomUUID().toString(),"Secret name","https://example.test/app/","","browser","",false,true,true)
  try {
   store.update {it.upsert(app)}
   assertEquals(listOf(app),store.read().apps)
   assertFalse(root.listFiles()!!.any {it.readBytes().toString(Charsets.ISO_8859_1).contains("Secret name")})
   live=false
   assertThrows(IllegalStateException::class.java){store.read()}
   live=true
   assertThrows(Exception::class.java){PrimaryPrivateAppStore(root,ByteArray(32){8},{true},{it()}).read()}
   assertThrows(IllegalStateException::class.java){store.update {live=false;it.remove(app.id())}}
   live=true;assertEquals(listOf(app),store.read().apps)
  }finally{key.fill(0);root.deleteRecursively()}
 }
}
