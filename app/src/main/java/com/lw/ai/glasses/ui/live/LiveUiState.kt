package com.lw.ai.glasses.ui.live

import com.fission.wear.glasses.sdk.constant.GlassesConstant

enum class AppFunctionMode {
    LOCAL_PLAYER, // 纯播放模式
    DOUYIN_PUSHER // 抖音推流模式
}

data class LiveUiState(
    val currentFunction: AppFunctionMode = AppFunctionMode.LOCAL_PLAYER,
    val isPlayingLocal: Boolean = false,
    val isDeviceStreaming: Boolean = false,
    val isPushingToDouyin: Boolean = false,
    val isConnecting: Boolean = false,
    val rtspUrl: String = "",
    val logs: List<String> = emptyList(),
    val errorMessage: String? = null,
    val targetFps: Int = 20,
    val targetResolution: String = "720*960",
    val bitrate: Int = 3000000,
    val liveChannel: GlassesConstant.LiveChannel = GlassesConstant.LiveChannel.WIFI_AP
)
