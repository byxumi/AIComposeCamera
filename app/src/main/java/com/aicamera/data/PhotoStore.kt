package com.aicamera.data

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File

/**
 * 照片/视频保存、删除与分享。
 * 写入采用 MediaStore（Android 10+ 无需存储权限；低版本由调用方保证
 * WRITE_EXTERNAL_STORAGE 已授予）。
 */
object PhotoStore {

    private const val RELATIVE_DIR = "Pictures/AICamera"

    /**
     * 将图片文件写入系统相册（MediaStore.Images）。
     * @return 写入成功与否
     */
    fun saveImage(context: Context, file: File): Boolean {
        val mimeType = "image/jpeg"
        return insertInto(
            context = context,
            collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
            legacyCollection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            displayName = file.name,
            mimeType = mimeType,
            file = file
        )
    }

    /**
     * 将视频文件写入系统相册（MediaStore.Video）。
     * @return 写入成功与否
     */
    fun saveVideo(context: Context, file: File): Boolean {
        val mimeType = "video/mp4"
        return insertInto(
            context = context,
            collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
            legacyCollection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            displayName = file.name,
            mimeType = mimeType,
            file = file
        )
    }

    private fun insertInto(
        context: Context,
        collection: Uri,
        legacyCollection: Uri,
        displayName: String,
        mimeType: String,
        file: File
    ): Boolean {
        if (!file.exists()) return false

        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, RELATIVE_DIR)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        // API 29+ 走系统相册集合（无需存储权限）；低版本走 EXTERNAL_CONTENT_URI（需 WRITE_EXTERNAL_STORAGE）
        val targetCollection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            collection
        } else {
            legacyCollection
        }

        val uri = resolver.insert(targetCollection, values) ?: return false
        return try {
            resolver.openOutputStream(uri)?.use { output ->
                file.inputStream().use { input -> input.copyTo(output) }
            } ?: return false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // 标记为完成，媒体扫描才会收录
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            true
        } catch (e: Exception) {
            // 写入失败时清理残留条目
            resolver.delete(uri, null, null)
            false
        }
    }

    /**
     * 删除相册中的一条媒体。
     * @return 删除成功与否
     */
    fun delete(context: Context, uri: Uri): Boolean {
        return try {
            context.contentResolver.delete(uri, null, null) > 0
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 通过系统分享面板分享一条媒体。
     * @param uri 指向相册媒体（图片/视频）的 content:// Uri
     */
    fun share(context: Context, uri: Uri) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = if (uri.toString().contains("video")) "video/*" else "image/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "分享"))
    }

    /**
     * 为分享等场景生成对外可读的 content:// Uri。
     * @param authority 需与 AndroidManifest 中 FileProvider 的 authority 一致
     */
    fun uriForFile(context: Context, file: File, authority: String): Uri =
        FileProvider.getUriForFile(context, authority, file)
}