package com.lw.ai.glasses.config

import android.content.Context
import com.fission.wear.glasses.sdk.AiAssistantClient
import com.fission.wear.glasses.sdk.config.AiAgentConfig
import com.fission.wear.glasses.sdk.constant.GlassesConstant
import com.lw.top.lib_core.data.datastore.AppDataManager
import com.lw.top.lib_core.data.datastore.BluetoothDataManager

object AiAssistantConnectionHelper {

    suspend fun connectIfEnabled(
        appDataManager: AppDataManager,
        bluetoothDataManager: BluetoothDataManager,
    ) {
        if (!appDataManager.getAutoConnectAiEnabled()) return
        val address = bluetoothDataManager.getBluetoothAddress()
        val name = bluetoothDataManager.getBluetoothName()
        if (address.isNullOrBlank() || name.isNullOrBlank()) return

        AiAssistantClient.getInstance().connectAiAssistant(
            address,
            name,
            "6600",
            "ukuSPzMnpLvLS2TTLL9S8PvUJzfTCHnu",
            "tz5dgRLm6tXS8gRr",
        )
    }


    suspend fun disconnect(context: Context, appDataManager: AppDataManager) {
        AiAssistantClient.getInstance().disconnect()
        initializeAiClient(context, appDataManager)
    }

    suspend fun initializeAiClient(context: Context, appDataManager: AppDataManager) {
        val snapshot = AppConfigLoader.loadSnapshot(appDataManager)
        val localConfig = AppConfigLoader.localCustomEnvironment(snapshot)
        if (localConfig != null) {
            AiAssistantClient.getInstance().applyServerEnvironmentToGlobals(localConfig)
        } else {
            AiAssistantClient.getInstance().applyServerEnvironmentToGlobals(snapshot.selectedEnvironment)
        }
        AiAssistantClient.getInstance().initializeAiClient(
            AiAgentConfig(
                context = context,
                channel = snapshot.selectedChannel,
                aiModelType = GlassesConstant.AiModelVendor.DEFAULT,
                serverEnvironment = snapshot.selectedEnvironment,
                customServerEnvironment = localConfig,
                enableDefaultPlaySimultaneousAudio = false,
            ),
        )
    }
}
