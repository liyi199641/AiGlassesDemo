package com.lw.ai.glasses.config

import java.util.Locale

/** AI 对话源语种默认值：国内中文 140，非国内英文 47。 */
object AiDialogueLanguageDefaults {
    const val CHINESE = 140
    const val ENGLISH = 47

    fun defaultLangType(locale: Locale = Locale.getDefault()): Int {
        return if (locale.country.equals("CN", ignoreCase = true)) CHINESE else ENGLISH
    }
}
