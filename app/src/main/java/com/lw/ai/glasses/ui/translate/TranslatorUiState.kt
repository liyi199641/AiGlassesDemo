package com.lw.ai.glasses.ui.translate

import com.lw.top.lib_core.data.local.entity.TranslationMessageEntity
import com.lw.top.lib_core.data.local.entity.TranslationWithMessages
import kotlinx.serialization.Serializable

/**
 * 翻译模式枚举
 */
enum class TranslationMode {
    REAL_TIME, // 实时翻译 (通常用于听讲座、看电影)
    DIALOGUE   // 对话翻译 (通常用于面对面交谈)
}

data class TranslatorUiState(
    // 所有的历史会话及其消息
    val history: List<TranslationWithMessages> = emptyList(),
    // 当前正在展示的消息列表（通常取最近的一个 session 里的 messages）
    val currentMessages: List<TranslationMessageEntity> = emptyList(),
    val isRecording: Boolean = false,
    /** 实时翻译会话进行中（松手暂停，再次按住继续）。 */
    val isRealTimeSessionActive: Boolean = false,
    val recordingLanguage: String = "",
    val currentAmplitude: Float = 0f,
    val allLanguages: List<Language> = emptyList(),
    val srcLang: Language? = null,
    val targetLang: Language? = null,
    val currentMode: TranslationMode = TranslationMode.REAL_TIME,
    val error: String? = null
)

@Serializable
data class Language(
    val name: String,
    val nameEn: String,
    val langType: Int,
    val code: String
)
