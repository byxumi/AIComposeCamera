package com.aicamera.data

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.aicamera.domain.model.GalleryItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/** 相册仓库：从 MediaStore 查询照片，支持持续监听与一次性加载 */
class GalleryRepository(private val context: Context) {

    companion object {
        private val PROJECTION = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.SIZE
        )
        private const val SORT_ORDER = "${MediaStore.Images.Media.DATE_TAKEN} DESC"
    }

    /**
     * 持续观察相册中的照片（按拍摄时间倒序）。
     * 每次订阅都会执行一次查询；MediaStore 内容变化（如新拍摄、删除）
     * 可通过外部配合 [android.database.ContentObserver] 触发重新订阅。
     */
    fun observeImages(): Flow<List<GalleryItem>> = flow {
        emit(loadImages())
    }.flowOn(Dispatchers.IO)

    /** 一次性读取相册中的全部照片（按拍摄时间倒序） */
    suspend fun loadImages(): List<GalleryItem> = withContext(Dispatchers.IO) {
        val images = mutableListOf<GalleryItem>()
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        context.contentResolver.query(collection, PROJECTION, null, null, SORT_ORDER)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
            val widthCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
            val heightCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val uri = Uri.withAppendedPath(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    id.toString()
                ).toString()
                images += GalleryItem(
                    id = id,
                    uri = uri,
                    dateTaken = cursor.getLong(dateCol),
                    mimeType = cursor.getString(mimeCol) ?: "image/jpeg",
                    width = cursor.getInt(widthCol),
                    height = cursor.getInt(heightCol),
                    sizeBytes = cursor.getLong(sizeCol)
                )
            }
        }
        images
    }
}

/**
 * 注：权限（Android 13+ 需 READ_MEDIA_IMAGES，Android 12 及以下需
 * READ_EXTERNAL_STORAGE）需在调用前由调用方检查并授予，此处不做运行时请求。
 */