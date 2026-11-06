package uk.co.traynor.privategallery.core.evidence

import java.io.OutputStream
import uk.co.traynor.privategallery.core.vault.VaultEgress
import uk.co.traynor.privategallery.core.vault.VaultEgressPolicy
import uk.co.traynor.privategallery.core.vault.VaultItemState

/** Explicit owner-confirmed plaintext report only. No original reads, files, caches or catalogue export. */
internal object EvidenceReportExport {
 private const val MAX_BYTES=400*1024
 private const val CHUNK_BYTES=16*1024

 /** Includes every retained source, even when an evidence record has since been removed. */
 fun validate(catalog:EvidenceCatalog,analysisId:String,reader:EvidenceSourceReader):EvidenceCatalog.Analysis {
  requireOwner(catalog,reader)
  val analysis=catalog.analyses().singleOrNull{it.id()==analysisId}
   ?:throw IllegalArgumentException("The selected saved report is unavailable")
  require(analysis.task()==EvidenceAi.Task.EVIDENCE_REPORT.name){"Choose a saved evidence report"}
  require(EvidenceAi.details(analysis).report!=null){"The selected analysis is not a supported evidence report"}
  validateSources(catalog,analysis,reader)
  return analysis
 }

 /** The caller owns and closes the authorised destination, and cleans up any failed partial export. */
 fun write(catalog:EvidenceCatalog,analysisId:String,reader:EvidenceSourceReader,output:OutputStream) {
  var bytes:ByteArray?=null
  try {
   val analysis=validate(catalog,analysisId,reader)
   val report=checkNotNull(EvidenceAi.details(analysis).report)
   val text="AI-generated evidence report*\nReport ID: ${analysis.id()}\nModel: ${analysis.model()}\nGenerated at (epoch milliseconds UTC): ${analysis.at()}\n\n"+report.text()+"\n"
   require(text.length<=MAX_BYTES/3){"The selected report is too large to export"}
   bytes=text.toByteArray(Charsets.UTF_8)
   val owned=checkNotNull(bytes);require(owned.size<=MAX_BYTES){"The selected report is too large to export"}
   var offset=0
   while(offset<owned.size) {
    validateSources(catalog,analysis,reader)
    val count=minOf(CHUNK_BYTES,owned.size-offset)
    output.write(owned,offset,count)
    validateSources(catalog,analysis,reader)
    offset+=count
   }
   validateSources(catalog,analysis,reader)
   output.flush()
   validateSources(catalog,analysis,reader)
  }finally{bytes?.fill(0)}
 }

 private fun requireOwner(catalog:EvidenceCatalog,reader:EvidenceSourceReader) {
  if(catalog.owner()!=reader.owner)throw SecurityException("The selected report belongs to another Space")
  check(reader.current()){"Report export authority retired"}
 }
 private fun validateSources(catalog:EvidenceCatalog,analysis:EvidenceCatalog.Analysis,reader:EvidenceSourceReader) {
  requireOwner(catalog,reader)
  // The persisted source references retain evidence-ID identity after records are removed.
  require(analysis.lineage().isNotEmpty()&&analysis.sourceReferences().isNotEmpty()){"The report has no verified source lineage"}
  val sources=LinkedHashMap<String,EvidenceCatalog.SourceFingerprint>()
  for(source in analysis.lineage()+analysis.sourceReferences().map{it.fingerprint()}) {
   val previous=sources.putIfAbsent(source.sourceId(),source)
   require(previous==null||previous==source){"The report has conflicting source lineage"}
  }
  for(source in sources.values) {
   check(reader.current()){"Report export authority retired"}
   if(source.vaultOnly())throw SecurityException("Vault-only evidence and its derived report cannot leave this Space")
   val live=reader.inspect(source.sourceId()).item
   VaultEgressPolicy.requireAllowed(live,VaultEgress.EXPORT)
   check(live.state==VaultItemState.COMPLETE&&live.id==source.sourceId()&&live.mimeType==source.mime()&&live.plaintextSize==source.size()&&
    live.vaultOnly==source.vaultOnly()&&live.plaintextSha256.joinToString(""){"%02x".format(it.toInt() and 255)}==source.sha256()){"A contributing original is unavailable, changed or restricted"}
   check(reader.current()){"Report export authority retired"}
  }
  requireOwner(catalog,reader)
 }
}
