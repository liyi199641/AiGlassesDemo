package com.lw.ai.glasses.ui.translate

import com.lw.top.lib_core.data.local.entity.TranslationMessageEntity
import com.lw.top.lib_core.data.local.entity.TranslationSessionEntity

/** 实时翻译：同一 requestId 的多条记录（文本/音频 messageId 不同）合并为一条。 */
fun mergeRealTimeSegments(
    messages: List<TranslationMessageEntity>,
): TranslationMessageEntity? {
    if (messages.isEmpty()) return null
    return messages.fold(messages.first()) { acc, msg ->
        acc.copy(
            messageId = TranslationSessionEntity.REAL_TIME_SEGMENT_MESSAGE_ID,
            originalText = mergeRealTimeTranslationText(
                existing = acc.originalText,
                incoming = msg.originalText,
                incomingIsFinished = msg.isFinished,
                existingIsFinished = acc.isFinished,
            ),
            translatedText = mergeRealTimeTranslationText(
                existing = acc.translatedText,
                incoming = msg.translatedText,
                incomingIsFinished = msg.isFinished,
                existingIsFinished = acc.isFinished,
            ),
            audioPath = msg.audioPath ?: acc.audioPath,
            isFinished = acc.isFinished || msg.isFinished,
            timestamp = maxOf(acc.timestamp, msg.timestamp),
        )
    }.copy(messageId = TranslationSessionEntity.REAL_TIME_SEGMENT_MESSAGE_ID)
}

private fun longestText(a: String, b: String): String = if (b.length >= a.length) b else a

fun longestTranslationText(existing: String?, incoming: String?): String {
    val left = existing.orEmpty()
    val right = incoming.orEmpty()
    return longestText(left, right)
}

/**
 * 实时翻译文本合并：recognizing 阶段取更长文本（打字机效果）；
 * recognized 最终结果直接覆盖，避免英文中间态因字符更长而卡住。
 */
fun mergeRealTimeTranslationText(
    existing: String?,
    incoming: String?,
    incomingIsFinished: Boolean,
    existingIsFinished: Boolean = false,
): String {
    val incomingText = incoming.orEmpty()
    if (incomingIsFinished && incomingText.isNotEmpty()) return incomingText
    if (existingIsFinished) return existing.orEmpty()
    return longestTranslationText(existing, incoming)
}

fun List<TranslationMessageEntity>.dedupeRealTimeByRequestId(): List<TranslationMessageEntity> {
    return groupBy { it.requestId }
        .mapNotNull { (_, group) -> mergeRealTimeSegments(group) }
        .sortedByDescending { it.timestamp }
}
