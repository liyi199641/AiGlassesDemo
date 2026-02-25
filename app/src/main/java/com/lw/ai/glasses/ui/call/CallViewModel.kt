package com.lw.ai.glasses.ui.call

import BaseViewModel
import android.view.TextureView
import androidx.lifecycle.viewModelScope
import com.blankj.utilcode.util.LogUtils
import com.blankj.utilcode.util.ToastUtils
import com.fission.wear.glasses.sdk.GlassesManage
import com.fission.wear.glasses.sdk.events.AiTranslationEvent
import com.fission.wear.glasses.sdk.events.CmdResultEvent
import com.lw.top.lib_core.data.datastore.BluetoothDataManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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
    private val bluetoothDataManager: BluetoothDataManager
) : BaseViewModel() {

    private val _uiState = MutableStateFlow(CallUiState())
    val uiState: StateFlow<CallUiState> = _uiState.asStateFlow()

    private var pendingLocalView: TextureView? = null
    private var pendingRemoteView: TextureView? = null

    init {
        viewModelScope.launch {
            GlassesManage.eventFlow().collect { event ->
                when (event) {
                    is CmdResultEvent.VoiceRoomParamsEvent -> {
                        val params = event.params
                        LogUtils.d("创建房间成功：$params")
                        GlassesManage.startCall(
                            appID = params.appId.toLong(),
                            token = params.appToken,
                            roomID = params.roomId,
                            streamId = params.streamId,
                            userID = params.userId,
                            isVideo = _uiState.value.callMode == CallMode.VIDEO,
                            local = pendingLocalView,
                            remote = pendingRemoteView
                        )
                        _uiState.update { it.copy(isInCall = true, isLoading = false) }
                    }

                    is CmdResultEvent.VoiceRoomParamsFailEvent ->{
                        ToastUtils.showLong("创建房间失败：${event.msg}")
                    }

                    is CmdResultEvent.CallConnected -> {
                        _uiState.update { it.copy(isRemoteVideoReady = true) }
                    }

                    is CmdResultEvent.CallDisconnected -> {
                        endCall()
                    }

                    is CmdResultEvent.RemoteVideoStateEvent -> {
                        LogUtils.d("远端摄像头状态：${event.isMuted}")
                        _uiState.update { it.copy(isRemoteVideoMuted = event.isMuted) }
                    }

                    is AiTranslationEvent.AiTranslationResult -> {
                        handleStreamingTranslation(event.data)
                    }

                    is CmdResultEvent.RemoteLanguageEvent->{
                        LogUtils.d("远端语言：${event.language}")
                    }

                    else -> {}
                }
            }
        }
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
                sender = if (result.isMe) "我" else "对方",
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
        GlassesManage.setPlayVolume(volume)
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
        GlassesManage.muteMicrophone(newMuteStatus)
    }

    fun toggleSpeaker() {
        val newSpeakerStatus = !uiState.value.isSpeakerOn
        _uiState.update { it.copy(isSpeakerOn = newSpeakerStatus) }
        GlassesManage.enableSpeaker(newSpeakerStatus)
    }

    fun toggleVideo() {
        val newMuteStatus = !uiState.value.isVideoMuted
        _uiState.update { it.copy(isVideoMuted = newMuteStatus) }
        GlassesManage.muteVideo(newMuteStatus)
    }

    fun flipCamera() {
        val nextIsFront = !uiState.value.isFrontCamera
        _uiState.update { it.copy(isFrontCamera = nextIsFront) }
        GlassesManage.switchCamera(nextIsFront)
    }

    fun startCall(localView: TextureView? = null, remoteView: TextureView? = null) {
        this.pendingLocalView = localView
        this.pendingRemoteView = remoteView

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val mac = bluetoothDataManager.getBluetoothAddress() ?: ""

            GlassesManage.getVoiceRoomParams(
                lang = 140,
                target = 47,
                type = if (_uiState.value.callMode == CallMode.VIDEO) 1 else 2,
                appId = "954308550",
                mac = mac
            )
        }
    }

    fun endCall() {
        GlassesManage.endCall()
        _uiState.update {
            it.copy(
                isInCall = false,
                isRemoteVideoReady = false,
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

    override fun onCleared() {
        super.onCleared()
        endCall()
    }
}
