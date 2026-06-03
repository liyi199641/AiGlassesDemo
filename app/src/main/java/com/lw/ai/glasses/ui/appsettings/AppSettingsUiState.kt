package com.lw.ai.glasses.ui.appsettings

import com.fission.wear.glasses.sdk.constant.GlassesConstant

data class AppSettingsUiState(
    val selectedEnvironment: GlassesConstant.ServerEnvironment = GlassesConstant.ServerEnvironment.DEV,
    val localEnvironmentBaseUrl: String = GlassesConstant.ServerEnvironment.LOCAL.baseUrl,
    val localEnvironmentWsUrl: String = GlassesConstant.ServerEnvironment.LOCAL.wsUrl,
    val selectedChannel: GlassesConstant.ChannelType = GlassesConstant.ChannelType.LY,
    val isDeviceBound: Boolean = false,
    val autoConnectAi: Boolean = true,
)
