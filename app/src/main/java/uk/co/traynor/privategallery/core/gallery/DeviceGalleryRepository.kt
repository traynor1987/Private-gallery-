package uk.co.traynor.privategallery.core.gallery

import android.content.ContentResolver
import android.content.Context
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.withContext

enum class DeviceMediaKind { IMAGE, VIDEO }

data class DeviceMediaItem(
    val id: Long,
    val uri: Uri,
    val kind: DeviceMediaKind,
    val displayName: String,
    val mimeType: String,
    val takenAtMillis: Long,
    val durationMillis: Long,
)

/** Pure permission policy; the Android permission names stay at the Activity boundary. */
object DeviceGalleryPolicy {
    fun canBrowse(imagesGranted: Boolean, videosGranted: Boolean, selectedGranted: Boolean): Boolean =
        imagesGranted || videosGranted || selectedGranted
}

/** Reads metadata only. Grid bitmaps are separately requested at thumbnail size. */
class DeviceGalleryRepository(context: Context) {
    private val appContext = context.applicationContext

    /** The observer is scoped to collection. Cancelling the Gallery collector always unregisters it. */
    fun pagedItems(): Flow<PagingData<DeviceMediaItem>> = callbackFlow<Unit> {
        val resolver = appContext.contentResolver
        val observer = object : ContentObserver(null) {
            override fun onChange(selfChange: Boolean, uri: Uri?) { trySend(Unit) }
        }
        resolver.registerContentObserver(MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL), true, observer)
        awaitClose { resolver.unregisterContentObserver(observer) }
    }.onStart { emit(Unit) }.flatMapLatest {
        Pager(
        config = PagingConfig(
            pageSize = DeviceGalleryPagePolicy.PAGE_SIZE,
            initialLoadSize = DeviceGalleryPagePolicy.PAGE_SIZE,
            prefetchDistance = DeviceGalleryPagePolicy.PAGE_SIZE / 2,
            maxSize = DeviceGalleryPagePolicy.MAX_RESIDENT_ITEMS,
            enablePlaceholders = false,
        ),
        pagingSourceFactory = { MediaStorePagingSource(appContext.contentResolver) },
    ).flow
    }

    private class MediaStorePagingSource(
        private val contentResolver: ContentResolver,
    ) : PagingSource<Int, DeviceMediaItem>() {
        override suspend fun load(params: LoadParams<Int>): LoadResult<Int, DeviceMediaItem> = withContext(Dispatchers.IO) {
            runCatching {
                val page = params.key ?: 0
                val data = queryPage(page, params.loadSize)
                LoadResult.Page(
                    data = data,
                    prevKey = if (page == 0) null else page - 1,
                    nextKey = if (DeviceGalleryPagePolicy.hasNextPage(data.size)) page + 1 else null,
                )
            }.getOrElse { LoadResult.Error(it) }
        }

        override fun getRefreshKey(state: PagingState<Int, DeviceMediaItem>): Int? =
            state.anchorPosition?.let { anchor -> state.closestPageToPosition(anchor)?.prevKey?.plus(1) }

        private fun queryPage(page: Int, requestedSize: Int): List<DeviceMediaItem> {
            val limit = requestedSize.coerceIn(1, DeviceGalleryPagePolicy.PAGE_SIZE)
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.DATE_TAKEN,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.Video.VideoColumns.DURATION,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
        )
        val query = Bundle().apply {
            putString(ContentResolver.QUERY_ARG_SQL_SELECTION, "${MediaStore.Files.FileColumns.MEDIA_TYPE}=? OR ${MediaStore.Files.FileColumns.MEDIA_TYPE}=?")
            putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, arrayOf(
                MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
                MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString(),
            ))
            putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, "${MediaStore.MediaColumns.DATE_TAKEN} DESC, ${MediaStore.MediaColumns.DATE_ADDED} DESC")
            putInt(ContentResolver.QUERY_ARG_LIMIT, limit)
            putInt(ContentResolver.QUERY_ARG_OFFSET, DeviceGalleryPagePolicy.offsetForPage(page))
        }
        return contentResolver.query(
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL),
            projection,
            query,
            null,
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val mimeIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
            val takenIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_TAKEN)
            val addedIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
            val durationIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.VideoColumns.DURATION)
            val mediaTypeIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
            buildList {
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idIndex)
                    val kind = if (cursor.getInt(mediaTypeIndex) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO) DeviceMediaKind.VIDEO else DeviceMediaKind.IMAGE
                    val uri = if (kind == DeviceMediaKind.VIDEO) {
                        Uri.withAppendedPath(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id.toString())
                    } else {
                        Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString())
                    }
                    add(DeviceMediaItem(
                        id = id,
                        uri = uri,
                        kind = kind,
                        displayName = cursor.getString(nameIndex).orEmpty(),
                        mimeType = cursor.getString(mimeIndex).orEmpty(),
                        takenAtMillis = cursor.getLong(takenIndex).takeIf { it > 0 } ?: cursor.getLong(addedIndex) * 1000,
                        durationMillis = cursor.getLong(durationIndex),
                    ))
                }
            }
        }.orEmpty()
        }
    }

    suspend fun thumbnail(item: DeviceMediaItem, size: Int): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.contentResolver.loadThumbnail(item.uri, Size(size, size), null)
            } else {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(item.uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                val largest = maxOf(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)
                var sample = 1
                while (largest / (sample * 2) >= size) sample *= 2
                context.contentResolver.openInputStream(item.uri)?.use {
                    BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
                }
            }
        }.getOrNull()
    }
}
