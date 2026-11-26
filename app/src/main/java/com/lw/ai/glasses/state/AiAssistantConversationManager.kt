package com.lw.ai.glasses.state

import com.blankj.utilcode.util.LogUtils
import com.fission.wear.glasses.sdk.AiAssistantClient
import com.fission.wear.glasses.sdk.manager.assistant.SttDisplayTextMerger
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
    /** auto 流式回答每次快照更新 +1，驱动 UI 即时刷新。 */
    val streamingContentRevision: Int = 0,
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
    /** STT messageId → 气泡 timestamp（每轮问句唯一）。 */
    private val sttMessageIdToTurnTimestamp = mutableMapOf<String, Long>()
    /** auto 连续对话：TTS messageId → 气泡 timestamp（与 STT messageId 不同）。 */
    private val answerMessageIdToTurnTimestamp = mutableMapOf<String, Long>()
    /**
     * semi-auto：STT 定稿后 [currentMessage] 会清空，TTS 增量应挂回该问句气泡（非列表里「有问无音频」的任意一条）。
     */
    private var pendingAnswerTurnTimestamp: Long? = null
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
            sttMessageIdToTurnTimestamp.clear()
            answerMessageIdToTurnTimestamp.clear()
            pendingAnswerTurnTimestamp = null
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
                val resolvedQuestionAudio =
                    questionAudioPath
                        ?: pendingQuestionAudioPath
                        ?: currentMessage?.questionAudioPath
                if (questionAudioPath != null || pendingQuestionAudioPath != null) {
                    pendingQuestionAudioPath = null
                }
                val recognized = result.isFinished
                val isImageQuestion = result.questionType == AiContentType.IMAGE_PATH ||
                    result.questionType == AiContentType.IMAGE_FILE
                // 识图照片必须单独成条：无 messageId 时 resolveTurnForQuestion 会误并入当前 STT 文本轮次，
                // 把路径拼进文案后 UI 无法按图片渲染。
                val existingTurn = if (isImageQuestion) {
                    null
                } else {
                    resolveTurnForQuestion(newList, serverMessageId)
                }
                currentMessage = if (existingTurn != null) {
                    val mergedQuestion = if (result.questionReplaceWhole) {
                        SttDisplayTextMerger.merge(
                            existing = existingTurn.question,
                            incoming = questionText,
                            recognized = recognized,
                        )
                    } else {
                        existingTurn.question + questionText
                    }
                    val updated = existingTurn.copy(
                        question = mergedQuestion,
                        questionType = mapContentType(result.questionType),
                        questionAudioPath = resolvedQuestionAudio ?: existingTurn.questionAudioPath,
                        messageId = serverMessageId ?: existingTurn.messageId,
                    )
                    bindSttMessageId(updated.messageId, updated.timestamp)
                    updated
                } else {
                    val previous = currentMessage
                    if (previous != null &&
                        (previous.answer.isNotEmpty() ||
                            !previous.answerAudioPath.isNullOrBlank() ||
                            (isImageQuestion && previous.question.isNotEmpty()))
                    ) {
                        // 新开识图轮次前，先落定上一轮（含仅有问句的 STT），避免被覆盖丢失。
                        finalizeCurrentMessage(newList)
                    }
                    pendingAnswerTurnTimestamp?.let { pendingTs ->
                        val pendingRow = newList.find { it.timestamp == pendingTs }
                        if (pendingRow == null ||
                            pendingRow.answer.isNotEmpty() ||
                            !pendingRow.answerAudioPath.isNullOrBlank()
                        ) {
                            pendingAnswerTurnTimestamp = null
                        }
                    }
                    AiAssistantEntity(
                        question = questionText,
                        questionType = mapContentType(result.questionType),
                        answer = "",
                        answerType = "",
                        questionAudioPath = if (isImageQuestion) null else resolvedQuestionAudio,
                        messageId = serverMessageId,
                        timestamp = System.currentTimeMillis(),
                    ).also { bindSttMessageId(serverMessageId, it.timestamp) }
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
                val targetMessage = resolveTurnForAnswer(newList, serverMessageId)
                currentMessage = if (targetMessage != null) {
                    bindAnswerMessageId(serverMessageId, targetMessage.timestamp)
                    val mergedAnswer = mergeStreamingAnswer(
                        existing = targetMessage.answer,
                        incoming = answerText,
                    )
                    targetMessage.copy(
                        answer = mergedAnswer,
                        answerType = mapContentType(result.answerType),
                    )
                } else {
                    AiAssistantEntity(
                        question = "",
                        questionType = "",
                        answer = answerText,
                        answerType = mapContentType(result.answerType),
                        messageId = serverMessageId,
                        timestamp = System.currentTimeMillis(),
                    ).also { bindAnswerMessageId(serverMessageId, it.timestamp) }
                }
            }

            answerAudioPath != null -> {
                val targetMessage = resolveTurnForAnswer(newList, serverMessageId)
                currentMessage = if (targetMessage != null) {
                    bindAnswerMessageId(serverMessageId, targetMessage.timestamp)
                    targetMessage.copy(
                        answerAudioPath = answerAudioPath,
                        answerType = mapContentType(result.answerType),
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
                    ).also { bindAnswerMessageId(serverMessageId, it.timestamp) }
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
            val isImageQuestion = result.questionType == AiContentType.IMAGE_PATH ||
                result.questionType == AiContentType.IMAGE_FILE
            val bumpStreamingRevision =
                answerText.isNotEmpty() || result.questionReplaceWhole || isImageQuestion
            if (bumpStreamingRevision) {
                syncTypewriterProgressToContent(
                    timestamp = message.timestamp,
                    questionLength = message.question.length,
                    answerLength = message.answer.length,
                )
            }
            _state.value = _state.value.copy(
                messages = newList,
                streamingMessageId = message.timestamp,
                streamingContentRevision = if (bumpStreamingRevision) {
                    _state.value.streamingContentRevision + 1
                } else {
                    _state.value.streamingContentRevision
                },
            )
        }

        if (result.isFinished) {
            if (AiAssistantClient.getInstance().isAutoDuplexDialogue()) {
                // auto：ASR recognized 只表示问句定稿，整轮结束以回答完成为准。
                val message = currentMessage
                val turnComplete = message != null &&
                    (message.answer.isNotEmpty() || !message.answerAudioPath.isNullOrBlank())
                if (turnComplete || answerAudioPath != null) {
                    message?.let { finalizeCurrentMessage(newList) }
                    _state.value = _state.value.copy(
                        messages = newList,
                        streamingMessageId = null,
                    )
                    currentMessage = null
                }
            } else {
                val finishing = currentMessage
                if (finishing != null && result.isFinished) {
                    when {
                        finishing.question.isNotEmpty() && finishing.answer.isEmpty() &&
                            finishing.answerAudioPath.isNullOrBlank() ->
                            pendingAnswerTurnTimestamp = finishing.timestamp
                        finishing.answer.isNotEmpty() || !finishing.answerAudioPath.isNullOrBlank() ->
                            pendingAnswerTurnTimestamp = null
                    }
                }
                finishing?.let { finalizeCurrentMessage(newList) }
                _state.value = _state.value.copy(
                    messages = newList,
                    streamingMessageId = null,
                )
                currentMessage = null
            }
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

    /** 同一轮 STT：含「已有回答但问句仍须更新」的定稿/补全场景。 */
    private fun resolveTurnForQuestion(
        list: List<AiAssistantEntity>,
        sttMessageId: String?,
    ): AiAssistantEntity? {
        if (!sttMessageId.isNullOrBlank()) {
            sttMessageIdToTurnTimestamp[sttMessageId]?.let { ts ->
                list.find { it.timestamp == ts }?.let { return it }
            }
            findMessageByServerId(list, sttMessageId)?.let { return it }
        }
        currentMessage?.let { cur ->
            if (sttMessageId.isNullOrBlank()) {
                if (cur.question.isNotEmpty() && cur.answer.isEmpty()) return cur
                return@let
            }
            if (cur.messageId == sttMessageId) return cur
        }
        _state.value.streamingMessageId?.let { streamingTs ->
            list.find { it.timestamp == streamingTs }?.let { row ->
                when {
                    sttMessageId.isNullOrBlank() -> return row
                    row.messageId.isNullOrBlank() -> return row
                    row.messageId == sttMessageId -> return row
                }
            }
        }
        return null
    }

    private fun bindSttMessageId(sttMessageId: String?, turnTimestamp: Long) {
        if (!sttMessageId.isNullOrBlank()) {
            sttMessageIdToTurnTimestamp[sttMessageId] = turnTimestamp
        }
    }

    private fun bindAnswerMessageId(answerMessageId: String?, turnTimestamp: Long) {
        if (!answerMessageId.isNullOrBlank()) {
            answerMessageIdToTurnTimestamp[answerMessageId] = turnTimestamp
        }
    }

    /** 将 TTS/回答侧 messageId 归并到当前轮次气泡（问题侧为 STT messageId）。 */
    private fun resolveTurnForAnswer(
        list: List<AiAssistantEntity>,
        answerServerMessageId: String?,
    ): AiAssistantEntity? {
        if (!answerServerMessageId.isNullOrBlank()) {
            answerMessageIdToTurnTimestamp[answerServerMessageId]?.let { ts ->
                list.find { it.timestamp == ts }?.let { return it }
            }
        }
        pendingAnswerTurnTimestamp?.let { ts ->
            list.find { it.timestamp == ts }?.let { return it }
        }
        currentMessage?.takeIf {
            it.question.isNotEmpty() &&
                (it.answer.isEmpty() || !answerServerMessageId.isNullOrBlank())
        }?.let { return it }
        return null
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

    /** 流式回答：服务端按序下发增量片，auto / semi-auto 均直接拼接。 */
    private fun mergeStreamingAnswer(existing: String, incoming: String): String {
        if (incoming.isEmpty()) return existing
        return existing + incoming
    }
}
