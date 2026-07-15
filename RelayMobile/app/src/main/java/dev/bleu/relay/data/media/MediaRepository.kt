package dev.bleu.relay.data.media

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import dev.bleu.relay.data.model.MediaCategory
import dev.bleu.relay.data.model.MediaFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MediaRepository(private val context: Context) {

    suspend fun queryImages(): List<MediaFile> = withContext(Dispatchers.IO) {
        query(
            collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            category = MediaCategory.IMAGES,
            extraMimeFilter = null
        )
    }

    suspend fun queryVideos(): List<MediaFile> = withContext(Dispatchers.IO) {
        query(
            collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            category = MediaCategory.VIDEOS,
            extraMimeFilter = null
        )
    }

    suspend fun queryFiles(): List<MediaFile> = withContext(Dispatchers.IO) {
        query(
            collection = MediaStore.Files.getContentUri("external"),
            category = MediaCategory.FILES,
            extraMimeFilter = "application/%"
        ) + query(
            collection = MediaStore.Files.getContentUri("external"),
            category = MediaCategory.FILES,
            extraMimeFilter = "text/%"
        )
    }

    private fun query(
        collection: Uri,
        category: MediaCategory,
        extraMimeFilter: String?
    ): List<MediaFile> {
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.DATE_MODIFIED
        )
        val selection = extraMimeFilter?.let { "${MediaStore.MediaColumns.MIME_TYPE} LIKE ?" }
        val selectionArgs = extraMimeFilter?.let { arrayOf(it) }
        val sortOrder = "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"

        val results = mutableListOf<MediaFile>()
        val cursor: Cursor = context.contentResolver.query(
            collection, projection, selection, selectionArgs, sortOrder
        ) ?: return results

        cursor.use { c ->
            val idCol       = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameCol     = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val sizeCol     = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val mimeCol     = c.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
            val dateCol     = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)

            while (c.moveToNext()) {
                val id   = c.getLong(idCol)
                val name = c.getString(nameCol) ?: continue
                val size = c.getLong(sizeCol)
                val mime = c.getString(mimeCol) ?: "application/octet-stream"
                val date = c.getLong(dateCol) * 1000L
                val uri  = Uri.withAppendedPath(collection, id.toString())
                results += MediaFile(uri, name, size, mime, date, category)
            }
        }
        return results
    }
}
