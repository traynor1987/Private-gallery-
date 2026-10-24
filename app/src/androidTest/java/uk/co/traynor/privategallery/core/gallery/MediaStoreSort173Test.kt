package uk.co.traynor.privategallery.core.gallery

import android.content.ContentResolver
import android.os.Bundle
import android.provider.MediaStore
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import uk.co.traynor.privategallery.core.ui.MediaSort
import uk.co.traynor.privategallery.core.ui.MediaOrder

class MediaStoreSort173Test {
 @Test fun allSortExpressionsAreAcceptedByRealScopedMediaProvider() {
  val resolver=InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
  MediaSort.entries.forEach {mode ->
   val args=Bundle().apply {
    putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER,MediaOrder.sql(mode.order))
    putInt(ContentResolver.QUERY_ARG_LIMIT,1)
   }
   resolver.query(MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL),arrayOf(MediaStore.MediaColumns._ID),args,null).use {cursor ->
    assertNotNull("MediaStore rejected ${mode.name}",cursor)
   }
  }
 }
}
