package com.lw.ai.glasses.utils

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

fun getPathFromUri(context: Context, uri: Uri): String? {
    // 如果是 file:/// 开头的uri, 直接返回路径
    if ("file".equals(uri.scheme, ignoreCase = true)) {
        return uri.path
    }
    // 如果是 content:// 开头的uri, 将文件复制到内部缓存
    if ("content".equals(uri.scheme, ignoreCase = true)) {
        try {
            // 从URI获取原始文件名
            val fileName = getFileNameFromUri(context, uri)
            // 在应用的缓存目录中创建一个目标文件
            val cacheDir = context.cacheDir
            val tempFile = File(cacheDir, fileName)

            // 使用InputStream和FileOutputStream来复制文件
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                FileOutputStream(tempFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            // 返回我们创建的这个临时文件的绝对路径
            return tempFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
    return null
}

fun getFileNameFromUri(context: Context, uri: Uri): String {
    var fileName = ""
    // ContentResolver 可以查询文件的元数据
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        // 确保cursor可以移动到第一行
        if (cursor.moveToFirst()) {
            // 获取 DISPLAY_NAME 列的索引
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex != -1) {
                // 读取文件名
                fileName = cursor.getString(nameIndex)
            }
        }
    }
    // 如果通过ContentResolver获取失败, 尝试从URI的路径段获取
    return fileName.ifEmpty { uri.lastPathSegment ?: "unknown_file" }
}


//uri转File
suspend fun uriToFile(context: Context, uri: Uri): File = withContext(Dispatchers.IO) {
    val file = createImageFile(context)
    context.contentResolver.openInputStream(uri)?.use { inputStream: InputStream ->
        FileOutputStream(file).use { outputStream ->
            inputStream.copyTo(outputStream)
        }
    }
    return@withContext file
}

private fun createImageFile(context: Context): File {
    val storageDir = context.externalCacheDir
    return File.createTempFile(
        "IMG_${System.currentTimeMillis()}",
        ".jpg",
        storageDir
    )
}

// 获取文件大小（字节）
 fun getFileSize(context: Context, uri: Uri): Long {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        // Android 10+ 适配
        val cursor = context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.SIZE), null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                return it.getLong(0)
            }
        }
        0
    } else {
        // 低版本适配
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            return inputStream.available().toLong()
        }
        0
    }
}