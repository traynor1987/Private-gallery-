package uk.co.traynor.privategallery.core.privateapps

import java.io.File
import java.util.function.UnaryOperator

/**
 * Session-bound adapter shared by the future library/editor callbacks. Captures authentication
 * identity once; lock/unlock cannot make an old handle valid again. Keys live only per operation.
 * The supplied gate MUST also serialize the caller's authentication revocation/commit boundary.
 */
class PrimaryPrivateAppAccess(
 private val root:File,
 private val identity:Any,
 private val currentIdentity:()->Any?,
 private val authorized:()->Boolean,
 private val copyKey:()->ByteArray,
 private val gate:Any,
):PrivateAppStore {
 override fun owner()=PrivateAppStore.Owner.PRIMARY
 private fun current()=synchronized(gate){currentIdentity()===identity && authorized()}
 private fun access(change:UnaryOperator<PrivateAppCatalog>?):PrivateAppCatalog {
  val key=synchronized(gate){check(current());copyKey()}
  try {
   val store=PrimaryPrivateAppStore(root,key,::current,{commit->synchronized(gate){check(current());commit()}})
   return if(change==null)store.read() else store.update(change)
  }finally{key.fill(0)}
 }
 override fun read()=access(null)
 override fun update(change:UnaryOperator<PrivateAppCatalog>)=access(change)
}
