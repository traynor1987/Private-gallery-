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
}
