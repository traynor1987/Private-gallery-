package uk.co.traynor.privategallery.core.evidence

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/** Actual Android cipher/filesystem checks supplement the host domain suite. */
class EvidenceStore179Test {
 @Test fun encryptedOwnersPersistSeparatelyAndNeverWriteSearchableEvidenceText() {
  val root=File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,"evidence179-test-${UUID.randomUUID()}")
  val key=ByteArray(32){(it+1).toByte()};val before=key.copyOf();val identity=Any();val valid=AtomicBoolean(true)
  val leases=EvidenceAuthorities.primary(identity,{identity},{valid.get()},{key.copyOf()},Any())
  try {
   val primary=EvidenceEncryptedStore(root,EvidenceCatalog.Owner.PRIMARY,leases)
   val hidden=EvidenceEncryptedStore(root,EvidenceCatalog.Owner.HIDDEN,leases)
   val e=record("PRIMARY-SECRET-OCR-179")
   primary.update{it.upsertEvidence(e,1,"OWNER_IMPORT")}
   assertEquals(e,primary.read().evidence().single());assertTrue(hidden.read().evidence().isEmpty())
   hidden.update{it.upsertEvidence(record("HIDDEN-SECRET-NOTE-179"),2,"OWNER_IMPORT")}
   assertEquals("PRIMARY-SECRET-OCR-179",primary.read().evidence().single().name())
   assertEquals("HIDDEN-SECRET-NOTE-179",hidden.read().evidence().single().name())
   root.walkTopDown().filter{it.isFile}.forEach{file->val bytes=file.readBytes();try{val raw=String(bytes,Charsets.ISO_8859_1);assertFalse(raw.contains("PRIMARY-SECRET"));assertFalse(raw.contains("HIDDEN-SECRET"))}finally{bytes.fill(0)}}
   assertArrayEquals(before,key)
   valid.set(false)
   try{primary.read();fail("revoked read allowed")}catch(expected:Exception){}
   try{hidden.update{it};fail("revoked write allowed")}catch(expected:Exception){}
  }finally{key.fill(0);before.fill(0);root.deleteRecursively()}
 }
 @Test fun interruptedUpdateLeavesLastCiphertextAndWrongKeyCannotReplaceIt() {
  val root=File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,"evidence179-test-${UUID.randomUUID()}")
  val key=ByteArray(32){7};val identity=Any();val valid=AtomicBoolean(true);val gate=Any()
  val leases=EvidenceAuthorities.primary(identity,{identity},{valid.get()},{key.copyOf()},gate)
  try{
   val store=EvidenceEncryptedStore(root,EvidenceCatalog.Owner.PRIMARY,leases);store.update{it.upsertEvidence(record("retained"),1,"IMPORT")}
   val saved=root.walkTopDown().filter{it.isFile}.associate{it.relativeTo(root).path to it.readBytes()}
   try{store.update{valid.set(false);it.upsertEvidence(record("must not commit"),2,"IMPORT")};fail("revoked commit allowed")}catch(expected:Exception){}
   saved.forEach{(path,bytes)->assertArrayEquals(bytes,File(root,path).readBytes());bytes.fill(0)}
   val wrong=EvidenceEncryptedStore(root,EvidenceCatalog.Owner.PRIMARY,EvidenceAuthorities.primary(identity,{identity},{true},{ByteArray(32){8}},gate))
   try{wrong.read();fail("wrong key read allowed")}catch(expected:Exception){}
  }finally{key.fill(0);root.deleteRecursively()}
 }
 @Test fun ciphertextTamperCannotReadOrOverwritePriorEncryptedEvidence() {
  val root=File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,"evidence179-test-${UUID.randomUUID()}")
  val key=ByteArray(32){9};val identity=Any()
  try {
   val store=EvidenceEncryptedStore(root,EvidenceCatalog.Owner.PRIMARY,EvidenceAuthorities.primary(identity,{identity},{true},{key.copyOf()},Any()))
   store.update{it.upsertEvidence(record("encrypted original"),1,"IMPORT")}
   val payload=root.walkTopDown().filter{it.isFile}.maxBy{it.length()}
   val corrupt=payload.readBytes();corrupt[corrupt.lastIndex]=(corrupt.last().toInt() xor 1).toByte();payload.writeBytes(corrupt)
   try{store.read();fail("tampered ciphertext accepted")}catch(expected:Exception){}
   try{store.update{it.upsertEvidence(record("replacement"),2,"IMPORT")};fail("corruption overwritten")}catch(expected:Exception){}
   assertArrayEquals(corrupt,payload.readBytes());corrupt.fill(0)
  }finally{key.fill(0);root.deleteRecursively()}
 }
 private fun record(name:String)=EvidenceCatalog.Evidence(UUID.randomUUID().toString(),UUID.randomUUID().toString(),name,"image/png","12".repeat(32),3,1,
  EvidenceCatalog.Metadata("source.png",0,0,1,0,1,1,emptyMap(),false),"","owner notes",listOf("private tag"),emptyList(),emptyList(),emptyList())
}
