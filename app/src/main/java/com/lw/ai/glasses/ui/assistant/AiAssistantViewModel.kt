package com.lw.ai.glasses.ui.assistant

import com.lw.ai.glasses.ui.base.viewmodel.BaseViewModel
import androidx.lifecycle.viewModelScope
import com.blankj.utilcode.util.LogUtils
import com.fission.wear.glasses.sdk.GlassesManage
import com.fission.wear.glasses.sdk.AiAssistantClient
import com.fission.wear.glasses.sdk.constant.GlassesConstant.BtMediaKeyAction
import com.fission.wear.glasses.sdk.data.dto.AiChatMessageDTO
import com.fission.wear.glasses.sdk.data.dto.AiContentType
import com.fission.wear.glasses.sdk.data.model.McpScheduleData
import com.fission.wear.glasses.sdk.events.AgentEvent
import com.lw.ai.glasses.state.WsConnectionStateManager
import com.fission.wear.glasses.sdk.events.AudioStateEvent
import com.fission.wear.glasses.sdk.events.CmdResultEvent
import com.lw.top.lib_core.data.local.entity.AiAssistantEntity
import com.lw.top.lib_core.data.repository.AiAssistantRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import jakarta.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

@HiltViewModel
class AiAssistantViewModel @Inject constructor(
    private val repository: AiAssistantRepository,
    private val wsConnectionStateManager: WsConnectionStateManager,
) : BaseViewModel() {
    private val _uiState = MutableStateFlow(AiAssistantUiState())
    val uiState: StateFlow<AiAssistantUiState> = _uiState

    private val _showConfirmDialog = MutableStateFlow(false)
    val showConfirmDialog: StateFlow<Boolean> = _showConfirmDialog.asStateFlow()

    private var currentMessage: AiAssistantEntity? = null
    private val _navigateToCalendar = MutableSharedFlow<McpScheduleData>()
    val navigateToCalendar: SharedFlow<McpScheduleData> = _navigateToCalendar.asSharedFlow()

    private val _pendingCalendarEvent = MutableStateFlow<McpScheduleData?>(null)
    val pendingCalendarEvent: SharedFlow<McpScheduleData?> = _pendingCalendarEvent.asStateFlow()

    /**
     * 触发显示确认弹窗（暂存日程事件）
     */
    fun triggerConfirmDialog(event:McpScheduleData) {
        viewModelScope.launch {
            _pendingCalendarEvent.value = event
            _showConfirmDialog.emit(true) // 显示弹窗
        }
    }

    /**
     * 用户确认添加日程
     */
    fun confirmAddCalendar() {
        viewModelScope.launch {
            _pendingCalendarEvent.value?.let {
                _navigateToCalendar.emit(it) // 发送唤起日历指令
            }
            _showConfirmDialog.emit(false) // 隐藏弹窗
            _pendingCalendarEvent.value = null // 清空暂存事件
        }
    }

    /**
     * 用户取消添加
     */
    fun cancelAddCalendar() {
        viewModelScope.launch {
            _showConfirmDialog.emit(false) // 隐藏弹窗
            _pendingCalendarEvent.value = null // 清空暂存事件
        }
    }

    fun createSampleCalendarEvent(schedule:McpScheduleData) {
        triggerConfirmDialog(schedule)
    }

    init {
        _uiState.update {
            it.copy(
                agentAudioPlaybackEnabled = AiAssistantClient.getInstance()
                    .isAgentAudioPlaybackEnabled(),
            )
        }
        loadHistoryMessages()
        observeGlobalWsConnectionState()
        observeGlassesEvents()
//
//        viewModelScope.launch {
//            delay(2000)
//            createSampleCalendarEvent(McpScheduleData(System.currentTimeMillis()/1000,"深圳北站","自己","开会"))
//        }
    }

    private fun loadHistoryMessages() {
        viewModelScope.launch {
            val history = repository.getAllMessages()
            _uiState.value = _uiState.value.copy(
                messages = history
            )
        }
    }


    fun clearAllMessages() {
        viewModelScope.launch {
            repository.clearAllMessages()
            _uiState.value = _uiState.value.copy(
                messages = emptyList()
            )
//            stopVadAudio()
        }
    }

    /** 切换 AI 对话回复音频自动播放。 */
    fun toggleAgentAudioPlayback() {
        val enabled = !_uiState.value.agentAudioPlaybackEnabled
        AiAssistantClient.getInstance().setAgentAudioPlaybackEnabled(enabled)
        _uiState.update { it.copy(agentAudioPlaybackEnabled = enabled) }
    }

//    fun stopVadAudio(){
//        viewModelScope.launch {
//            GlassesManage.stopVadAudio()
//        }
//    }


    private fun observeGlobalWsConnectionState() {
        viewModelScope.launch {
            wsConnectionStateManager.state.collect { ws ->
                _uiState.update { it.copy(wsConnection = ws) }
            }
        }
    }

    fun reconnectWebSocket() {
        wsConnectionStateManager.manualReconnect()
    }

    private fun observeGlassesEvents() {

        viewModelScope.launch {
            AiAssistantClient.getInstance().aiAgentEventFlow().collect {
                    event ->
                when (event) {
                    is AgentEvent.AiScheduleResult  -> {
                        createSampleCalendarEvent(event.data)
                    }

                    is AgentEvent.AiAssistantResult -> {
                        LogUtils.d("AiAssistantEvent.AiAssistantResult${event.data}")
                        handleStreamingResult(event.data)
                    }
                    else -> {

                    }
                }
            }
        }

        viewModelScope.launch {
            GlassesManage.eventFlow().collect { event ->
                when (event) {

                    is CmdResultEvent.ImageFile -> {
                        event.imageFile?.let {file->
                            handleStreamingResult(AiChatMessageDTO(
                                question = file.absolutePath,
                                questionType = AiContentType.IMAGE_PATH,
                                isFinished = true)
                            )
                        }
                    }

                    is AudioStateEvent.StartRecording -> {//唤醒词后开始录音
                        LogUtils.d("设备开始录音")
                    }

                    is AudioStateEvent.ReceivingAudioData -> {//持续发送给大模型
//                        LogUtils.d("接收录音数据 ${event.byteArray.toByteArray()}")
                    }

                    is AudioStateEvent.CancelRecording -> {
                        LogUtils.d("取消录音")
                    }

                    is AudioStateEvent.StopRecording -> {
                        LogUtils.d("停止录音")
                    }

                    is CmdResultEvent.DeviceBtnClickEvent ->{
                        when(event.type){
                            BtMediaKeyAction.CLICK -> {
                                GlassesManage.interruptAiAssistant()
                            }
                            else -> {

                            }
                        }
                    }

                    else -> {

                    }
                }

            }
        }
    }


    private suspend fun handleStreamingResult(result: AiChatMessageDTO) {
        val questionText = anyToStringSafe(result.question)
        val answerText = anyToStringSafe(result.answer)
        if (questionText.isEmpty() && answerText.isEmpty() && !result.isFinished) return

        val newList = _uiState.value.messages.toMutableList()

        when {
            questionText.isNotEmpty() -> {
                // 问题与回答分行：带 question 的事件绝不写入正在流式的 answer 行
                if (currentMessage?.answer?.isNotEmpty() == true) {
                    finalizeCurrentMessage(newList)
                }
                currentMessage = if (currentMessage?.answer.isNullOrEmpty() && currentMessage?.question?.isNotEmpty() == true) {
                    // 同一条 STT 流式快照（整句替换）
                    currentMessage!!.copy(
                        question = questionText,
                        questionType = mapContentType(result.questionType),
                    )
                } else {
                    AiAssistantEntity(
                        question = questionText,
                        questionType = mapContentType(result.questionType),
                        answer = "",
                        answerType = "",
                        timestamp = System.currentTimeMillis(),
                    )
                }
            }

            answerText.isNotEmpty() -> {
                // 回答只追加到 answer 行；若当前是仅问题行，则新开一行
                if (currentMessage?.question?.isNotEmpty() == true && currentMessage!!.answer.isEmpty()) {
                    finalizeCurrentMessage(newList)
                }
                currentMessage = if (currentMessage != null) {
                    currentMessage!!.copy(
                        answer = currentMessage!!.answer + answerText,
                        answerType = mapContentType(result.answerType),
                    )
                } else {
                    AiAssistantEntity(
                        question = "",
                        questionType = "",
                        answer = answerText,
                        answerType = mapContentType(result.answerType),
                        timestamp = System.currentTimeMillis(),
                    )
                }
            }
        }

        currentMessage?.let { message ->
            upsertMessageInList(newList, message)
            _uiState.value = _uiState.value.copy(
                messages = newList,
                streamingMessageId = message.hashCode().toLong(),
            )
        }

        if (result.isFinished) {
            currentMessage?.let { finalizeCurrentMessage(newList) }
            _uiState.value = _uiState.value.copy(
                messages = newList,
                streamingMessageId = null,
            )
            currentMessage = null
        }
    }

    private suspend fun finalizeCurrentMessage(list: MutableList<AiAssistantEntity>) {
        val message = currentMessage ?: return
        if (message.question.isEmpty() && message.answer.isEmpty()) {
            currentMessage = null
            return
        }
        repository.insertMessage(message)
        upsertMessageInList(list, message)
        currentMessage = null
    }

    private fun upsertMessageInList(list: MutableList<AiAssistantEntity>, message: AiAssistantEntity) {
        val index = list.indexOfFirst { it.timestamp == message.timestamp }
        if (index >= 0) {
            list[index] = message
        } else {
            list.add(0, message)
        }
    }

    private fun mapContentType(type: AiContentType): String {
        return when (type) {
            AiContentType.TEXT -> "txt"
            AiContentType.IMAGE_PATH, AiContentType.IMAGE_FILE -> "image"
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