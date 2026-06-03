package com.lw.top.lib_core.data.local.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/**
 * 主表：代表一次完整的对话 Session
 */
@Entity(tableName = "translation_sessions")
data class TranslationSessionEntity(
    @PrimaryKey
    val requestId: String, // 会话唯一 ID
    val sourceLang: String,
    val targetLang: String,
    /** [MODE_REAL_TIME] 或 [MODE_DIALOGUE] */
    val translationMode: String = MODE_REAL_TIME,
    val timestamp: Long = System.currentTimeMillis(),
) {
    companion object {
        const val MODE_REAL_TIME = "real_time"
        const val MODE_DIALOGUE = "dialogue"
        /** 实时翻译片段固定 messageId，与 requestId 组合后唯一标识一句话。 */
        const val REAL_TIME_SEGMENT_MESSAGE_ID = "0"
    }
}

/**
 * 副表：代表会话中的每一段消息片段
 * 使用 (requestId, messageId) 作为复合主键，确保不同会话间的片段互不干扰
 */
@Entity(
    tableName = "translation_messages",
    primaryKeys = ["requestId", "messageId"],
    foreignKeys = [
        ForeignKey(
            entity = TranslationSessionEntity::class,
            parentColumns = ["requestId"],
            childColumns = ["requestId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["requestId"])]
)
data class TranslationMessageEntity(
    val messageId: String, // 会话内片段 ID (1, 2, 3...)
    val requestId: String, // 关联的主表 ID
    val originalText: String,
    val translatedText: String,
    val audioPath: String? = null,
    val isUser: Boolean = true,
    val isFinished: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * 关系类：用于 UI 展示
 */
data class TranslationWithMessages(
    @Embedded val session: TranslationSessionEntity,
    @Relation(
        parentColumn = "requestId",
        entityColumn = "requestId"
    )
    val messages: List<TranslationMessageEntity>
)
