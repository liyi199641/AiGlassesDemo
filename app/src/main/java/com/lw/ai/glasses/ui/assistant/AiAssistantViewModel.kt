package com.lw.ai.glasses.ui.assistant

import BaseViewModel
import android.content.Context
import androidx.lifecycle.viewModelScope
import com.blankj.utilcode.util.LogUtils
import com.fission.wear.glasses.sdk.GlassesManage
import com.fission.wear.glasses.sdk.AiAssistantClient
import com.fission.wear.glasses.sdk.constant.GlassesConstant.BtMediaKeyAction
import com.fission.wear.glasses.sdk.data.model.McpScheduleData
import com.fission.wear.glasses.sdk.events.AgentEvent
import com.fission.wear.glasses.sdk.events.AudioStateEvent
import com.fission.wear.glasses.sdk.events.CmdResultEvent
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.lw.ai.glasses.config.AiDialogueLanguageDefaults
import com.lw.ai.glasses.state.AiAssistantConversationManager
import com.lw.ai.glasses.state.StreamState
import com.lw.ai.glasses.state.WsConnectionStateManager
import com.lw.ai.glasses.ui.translate.Language
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import jakarta.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class AiAssistantViewModel @Inject constructor(
    private val conversationManager: AiAssistantConversationManager,
    private val wsConnectionStateManager: WsConnectionStateManager,
    @ApplicationContext private val context: Context,
) : BaseViewModel() {
    private val _uiState = MutableStateFlow(AiAssistantUiState())
    val uiState: StateFlow<AiAssistantUiState> = _uiState

    private val _showConfirmDialog = MutableStateFlow(false)
    val showConfirmDialog: StateFlow<Boolean> = _showConfirmDialog.asStateFlow()

    private var mediaPlayer: android.media.MediaPlayer? = null
    private val _navigateToCalendar = MutableSharedFlow<McpScheduleData>()
    val navigateToCalendar: SharedFlow<McpScheduleData> = _navigateToCalendar.asSharedFlow()

    private val _pendingCalendarEvent = MutableStateFlow<McpScheduleData?>(null)
    val pendingCalendarEvent: SharedFlow<McpScheduleData?> = _pendingCalendarEvent.asStateFlow()

    /**
     * 触发显示确认弹窗（暂存日程事件）
     */
    fun triggerConfirmDialog(event: McpScheduleData) {
        viewModelScope.launch {
            _pendingCalendarEvent.value = event
            _showConfirmDialog.emit(true)
        }
    }

    /**
     * 用户确认添加日程
     */
    fun confirmAddCalendar() {
        viewModelScope.launch {
            _pendingCalendarEvent.value?.let {
                _navigateToCalendar.emit(it)
            }
            _showConfirmDialog.emit(false)
            _pendingCalendarEvent.value = null
        }
    }

    /**
     * 用户取消添加
     */
    fun cancelAddCalendar() {
        viewModelScope.launch {
            _showConfirmDialog.emit(false)
            _pendingCalendarEvent.value = null
        }
    }

    fun createSampleCalendarEvent(schedule: McpScheduleData) {
        triggerConfirmDialog(schedule)
    }

    init {
        _uiState.update {
            it.copy(
                agentAudioPlaybackEnabled = AiAssistantClient.getInstance()
                    .isAgentAudioPlaybackEnabled(),
            )
        }
        loadLanguages()
        observeConversationState()
        observeGlobalWsConnectionState()
        observeScheduleEvents()
        observeAiDialogueState()
        observeDeviceButtonEvents()
    }

    private fun loadLanguages() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val jsonString =
                    context.assets.open("languages.json").bufferedReader().use { it.readText() }
                val listType = object : TypeToken<List<Language>>() {}.type
                val languages: List<Language> = Gson().fromJson(jsonString, listType)
                val currentLangType = AiAssistantClient.getInstance().getAiDialogueLanguage()
                val defaultLangType = AiDialogueLanguageDefaults.defaultLangType()
                val selected = languages.find { it.langType == currentLangType }
                    ?: languages.find { it.langType == defaultLangType }
                    ?: languages.firstOrNull()
                if (selected != null && selected.langType != currentLangType) {
                    AiAssistantClient.getInstance().setAiDialogueLanguage(selected.langType)
                }
                _uiState.update {
                    it.copy(
                        allLanguages = languages,
                        selectedLanguage = selected,
                    )
                }
            } catch (e: Exception) {
                LogUtils.e("AiAssistantViewModel", "load languages failed", e)
            }
        }
    }

    fun setDialogueLanguage(language: Language) {
        AiAssistantClient.getInstance().setAiDialogueLanguage(language.langType)
        _uiState.update { it.copy(selectedLanguage = language) }
    }

    fun clearAllMessages() {
        conversationManager.clearAllMessages()
    }

    fun getTypewriterProgress(timestamp: Long): StreamState {
        return conversationManager.getTypewriterProgress(timestamp)
    }

    fun updateTypewriterProgress(timestamp: Long, questionLength: Int? = null, answerLength: Int? = null) {
        conversationManager.updateTypewriterProgress(timestamp, questionLength, answerLength)
    }

    /** 进入 AI 助手界面时调用：已收到的流式内容直接展示，不再从头打字。 */
    fun onScreenVisible() {
        conversationManager.syncStreamingTypewriterProgressToContent()
        _uiState.update { it.copy(typewriterRevision = it.typewriterRevision + 1) }
    }

    /** 离开 AI 助手界面时调用：缓存当前已展示进度。 */
    fun onScreenHidden() {
        conversationManager.syncStreamingTypewriterProgressToContent()
    }

    private fun stopAnswerAudioPlayback() {
        mediaPlayer?.release()
        mediaPlayer = null
        _uiState.update { it.copy(playingAnswerAudioPath = null) }
    }

    fun playAnswerAudio(path: String) {
        if (_uiState.value.isAiDialogueInProgress) return
        try {
            GlassesManage.interruptAiAssistant()//打断sdk的播放
            stopAnswerAudioPlayback()
            _uiState.update { it.copy(playingAnswerAudioPath = path) }
            mediaPlayer = android.media.MediaPlayer().apply {
                setDataSource(path)
                prepare()
                start()
                setOnCompletionListener { player ->
                    player.release()
                    mediaPlayer = null
                    _uiState.update { state ->
                        if (state.playingAnswerAudioPath == path) {
                            state.copy(playingAnswerAudioPath = null)
                        } else {
                            state
                        }
                    }
                }
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(playingAnswerAudioPath = null) }
            e.printStackTrace()
        }
    }

    /** 切换 AI 对话回复音频自动播放。 */
    fun toggleAgentAudioPlayback() {
        val enabled = !_uiState.value.agentAudioPlaybackEnabled
        AiAssistantClient.getInstance().setAgentAudioPlaybackEnabled(enabled)
        _uiState.update { it.copy(agentAudioPlaybackEnabled = enabled) }
    }

    fun startAiAssistant() {
        GlassesManage.startAiAssistant()
    }

    fun stopAiAssistant() {
        GlassesManage.stopAiAssistant()
    }

    fun interruptAiAssistant() {
        GlassesManage.interruptAiAssistant()
    }

    private fun observeConversationState() {
        viewModelScope.launch {
            conversationManager.state.collect { conversation ->
                _uiState.update {
                    it.copy(
                        messages = conversation.messages,
                        streamingMessageId = conversation.streamingMessageId,
                    )
                }
            }
        }
    }

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

    private fun observeScheduleEvents() {
        viewModelScope.launch {
            AiAssistantClient.getInstance().aiAgentEventFlow().collect { event ->
                if (event is AgentEvent.AiScheduleResult) {
                    createSampleCalendarEvent(event.data)
                }
            }
        }
    }

    private fun observeAiDialogueState() {
        viewModelScope.launch {
            AiAssistantClient.getInstance().aiDialogueInProgressFlow().collect { inProgress ->
                _uiState.update { it.copy(isAiDialogueInProgress = inProgress) }
            }
        }
    }

    private fun observeDeviceButtonEvents() {
        viewModelScope.launch {
            GlassesManage.eventFlow().collect { event ->
                when (event) {
                    is AudioStateEvent.StartRecording -> {
//                        LogUtils.d("设备开始录音")
                        stopAnswerAudioPlayback()
                    }

                    is AudioStateEvent.CancelRecording -> {
//                        LogUtils.d("取消录音")
                    }

                    is AudioStateEvent.StopRecording -> {
//                        LogUtils.d("停止录音")
                    }

                    is CmdResultEvent.DeviceBtnClickEvent -> {
                        when (event.type) {
                            BtMediaKeyAction.CLICK -> {
                                GlassesManage.interruptAiAssistant()
                            }
                            else -> Unit
                        }
                    }

                    else -> Unit
                }
            }
        }
    }

    override fun onCleared() {
        stopAnswerAudioPlayback()
        super.onCleared()
    }
}
