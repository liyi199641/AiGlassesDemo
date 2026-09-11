package com.lw.ai.glasses.ui.live

import com.fission.wear.glasses.sdk.constant.GlassesConstant.LiveStreamingMode


enum class LiveScreenPhase {
    CONFIG,
    LIVE,
}

/** 连接阶段：先预览，预览成功后再走抖音直播，结束时单独标识。 */
enum class LiveConnectPhase {
    NONE,
    PREVIEW,
    DOUYIN,
    STOPPING,
}

data class LiveUiState(
    val screenPhase: LiveScreenPhase = LiveScreenPhase.CONFIG,
    val connectPhase: LiveConnectPhase = LiveConnectPhase.NONE,
    /** LY 方案仅支持本地预览，不支持参数配置与抖音推流。 */
    val isLyScheme: Boolean = false,
    val streamingMode: LiveStreamingMode = LiveStreamingMode.PREVIEW,
    val isPlayingLocal: Boolean = false,
    val isDeviceStreaming: Boolean = false,
    val isPushingToDouyin: Boolean = false,
    val isConnecting: Boolean = false,
    val rtspUrl: String = "",
    val logs: List<String> = emptyList(),
    val errorMessage: String? = null,
    val targetFps: Int = 25,
    val targetResolution: String = "1280x720",
    val bitrate: Int = 1_000_000,
    val isMicOn: Boolean = true,
    /** 预览画面旋转角度：0=横屏，90=竖屏 */
    val previewRotation: Int = 0,
    /** 手动推流模式下的 RTMP 地址 */
    val manualPushUrl: String = "",
) {
    val showsLocalPreview: Boolean
        get() = streamingMode != LiveStreamingMode.PUSH

    val requiresDouyinPush: Boolean
        get() = streamingMode == LiveStreamingMode.PUSH ||
            streamingMode == LiveStreamingMode.PREVIEW_PUSH

    val requiresManualPushUrl: Boolean
        get() = !isLyScheme && streamingMode == LiveStreamingMode.PREVIEW_PUSH_MANUAL

    val showsParameterConfig: Boolean
        get() = !isLyScheme

    val showsStreamingModeOptions: Boolean
        get() = !isLyScheme

    val showsPreviewControls: Boolean
        get() = !isLyScheme

    val showsPushStatus: Boolean
        get() = !isLyScheme
}

val LiveUiState.canRetryDouyinBroadcast: Boolean
    get() = streamingMode == LiveStreamingMode.PREVIEW_PUSH &&
        isPlayingLocal &&
        !isPushingToDouyin &&
        !isConnecting &&
        errorMessage != null
