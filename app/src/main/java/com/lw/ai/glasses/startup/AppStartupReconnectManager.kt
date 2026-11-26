package com.lw.ai.glasses.startup

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.blankj.utilcode.util.AppUtils
import com.blankj.utilcode.util.LogUtils
import com.blankj.utilcode.util.Utils.OnAppStatusChangedListener
import com.fission.wear.glasses.sdk.AiAssistantClient
import com.fission.wear.glasses.sdk.GlassesManage
import com.fission.wear.glasses.sdk.config.BleComConfig
import com.fission.wear.glasses.sdk.config.SdkConfig
import com.fission.wear.glasses.sdk.constant.GlassesConstant
import com.fission.wear.glasses.sdk.events.CmdResultEvent
import com.fission.wear.glasses.sdk.events.ConnectionStateEvent
import com.fission.wear.glasses.sdk.events.ScanStateEvent
import com.lw.ai.glasses.config.AiAgentConfigBuilder
import com.lw.ai.glasses.config.AiAssistantConnectionHelper
import com.lw.ai.glasses.config.AiDialogueLanguageDefaults
import com.lw.ai.glasses.config.AppConfigLoader
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
    fun start() {
        if (!started.compareAndSet(false, true)) return

        wsConnectionStateManager.start()
        aiAssistantConversationManager.start()
        mediaSyncStateManager.start()
        deviceActionStateManager.start()
        observeGlassesEvents()
        registerAppLifecycle()
        appScope.launch {
            val environment = restoreSavedEnvironment()
            val savedAddress = bluetoothDataManager.getBluetoothAddress()
            val savedName = bluetoothDataManager.getBluetoothName()
            val isDeviceBound = !savedAddress.isNullOrBlank() && !savedName.isNullOrBlank()
            val channel = SdkChannelResolver.loadForSdkInit(bluetoothDataManager, appDataManager)
            if (isDeviceBound) {
                initGlassesSdkAndAiClient(environment, channel, savedName)
            } else {
                initAiClientOnly(channel)
            }
            autoReconnectLastDevice()
        }
    }

    /**
     * 退后台释放 SPP（保留系统配对），避免与另一 App 抢 RTK 经典蓝牙；
     * 回前台重新 initialize 并自动重连。
     */
    private fun registerAppLifecycle() {
        AppUtils.registerAppStatusChangedListener(object : OnAppStatusChangedListener {
            override fun onForeground(activity: Activity?) {
                LogUtils.d("AppStartupReconnect", "App foreground, try reconnect")
//                appScope.launch { reconnectAfterForeground() }
            }

            override fun onBackground(activity: Activity?) {
                LogUtils.d("AppStartupReconnect", "App background, release SPP (keep bond)")
//                runCatching {
//                    GlassesManage.disConnect(unpair = false)
//                }.onFailure {
//                    LogUtils.w("AppStartupReconnect", "background disconnect failed: ${it.message}")
//                }
            }
        })
    }

    private suspend fun reconnectAfterForeground() {
        val savedAddress = bluetoothDataManager.getBluetoothAddress()
        val savedName = bluetoothDataManager.getBluetoothName()
        if (savedAddress.isNullOrBlank() || savedName.isNullOrBlank()) return
        if (!hasBluetoothConnectPermission()) {
            LogUtils.w("AppStartupReconnect", "Skip foreground reconnect: no BLUETOOTH_CONNECT")
            return
        }
        val environment = restoreSavedEnvironment()
        val channel = SdkChannelResolver.loadForSdkInit(bluetoothDataManager, appDataManager)
        initGlassesSdkAndAiClient(environment, channel, savedName)
        GlassesManage.connect(
            BleComConfig(
                context = context,
                mac = savedAddress,
                isOtaMode = false,
                deviceName = savedName,
                adaptationNumber = bluetoothDataManager.getBluetoothAdapter(),
            )
        )
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
        val snapshot = AppConfigLoader.loadSnapshot(appDataManager)
        val resolved = AiAgentConfigBuilder.resolveEnvironment(snapshot)
        AiAgentConfigBuilder.applyResolvedEnvironmentToGlobals(resolved)
        return resolved.serverEnvironment
    }

    private suspend fun initGlassesSdkAndAiClient(
        environment: GlassesConstant.ServerEnvironment,
        channel: GlassesConstant.ChannelType,
        deviceName: String?,
    ) {
        val adaptationNumber = bluetoothDataManager.getBluetoothAdapter()
        val productSeries = ProductSeriesResolver.resolve(deviceName, adaptationNumber)
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
        initAiClientOnly(channel)
    }

    private suspend fun initAiClientOnly(
        channel: GlassesConstant.ChannelType,
    ) {
        val snapshot = AppConfigLoader.loadSnapshot(appDataManager)
        val resolved = AiAgentConfigBuilder.resolveEnvironment(snapshot)
        AiAssistantClient.getInstance().initializeAiClient(
            AiAgentConfigBuilder.buildAiAgentConfig(
                context = context,
                channel = channel,
                resolved = resolved,
                aiDialogueLanguage = AiDialogueLanguageDefaults.defaultLangType(),
            ),
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

        GlassesManage.connect(
            BleComConfig(
                context = context,
                mac = savedAddress,
                isOtaMode = false,
                deviceName = savedName,
                adaptationNumber = bluetoothDataManager.getBluetoothAdapter(),
            )
        )
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
