package uk.co.traynor.privategallery.core.backup

import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject
import uk.co.traynor.privategallery.core.vault.*

/** Logical values only: never device keys, source URIs, authentication or external destinations. */
internal object BackupMedia {
 fun metadata(item:VaultItem)=JSONObject().put("category","media").put("id",item.id).put("name",item.displayName)
  .put("mime",item.mimeType).put("size",item.plaintextSize).put("hash",java.util.Base64.getEncoder().encodeToString(item.plaintextSha256))
  .put("wasTrashed",item.state==VaultItemState.TRASHED).put("time",item.importedAtEpochMillis).put("origin",item.origin.name).put("vaultOnly",item.vaultOnly)
  .put("dates",VaultPresentationMetadata.encode(item))
 fun item(json:JSONObject):VaultItem {
  require(json.getString("category")=="media")
  val id=PortablePreferenceSchema.string(json,"id",36,36);require(UUID.fromString(id).toString()==id)
  val name=PortablePreferenceSchema.string(json,"name",1,200)
  val mime=PortablePreferenceSchema.string(json,"mime",1,120);require(mime.startsWith("image/")||mime.startsWith("video/")||VaultDocumentPolicy.isDocument(mime))
  val size=PortablePreferenceSchema.integer(json,"size",1,PortableArchive.MAX_ITEM)
  val hash=java.util.Base64.getDecoder().decode(PortablePreferenceSchema.string(json,"hash",44,44));require(hash.size==32)
  require(json.get("vaultOnly") is Boolean&&json.get("wasTrashed") is Boolean)
  val item=VaultItem(id,mime,name,PortablePreferenceSchema.integer(json,"time",1,253402300799000L),size,hash,ByteArray(12),VaultItemState.COMPLETE,origin=MediaOrigin.valueOf(PortablePreferenceSchema.string(json,"origin",1,40)),vaultOnly=json.getBoolean("vaultOnly"))
  return VaultPresentationMetadata.apply(item,if(json.has("dates"))json.getJSONObject("dates")else JSONObject())
 }
 fun verify(expected:VaultItem,size:Long,hash:ByteArray){require(size==expected.plaintextSize&&MessageDigest.isEqual(hash,expected.plaintextSha256)){"Restored media differs from authenticated manifest"}}
 private fun crop(c:NormalizedCrop)=JSONArray(listOf(c.left,c.top,c.right,c.bottom))
 private fun crop(a:JSONArray):NormalizedCrop {require(a.length()==4);val v=List(4){n->val value=a.get(n);require(value is Number&&value.toDouble().isFinite());value.toFloat()};return NormalizedCrop(v[0],v[1],v[2],v[3])}
 fun collections(snapshot:VaultIndexSnapshot):ByteArray {
  val eligible=snapshot.items.filter{it.state in setOf(VaultItemState.COMPLETE,VaultItemState.TRASHED)}.mapTo(hashSetOf()){it.id}
  return JSONObject().put("collections",JSONArray(snapshot.collections.map{c->JSONObject().put("id",c.id).put("name",c.name).put("time",c.createdAtEpochMillis).put("cover",c.coverVaultItemId?.takeIf{it in eligible}?:"").put("pinned",c.isPinned)}))
   .put("memberships",JSONArray(snapshot.memberships.filter{it.vaultItemId in eligible}.map{m->JSONObject().put("collection",m.collectionId).put("item",m.vaultItemId).put("time",m.addedAtEpochMillis)}))
   .put("favourite",snapshot.favouriteCollectionId ?: "")
   .put("edits",JSONArray(snapshot.imageEdits.filterKeys{it in eligible}.map{(id,edit)->JSONObject().put("item",id).put("crop",crop(edit.crop)).apply{edit.previousCrop?.let{put("previous",crop(it))}}}))
   .toString().encodeToByteArray()
 }
 fun requireOwnerCollections(bytes:ByteArray,owner:String) {
  // Hidden's editor creates derivative media, not an independent non-destructive crop ledger.
  if(owner=="hidden")require((JSONObject(bytes.decodeToString()).optJSONArray("edits")?.length()?:0)==0){"Hidden does not use a separate crop ledger; recover edited media copies"}
 }
 fun merge(original:VaultIndexSnapshot,restored:List<VaultItem>,ids:Map<String,String>,collectionBytes:ByteArray?,restoreFavourite:Boolean=false):VaultIndexSnapshot {
  if(collectionBytes==null)return original.copy(items=original.items+restored)
  val json=JSONObject(collectionBytes.decodeToString());PortablePreferenceSchema.keys(json,setOf("collections","memberships","favourite","edits"))
  val source=json.getJSONArray("collections");require(source.length()<=1000)
  val mapped=linkedMapOf<String,String>()
  val collections=List(source.length()){n->
   val c=source.getJSONObject(n);PortablePreferenceSchema.keys(c,setOf("id","name","time","cover","pinned"))
   val old=PortablePreferenceSchema.string(c,"id",1,120);require(!mapped.containsKey(old));val id=UUID.randomUUID().toString();mapped[old]=id
   if(c.has("pinned"))require(c.get("pinned") is Boolean)
   VaultCollection(id,PortablePreferenceSchema.string(c,"name",1,100),PortablePreferenceSchema.integer(c,"time",1,253402300799000L),
    coverVaultItemId=PortablePreferenceSchema.string(c,"cover",0,120).takeIf{it.isNotEmpty()}?.let{checkNotNull(ids[it])},isPinned=c.optBoolean("pinned",false))
  }
  val members=json.getJSONArray("memberships");require(members.length()<=100000);val pairs=hashSetOf<Pair<String,String>>()
  val memberships=List(members.length()){n->val m=members.getJSONObject(n);PortablePreferenceSchema.keys(m,setOf("collection","item","time"))
   val collection=checkNotNull(mapped[PortablePreferenceSchema.string(m,"collection",1,120)]);val item=checkNotNull(ids[PortablePreferenceSchema.string(m,"item",1,120)]);require(pairs.add(collection to item))
   VaultCollectionMembership(collection,item,PortablePreferenceSchema.integer(m,"time",1,253402300799000L))
  }
  val favourite=if(json.has("favourite"))PortablePreferenceSchema.string(json,"favourite",0,120).takeIf{it.isNotEmpty()}?.let{checkNotNull(mapped[it])}else null
  val sourceEdits=if(json.has("edits"))json.getJSONArray("edits")else JSONArray();require(sourceEdits.length()<=10000)
  val edits=linkedMapOf<String,ImageEditState>()
  repeat(sourceEdits.length()){n->val e=sourceEdits.getJSONObject(n);PortablePreferenceSchema.keys(e,setOf("item","crop","previous"));val id=checkNotNull(ids[PortablePreferenceSchema.string(e,"item",1,120)])
   require(!edits.containsKey(id));restored.firstOrNull{it.id==id}?.let{require(it.mimeType.startsWith("image/"))}
   edits[id]=ImageEditState(crop(e.getJSONArray("crop")),if(e.has("previous"))crop(e.getJSONArray("previous"))else null)
  }
  return original.copy(items=original.items+restored,collections=original.collections+collections,memberships=original.memberships+memberships,
   imageEdits=original.imageEdits+edits,favouriteCollectionId=original.favouriteCollectionId ?: favourite.takeIf{restoreFavourite})
 }
}

/** Producer is consumed once under the repository transaction; only its final index is visible. */
internal fun interface BackupMediaProducer {fun produce(accept:(VaultItem,InputStream)->Unit):ByteArray?}
