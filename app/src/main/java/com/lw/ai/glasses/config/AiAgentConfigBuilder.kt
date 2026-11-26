package com.lw.ai.glasses.config

import android.content.Context
import com.fission.wear.glasses.sdk.config.AiAgentConfig
import com.fission.wear.glasses.sdk.config.AiServerEnvironmentConfig
import com.fission.wear.glasses.sdk.constant.GlassesConstant
import com.lw.top.lib_core.data.datastore.AppDataManager

/**
 * 按应用设置（环境、连续问答）构建 [AiAgentConfig] 与全局 AI 服务地址。
 */
object AiAgentConfigBuilder {

    data class ResolvedEnvironment(
        val serverEnvironment: GlassesConstant.ServerEnvironment,
        val customServerEnvironment: AiServerEnvironmentConfig?,
        val aiDialogueMode: String,
        val aiProvider: String? = null,
    )

    suspend fun isContinuousDialogueEnabled(appDataManager: AppDataManager): Boolean {
        return appDataManager.getAiContinuousDialogueEnabled()
    }

    fun resolveEnvironment(snapshot: AppConfigLoader.Snapshot): ResolvedEnvironment {
        val env = snapshot.selectedEnvironment
        val continuousDialogue = snapshot.aiContinuousDialogue
        val aiDialogueMode = if (continuousDialogue) {
            GlassesConstant.AI_ASSISTANT_TYPE_LISTEN_MODE_AUTO_DUPLEX
        } else {
            GlassesConstant.AI_ASSISTANT_TYPE_LISTEN_MODE_AUTO
        }
        val aiProvider = when {
            !continuousDialogue -> null
            snapshot.aiContinuousXueqiu -> GlassesConstant.AI_PROVIDER_XUEQIU
            else -> null
        }
        val custom = when {
            env == GlassesConstant.ServerEnvironment.CUSTOM -> {
                val baseUrl = snapshot.localEnvironmentBaseUrl
                val wsUrl = if (continuousDialogue) {
                    continuousDialogueWsUrl(snapshot.localEnvironmentWsUrl, env)
                } else {
                    snapshot.localEnvironmentWsUrl
                }
                AiServerEnvironmentConfig(baseUrl = baseUrl, wsUrl = wsUrl)
            }
            continuousDialogue -> {
                AiServerEnvironmentConfig(
                    baseUrl = env.baseUrl,
                    wsUrl = GlassesConstant.ServerEnvironment.realtimeWsUrlFor(env),
                )
            }
            else -> null
        }
        return ResolvedEnvironment(env, custom, aiDialogueMode, aiProvider)
    }

    private fun continuousDialogueWsUrl(savedWsUrl: String, env: GlassesConstant.ServerEnvironment): String {
        val trimmed = savedWsUrl.trim()
        if (trimmed.contains("realtime-")) return trimmed
        if (trimmed.contains("/ai-agent/")) {
            return trimmed.replace(Regex("wss?://voice-"), "wss://realtime-")
        }
        return GlassesConstant.ServerEnvironment.realtimeWsUrlFor(env)
    }

    fun applyResolvedEnvironmentToGlobals(resolved: ResolvedEnvironment) {
        val client = com.fission.wear.glasses.sdk.AiAssistantClient.getInstance()
        val custom = resolved.customServerEnvironment
        if (custom != null) {
            client.applyServerEnvironmentToGlobals(custom)
        } else {
            client.applyServerEnvironmentToGlobals(resolved.serverEnvironment)
        }
    }

    fun buildAiAgentConfig(
        context: Context,
        channel: GlassesConstant.ChannelType,
        resolved: ResolvedEnvironment,
        enableDefaultPlaySimultaneousAudio: Boolean = false,
        enableDefaultPlayAgentAudio: Boolean = true,
        translationAudioStorageDirName: String? = null,
        aiDialogueLanguage: Int,
    ): AiAgentConfig {
        return AiAgentConfig(
            context = context,
            channel = channel,
            aiModelType = GlassesConstant.AiModelVendor.DEFAULT,
            serverEnvironment = resolved.serverEnvironment,
            customServerEnvironment = resolved.customServerEnvironment,
            enableDefaultPlaySimultaneousAudio = enableDefaultPlaySimultaneousAudio,
            enableDefaultPlayAgentAudio = enableDefaultPlayAgentAudio,
            translationAudioStorageDirName = translationAudioStorageDirName
                ?: GlassesConstant.DEFAULT_TRANS_AUDIO_FILES_STORAGE_DIR,
            aiDialogueLanguage = aiDialogueLanguage,
            aiDialogueMode = resolved.aiDialogueMode,
            aiProvider = resolved.aiProvider,
        )
    }
}