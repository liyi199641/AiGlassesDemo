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
import com.lw.ai.glasses.config.AppConfigLoader
import com.lw.ai.glasses.config.SdkChannelResolver
import com.lw.ai.glasses.service.AiAssistantService
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
) {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val started = AtomicBoolean(false)
    private var restoredLocalEnvironmentConfig: AiServerEnvironmentConfig? = null

    fun start() {
        if (!started.compareAndSet(false, true)) return

        wsConnectionStateManager.start()
        observeGlassesEvents()
        appScope.launch {
            val environment = restoreSavedEnvironment()
            val savedAddress = bluetoothDataManager.getBluetoothAddress()
            val savedName = bluetoothDataManager.getBluetoothName()
            val isDeviceBound = !savedAddress.isNullOrBlank() && !savedName.isNullOrBlank()
            val channel = SdkChannelResolver.loadSaved(appDataManager)
            if (isDeviceBound) {
                initGlassesSdkAndAiClient(environment, channel)
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
                        AiAssistantService.start(context)
                        GlassesManage.setVoiceWakeUp(true, true)
                        GlassesManage.getBatteryLevel()
                        GlassesManage.getMediaFileCount()
                        appScope.launch {
                            AiAssistantConnectionHelper.connectIfEnabled(
                                appDataManager,
                                bluetoothDataManager,
                            )
                        }
                        GlassesManage.getActionState()
                    }

                    is ConnectionStateEvent.Disconnected -> {
                        bluetoothDataManager.saveBluetoothState(ConnectionState.DISCONNECTED.value)
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
            ?.let { name ->
                runCatching { GlassesConstant.ServerEnvironment.valueOf(name) }.getOrNull()
                    ?.let(AppConfigLoader::sanitizeSelectableEnvironment)
            }
            ?: GlassesConstant.ServerEnvironment.entries.firstOrNull {
                it.wsUrl == GlassesConstant.AI_ASSISTANT_BASE_WS_URL &&
                    !GlassesConstant.isVendorDirectEnvironment(it)
            }
            ?: GlassesConstant.ServerEnvironment.DEV

        if (environment == GlassesConstant.ServerEnvironment.LOCAL) {
            restoredLocalEnvironmentConfig = AiServerEnvironmentConfig(
                baseUrl = savedLocalBaseUrl ?: GlassesConstant.ServerEnvironment.LOCAL.baseUrl,
                wsUrl = savedLocalWsUrl ?: GlassesConstant.ServerEnvironment.LOCAL.wsUrl,
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
    ) {
        GlassesManage.initialize(SdkConfig(true, context, channel, LogUtils.V))
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

    private suspend fun connectAiAssistant() {
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
}
