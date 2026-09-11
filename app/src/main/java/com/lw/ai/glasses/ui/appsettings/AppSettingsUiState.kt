package com.lw.ai.glasses.ui.appsettings

import com.fission.wear.glasses.sdk.constant.GlassesConstant

data class AppSettingsUiState(
    val selectedEnvironment: GlassesConstant.ServerEnvironment = GlassesConstant.ServerEnvironment.DEV,
    val localEnvironmentBaseUrl: String = GlassesConstant.ServerEnvironment.CUSTOM.baseUrl,
    val localEnvironmentWsUrl: String = GlassesConstant.ServerEnvironment.CUSTOM.wsUrl,
    val autoConnectAi: Boolean = true,
)
