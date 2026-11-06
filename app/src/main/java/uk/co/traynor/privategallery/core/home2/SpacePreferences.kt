package uk.co.traynor.privategallery.core.home2

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import java.io.*
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import uk.co.traynor.privategallery.core.crypto.EncryptionHeader
import uk.co.traynor.privategallery.core.crypto.VaultCipher
import uk.co.traynor.privategallery.core.evidence.EvidenceAccess
import uk.co.traynor.privategallery.core.evidence.EvidenceEncryptedStore

/** A captured Space capability: no framework preferences, retained keys or plaintext files.
 * Closing this wrapper retires its handles without closing the caller's EvidenceAccess.
 */
internal class SpaceContext(base: Context, private val access: EvidenceAccess) : ContextWrapper(base), AutoCloseable {
 private val closed = AtomicBoolean()
 private val preferences = mutableMapOf<String, Preferences>()
 private val editors = mutableSetOf<Editor>()
 private val root = File(access.root, "home2")
 @Volatile private var retirement: AutoCloseable? = null
 val spaceId: String = access.owner.name.lowercase(java.util.Locale.ROOT)

 init {
  checkCurrent()
  synchronized(gate) { recoverBackupSwap() }
  retirement = access.onRetire { close() }
  if (closed.get()) retirement?.close()
  checkCurrent()
 }
 override fun getApplicationContext(): Context = this
 fun current(): Boolean = !closed.get() && access.current()
 internal fun publishIfCurrent(action:()->Unit)=access.publishIfCurrent {if(!closed.get())action()}
 internal fun onRetire(action:()->Unit)=access.onRetire(action)
 private fun checkCurrent() { check(current()) { "Space access retired; unlock the originating vault again" } }
 override fun close() {
  if (closed.compareAndSet(false, true)) {
   synchronized(gate) {
    editors.forEach { it.discard() }; editors.clear()
    preferences.values.forEach { it.listeners.clear() }; preferences.clear()
   }
   retirement?.close()
  }
 }
 override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = synchronized(gate) {
  checkCurrent(); require(mode == Context.MODE_PRIVATE) { "Space preferences must be private" }
  require(name.length <= 4096)
  preferences.getOrPut(name) { Preferences(name) }
 }

 /** Only audited logical namespaces are portable. Never exports device ciphertext or keys. */
 internal fun backupValues():Map<String,ByteArray> = synchronized(gate) {
  checkCurrent();val result=linkedMapOf<String,ByteArray>()
  try {
   uk.co.traynor.privategallery.core.backup.BackupPolicy.preferences.forEach {name ->
    @Suppress("UNCHECKED_CAST") val values=getSharedPreferences(name,0).all as Map<String,Any>
    val safe=portableValues(name,values,false)
    if(safe.isNotEmpty())result["preferences:$name"]=encode(safe)
   }
   val wallpaperPreferences=getSharedPreferences("nova_preview_v1",0)
   val applied=wallpaperPreferences.getString("wallpaper-applied-v1",null)
   val wallpaperJson=applied?.let({org.json.JSONObject(it)}) ?: wallpaperPreferences.getString("state",null)?.let{org.json.JSONObject(it).optJSONObject(spaceId)?.optJSONObject("wallpaper")}
   val wallpaper=wallpaperJson?.optString("image")?.takeIf(UUID_PATTERN::matches)
   if(wallpaper!=null)result["wallpaper:$wallpaper"]=checkNotNull(readWallpaper(wallpaper)){"Referenced wallpaper is unavailable; backup cannot be complete"}
   checkCurrent();result
  }catch(failure:Throwable){result.values.forEach{it.fill(0)};throw failure}
 }
 internal fun validateBackupValues(values:Map<String,ByteArray>) = synchronized(gate) {
  checkCurrent();require(values.size<=16&&values.values.sumOf{it.size.toLong()}<=64L*1024*1024)
  values.forEach { (name,bytes) ->
   require(bytes.size in 1..MAX_BYTES)
   when {
    name.startsWith("preferences:")-> {val namespace=name.removePrefix("preferences:");require(uk.co.traynor.privategallery.core.backup.BackupPolicy.allowed(namespace));portableValues(namespace,decode(bytes),true)}
    name.startsWith("wallpaper:")->{require(UUID_PATTERN.matches(name.removePrefix("wallpaper:")));StoredWallpaperImage.validateImport(bytes)}
    else->error("Unsupported backup preference")
   }
  }
  values["preferences:nova_preview_v1"]?.let {bytes->
   val preferences=decode(bytes)
   val applied=preferences["wallpaper-applied-v1"] as? String
   val wallpaper=applied?.let{org.json.JSONObject(it)}?: (preferences["state"] as? String)?.let{org.json.JSONObject(it).getJSONObject("profile").optJSONObject("wallpaper")}
   val id=wallpaper?.optString("image")?.takeIf(UUID_PATTERN::matches)
   require(id==null||values.containsKey("wallpaper:$id")){"Referenced wallpaper missing from archive"}
  }
 }
 /** One recoverable directory swap replaces supported settings after explicit UI confirmation.
  * The old directory survives until promotion finishes; startup repairs an interrupted rename. */
 internal fun restoreBackupValues(values:Map<String,ByteArray>,appIds:Map<String,String> = emptyMap(),installedApps:org.json.JSONArray = org.json.JSONArray()) = synchronized(gate) {
  validateBackupValues(values);recoverBackupSwap()
  access.leases.acquire().use {lease ->
   checkLease(lease);check(root.parentFile!!.isDirectory||root.parentFile!!.mkdirs())
   val next=File(root.parentFile,"home2-backup-next-${UUID.randomUUID()}")
   check(next.mkdir())
   try {
    root.listFiles().orEmpty().forEach {file ->checkLease(lease);if(file.isFile&&file.name.endsWith(".enc"))FileInputStream(file).use{input->FileOutputStream(File(next,file.name)).use{output->input.copyTo(output,65536);output.fd.sync()}}}
    values.forEach {(name,bytes)->
     checkLease(lease)
     val namespace=name.substringAfter(':')
     val kind=if(name.startsWith("preferences:"))"preferences"else "wallpaper"
     val plain=if(kind=="preferences")encode(portableValues(namespace,decode(bytes),true,appIds,installedApps))else bytes.copyOf()
     val file=if(kind=="preferences")File(next,"prefs-${MessageDigest.getInstance("SHA-256").digest(namespace.encodeToByteArray()).joinToString(""){"%02x".format(it)}}.enc")else File(next,"wallpaper-$namespace.enc")
     try {
      writeEncrypted(file,aad(kind,namespace),plain,lease)
      val verified=checkNotNull(readEncrypted(file,aad(kind,namespace),lease))
      try{check(MessageDigest.isEqual(plain,verified)){"Staged settings verification failed"}}finally{verified.fill(0)}
     }finally{plain.fill(0)}
    }
    uk.co.traynor.privategallery.core.backup.SettingsDirectoryTransaction(root).install(next,{current()&&lease.current()}) {action->lease.commit {checkLease(lease);action()}}
   }finally {next.deleteRecursively()}
  }
 }
 private fun recoverBackupSwap() {
  if(root.parentFile?.isDirectory==true)uk.co.traynor.privategallery.core.backup.SettingsDirectoryTransaction(root).recover()
  root.parentFile?.listFiles().orEmpty().filter{it.name.startsWith("home2-backup-next-")}.forEach{it.deleteRecursively()}
 }
 private fun portableValues(name:String,values:Map<String,Any>,restore:Boolean,appIds:Map<String,String> = emptyMap(),installedApps:org.json.JSONArray = org.json.JSONArray()):MutableMap<String,Any> {
  require(uk.co.traynor.privategallery.core.backup.BackupPolicy.allowed(name))
  if(restore)uk.co.traynor.privategallery.core.backup.PortablePreferenceSchema.validate(name,values,spaceId)
  val safe=linkedMapOf<String,Any>()
  when(name) {
   "nova_preview_v1"->{
    values["state"]?.let {raw->
     val state=org.json.JSONObject(raw as String)
     if(restore) {
      require(state.getInt("schema")==1&&state.getString("current")==spaceId)
      val profile=state.getJSONObject("profile");profile.put("installed",installedApps)
      fun remap(array:org.json.JSONArray):org.json.JSONArray=org.json.JSONArray().apply{repeat(array.length()){index->val id=array.getString(index);if(id.startsWith("web:"))appIds[id]?.let{put(it)}else put(id)}}
      val pages=profile.getJSONArray("pages");for(index in 0 until pages.length())pages.put(index,remap(pages.getJSONArray(index)))
      profile.put("dock",remap(profile.getJSONArray("dock")));profile.put("drawer",remap(profile.optJSONArray("drawer")?:org.json.JSONArray()))
      val folders=profile.getJSONArray("folders");for(index in 0 until folders.length()){val folder=folders.getJSONObject(index);folder.put("apps",remap(folder.getJSONArray("apps")))}
      // The legacy Home codec requires two slots; the inactive one is an empty local default.
      val empty=org.json.JSONObject().put("installed",org.json.JSONArray()).put("pages",org.json.JSONArray().put(org.json.JSONArray()))
       .put("dock",org.json.JSONArray()).put("folders",org.json.JSONArray()).put("wallpaper",org.json.JSONObject().put("builtin","Aurora"))
      safe["state"]=org.json.JSONObject().put("schema",1).put("current",spaceId).put("theme",state.getString("theme"))
       .put(spaceId,profile).put(if(spaceId=="primary")"hidden"else "primary",empty).toString()
     } else {
      require(state.getString("current")==spaceId)
      val profile=state.getJSONObject(spaceId);profile.put("installed",org.json.JSONArray())
      safe["state"]=org.json.JSONObject().put("schema",1).put("current",spaceId).put("theme",state.getString("theme")).put("profile",profile).toString()
     }
    }
    values["wallpaper-applied-v1"]?.let{safe["wallpaper-applied-v1"]=it}
    values["native-theme-bridged"]?.let{safe["native-theme-bridged"]=it}
   }
   "nova_widgets_v1"->{
    listOf("reduced","clock:$spaceId").forEach{key->values[key]?.let{safe[key]=it}}
    values["layout:$spaceId"]?.let{raw->
     safe["layout:$spaceId"]=uk.co.traynor.privategallery.core.backup.PortableWidgets.sanitize(raw as String)
    }
   }
   "nova_mind_v1"->listOf(spaceId,"preferred").forEach{key->values[key]?.let{safe[key]=it}}
   "nova_shell_v1"->listOf("displayDim","reverse","gestures","edge","left","edgePosition","favourites:$spaceId").forEach{key->values[key]?.let{safe[key]=it}}
   else->values.forEach{(key,value)->require(!Regex("(?i)(secret|pin|password|token|credential|cookie|api.?key)").containsMatchIn(key)){"Sensitive app setting is not eligible"};safe[key]=copy(value)}
  }
  if(!restore)uk.co.traynor.privategallery.core.backup.PortablePreferenceSchema.validate(name,safe,spaceId)
  return safe
 }

 /** Returns an opaque encrypted wallpaper reference; callers decode the returned bytes in memory. */
 internal fun <T> wallpaperTransaction(action:()->T):T = synchronized(gate) {
  checkCurrent();action().also { checkCurrent() }
 }
 fun writeWallpaper(bytes: ByteArray): String = synchronized(gate) {
  checkCurrent(); require(bytes.size in 1..MAX_BYTES)
  val id = UUID.randomUUID().toString()
  val owned = bytes.copyOf()
  try { access.leases.acquire().use { lease ->
   checkLease(lease); writeEncrypted(File(root, "wallpaper-$id.enc"), aad("wallpaper", id), owned, lease)
  } } finally { owned.fill(0) }
  id
 }
 fun readWallpaper(id: String): ByteArray? = synchronized(gate) {
  checkCurrent(); require(UUID_PATTERN.matches(id)) { "Invalid wallpaper reference" }
  access.leases.acquire().use { lease ->
   checkLease(lease)
   readEncrypted(File(root, "wallpaper-$id.enc"), aad("wallpaper", id), lease)
  }
 }
 /** Invoke only after committing the preference(s) that reference the retained wallpaper IDs. */
 fun pruneWallpapers(keepIds: Set<String>) = synchronized(gate) {
  checkCurrent(); require(keepIds.all(UUID_PATTERN::matches))
  access.leases.acquire().use { lease ->
   checkLease(lease)
   val obsolete = root.listFiles().orEmpty().filter { file ->
    val id = file.name.removePrefix("wallpaper-").removeSuffix(".enc")
    file.name == "wallpaper-$id.enc" && UUID_PATTERN.matches(id) && id !in keepIds
   }
   obsolete.forEach { file -> lease.commit { checkLease(lease); check(!file.exists() || file.delete()) { "Wallpaper cleanup failed" } } }
   checkLease(lease)
  }
 }

 private fun aad(kind: String, name: String) = "private-gallery:home2:v1:${access.owner.name}:$kind:$name".encodeToByteArray()
 private fun checkLease(lease: EvidenceEncryptedStore.KeyLease) { checkCurrent(); check(lease.current()) { "Space lease retired" } }
 private fun readEncrypted(file: File, aad: ByteArray, lease: EvidenceEncryptedStore.KeyLease): ByteArray? {
  checkLease(lease)
  if (!file.exists()) { checkLease(lease); return null }
  require(file.isFile && file.length() in 28L..(MAX_BYTES + 28L)) { "Invalid encrypted Space data" }
  val plain = WipingBuffer()
  try {
   FileInputStream(file).use { input ->
    val nonce = ByteArray(EncryptionHeader.NONCE_BYTES)
    DataInputStream(input).readFully(nonce)
    VaultCipher.decrypt(input, plain, lease.key(), aad, EncryptionHeader(nonce))
   }
   checkLease(lease)
   return plain.toByteArray().also { bytes ->
    try { checkLease(lease) } catch (failure: Throwable) { bytes.fill(0); throw failure }
   }
  } finally { plain.wipe() }
 }
 private fun writeEncrypted(file: File, aad: ByteArray, bytes: ByteArray, lease: EvidenceEncryptedStore.KeyLease) {
  checkLease(lease); require(bytes.size <= MAX_BYTES)
  check(root.isDirectory || root.mkdirs()) { "Space storage unavailable" }
  val stage = File.createTempFile("space-", ".enc", root)
  try {
   val nonce = ByteArray(EncryptionHeader.NONCE_BYTES).also(SecureRandom()::nextBytes)
   FileOutputStream(stage).use { output ->
    output.write(nonce)
    VaultCipher.encrypt(ByteArrayInputStream(bytes), output, lease.key(), aad, nonce)
    output.fd.sync()
   }
   // The only publication is ciphertext promotion under the originating authentication monitor.
   lease.commit {
    checkLease(lease)
    Files.move(stage.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
   }
   checkLease(lease)
  } finally { stage.delete() }
 }

 private inner class Preferences(private val name: String) : SharedPreferences {
  private val file = File(root, "prefs-${MessageDigest.getInstance("SHA-256").digest(name.encodeToByteArray()).joinToString("") { "%02x".format(it) }}.enc")
  private val associated = aad("preferences", name)
  val listeners = mutableSetOf<SharedPreferences.OnSharedPreferenceChangeListener>()
  private fun load(lease: EvidenceEncryptedStore.KeyLease): MutableMap<String, Any> {
   val bytes = readEncrypted(file, associated, lease) ?: return linkedMapOf()
   return try { decode(bytes) } finally { bytes.fill(0) }
  }
  private fun <T> read(action: (MutableMap<String, Any>) -> T): T = synchronized(gate) {
   checkCurrent()
   access.leases.acquire().use { lease ->
    val values = load(lease)
    try { action(values).also { checkLease(lease) } } finally { values.clear() }
   }
  }
  override fun getAll(): MutableMap<String, *> = read { values -> values.mapValuesTo(linkedMapOf()) { copy(it.value) } }
  override fun contains(key: String): Boolean = read { it.containsKey(key) }
  override fun getString(key: String, defValue: String?): String? = read { if (it.containsKey(key)) it[key] as String else defValue }
  override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? = read {
   if (it.containsKey(key)) { @Suppress("UNCHECKED_CAST") (it[key] as Set<String>).toMutableSet() } else defValues?.toMutableSet()
  }
  override fun getInt(key: String, defValue: Int): Int = read { if (it.containsKey(key)) it[key] as Int else defValue }
  override fun getLong(key: String, defValue: Long): Long = read { if (it.containsKey(key)) it[key] as Long else defValue }
  override fun getFloat(key: String, defValue: Float): Float = read { if (it.containsKey(key)) it[key] as Float else defValue }
  override fun getBoolean(key: String, defValue: Boolean): Boolean = read { if (it.containsKey(key)) it[key] as Boolean else defValue }
  override fun edit(): SharedPreferences.Editor = synchronized(gate) {
   checkCurrent(); Editor(this).also { editors.add(it) }
  }
  override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) = synchronized(gate) {
   checkCurrent(); listeners.add(listener); Unit
  }
  override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) = synchronized(gate) {
   checkCurrent(); listeners.remove(listener); Unit
  }
  fun commit(changes: Map<String, Any?>, clear: Boolean): Boolean {
   var changed = emptyList<String?>()
   val succeeded = synchronized(gate) {
    checkCurrent()
    access.leases.acquire().use { lease ->
     // Always authenticate the existing record, including clear(), before allowing replacement.
     val previous = load(lease)
     val next = if (clear) linkedMapOf() else previous.toMutableMap()
     var encoded: ByteArray? = null
     try {
      changes.forEach { (key, value) -> if (value == null) next.remove(key) else next[key] = copy(value) }
      val changedKeys = (previous.keys + next.keys).filter { previous[it] != next[it] }
      changed = if (clear) listOf(null) + changedKeys else changedKeys
      if (changedKeys.isNotEmpty()) {
       encoded = encode(next)
       writeEncrypted(file, associated, encoded, lease)
      }
      checkLease(lease); true
     } catch (_: IOException) { false }
     finally { encoded?.fill(0); previous.clear(); next.clear() }
    }
   }
   if (succeeded && changed.isNotEmpty()) {
    val keys = changed
    Handler(Looper.getMainLooper()).post {
     keys.forEach { key ->
      val snapshot = synchronized(gate) { if (current()) listeners.toList() else emptyList() }
      snapshot.forEach { listener -> if (current()) listener.onSharedPreferenceChanged(this, key) }
     }
    }
   }
   return succeeded
  }
 }
 private inner class Editor(private val target: Preferences) : SharedPreferences.Editor {
  private val pending = linkedMapOf<String, Any?>()
  private var clearing = false
  fun discard() { pending.clear(); clearing = false }
  private fun put(key: String, value: Any?): SharedPreferences.Editor = synchronized(gate) {
   checkCurrent(); require(key.length <= MAX_BYTES)
   pending[key] = value?.let(::copy); editors.add(this); this
  }
  override fun putString(key: String, value: String?) = put(key, value)
  override fun putStringSet(key: String, values: MutableSet<String>?) = put(key, values)
  override fun putInt(key: String, value: Int) = put(key, value)
  override fun putLong(key: String, value: Long) = put(key, value)
  override fun putFloat(key: String, value: Float) = put(key, value)
  override fun putBoolean(key: String, value: Boolean) = put(key, value)
  override fun remove(key: String) = put(key, null)
  override fun clear(): SharedPreferences.Editor = synchronized(gate) { checkCurrent(); clearing = true; editors.add(this); this }
  override fun commit(): Boolean = synchronized(gate) {
   checkCurrent()
   try { target.commit(pending, clearing) } finally { discard(); editors.remove(this) }
  }
  // Synchronous persistence keeps apply() inside the captured lease instead of enqueueing stale writes.
  override fun apply() { check(commit()) { "Space preferences could not be saved" } }
 }
 private class WipingBuffer : ByteArrayOutputStream() {
  override fun write(value: Int) { require(count < MAX_BYTES) { "Space data too large" }; super.write(value) }
  override fun write(bytes: ByteArray, offset: Int, length: Int) {
   require(count.toLong() + length <= MAX_BYTES) { "Space data too large" }; super.write(bytes, offset, length)
  }
  fun wipe() { buf.fill(0); reset() }
 }
 private companion object {
  val gate = Any() // Serializes read/modify/promotion across independently created wrappers.
  const val MAX_BYTES = 8 * 1024 * 1024
  const val MAX_ENTRIES = 16_384
  val UUID_PATTERN = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
  fun copy(value: Any): Any = if (value is Set<*>) value.toMutableSet() else value
  fun writeString(output: DataOutputStream, value: String) {
   val bytes = value.encodeToByteArray()
   try { require(bytes.size <= MAX_BYTES); output.writeInt(bytes.size); output.write(bytes) } finally { bytes.fill(0) }
  }
  fun readString(input: DataInputStream): String {
   val count = input.readInt(); require(count in 0..MAX_BYTES && count <= input.available())
   val bytes = ByteArray(count)
   try {
    input.readFully(bytes)
    return Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
   } finally { bytes.fill(0) }
  }
  fun encode(values: Map<String, Any>): ByteArray {
   require(values.size <= MAX_ENTRIES)
   val buffer = WipingBuffer()
   try {
    DataOutputStream(buffer).use { output ->
     output.writeInt(1); output.writeInt(values.size)
     values.forEach { (key, value) ->
      writeString(output, key)
      when (value) {
       is String -> { output.writeByte(1); writeString(output, value) }
       is Int -> { output.writeByte(2); output.writeInt(value) }
       is Long -> { output.writeByte(3); output.writeLong(value) }
       is Float -> { output.writeByte(4); output.writeFloat(value) }
       is Boolean -> { output.writeByte(5); output.writeBoolean(value) }
       is Set<*> -> { require(value.size <= MAX_ENTRIES); output.writeByte(6); output.writeInt(value.size); value.forEach { writeString(output, it as String) } }
       else -> error("Unsupported preference type")
      }
     }
    }
    return buffer.toByteArray().also { require(it.size <= MAX_BYTES) }
   } finally { buffer.wipe() }
  }
  fun decode(bytes: ByteArray): MutableMap<String, Any> = DataInputStream(ByteArrayInputStream(bytes)).use { input ->
   require(input.readInt() == 1) { "Unsupported Space preferences" }
   val size = input.readInt(); require(size in 0..MAX_ENTRIES)
   val values = linkedMapOf<String, Any>()
   repeat(size) {
    val key = readString(input); require(!values.containsKey(key))
    values[key] = when (input.readUnsignedByte()) {
     1 -> readString(input)
     2 -> input.readInt()
     3 -> input.readLong()
     4 -> input.readFloat()
     5 -> input.readUnsignedByte().also { require(it in 0..1) } == 1
     6 -> {
      val count = input.readInt(); require(count in 0..MAX_ENTRIES)
      linkedSetOf<String>().also { set -> repeat(count) { require(set.add(readString(input))) } }
     }
     else -> error("Unknown preference type")
    }
   }
   require(input.available() == 0); values
  }
 }
}
