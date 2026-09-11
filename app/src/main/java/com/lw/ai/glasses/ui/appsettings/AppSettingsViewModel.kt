package com.lw.ai.glasses.ui.appsettings

import BaseViewModel
import android.content.Context
import androidx.lifecycle.viewModelScope
import com.blankj.utilcode.util.ToastUtils
import com.fission.wear.glasses.sdk.AiAssistantClient
import com.fission.wear.glasses.sdk.config.AiAgentConfig
import com.fission.wear.glasses.sdk.constant.GlassesConstant
import com.lw.ai.glasses.R
import com.lw.ai.glasses.config.AiAssistantConnectionHelper
import com.lw.ai.glasses.config.AppConfigLoader
import com.lw.ai.glasses.config.SdkChannelResolver
import com.lw.top.lib_core.data.datastore.AppDataManager
import com.lw.top.lib_core.data.datastore.BluetoothDataManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppSettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appDataManager: AppDataManager,
    private val bluetoothDataManager: BluetoothDataManager,
) : BaseViewModel() {

    private val _uiState = MutableStateFlow(AppSettingsUiState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            loadSettings()
        }
    }

    private suspend fun loadSettings() {
        val snapshot = AppConfigLoader.loadSnapshot(appDataManager)
        _uiState.update {
            it.copy(
                selectedEnvironment = snapshot.selectedEnvironment,
                localEnvironmentBaseUrl = snapshot.localEnvironmentBaseUrl,
                localEnvironmentWsUrl = snapshot.localEnvironmentWsUrl,
                autoConnectAi = snapshot.autoConnectAi,
            )
        }
    }

    fun updateAutoConnectAi(enabled: Boolean) {
        if (_uiState.value.autoConnectAi == enabled) return
        viewModelScope.launch {
            appDataManager.saveAutoConnectAiEnabled(enabled)
            _uiState.update { it.copy(autoConnectAi = enabled) }
            if (enabled) {
                AiAssistantConnectionHelper.connectIfEnabled(appDataManager, bluetoothDataManager)
            } else {
                AiAssistantConnectionHelper.disconnect(context, appDataManager, bluetoothDataManager)
            }
        }
    }

    fun updateEnvironment(
        env: GlassesConstant.ServerEnvironment,
        localBaseUrl: String? = null,
        localWsUrl: String? = null,
    ) {
        val normalizedLocalBaseUrl = localBaseUrl?.trim().orEmpty()
        val normalizedLocalWsUrl = localWsUrl?.trim().orEmpty()
        if (env == GlassesConstant.ServerEnvironment.CUSTOM) {
            if (normalizedLocalBaseUrl.isBlank()) {
                ToastUtils.showLong(context.getString(R.string.local_base_url_empty))
                return
            }
            if (!normalizedLocalBaseUrl.startsWith("http://") && !normalizedLocalBaseUrl.startsWith("https://")) {
                ToastUtils.showLong(context.getString(R.string.local_base_url_scheme_invalid))
                return
            }
            if (normalizedLocalWsUrl.isBlank()) {
                ToastUtils.showLong(context.getString(R.string.local_ws_empty))
                return
            }
            if (!normalizedLocalWsUrl.startsWith("ws://") && !normalizedLocalWsUrl.startsWith("wss://")) {
                ToastUtils.showLong(context.getString(R.string.local_ws_scheme_invalid))
                return
            }
        }

        val appliedLocalBaseUrl = normalizedLocalBaseUrl.ifBlank {
            _uiState.value.localEnvironmentBaseUrl
        }
        val appliedLocalWsUrl = normalizedLocalWsUrl.ifBlank {
            _uiState.value.localEnvironmentWsUrl
        }
        applyAiServerEnvironment(env, appliedLocalBaseUrl, appliedLocalWsUrl)

        viewModelScope.launch {
            val channel = SdkChannelResolver.loadForSdkInit(bluetoothDataManager, appDataManager)
            AiAssistantClient.getInstance().initializeAiClient(
                AiAgentConfig(
                    context = context,
                    channel = channel,
                    aiModelType = GlassesConstant.AiModelVendor.DEFAULT,
                    serverEnvironment = env,
                    customServerEnvironment = if (env == GlassesConstant.ServerEnvironment.CUSTOM) {
                        com.fission.wear.glasses.sdk.config.AiServerEnvironmentConfig(
                            baseUrl = appliedLocalBaseUrl,
                            wsUrl = appliedLocalWsUrl,
                        )
                    } else {
                        null
                    },
                    enableDefaultPlaySimultaneousAudio = true,
                    enableDefaultPlayAgentAudio = true,
                    translationAudioStorageDirName = "transAudioFiles",
                    aiDialogueLanguage = AiAssistantClient.getInstance().getAiDialogueLanguage(),
                ),
            )

            AiAssistantConnectionHelper.connectIfEnabled(appDataManager, bluetoothDataManager)
            appDataManager.saveEnvironment(env.name)
            if (env == GlassesConstant.ServerEnvironment.CUSTOM) {
                appDataManager.saveLocalEnvironmentBaseUrl(appliedLocalBaseUrl)
                appDataManager.saveLocalEnvironmentWsUrl(appliedLocalWsUrl)
            }
        }

        _uiState.update {
            it.copy(
                selectedEnvironment = env,
                localEnvironmentBaseUrl = if (env == GlassesConstant.ServerEnvironment.CUSTOM) {
                    appliedLocalBaseUrl
                } else {
                    it.localEnvironmentBaseUrl
                },
                localEnvironmentWsUrl = if (env == GlassesConstant.ServerEnvironment.CUSTOM) {
                    appliedLocalWsUrl
                } else {
                    it.localEnvironmentWsUrl
                },
            )
        }
    }

    fun saveLocalEnvironment(localBaseUrl: String, localWsUrl: String) {
        updateEnvironment(
            env = GlassesConstant.ServerEnvironment.CUSTOM,
            localBaseUrl = localBaseUrl,
            localWsUrl = localWsUrl,
        )
    }

    private fun applyAiServerEnvironment(
        env: GlassesConstant.ServerEnvironment,
        localBaseUrlOverride: String? = null,
        localWsUrlOverride: String? = null,
    ) {
        val localConfig = if (env == GlassesConstant.ServerEnvironment.CUSTOM) {
            com.fission.wear.glasses.sdk.config.AiServerEnvironmentConfig(
                baseUrl = localBaseUrlOverride ?: _uiState.value.localEnvironmentBaseUrl,
                wsUrl = localWsUrlOverride ?: _uiState.value.localEnvironmentWsUrl,
            )
        } else {
            null
        }
        if (localConfig != null) {
            AiAssistantClient.getInstance().applyServerEnvironmentToGlobals(localConfig)
        } else {
            AiAssistantClient.getInstance().applyServerEnvironmentToGlobals(env)
        }
    }
}
