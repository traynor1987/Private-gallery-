package uk.co.traynor.privategallery.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.paging.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import uk.co.traynor.privategallery.GalleryHome
import uk.co.traynor.privategallery.core.gallery.*
import java.util.concurrent.atomic.AtomicInteger

class GalleryPagingStabilityTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun phonePagingRemainsBoundedAndScrollBackKeepsIdentity() = verifyPaging(390, 800)
    @Test fun foldPagingRemainsBoundedAndScrollBackKeepsIdentity() = verifyPaging(840, 900)

    private fun verifyPaging(width: Int, height: Int) {
        val loads = AtomicInteger()
        val trace = java.util.concurrent.ConcurrentLinkedQueue<String>()
        val pages = Pager(PagingConfig(pageSize = 120, initialLoadSize = 120, prefetchDistance = 30, maxSize = 480, enablePlaceholders = false)) {
            object : PagingSource<Int, DeviceMediaItem>() {
                override suspend fun load(params: LoadParams<Int>): LoadResult<Int, DeviceMediaItem> {
                    loads.incrementAndGet()
                    val start = params.key ?: 0
                    trace.add("${params.javaClass.simpleName}:$start:${params.loadSize}")
                    android.util.Log.i("GalleryPagingTest", trace.joinToString())
                    return LoadResult.Page((start until (start + 120).coerceAtMost(1200)).map { id ->
                        DeviceMediaItem(id.toLong(), android.net.Uri.parse("content://synthetic/$id"), DeviceMediaKind.IMAGE, "Photo $id", "image/jpeg", 0, 0)
                    }, if (start == 0) null else start - 120, if (start + 120 < 1200) start + 120 else null)
                }
                override fun getRefreshKey(state: PagingState<Int, DeviceMediaItem>): Int? = state.anchorPosition?.let { state.closestItemToPosition(it)?.id?.toInt()?.div(120)?.times(120) }
            }
        }.flow
        // CI's 1200x1800 dp display legitimately fills more than two pages. Test
        // actual phone/Fold viewports so the load bound measures unwanted paging.
        compose.setContent { PrivateGalleryTheme {
            Box(Modifier.requiredSize(width.dp, height.dp)) {
                GalleryHome(true, {}, { pages }, { _, done -> done(null) }, { _, _ -> }, { _, _ -> }, { _, _ -> })
            }
        } }
        compose.waitUntil(5000) { compose.onAllNodesWithContentDescription("Photo 0").fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        assertTrue("Grid key lookup must not trigger eager paging; loads=$trace", loads.get() <= 2)
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(100)
        compose.waitUntil(5000) { loads.get() >= 2 }
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
        compose.onNodeWithContentDescription("Photo 0").assertIsDisplayed()
        compose.onAllNodesWithContentDescription("Photo 0").assertCountEquals(1)
    }
}
