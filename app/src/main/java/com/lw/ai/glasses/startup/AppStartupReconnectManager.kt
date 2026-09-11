package com.lw.ai.glasses.startup

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.blankj.utilcode.util.LogUtils
import com.fission.wear.glasses.sdk.AiAssistantClient
import com.fission.wear.glasses.sdk.GlassesManage
import com.fission.wear.glasses.sdk.config.AiAgentConfig
import com.fission.wear.glasses.sdk.config.AiServerEnvironmentConfig
import com.fission.wear.glasses.sdk.config.BleComConfig
import com.fission.wear.glasses.sdk.config.SdkConfig
import com.fission.wear.glasses.sdk.constant.GlassesConstant
import com.fission.wear.glasses.sdk.events.CmdResultEvent
import com.fission.wear.glasses.sdk.events.ConnectionStateEvent
import com.fission.wear.glasses.sdk.events.ScanStateEvent
import com.lw.ai.glasses.config.AiAssistantConnectionHelper
import com.lw.ai.glasses.config.AiDialogueLanguageDefaults
import com.lw.ai.glasses.config.ProductSeriesResolver
import com.lw.ai.glasses.config.SdkChannelResolver
import com.lw.ai.glasses.service.AiAssistantService
import com.lw.ai.glasses.state.AiAssistantConversationManager
import com.lw.ai.glasses.state.DeviceActionStateManager
import com.lw.ai.glasses.state.MediaSyncStateManager
import com.lw.ai.glasses.state.WsConnectionStateManager
import com.lw.ai.glasses.ui.home.ConnectionState
import com.lw.top.lib_core.data.datastore.AppDataManager
import com.lw.top.lib_core.data.datastore.BluetoothDataManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@Singleton
class AppStartupReconnectManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bluetoothDataManager: BluetoothDataManager,
    private val appDataManager: AppDataManager,
    private val wsConnectionStateManager: WsConnectionStateManager,
    private val aiAssistantConversationManager: AiAssistantConversationManager,
    private val mediaSyncStateManager: MediaSyncStateManager,
    private val deviceActionStateManager: DeviceActionStateManager,
) {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val started = AtomicBoolean(false)
    private var restoredLocalEnvironmentConfig: AiServerEnvironmentConfig? = null

    fun start() {
        if (!started.compareAndSet(false, true)) return

        wsConnectionStateManager.start()
        aiAssistantConversationManager.start()
        mediaSyncStateManager.start()
        deviceActionStateManager.start()
        observeGlassesEvents()
        appScope.launch {
            val environment = restoreSavedEnvironment()
            val savedAddress = bluetoothDataManager.getBluetoothAddress()
            val savedName = bluetoothDataManager.getBluetoothName()
            val isDeviceBound = !savedAddress.isNullOrBlank() && !savedName.isNullOrBlank()
            val channel = SdkChannelResolver.loadForSdkInit(bluetoothDataManager, appDataManager)
            if (isDeviceBound) {
                initGlassesSdkAndAiClient(environment, channel, savedName)
            } else {
                initAiClientOnly(environment, channel)
            }
            autoReconnectLastDevice()
        }
    }

    private fun observeGlassesEvents() {
        appScope.launch {
            GlassesManage.eventFlow().collect { event ->
                when (event) {
                    is ConnectionStateEvent.Connecting -> {
                        bluetoothDataManager.saveBluetoothState(ConnectionState.CONNECTING.value)
                    }

                    is ConnectionStateEvent.Connected -> {
                        bluetoothDataManager.saveBluetoothState(ConnectionState.CONNECTED.value)
//                        AiAssistantService.start(context)
                        GlassesManage.setVoiceWakeUp(true, true)
                        GlassesManage.getBatteryLevel()
                        GlassesManage.requestDeviceVersionInfo()
                        GlassesManage.getMediaFileCount()
                        appScope.launch {
                            AiAssistantConnectionHelper.connectIfEnabled(
                                appDataManager,
                                bluetoothDataManager,
                            )
                        }
                    }

                    is ConnectionStateEvent.Disconnected -> {
                        bluetoothDataManager.saveBluetoothState(ConnectionState.DISCONNECTED.value)
                        AiAssistantService.stop(context)
                    }

                    is ConnectionStateEvent.Idle -> {
                        appScope.launch {
                            val address = bluetoothDataManager.getBluetoothAddress()
                            val state = if (address.isNullOrBlank()) {
                                ConnectionState.IDLE
                            } else {
                                ConnectionState.DISCONNECTED
                            }
                            bluetoothDataManager.saveBluetoothState(state.value)
                        }
                        AiAssistantService.stop(context)
                    }

                    is CmdResultEvent.DevicePower,
                    is CmdResultEvent.MediaFileCount,
                    is ScanStateEvent.DeviceFound,
                    is ScanStateEvent.ScanFinished,
                    is ScanStateEvent.Error -> Unit

                    else -> Unit
                }
            }
        }
    }

    private suspend fun restoreSavedEnvironment(): GlassesConstant.ServerEnvironment {
        val savedLocalBaseUrl = appDataManager.getLocalEnvironmentBaseUrl()
        val savedLocalWsUrl = appDataManager.getLocalEnvironmentWsUrl()
        val savedEnvName = appDataManager.getEnvironment()

        val environment = savedEnvName
            ?.let(GlassesConstant.ServerEnvironment::fromPersistedName)
            ?: GlassesConstant.ServerEnvironment.entries.firstOrNull {
                it.wsUrl == GlassesConstant.AI_ASSISTANT_BASE_WS_URL
            }
            ?: GlassesConstant.ServerEnvironment.DEV

        if (environment == GlassesConstant.ServerEnvironment.CUSTOM) {
            restoredLocalEnvironmentConfig = AiServerEnvironmentConfig(
                baseUrl = savedLocalBaseUrl ?: GlassesConstant.ServerEnvironment.CUSTOM.baseUrl,
                wsUrl = savedLocalWsUrl ?: GlassesConstant.ServerEnvironment.CUSTOM.wsUrl,
            )
            AiAssistantClient.getInstance().applyServerEnvironmentToGlobals(restoredLocalEnvironmentConfig!!)
        } else {
            restoredLocalEnvironmentConfig = null
            AiAssistantClient.getInstance().applyServerEnvironmentToGlobals(environment, savedLocalWsUrl)
        }
        return environment
    }

    private fun initGlassesSdkAndAiClient(
        environment: GlassesConstant.ServerEnvironment,
        channel: GlassesConstant.ChannelType,
        deviceName: String?,
    ) {
        val productSeries = ProductSeriesResolver.fromDeviceName(deviceName)
        LogUtils.i(
            "AppStartupReconnect",
            "init SDK productSeries=${productSeries.code} deviceName=$deviceName"
        )
        GlassesManage.initialize(
            SdkConfig(
                true,
                context,
                channel,
                LogUtils.V,
                productSeries = productSeries,
            )
        )
        initAiClientOnly(environment, channel)
    }

    private fun initAiClientOnly(
        environment: GlassesConstant.ServerEnvironment,
        channel: GlassesConstant.ChannelType,
    ) {
        AiAssistantClient.getInstance().initializeAiClient(
            AiAgentConfig(
                context = context,
                channel = channel,
                aiModelType = GlassesConstant.AiModelVendor.DEFAULT,
                serverEnvironment = environment,
                customServerEnvironment = restoredLocalEnvironmentConfig,
                aiDialogueLanguage = AiDialogueLanguageDefaults.defaultLangType(),
            )
        )
    }

    private suspend fun autoReconnectLastDevice() {
        val connectionState = ConnectionState.fromValue(bluetoothDataManager.getBluetoothState())
        val savedAddress = bluetoothDataManager.getBluetoothAddress()
        val savedName = bluetoothDataManager.getBluetoothName()

        if (connectionState == ConnectionState.IDLE || savedAddress.isNullOrBlank() || savedName.isNullOrBlank()) {
            return
        }
        if (!hasBluetoothConnectPermission()) {
            LogUtils.w("AppStartupReconnect", "Skip auto reconnect because BLUETOOTH_CONNECT is not granted.")
            return
        }

        GlassesManage.connect(BleComConfig(context, savedAddress, false))
    }

    private fun hasBluetoothConnectPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
    }

}
