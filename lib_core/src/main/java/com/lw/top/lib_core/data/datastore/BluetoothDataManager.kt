package com.lw.top.lib_core.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BluetoothDataManager @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private object BluetoothKeys {
        val BLUETOOTH_ADDRESS = stringPreferencesKey("bluetooth_address")
        val BLUETOOTH_NAME = stringPreferencesKey("bluetooth_name")
        val BLUETOOTH_STATE = intPreferencesKey("bluetooth_state")
        val SDK_CHANNEL = stringPreferencesKey("bluetooth_sdk_channel")
    }

    val savedBluetoothAddress: Flow<String?> = dataStore.data.map { preferences ->
        preferences[BluetoothKeys.BLUETOOTH_ADDRESS]
    }

    val savedBluetoothName: Flow<String?> = dataStore.data.map { preferences ->
        preferences[BluetoothKeys.BLUETOOTH_NAME]
    }
    val savedBluetoothState: Flow<Int> = dataStore.data.map { preferences ->
        preferences[BluetoothKeys.BLUETOOTH_STATE] ?: 0
    }

    suspend fun getBluetoothAddress(): String? {
        return savedBluetoothAddress.firstOrNull()
    }

    suspend fun getBluetoothName(): String? {
        return savedBluetoothName.firstOrNull()
    }

    suspend fun getBluetoothState(): Int {
        return savedBluetoothState.first()
    }

    /**
     * 保存蓝牙设备的地址、名称及广播解析出的 SDK 渠道。
     */
    suspend fun saveBluetoothDevice(address: String, name: String, sdkChannel: String? = null) {
        dataStore.edit { preferences ->
            preferences[BluetoothKeys.BLUETOOTH_ADDRESS] = address
            preferences[BluetoothKeys.BLUETOOTH_NAME] = name
            if (!sdkChannel.isNullOrBlank()) {
                preferences[BluetoothKeys.SDK_CHANNEL] = sdkChannel
            }
        }
    }

    suspend fun getSdkChannelName(): String? {
        return dataStore.data.map { it[BluetoothKeys.SDK_CHANNEL] }.firstOrNull()
    }

    suspend fun saveBluetoothState(state: Int) {
        dataStore.edit { preferences ->
            preferences[BluetoothKeys.BLUETOOTH_STATE] = state
        }
    }

    /**
     * 清除存储的蓝牙设备信息。
     */
    suspend fun clearBluetoothDevice() {
        dataStore.edit { preferences ->
            preferences.remove(BluetoothKeys.BLUETOOTH_ADDRESS)
            preferences.remove(BluetoothKeys.BLUETOOTH_NAME)
            preferences.remove(BluetoothKeys.SDK_CHANNEL)
            preferences[BluetoothKeys.BLUETOOTH_STATE] = 0
        }
    }


}