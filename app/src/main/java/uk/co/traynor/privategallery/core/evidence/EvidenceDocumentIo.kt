package uk.co.traynor.privategallery.core.evidence

import android.content.Context
import android.net.Uri
import android.os.CancellationSignal
import android.os.Looper
import android.provider.DocumentsContract
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Picker-granted IO only. Cancellation admission is immediate; provider close never blocks Main. */
internal class EvidenceDocumentIo(private val context:Context,private val access:EvidenceAccess,private val operationCurrent:()->Boolean={true}):AutoCloseable {
 private val ended=AtomicBoolean()
 private val admission=Any()
 private val signal=CancellationSignal()
 private val resources=LinkedHashSet<AutoCloseable>()
 private var retirement:AutoCloseable?=null
 init{retirement=access.onRetire{close()};if(ended.get())retirement?.close()}
 fun current()=!ended.get()&&operationCurrent()&&access.current()
 private fun checkCurrent(){check(current()){"Document operation cancelled"}}
 private fun own(resource:AutoCloseable){
  val accepted=synchronized(admission){if(ended.get())false else{resources.add(resource);true}}
  if(!accepted){cleanup.execute{runCatching{resource.close()}};error("Document operation cancelled")}
 }
 private fun release(resource:AutoCloseable){synchronized(admission){resources.remove(resource)};resource.close()}
 fun <T> read(uri:Uri,consume:(InputStream)->T):T {
  check(Looper.myLooper()!==Looper.getMainLooper());require(uri.scheme=="content");checkCurrent()
  val descriptor=checkNotNull(context.contentResolver.openAssetFileDescriptor(uri,"r",signal));own(descriptor)
  try {
   val input=descriptor.createInputStream();own(input)
   try {
    checkCurrent()
    val guarded=object:java.io.FilterInputStream(input){
     override fun read():Int{checkCurrent();return super.read().also{checkCurrent()}}
     override fun read(buffer:ByteArray,offset:Int,length:Int):Int{checkCurrent();return super.read(buffer,offset,length).also{checkCurrent()}}
     override fun close(){}
    }
    return consume(guarded).also{checkCurrent()}
   }finally{release(input)}
  }finally{release(descriptor)}
 }
 fun <T> write(uri:Uri,consume:(OutputStream)->T):T {
  check(Looper.myLooper()!==Looper.getMainLooper());require(uri.scheme=="content");checkCurrent()
  try {
   val descriptor=checkNotNull(context.contentResolver.openAssetFileDescriptor(uri,"wt",signal));own(descriptor)
   try {
    val output=descriptor.createOutputStream();own(output)
    try {
     checkCurrent()
     val guarded=object:java.io.FilterOutputStream(output){
      override fun write(value:Int){checkCurrent();out.write(value);checkCurrent()}
      override fun write(buffer:ByteArray,offset:Int,length:Int){checkCurrent();out.write(buffer,offset,length);checkCurrent()}
      override fun flush(){checkCurrent();out.flush();checkCurrent()}
      override fun close(){}
     }
     val result=consume(guarded);guarded.flush();checkCurrent();return result
    }finally{release(output)}
   }finally{release(descriptor)}
  }catch(failure:Throwable){requestPartialCleanup(context,uri);throw failure}
 }
 override fun close(){
  if(!ended.compareAndSet(false,true))return
  retirement?.close()
  val owned=synchronized(admission){resources.toList().also{resources.clear()}}
  cleanup.execute{runCatching{signal.cancel()};owned.reversed().forEach{runCatching{it.close()}}}
 }
 companion object {
  private val cleanup=Executors.newCachedThreadPool{r->Thread(r,"evidence-document-cleanup").apply{isDaemon=true}}
  fun requestPartialCleanup(context:Context,uri:Uri){cleanup.execute{runCatching{DocumentsContract.deleteDocument(context.contentResolver,uri)}}}
 }
}
