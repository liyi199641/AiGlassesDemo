package com.lw.ai.glasses.ui.call

data class CallUiState(
    val isInCall: Boolean = false,
    /** 创建房间成功后由服务端返回，用于系统分享邀请对方加入 */
    val hostUrl: String? = null,
    val callMode: CallMode = CallMode.VIDEO,
    val selectedLanguage: String = "en",
    val translationLogs: List<TranslationMessage> = emptyList(),
    val isRemoteVideoReady: Boolean = false,
    val isRemoteAudioMuted: Boolean = false,
    val isLoading: Boolean = false,
    val isMicMuted: Boolean = false,
    val isSpeakerOn: Boolean = true,
    val isVideoMuted: Boolean = false,
    val isFrontCamera: Boolean = true,
    val isRemoteVideoMuted: Boolean = false
)
