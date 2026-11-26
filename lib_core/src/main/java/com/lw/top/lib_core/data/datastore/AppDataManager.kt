package com.lw.top.lib_core.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppDataManager @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    companion object {
        const val DEFAULT_SDK_CHANNEL_NAME = "LY"
        const val DEFAULT_AUTO_CONNECT_AI = true
        const val DEFAULT_AI_CONTINUOUS_DIALOGUE = true
        const val DEFAULT_AI_CONTINUOUS_XUEQIU = false
    }

    private object AppKeys {
        val SERVER_ENVIRONMENT = stringPreferencesKey("server_environment")
        val LOCAL_SERVER_BASE_URL = stringPreferencesKey("local_server_base_url")
        val LOCAL_SERVER_WS_URL = stringPreferencesKey("local_server_ws_url")
        val SDK_CHANNEL = stringPreferencesKey("sdk_channel")
        val AUTO_CONNECT_AI = booleanPreferencesKey("auto_connect_ai")
        val AI_CONTINUOUS_DIALOGUE = booleanPreferencesKey("ai_continuous_dialogue")
        val AI_CONTINUOUS_XUEQIU = booleanPreferencesKey("ai_continuous_xueqiu")
    }

    val savedEnvironment: Flow<String?> = dataStore.data.map { preferences ->
        preferences[AppKeys.SERVER_ENVIRONMENT]
    }

    val savedLocalEnvironmentBaseUrl: Flow<String?> = dataStore.data.map { preferences ->
        preferences[AppKeys.LOCAL_SERVER_BASE_URL]
    }

    val savedLocalEnvironmentWsUrl: Flow<String?> = dataStore.data.map { preferences ->
        preferences[AppKeys.LOCAL_SERVER_WS_URL]
    }

    suspend fun getEnvironment(): String? {
        return savedEnvironment.firstOrNull()
    }

    suspend fun getLocalEnvironmentBaseUrl(): String? {
        return savedLocalEnvironmentBaseUrl.firstOrNull()
    }

    suspend fun getLocalEnvironmentWsUrl(): String? {
        return savedLocalEnvironmentWsUrl.firstOrNull()
    }

    /**
     * 保存当前选择的环境名称 (枚举的 name)
     */
    suspend fun saveEnvironment(envName: String) {
        dataStore.edit { preferences ->
            preferences[AppKeys.SERVER_ENVIRONMENT] = envName
        }
    }

    /**
     * 保存本地环境 HTTP Base URL
     */
    suspend fun saveLocalEnvironmentBaseUrl(baseUrl: String) {
        dataStore.edit { preferences ->
            preferences[AppKeys.LOCAL_SERVER_BASE_URL] = baseUrl
        }
    }

    /**
     * 保存本地环境 WebSocket 地址
     */
    suspend fun saveLocalEnvironmentWsUrl(wsUrl: String) {
        dataStore.edit { preferences ->
            preferences[AppKeys.LOCAL_SERVER_WS_URL] = wsUrl
        }
    }

    suspend fun getSdkChannelName(): String {
        return dataStore.data.map { it[AppKeys.SDK_CHANNEL] }.firstOrNull()
            ?: DEFAULT_SDK_CHANNEL_NAME
    }

    suspend fun saveSdkChannelName(channelName: String) {
        dataStore.edit { preferences ->
            preferences[AppKeys.SDK_CHANNEL] = channelName
        }
    }

    suspend fun getAutoConnectAiEnabled(): Boolean {
        return dataStore.data.map { preferences ->
            preferences[AppKeys.AUTO_CONNECT_AI] ?: DEFAULT_AUTO_CONNECT_AI
        }.firstOrNull() ?: DEFAULT_AUTO_CONNECT_AI
    }

    fun observeAutoConnectAiEnabled(): Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[AppKeys.AUTO_CONNECT_AI] ?: DEFAULT_AUTO_CONNECT_AI
    }

    suspend fun saveAutoConnectAiEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[AppKeys.AUTO_CONNECT_AI] = enabled
        }
    }

    suspend fun getAiContinuousDialogueEnabled(): Boolean {
        return dataStore.data.map { preferences ->
            preferences[AppKeys.AI_CONTINUOUS_DIALOGUE] ?: DEFAULT_AI_CONTINUOUS_DIALOGUE
        }.firstOrNull() ?: DEFAULT_AI_CONTINUOUS_DIALOGUE
    }

    suspend fun saveAiContinuousDialogueEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[AppKeys.AI_CONTINUOUS_DIALOGUE] = enabled
        }
    }

    suspend fun getAiContinuousXueqiuEnabled(): Boolean {
        return dataStore.data.map { preferences ->
            preferences[AppKeys.AI_CONTINUOUS_XUEQIU] ?: DEFAULT_AI_CONTINUOUS_XUEQIU
        }.firstOrNull() ?: DEFAULT_AI_CONTINUOUS_XUEQIU
    }

    suspend fun saveAiContinuousXueqiuEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[AppKeys.AI_CONTINUOUS_XUEQIU] = enabled
        }
    }

}
