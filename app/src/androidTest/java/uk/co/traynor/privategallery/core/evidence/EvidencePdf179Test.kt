package uk.co.traynor.privategallery.core.evidence
import org.junit.Assert.*
import org.junit.Test
class EvidencePdf179Test {
 @Test fun reportHasPdfHeaderAndReadableProvenanceTextSource(){
  val f=EvidenceArchive179Test.Fixture();val selected=EvidenceArchive.select(f.plan(),f.reader)
  val text=EvidencePdf.text(selected)
  for(label in listOf("raw OCR","corrected transcript","Provider claim","not legal certification","SHA-256","Source filename"))assertTrue(label,text.contains(label))
  val pdf=EvidencePdf.render(f.plan(),f.reader);assertTrue(pdf.size<=20*1024*1024);assertEquals("%PDF-",String(pdf.copyOfRange(0,5)))
  val context=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
  val (bitmap,pages)=uk.co.traynor.privategallery.core.vault.VaultPdfPreview.page(context,pdf,0){true}
  try{assertTrue(pages>0);val pixels=IntArray(bitmap.width*bitmap.height);bitmap.getPixels(pixels,0,bitmap.width,0,0,bitmap.width,bitmap.height);assertTrue(pixels.any{it!=android.graphics.Color.WHITE});pixels.fill(0)}finally{bitmap.eraseColor(0);bitmap.recycle();pdf.fill(0)}
 }
 @Test fun multipageWrappingUnicodeAndProvenanceRemainReadableAtEndOfReport(){
  val f=EvidenceArchive179Test.Fixture()
  val note=(1..180).joinToString("\n"){"Paragraph $it — café résumé naïve: "+"wrapped source provenance and owner notes ".repeat(6)}+"\nFINAL_REPORT_SENTINEL"
  val r=f.record;val amended=EvidenceCatalog.Evidence(r.id(),r.sourceId(),r.name(),r.mime(),r.sha256(),r.size(),r.importedAt(),r.metadata(),r.description(),note,r.tags(),r.participants(),r.ocr(),r.corrections())
  f.catalog=f.catalog.upsertEvidence(amended,10,"OWNER_NOTE")
  val pdf=EvidencePdf.render(f.plan(),f.reader)
  val context=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
  val thread=android.os.HandlerThread("evidence-pdf-test").apply{start()}
  var reply:ByteArray?=null
  val callback=object:android.os.ProxyFileDescriptorCallback(){
   override fun onGetSize()=pdf.size.toLong()
   override fun onRead(offset:Long,size:Int,data:ByteArray):Int{reply?.takeIf{it!==data}?.fill(0);reply=data;data.fill(0);val n=minOf(size.toLong(),(pdf.size-offset).coerceAtLeast(0)).toInt();if(n>0)pdf.copyInto(data,0,offset.toInt(),offset.toInt()+n);return n}
   override fun onRelease(){reply?.fill(0)}
  }
  try{
   val descriptor=context.getSystemService(android.os.storage.StorageManager::class.java).openProxyFileDescriptor(android.os.ParcelFileDescriptor.MODE_READ_ONLY,callback,android.os.Handler(thread.looper))
   android.graphics.pdf.PdfRenderer(descriptor).use{renderer->
    assertTrue("long paragraphs must paginate",renderer.pageCount>4)
    val text=StringBuilder()
    for(index in 0 until renderer.pageCount)renderer.openPage(index).use{page->
     val fragments=page.textContents;assertTrue("page $index has readable text",fragments.isNotEmpty())
     text.append(fragments.joinToString(" "){it.text}).append('\n')
     for(fragment in fragments)for(bounds in fragment.bounds){assertTrue("text stays on page",bounds.left>=0&&bounds.right<=page.width+1&&bounds.top>=0&&bounds.bottom<=page.height+1)}
    }
    val extracted=text.toString()
    for(expected in listOf("café","résumé","naïve","FINAL_REPORT_SENTINEL","SHA-256","Source filename","corrected transcript"))assertTrue("PDF text missing $expected",extracted.contains(expected))
   }
  }finally{reply?.fill(0);pdf.fill(0);thread.quitSafely()}
 }
}
