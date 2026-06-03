package com.lw.ai.glasses.ui.call

import com.lw.ai.glasses.ui.common.WsConnectionUiState

data class CallUiState(
    val isInCall: Boolean = false,
    /** 创建房间成功后由服务端返回，用于系统分享邀请对方加入 */
    val hostUrl: String? = null,
    val callMode: CallMode = CallMode.VIDEO,
    val selectedLanguage: String = "en",
    val translationLogs: List<TranslationMessage> = emptyList(),
    val isRemoteVideoReady: Boolean = false,
    /** 双方已接通（收到 CallConnected） */
    val isCallConnected: Boolean = false,
    val isRemoteAudioMuted: Boolean = false,
    val isLoading: Boolean = false,
    val isMicMuted: Boolean = false,
    val isSpeakerOn: Boolean = true,
    val isVideoMuted: Boolean = false,
    val isFrontCamera: Boolean = true,
    val isRemoteVideoMuted: Boolean = false,
    /** 通话接通后的时长（秒），接通后每秒递增 */
    val callDurationSeconds: Int = 0,
    val wsConnection: WsConnectionUiState = WsConnectionUiState(),
)

fun formatCallDuration(totalSeconds: Int): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
