package com.lw.ai.glasses.config

import com.fission.wear.glasses.sdk.config.AiServerEnvironmentConfig
import com.fission.wear.glasses.sdk.constant.GlassesConstant
import com.lw.top.lib_core.data.datastore.AppDataManager

object AppConfigLoader {

    data class Snapshot(
        val selectedEnvironment: GlassesConstant.ServerEnvironment,
        val localEnvironmentBaseUrl: String,
        val localEnvironmentWsUrl: String,
        val selectedChannel: GlassesConstant.ChannelType,
        val autoConnectAi: Boolean,
    )

    fun sanitizeSelectableEnvironment(env: GlassesConstant.ServerEnvironment): GlassesConstant.ServerEnvironment {
        return if (GlassesConstant.isVendorDirectEnvironment(env)) {
            GlassesConstant.ServerEnvironment.DEV
        } else {
            env
        }
    }

    suspend fun loadSnapshot(appDataManager: AppDataManager): Snapshot {
        val localBaseUrl = appDataManager.getLocalEnvironmentBaseUrl()
            ?: GlassesConstant.ServerEnvironment.LOCAL.baseUrl
        val localWsUrl = appDataManager.getLocalEnvironmentWsUrl()
            ?: GlassesConstant.ServerEnvironment.LOCAL.wsUrl
        val savedEnv = appDataManager.getEnvironment()
            ?.let { name ->
                runCatching { GlassesConstant.ServerEnvironment.valueOf(name) }.getOrNull()
                    ?.let(::sanitizeSelectableEnvironment)
            }
            ?: GlassesConstant.ServerEnvironment.entries.firstOrNull {
                it.wsUrl == GlassesConstant.AI_ASSISTANT_BASE_WS_URL &&
                    !GlassesConstant.isVendorDirectEnvironment(it)
            }
            ?: GlassesConstant.ServerEnvironment.DEV

        return Snapshot(
            selectedEnvironment = savedEnv,
            localEnvironmentBaseUrl = localBaseUrl,
            localEnvironmentWsUrl = localWsUrl,
            selectedChannel = SdkChannelResolver.loadSaved(appDataManager),
            autoConnectAi = appDataManager.getAutoConnectAiEnabled(),
        )
    }

    fun localCustomEnvironment(snapshot: Snapshot): AiServerEnvironmentConfig? {
        return if (snapshot.selectedEnvironment == GlassesConstant.ServerEnvironment.LOCAL) {
            AiServerEnvironmentConfig(
                baseUrl = snapshot.localEnvironmentBaseUrl,
                wsUrl = snapshot.localEnvironmentWsUrl,
            )
        } else {
            null
        }
    }
}
