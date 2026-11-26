package com.lw.ai.glasses.config

import android.content.Context
import com.fission.wear.glasses.sdk.AiAssistantClient
import com.lw.ai.glasses.config.AiDialogueLanguageDefaults
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
        val adapterNum = bluetoothDataManager.getBluetoothAdapter().orEmpty()
        if (address.isNullOrBlank() || name.isNullOrBlank()) return

        AiAssistantClient.getInstance().connectAiAssistant(
            address,
            name,
            adapterNum,
            "ukuSPzMnpLvLS2TTLL9S8PvUJzfTCHnu",
            "tz5dgRLm6tXS8gRr",
        )
    }


    suspend fun disconnect(
        context: Context,
        appDataManager: AppDataManager,
        bluetoothDataManager: BluetoothDataManager,
    ) {
        AiAssistantClient.getInstance().disconnect()
        initializeAiClient(context, appDataManager, bluetoothDataManager)
    }

    suspend fun initializeAiClient(
        context: Context,
        appDataManager: AppDataManager,
        bluetoothDataManager: BluetoothDataManager,
    ) {
        val snapshot = AppConfigLoader.loadSnapshot(appDataManager)
        val channel = SdkChannelResolver.loadForSdkInit(bluetoothDataManager, appDataManager)
        val resolved = AiAgentConfigBuilder.resolveEnvironment(snapshot)
        AiAgentConfigBuilder.applyResolvedEnvironmentToGlobals(resolved)
        AiAssistantClient.getInstance().initializeAiClient(
            AiAgentConfigBuilder.buildAiAgentConfig(
                context = context,
                channel = channel,
                resolved = resolved,
                enableDefaultPlaySimultaneousAudio = false,
                aiDialogueLanguage = AiDialogueLanguageDefaults.defaultLangType(),
            ),
        )
    }

    suspend fun reinitializeAndReconnect(
        context: Context,
        appDataManager: AppDataManager,
        bluetoothDataManager: BluetoothDataManager,
    ) {
        AiAssistantClient.getInstance().disconnect()
        initializeAiClient(context, appDataManager, bluetoothDataManager)
        connectIfEnabled(appDataManager, bluetoothDataManager)
    }
}
