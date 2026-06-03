package com.lw.ai.glasses.ui.call

import com.lw.ai.glasses.ui.base.viewmodel.BaseViewModel
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import android.view.TextureView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewModelScope
import com.blankj.utilcode.util.LogUtils
import com.blankj.utilcode.util.ToastUtils
import com.fission.wear.glasses.sdk.AiAssistantClient
import com.fission.wear.glasses.sdk.GlassesManage
import com.fission.wear.glasses.sdk.events.AgentEvent
import com.fission.wear.glasses.sdk.events.AiTranslationEvent
import com.lw.ai.glasses.R
import com.lw.ai.glasses.state.WsConnectionStateManager
import com.lw.top.lib_core.data.datastore.BluetoothDataManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 模拟通话模式
 */
enum class CallMode {
    VIDEO, AUDIO
}

/**
 * 翻译记录数据类 (适配 UI 显示)
 */
data class TranslationMessage(
    val id: String, // 复合 ID: requestId-messageId
    val sender: String,
    val text: String,
    val isFromMe: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

@HiltViewModel
class CallViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bluetoothDataManager: BluetoothDataManager,
    private val wsConnectionStateManager: WsConnectionStateManager,
) : BaseViewModel() {

    private val _uiState = MutableStateFlow(CallUiState())
    val uiState: StateFlow<CallUiState> = _uiState.asStateFlow()
    private val _requestAudioPermissionEvent = MutableSharedFlow<Unit>()
    val requestAudioPermissionEvent = _requestAudioPermissionEvent.asSharedFlow()

    private var pendingLocalView: TextureView? = null
    private var pendingRemoteView: TextureView? = null
    private var callDurationJob: Job? = null

    init {

        disableOpusStreamPushForTranslation()

        observeGlobalWsConnectionState()

        viewModelScope.launch {
            AiAssistantClient.getInstance().aiAgentEventFlow().collect { event ->
                when (event) {
                    is AiTranslationEvent.AiTranslationResult -> {
                        handleStreamingTranslation(event.data)
                    }

                    is AgentEvent.VoiceRoomParamsEvent -> {
                        val params = event.params
                        AiAssistantClient.getInstance().startCall(
                            appID = params.appId.toLong(),
                            token = params.appToken,
                            roomID = params.roomId,
                            streamId = params.streamId,
                            userID = params.userId,
                            isVideo = _uiState.value.callMode == CallMode.VIDEO,
                            local = pendingLocalView,
                            remote = pendingRemoteView
                        )
                        _uiState.update {
                            it.copy(
                                isInCall = true,
                                isLoading = false,
                                hostUrl = params.hostUrl
                            )
                        }
                    }

                    is AgentEvent.VoiceRoomParamsFailEvent ->{
                        _uiState.update { it.copy(isLoading = false) }
                        ToastUtils.showLong(context.getString(R.string.room_creation_failed, event.msg))
                    }

                    is AgentEvent.CallConnected -> {
                        LogUtils.d("通话已接通：CallConnected")
                        markCallConnectedAndStartTimer()
                    }

                    is AgentEvent.CallDisconnected -> {
                        endCall()
                    }

                    is AgentEvent.RemoteVideoStateEvent -> {
                        LogUtils.d("远端摄像头状态：${event.isMuted}")
                        _uiState.update { it.copy(isRemoteVideoMuted = event.isMuted) }
                    }

                    is AgentEvent.RemoteLanguageEvent -> {
                        LogUtils.d("远端语言：${event.language}")
                    }

                    else -> {}
                }
            }
        }
    }

    private fun markCallConnectedAndStartTimer() {
        if (_uiState.value.isCallConnected && callDurationJob?.isActive == true) return
        _uiState.update {
            it.copy(
                isRemoteVideoReady = true,
                isCallConnected = true,
            )
        }
        startCallDurationTimer()
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

    /**
     * 处理流式翻译：还原为 requestId-messageId 的复合 Key，防止消息覆盖
     */
    private fun handleStreamingTranslation(result: com.fission.wear.glasses.sdk.data.dto.AiTranslationDTO) {
        val requestId = result.id ?: ""
        val msgId = result.messageId ?: "0"
        val compositeKey = "$requestId-$msgId"

        _uiState.update { state ->
            val currentLogs = state.translationLogs.toMutableList()
            val existingIndex = currentLogs.indexOfFirst { it.id == compositeKey }

            // 自己的展示原文，对方的展示译文
            val displayContent = if (result.isMe) {
                result.originalText
            } else {
                result.translatedText
            } ?: ""

            if (displayContent.isEmpty()) return@update state

            val newMessage = TranslationMessage(
                id = compositeKey,
                sender = if (result.isMe) {
                    context.getString(R.string.call_sender_me)
                } else {
                    context.getString(R.string.call_sender_other)
                },
                text = displayContent,
                isFromMe = result.isMe
            )

            if (existingIndex != -1) {
                // 1. 如果 compositeKey 相同，更新内容 (打字机效果)
                currentLogs[existingIndex] = newMessage
            } else {
                // 2. 如果是新的片段，插入列表
                currentLogs.add(newMessage)
            }

            state.copy(translationLogs = currentLogs)
        }
    }

    fun toggleRemoteAudio() {
        val currentMuteStatus = uiState.value.isRemoteAudioMuted
        val nextMuteStatus = !currentMuteStatus

        _uiState.update { it.copy(isRemoteAudioMuted = nextMuteStatus) }

        val volume = if (nextMuteStatus) 0 else 100
        AiAssistantClient.getInstance().setPlayVolume(volume)
    }

    fun setCallMode(mode: CallMode) {
        _uiState.update { it.copy(callMode = mode) }
    }

    fun setLanguage(lang: String) {
        _uiState.update { it.copy(selectedLanguage = lang) }
    }

    fun toggleMic() {
        val newMuteStatus = !uiState.value.isMicMuted
        _uiState.update { it.copy(isMicMuted = newMuteStatus) }
        AiAssistantClient.getInstance().muteMicrophone(newMuteStatus)
    }

    fun toggleSpeaker() {
        val newSpeakerStatus = !uiState.value.isSpeakerOn
        _uiState.update { it.copy(isSpeakerOn = newSpeakerStatus) }
        AiAssistantClient.getInstance().enableSpeaker(newSpeakerStatus)
    }

    fun toggleVideo() {
        val newMuteStatus = !uiState.value.isVideoMuted
        _uiState.update { it.copy(isVideoMuted = newMuteStatus) }
        AiAssistantClient.getInstance().muteVideo(newMuteStatus)
    }

    fun flipCamera() {
        val nextIsFront = !uiState.value.isFrontCamera
        _uiState.update { it.copy(isFrontCamera = nextIsFront) }
        AiAssistantClient.getInstance().switchCamera(nextIsFront)
    }

    fun startCall(localView: TextureView? = null, remoteView: TextureView? = null) {
        this.pendingLocalView = localView
        this.pendingRemoteView = remoteView
        if (!hasRecordAudioPermission()) {
            _uiState.update { it.copy(isLoading = false) }
            viewModelScope.launch {
                _requestAudioPermissionEvent.emit(Unit)
            }
            return
        }
        startCallInternal()
    }

    fun onRecordAudioPermissionResult(isGranted: Boolean) {
        if (isGranted) {
            startCallInternal()
        } else {
            _uiState.update { it.copy(isLoading = false) }
            ToastUtils.showLong(context.getString(R.string.permission_audio_required))
        }
    }

    private fun startCallInternal() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val mac = bluetoothDataManager.getBluetoothAddress() ?: ""

            AiAssistantClient.getInstance().getVoiceRoomParams(
                lang = 140,
                target = 47,
                type = if (_uiState.value.callMode == CallMode.VIDEO) 1 else 2,
                appId = "954308550",
                mac = mac
            )
        }
    }

    private fun hasRecordAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun startCallDurationTimer() {
        callDurationJob?.cancel()
        _uiState.update { it.copy(callDurationSeconds = 0) }
        callDurationJob = viewModelScope.launch {
            while (isActive) {
                delay(1000)
                _uiState.update { it.copy(callDurationSeconds = it.callDurationSeconds + 1) }
            }
        }
    }

    private fun stopCallDurationTimer() {
        callDurationJob?.cancel()
        callDurationJob = null
    }

    fun endCall() {
        stopCallDurationTimer()
        AiAssistantClient.getInstance().endCall()
        _uiState.update {
            it.copy(
                isInCall = false,
                hostUrl = null,
                isRemoteVideoReady = false,
                isCallConnected = false,
                callDurationSeconds = 0,
                translationLogs = emptyList(),
                isMicMuted = false,
                isSpeakerOn = true,
                isVideoMuted = false,
                isRemoteVideoMuted = false
            )
        }
        pendingLocalView = null
        pendingRemoteView = null
    }

    private fun disableOpusStreamPushForTranslation() {
        viewModelScope.launch {
            GlassesManage.setVoiceWakeUp(
                localOfflineEnabled = false,
                opusPushEnabled = false,
            )
        }
    }

    private fun restoreOpusStreamPushAfterTranslation() {
        GlassesManage.setVoiceWakeUp(
            localOfflineEnabled = true,
            opusPushEnabled = true,
        )
    }

    override fun onCleared() {
        super.onCleared()
        restoreOpusStreamPushAfterTranslation()
        endCall()
    }
}
