package uk.co.traynor.privategallery.core.domain

import java.security.AlgorithmParameters
import java.security.Key
import java.security.Provider
import java.security.SecureRandom
import java.security.Security
import java.security.spec.AlgorithmParameterSpec
import javax.crypto.Cipher
import javax.crypto.CipherSpi

/** Counts the real JCA boundary while delegating every byte to the real SunJCE GCM provider. */
internal class CountingGcm(failingHmacFinal: Int = 0) : AutoCloseable {
  init { hmacFinals = 0; failHmacAt = failingHmacFinal; initializations = 0; attempts = 0; check(Security.insertProviderAt(CountingProvider(), 1) == 1) }
  override fun close() { afterDecrypt = null; Security.removeProvider("Phase3CountingGcm") }
  companion object { var initializations = 0; var attempts = 0; var afterDecrypt: ((ByteArray) -> Unit)? = null; var hmacFinals = 0; var failHmacAt = 0 }
  class CountingProvider : Provider("Phase3CountingGcm", 1.0, "Test-only real AES GCM accounting observer") {
    init {
      put("Cipher.AES/GCM/NoPadding", CountingCipher::class.java.name)
      if(failHmacAt > 0) put("Mac.HmacSHA256", FailingHmac::class.java.name)
    }
  }
  /** Delegates real HKDF HMAC until the selected provider interruption. */
  class FailingHmac : javax.crypto.MacSpi() {
    private val mac=javax.crypto.Mac.getInstance("HmacSHA256","SunJCE")
    override fun engineGetMacLength()=mac.macLength
    override fun engineInit(key: Key, params: AlgorithmParameterSpec?) { mac.init(key,params) }
    override fun engineUpdate(input: Byte) { mac.update(input) }
    override fun engineUpdate(input: ByteArray, offset: Int, length: Int) { mac.update(input,offset,length) }
    override fun engineReset() { mac.reset() }
    override fun engineDoFinal(): ByteArray {
      hmacFinals++
      if(hmacFinals==failHmacAt) throw java.security.ProviderException("synthetic HMAC interruption")
      return mac.doFinal()
    }
  }
  class CountingCipher : CipherSpi() {
    private var operationMode = 0
    private val cipher = Cipher.getInstance("AES/GCM/NoPadding", "SunJCE")
    override fun engineSetMode(mode: String) { require(mode == "GCM") }
    override fun engineSetPadding(padding: String) { require(padding == "NoPadding") }
    override fun engineGetBlockSize() = cipher.blockSize
    override fun engineGetOutputSize(inputLen: Int) = cipher.getOutputSize(inputLen)
    override fun engineGetIV(): ByteArray? = cipher.iv
    override fun engineGetParameters(): AlgorithmParameters? = cipher.parameters
    override fun engineInit(mode: Int, key: Key, random: SecureRandom?) { operationMode=mode; initializations++; cipher.init(mode,key,random) }
    override fun engineInit(mode: Int, key: Key, params: AlgorithmParameterSpec?, random: SecureRandom?) { operationMode=mode; initializations++; cipher.init(mode,key,params,random) }
    override fun engineInit(mode: Int, key: Key, params: AlgorithmParameters?, random: SecureRandom?) { operationMode=mode; initializations++; cipher.init(mode,key,params,random) }
    override fun engineUpdate(input: ByteArray, offset: Int, length: Int): ByteArray = cipher.update(input,offset,length) ?: byteArrayOf()
    override fun engineUpdate(input: ByteArray, offset: Int, length: Int, output: ByteArray, outputOffset: Int) = cipher.update(input,offset,length,output,outputOffset)
    override fun engineUpdateAAD(input: ByteArray, offset: Int, length: Int) = cipher.updateAAD(input,offset,length)
    override fun engineDoFinal(input: ByteArray, offset: Int, length: Int): ByteArray { attempts++; return cipher.doFinal(input,offset,length).also { if(operationMode == Cipher.DECRYPT_MODE) afterDecrypt?.invoke(it) } }
    override fun engineDoFinal(input: ByteArray, offset: Int, length: Int, output: ByteArray, outputOffset: Int): Int { attempts++; return cipher.doFinal(input,offset,length,output,outputOffset) }
  }
}
