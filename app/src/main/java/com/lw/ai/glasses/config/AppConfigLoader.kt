package com.lw.ai.glasses.config

import com.fission.wear.glasses.sdk.config.AiServerEnvironmentConfig
import com.fission.wear.glasses.sdk.constant.GlassesConstant
import com.lw.top.lib_core.data.datastore.AppDataManager

object AppConfigLoader {

    data class Snapshot(
        val selectedEnvironment: GlassesConstant.ServerEnvironment,
        val localEnvironmentBaseUrl: String,
        val localEnvironmentWsUrl: String,
        val autoConnectAi: Boolean,
        val aiContinuousDialogue: Boolean = AppDataManager.DEFAULT_AI_CONTINUOUS_DIALOGUE,
        val aiContinuousXueqiu: Boolean = AppDataManager.DEFAULT_AI_CONTINUOUS_XUEQIU,
    )

    suspend fun loadSnapshot(appDataManager: AppDataManager): Snapshot {
        val localBaseUrl = appDataManager.getLocalEnvironmentBaseUrl()
            ?: GlassesConstant.ServerEnvironment.CUSTOM.baseUrl
        val localWsUrl = GlassesConstant.ServerEnvironment.customWsUrlOrDefault(
            appDataManager.getLocalEnvironmentWsUrl(),
        )
        val savedEnv = appDataManager.getEnvironment()
            ?.let(GlassesConstant.ServerEnvironment::fromPersistedName)
            ?: GlassesConstant.ServerEnvironment.entries.firstOrNull {
                it.wsUrl == GlassesConstant.AI_ASSISTANT_BASE_WS_URL
            }
            ?: GlassesConstant.ServerEnvironment.DEV

        return Snapshot(
            selectedEnvironment = savedEnv,
            localEnvironmentBaseUrl = localBaseUrl,
            localEnvironmentWsUrl = localWsUrl,
            autoConnectAi = appDataManager.getAutoConnectAiEnabled(),
            aiContinuousDialogue = appDataManager.getAiContinuousDialogueEnabled(),
            aiContinuousXueqiu = appDataManager.getAiContinuousXueqiuEnabled(),
        )
    }

    fun localCustomEnvironment(snapshot: Snapshot): AiServerEnvironmentConfig? {
        return if (snapshot.selectedEnvironment == GlassesConstant.ServerEnvironment.CUSTOM) {
            AiServerEnvironmentConfig(
                baseUrl = snapshot.localEnvironmentBaseUrl,
                wsUrl = snapshot.localEnvironmentWsUrl,
            )
        } else {
            null
        }
    }
}
