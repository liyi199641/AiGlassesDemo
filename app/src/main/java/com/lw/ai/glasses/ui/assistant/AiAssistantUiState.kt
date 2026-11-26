package com.lw.ai.glasses.ui.assistant

import com.lw.ai.glasses.ui.common.WsConnectionUiState
import com.lw.ai.glasses.ui.translate.Language
import com.lw.top.lib_core.data.local.entity.AiAssistantEntity

data class AiAssistantUiState(
    val messages: List<AiAssistantEntity> = emptyList(),
    val streamingMessageId: Long? = null,
    val playingAnswerAudioPath: String? = null,
    val playingQuestionAudioPath: String? = null,
    val agentAudioPlaybackEnabled: Boolean = true,
    /** SDK 上报的 AI 对话进行中（录音 / 等待回复 / TTS 下发），收到 tts stop 即结束。 */
    val isAiDialogueInProgress: Boolean = false,
    val wsConnection: WsConnectionUiState = WsConnectionUiState(),
    /** 进入界面同步打字机进度后递增，驱动 UI 读取最新缓存长度。 */
    val typewriterRevision: Int = 0,
    val allLanguages: List<Language> = emptyList(),
    val selectedLanguage: Language? = null,
    val locationPermissionGranted: Boolean = false,
    /** semi-auto 一问一答展示用户录音；auto 全双工为持续收音，不展示问题侧回放。 */
    val showQuestionAudioPlayback: Boolean = true,
    /** auto 流式回答内容版本，与 [typewriterRevision] 一并驱动列表刷新。 */
    val streamingContentRevision: Int = 0,
)
