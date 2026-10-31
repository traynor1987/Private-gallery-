package uk.co.traynor.privategallery.core.evidence

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.security.KeyStore
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test
import uk.co.traynor.privategallery.core.editor.AiCredentialStore
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import javax.crypto.KeyGenerator

/** Never resets, deletes or substitutes the real shared application alias. */
class AiCredentialAlias179Test {
 private fun isolated(context:Context,alias:String):AiCredentialStore =
  AiCredentialStore::class.java.getDeclaredConstructor(Context::class.java,String::class.java).apply{isAccessible=true}.newInstance(context,alias)
 @Test fun existingDisposableIncompatibleAliasIsNeverRegeneratedOrReplaced() {
  val base=ApplicationProvider.getApplicationContext<Context>();val root=File(base.cacheDir,"ai-alias-invalid-${UUID.randomUUID()}")
  val context=object:ContextWrapper(base){override fun getNoBackupFilesDir()=root.apply{mkdirs()};override fun getApplicationContext():Context=this}
  val alias="evidence179-disposable-${UUID.randomUUID()}";val fixture="fixture-only-key".toByteArray()
  try {
   KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256,"AndroidKeyStore").apply{init(KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY).setDigests(KeyProperties.DIGEST_SHA256).build())}.generateKey()
   try{isolated(context,alias).save("fixture-invalid",fixture);fail("incompatible existing key accepted")}catch(_:java.security.InvalidKeyException){}
   val store=KeyStore.getInstance("AndroidKeyStore").apply{load(null)};assertEquals(KeyProperties.KEY_ALGORITHM_HMAC_SHA256,store.getKey(alias,null).algorithm)
   assertFalse(isolated(context,alias).isConfigured("fixture-invalid"))
  }finally{fixture.fill(0);root.deleteRecursively();KeyStore.getInstance("AndroidKeyStore").apply{load(null);deleteEntry(alias)}}
 }
 @Test fun disposableAliasConcurrentFirstUseAcrossInstancesKeepsBothOwnersDecryptable() {
  val base=ApplicationProvider.getApplicationContext<Context>();val root=File(base.cacheDir,"ai-alias-${UUID.randomUUID()}")
  val context=object:ContextWrapper(base){override fun getNoBackupFilesDir()=root.apply{mkdirs()};override fun getApplicationContext():Context=this}
  val alias="evidence179-disposable-${UUID.randomUUID()}";val first=isolated(context,alias);val second=isolated(context,alias)
  val a="fixture-primary-first-use".toByteArray();val b="fixture-hidden-first-use".toByteArray();val worker=Executors.newFixedThreadPool(2)
  val ready=CountDownLatch(2);val go=CountDownLatch(1)
  try {
   val one=worker.submit{ready.countDown();check(go.await(5,TimeUnit.SECONDS));first.save("fixture-primary",a)}
   val two=worker.submit{ready.countDown();check(go.await(5,TimeUnit.SECONDS));second.save("fixture-hidden",b)}
   assertTrue(ready.await(5,TimeUnit.SECONDS));go.countDown();one.get(10,TimeUnit.SECONDS);two.get(10,TimeUnit.SECONDS)
   val actualA=second.read("fixture-primary")!!;val actualB=first.read("fixture-hidden")!!
   try{assertArrayEquals(a,actualA);assertArrayEquals(b,actualB)}finally{actualA.fill(0);actualB.fill(0)}
   // Existing alias and ciphertext remain readable through a third instance.
   val third=isolated(context,alias);val again=third.read("fixture-primary")!!;try{assertArrayEquals(a,again)}finally{again.fill(0)}
  }finally{go.countDown();worker.shutdownNow();a.fill(0);b.fill(0);root.deleteRecursively();KeyStore.getInstance("AndroidKeyStore").apply{load(null);deleteEntry(alias)}}
 }
}
