package com.aicamera.camera

import android.content.ContentValues
import android.content.Context
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileInputStream
import java.io.OutputStream

/**
 * 照片保存：写入系统相册（Pictures/AICamera），并写 EXIF 时间与备注。
 */
object PhotoSaver {

    /** 保存到相册，返回 content Uri（失败返回 null） */
    fun saveToGallery(context: Context, file: File): Uri? {
        // 先确保 EXIF 完整
        try {
            val ei = ExifInterface(file.absolutePath)
            ei.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, "now")
            ei.saveAttributes()
        } catch (_: Exception) {}

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/AICamera")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val uri = resolver.insert(collection, values) ?: return null
        try {
            resolver.openOutputStream(uri)?.use { out: OutputStream ->
                FileInputStream(file).use { input ->
                    input.copyTo(out)
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            return uri
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            return null
        }
    }

    /** 读取 EXIF 备注（可选） */
    fun readExifComment(file: File): String? = try {
        ExifInterface(file.absolutePath).getAttribute(ExifInterface.TAG_USER_COMMENT)
    } catch (_: Exception) {
        null
    }
}