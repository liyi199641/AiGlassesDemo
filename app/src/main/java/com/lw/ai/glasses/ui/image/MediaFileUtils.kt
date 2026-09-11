package com.lw.ai.glasses.ui.image

import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class MediaFileType {
    IMAGE, VIDEO, AUDIO, UNKNOWN
}

object MediaFileUtils {

    private val videoExtensions = setOf("mp4", "mov", "avi", "mkv", "webm")
    private val audioExtensions = setOf("opus", "mp3", "wav", "aac", "m4a", "ogg")

    private val timePatterns = listOf(
        "yyyy/MM/dd HH:mm:ss",
        "yyyy-MM-dd HH:mm:ss",
        "yyyyMMddHHmmss",
        "yyyy/MM/dd",
        "yyyy-MM-dd",
    )

    fun typeOf(filePath: String): MediaFileType {
        val fileName = File(filePath).name
        if (fileName.startsWith("AUDIO_", ignoreCase = true)) {
            return MediaFileType.AUDIO
        }
        if (fileName.startsWith("VIDEO_", ignoreCase = true)) {
            return MediaFileType.VIDEO
        }
        return when (File(filePath).extension.lowercase()) {
            in videoExtensions -> MediaFileType.VIDEO
            in audioExtensions -> MediaFileType.AUDIO
            in setOf("jpg", "jpeg", "png", "webp", "gif", "bmp") -> MediaFileType.IMAGE
            else -> MediaFileType.UNKNOWN
        }
    }

    fun typeString(filePath: String): String = typeOf(filePath).name

    fun resolveCreatedAt(filePath: String, fileModifiedTime: String): Long {
        parseTimeString(fileModifiedTime)?.let { return it }
        val file = File(filePath)
        if (file.exists()) {
            return file.lastModified()
        }
        return System.currentTimeMillis()
    }

    fun startOfDay(timestamp: Long): Long {
        return Calendar.getInstance().apply {
            timeInMillis = timestamp
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun parseTimeString(time: String): Long? {
        val trimmed = time.trim()
        if (trimmed.isEmpty()) return null
        trimmed.toLongOrNull()?.let { return it }
        for (pattern in timePatterns) {
            try {
                val parsed = SimpleDateFormat(pattern, Locale.getDefault()).parse(trimmed)
                if (parsed != null) return parsed.time
            } catch (_: Exception) {
            }
        }
        return null
    }
}
