package uk.co.traynor.privategallery.core.evidence

import java.security.MessageDigest
import uk.co.traynor.privategallery.core.vault.VaultEgress
import uk.co.traynor.privategallery.core.vault.VaultEgressPolicy

/** Live policy on originals AND all retained contributors, including removed incident references. */
internal object EvidenceEgress {
 fun digest(catalog:EvidenceCatalog):String {
  val encoded=catalog.encode()
  return try{hex(MessageDigest.getInstance("SHA-256").digest(encoded))}finally{encoded.fill(0)}
 }
 fun fingerprint(e:EvidenceCatalog.Evidence)=EvidenceCatalog.SourceFingerprint(e.sourceId(),e.mime(),e.sha256(),e.size(),e.metadata().vaultOnly())
 fun requireAllowed(catalog:EvidenceCatalog,evidenceIds:List<String>,incidentIds:List<String>,reader:EvidenceSourceReader,action:VaultEgress=VaultEgress.EXPORT):List<EvidenceCatalog.SourceFingerprint> {
  if(catalog.owner()!=reader.owner)throw SecurityException("Evidence belongs to another vault")
  check(reader.current()){"Evidence authority retired"}
  require(evidenceIds.distinct().size==evidenceIds.size&&incidentIds.distinct().size==incidentIds.size)
  val records=evidenceIds.map{id->catalog.evidence().single{it.id()==id}}
  val incidents=incidentIds.map{id->catalog.incidents().single{it.id()==id}}
  val contributions=records.map(::fingerprint)+incidents.flatMap{it.lineage()}+
   incidents.flatMap{it.evidenceIds()}.distinct().map{id->fingerprint(catalog.evidence().single{it.id()==id})}
  val result=contributions.distinctBy{it.sourceId()}
  check(contributions.all{source->result.single{it.sourceId()==source.sourceId()}==source}){"Conflicting source lineage"}
  for(source in result){
   if(source.vaultOnly())throw SecurityException("Vault-only evidence and its derived text cannot leave this vault")
   check(reader.current())
   val live=reader.inspect(source.sourceId()).item
   VaultEgressPolicy.requireAllowed(live,action)
   check(live.id==source.sourceId()&&live.mimeType==source.mime()&&live.plaintextSize==source.size()&&hex(live.plaintextSha256)==source.sha256()){"Contributing original unavailable or changed"}
   check(reader.current())
  }
  return result
 }
 private fun hex(bytes:ByteArray)=bytes.joinToString(""){"%02x".format(it.toInt() and 255)}
}
