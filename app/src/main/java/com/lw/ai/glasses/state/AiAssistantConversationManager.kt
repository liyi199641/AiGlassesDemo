package com.lw.ai.glasses.state

import com.blankj.utilcode.util.LogUtils
import com.fission.wear.glasses.sdk.AiAssistantClient
import com.fission.wear.glasses.sdk.GlassesManage
import com.fission.wear.glasses.sdk.data.dto.AiChatMessageDTO
import com.fission.wear.glasses.sdk.data.dto.AiContentType
import com.fission.wear.glasses.sdk.events.AgentEvent
import com.fission.wear.glasses.sdk.events.CmdResultEvent
import com.lw.top.lib_core.data.local.entity.AiAssistantEntity
import com.lw.top.lib_core.data.repository.AiAssistantRepository
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class StreamState(
    val displayedQuestionLength: Int = 0,
    val displayedAnswerLength: Int = 0,
)

data class AiAssistantConversationState(
    val messages: List<AiAssistantEntity> = emptyList(),
    /** 当前正在流式回复的消息 timestamp；历史记录为 null，不参与打字机动画。 */
    val streamingMessageId: Long? = null,
)

/**
 * 全局 AI 对话消息接收：应用启动时订阅 [AgentEvent] 与眼镜事件，不依赖 AI 对话页生命周期。
 */
@Singleton
class AiAssistantConversationManager @Inject constructor(
    private val repository: AiAssistantRepository,
) {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val started = AtomicBoolean(false)

    private var currentMessage: AiAssistantEntity? = null
    /** 停听音频先于 ASR 文本到达时暂存，等有问题文本再挂到气泡上。 */
    private var pendingQuestionAudioPath: String? = null
    private val typewriterProgress = mutableMapOf<Long, StreamState>()

    private val _state = MutableStateFlow(AiAssistantConversationState())
    val state: StateFlow<AiAssistantConversationState> = _state.asStateFlow()

    fun start() {
        if (!started.compareAndSet(false, true)) return
        loadHistoryMessages()
        observeAiAssistantEvents()
        observeGlassesEvents()
    }

    fun clearAllMessages() {
        appScope.launch {
            repository.clearAllMessages()
            currentMessage = null
            pendingQuestionAudioPath = null
            typewriterProgress.clear()
            _state.value = AiAssistantConversationState()
        }
    }

    fun getTypewriterProgress(timestamp: Long): StreamState {
        return typewriterProgress[timestamp] ?: StreamState()
    }

    fun updateTypewriterProgress(
        timestamp: Long,
        questionLength: Int? = null,
        answerLength: Int? = null,
    ) {
        val current = getTypewriterProgress(timestamp)
        typewriterProgress[timestamp] = current.copy(
            displayedQuestionLength = questionLength ?: current.displayedQuestionLength,
            displayedAnswerLength = answerLength ?: current.displayedAnswerLength,
        )
    }

    /** 将已收到的内容标记为已展示，避免重新从首字播放打字机。 */
    fun syncTypewriterProgressToContent(timestamp: Long, questionLength: Int, answerLength: Int) {
        val current = getTypewriterProgress(timestamp)
        typewriterProgress[timestamp] = current.copy(
            displayedQuestionLength = maxOf(current.displayedQuestionLength, questionLength),
            displayedAnswerLength = maxOf(current.displayedAnswerLength, answerLength),
        )
    }

    fun syncStreamingTypewriterProgressToContent() {
        val streamingId = _state.value.streamingMessageId ?: return
        val message = _state.value.messages.find { it.timestamp == streamingId } ?: return
        syncTypewriterProgressToContent(
            timestamp = streamingId,
            questionLength = message.question.length,
            answerLength = message.answer.length,
        )
    }

    private fun loadHistoryMessages() {
        appScope.launch {
            val history = repository.getAllMessages()
            _state.value = _state.value.copy(messages = history)
        }
    }

    private fun observeAiAssistantEvents() {
        appScope.launch {
            AiAssistantClient.getInstance().aiAgentEventFlow().collect { event ->
                when (event) {
                    is AgentEvent.AiAssistantResult -> {
//                        LogUtils.d("AiAssistantEvent.AiAssistantResult${event.data}")
                        handleStreamingResult(event.data)
                    }
                    else -> Unit
                }
            }
        }
    }

    private fun observeGlassesEvents() {
        appScope.launch {
            GlassesManage.eventFlow().collect { event ->
                when (event) {
                    is CmdResultEvent.ImageFile -> {
                        event.imageFile?.let { file ->
                            handleStreamingResult(
                                AiChatMessageDTO(
                                    question = file.absolutePath,
                                    questionType = AiContentType.IMAGE_PATH,
                                    isFinished = true,
                                ),
                            )
                        }
                    }
                    else -> Unit
                }
            }
        }
    }

    private suspend fun handleStreamingResult(result: AiChatMessageDTO) {
        val questionText = anyToStringSafe(result.question)
        val answerText = anyToStringSafe(result.answer)
        val questionAudioPath = result.questionAudioPath?.takeIf { it.isNotBlank() }
        val answerAudioPath = result.answerAudioPath?.takeIf { it.isNotBlank() }
        val serverMessageId = result.id?.takeIf { it.isNotBlank() }
        if (questionText.isEmpty() && answerText.isEmpty() &&
            questionAudioPath == null && answerAudioPath == null && !result.isFinished
        ) return

        val newList = _state.value.messages.toMutableList()

        when {
            questionText.isNotEmpty() -> {
                if (currentMessage?.answer?.isNotEmpty() == true) {
                    finalizeCurrentMessage(newList)
                }
                val resolvedQuestionAudio =
                    questionAudioPath
                        ?: pendingQuestionAudioPath
                        ?: currentMessage?.questionAudioPath
                if (questionAudioPath != null || pendingQuestionAudioPath != null) {
                    pendingQuestionAudioPath = null
                }
                currentMessage = if (
                    currentMessage?.answer.isNullOrEmpty() &&
                    currentMessage?.question?.isNotEmpty() == true
                ) {
                    currentMessage!!.copy(
                        question = questionText,
                        questionType = mapContentType(result.questionType),
                        questionAudioPath = resolvedQuestionAudio,
                        messageId = serverMessageId ?: currentMessage!!.messageId,
                    )
                } else {
                    AiAssistantEntity(
                        question = questionText,
                        questionType = mapContentType(result.questionType),
                        answer = "",
                        answerType = "",
                        questionAudioPath = resolvedQuestionAudio,
                        messageId = serverMessageId,
                        timestamp = System.currentTimeMillis(),
                    )
                }
            }

            questionAudioPath != null -> {
                // 仅挂到已有 ASR 文本的消息；纯音频默认不单独展示。
                val targetMessage = findMessageByServerId(newList, serverMessageId)
                    ?: currentMessage?.takeIf {
                        it.question.isNotEmpty() &&
                            (serverMessageId == null || it.messageId == serverMessageId)
                    }
                    ?: newList.firstOrNull()?.takeIf {
                        it.question.isNotEmpty() && it.questionAudioPath.isNullOrBlank()
                    }
                if (targetMessage != null) {
                    currentMessage = targetMessage.copy(
                        questionAudioPath = questionAudioPath,
                        messageId = serverMessageId ?: targetMessage.messageId,
                    )
                } else {
                    pendingQuestionAudioPath = questionAudioPath
                    return
                }
            }

            answerText.isNotEmpty() -> {
                if (currentMessage?.question?.isNotEmpty() == true && currentMessage!!.answer.isEmpty()) {
                    finalizeCurrentMessage(newList)
                }
                currentMessage = if (currentMessage != null) {
                    currentMessage!!.copy(
                        answer = currentMessage!!.answer + answerText,
                        answerType = mapContentType(result.answerType),
                        messageId = serverMessageId ?: currentMessage!!.messageId,
                    )
                } else {
                    findMessageByServerId(newList, serverMessageId)?.let { existing ->
                        existing.copy(
                            answer = existing.answer + answerText,
                            answerType = mapContentType(result.answerType),
                        )
                    } ?: AiAssistantEntity(
                        question = "",
                        questionType = "",
                        answer = answerText,
                        answerType = mapContentType(result.answerType),
                        messageId = serverMessageId,
                        timestamp = System.currentTimeMillis(),
                    )
                }
            }

            answerAudioPath != null -> {
                val targetMessage = findMessageByServerId(newList, serverMessageId)
                    ?: currentMessage?.takeIf {
                        serverMessageId == null || it.messageId == serverMessageId
                    }
                currentMessage = if (targetMessage != null) {
                    targetMessage.copy(
                        answerAudioPath = answerAudioPath,
                        answerType = mapContentType(result.answerType),
                        messageId = serverMessageId ?: targetMessage.messageId,
                    )
                } else {
                    AiAssistantEntity(
                        question = "",
                        questionType = "",
                        answer = "",
                        answerType = mapContentType(result.answerType),
                        answerAudioPath = answerAudioPath,
                        messageId = serverMessageId,
                        timestamp = System.currentTimeMillis(),
                    )
                }
            }
        }

        currentMessage?.let { message ->
            upsertMessageInList(newList, message)
            // 停听音频地址回填到已落库消息时，同步写库并结束当前流。
            if (message.id != 0L &&
                questionAudioPath != null &&
                questionText.isEmpty() &&
                answerText.isEmpty() &&
                !result.isFinished
            ) {
                repository.insertMessage(message)
                currentMessage = null
                _state.value = _state.value.copy(
                    messages = newList,
                    streamingMessageId = null,
                )
                return
            }
            _state.value = _state.value.copy(
                messages = newList,
                streamingMessageId = message.timestamp,
            )
        }

        if (result.isFinished) {
            currentMessage?.let { finalizeCurrentMessage(newList) }
            _state.value = _state.value.copy(
                messages = newList,
                streamingMessageId = null,
            )
            currentMessage = null
        }
    }

    private suspend fun finalizeCurrentMessage(list: MutableList<AiAssistantEntity>) {
        val message = currentMessage ?: return
        // 无 ASR 文本的纯录音不落库、不展示。
        if (message.question.isEmpty() && message.answer.isEmpty() &&
            message.answerAudioPath.isNullOrBlank()
        ) {
            currentMessage = null
            return
        }
        val existingInList = message.messageId?.let { msgId ->
            list.find { it.messageId == msgId && it.id != 0L }
        }
        val toPersist = if (existingInList != null) {
            message.copy(id = existingInList.id)
        } else {
            message
        }
        val persistedId = if (toPersist.id != 0L) {
            repository.insertMessage(toPersist)
            toPersist.id
        } else {
            repository.insertMessageAndGetId(toPersist)
        }
        upsertMessageInList(list, toPersist.copy(id = persistedId))
        currentMessage = null
    }

    private fun upsertMessageInList(list: MutableList<AiAssistantEntity>, message: AiAssistantEntity) {
        val index = list.indexOfFirst {
            it.timestamp == message.timestamp ||
                (!message.messageId.isNullOrBlank() && it.messageId == message.messageId)
        }
        if (index >= 0) {
            list[index] = message
        } else {
            list.add(0, message)
        }
    }

    private fun findMessageByServerId(
        list: List<AiAssistantEntity>,
        serverMessageId: String?,
    ): AiAssistantEntity? {
        if (serverMessageId.isNullOrBlank()) return null
        return list.find { it.messageId == serverMessageId }
    }

    private fun mapContentType(type: AiContentType): String {
        return when (type) {
            AiContentType.TEXT -> "txt"
            AiContentType.IMAGE_PATH, AiContentType.IMAGE_FILE -> "image"
            AiContentType.AUDIO_DATA -> "audio"
            else -> ""
        }
    }

    private fun anyToStringSafe(any: Any?): String {
        return when (any) {
            null -> ""
            is String -> any
            is ByteArray -> ""
            is File -> any.absolutePath
            else -> any.toString()
        }
    }
}
