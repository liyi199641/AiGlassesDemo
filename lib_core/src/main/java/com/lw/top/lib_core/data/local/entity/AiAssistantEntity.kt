package com.lw.top.lib_core.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey


@Entity(tableName = "ai_assistant")
data class AiAssistantEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val question: String,
    val answer: String,
    val questionType: String,
    val answerType: String,
    val questionAudioPath: String? = null,
    /** AI 识图本地照片路径；与 [question] 文本同属问句侧，展示在答案上方。 */
    val questionImagePath: String? = null,
    val answerAudioPath: String? = null,
    val messageId: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
