package com.lw.ai.glasses.ui.home

import BaseViewModel
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewModelScope
import com.blankj.utilcode.util.LogUtils
import com.blankj.utilcode.util.ToastUtils
import com.fission.wear.glasses.sdk.AiAssistantClient
import com.fission.wear.glasses.sdk.GlassesManage
import com.fission.wear.glasses.sdk.config.AiAgentConfig
import com.fission.wear.glasses.sdk.config.BleComConfig
import com.fission.wear.glasses.sdk.config.BleScanConfig
import com.fission.wear.glasses.sdk.config.SdkConfig
import com.fission.wear.glasses.sdk.constant.GlassesConstant
import com.fission.wear.glasses.sdk.constant.GlassesConstant.ActionSyncType
import com.fission.wear.glasses.sdk.events.AgentEvent
import com.fission.wear.glasses.sdk.events.CmdResultEvent
import com.fission.wear.glasses.sdk.events.ScanStateEvent
import com.fission.wear.glasses.sdk.state.SdkBleConnectionState
import com.fission.wear.glasses.sdk.state.SdkBtConnectionState
import com.fission.wear.glasses.sdk.state.GlassesConnectionState
import com.fission.wear.glasses.sdk.data.model.ScannedBleDevice
import com.fission.wear.glasses.sdk.data.model.toHexStringUnsigned
import com.fission.wear.glasses.sdk.util.BleAdvertisementParser
import com.fission.wear.glasses.sdk.util.FissionLogUtils
import com.lw.ai.glasses.R
import com.lw.ai.glasses.config.AiDialogueLanguageDefaults
import com.lw.ai.glasses.config.AppConfigLoader
import com.lw.ai.glasses.config.ProductSeriesResolver
import com.lw.ai.glasses.config.SdkChannelResolver
import com.lw.top.lib_core.data.datastore.AppDataManager
import com.lw.top.lib_core.data.datastore.BluetoothDataManager
import com.polidea.rxandroidble3.scan.ScanFilter
import com.polidea.rxandroidble3.scan.ScanSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.abs

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bluetoothDataManager: BluetoothDataManager,
    private val appDataManager: AppDataManager
) : BaseViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState = _uiState.asStateFlow()

    private val _scannedDevices = MutableStateFlow<List<ScannedBleDevice>>(emptyList())
    val scannedDevices = _scannedDevices.asStateFlow()

    private val _permissionEvent = MutableSharedFlow<List<String>>(replay = 1)
    val permissionEvent = _permissionEvent.asSharedFlow()

    private val _requestAudioPermissionEvent = MutableSharedFlow<Unit>()
    val requestAudioPermissionEvent = _requestAudioPermissionEvent.asSharedFlow()

    private val _requestLiveStreamingPermissionEvent = MutableSharedFlow<List<String>>()
    val requestLiveStreamingPermissionEvent = _requestLiveStreamingPermissionEvent.asSharedFlow()

    private val _navigationEvent = MutableSharedFlow<String>()
    val navigationEvent = _navigationEvent.asSharedFlow()

    private var isDeviceBound = false
    /** 用户主动发起连接，在 SDK 上报 Connecting 前保持首页「连接中」。 */
    private var pendingUserConnect = false
    private var sawSdkConnecting = false

    init {
        observeSavedDeviceBinding()
        observeConnectionState()
        observeSdkChannel()
        checkAndRequestPermissions()
        observeGlassesEvents()
        updateFeatures()
    }

    private fun observeSdkChannel() {
        viewModelScope.launch {
            val channel = SdkChannelResolver.loadForSdkInit(bluetoothDataManager, appDataManager)
            _uiState.update {
                it.copy(showBtConnectionStatus = channel != GlassesConstant.ChannelType.RTK)
            }
        }
    }

    fun onFeatureClick(feature: Feature) {
        when (feature.id) {
            "ai_translate" -> {
                checkAudioPermissionAndNavigate(feature.route)
            }
            "live_streaming" -> {
                checkLiveStreamingPermissionAndNavigate(feature.route)
            }

            else -> {
                viewModelScope.launch {
                    _navigationEvent.emit(feature.route)
                }
            }
        }
    }

    private fun checkLiveStreamingPermissionAndNavigate(route: String) {
        val permissionsNeeded = getLiveStreamingPermissions()
        val permissionsToRequest = permissionsNeeded.filter { permission ->
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        }

        viewModelScope.launch {
            if (permissionsToRequest.isEmpty()) {
                _navigationEvent.emit(route)
            } else {
                _requestLiveStreamingPermissionEvent.emit(permissionsToRequest)
            }
        }
    }

    fun onLiveStreamingPermissionResult(permissions: Map<String, Boolean>) {
        val allRequestedGranted = permissions.values.all { it }
        val allPermissionsGranted = getLiveStreamingPermissions().all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }

        if (allRequestedGranted && allPermissionsGranted) {
            viewModelScope.launch {
                _navigationEvent.emit("live_streaming")
            }
        } else {
            ToastUtils.showLong(context.getString(R.string.permission_live_required))
        }
    }

    private fun getLiveStreamingPermissions(): List<String> {
        val permissionsNeeded = ArrayList<String>()
        permissionsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION)
        permissionsNeeded.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsNeeded.add(Manifest.permission.NEARBY_WIFI_DEVICES)
            permissionsNeeded.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        return permissionsNeeded
    }

    private fun checkAudioPermissionAndNavigate(route: String) {
        val hasAudioPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (hasAudioPermission) {
            viewModelScope.launch {
                _navigationEvent.emit(route)
            }
        } else {
            viewModelScope.launch {
                _requestAudioPermissionEvent.emit(Unit)
            }
        }
    }

    fun onRecordAudioPermissionResult(isGranted: Boolean) {
        if (isGranted) {
            viewModelScope.launch {
                _navigationEvent.emit("ai_translate")
            }
        } else {
            ToastUtils.showLong(context.getString(R.string.permission_audio_required))
        }
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = getPermissionsToRequest()
        if (permissionsToRequest.isNotEmpty()) {
            viewModelScope.launch {
                _permissionEvent.emit(permissionsToRequest)
            }
        }
    }

    fun onPermissionResult(isGranted: Boolean) {
        if (!isGranted) {
            ToastUtils.showLong(context.getString(R.string.permission_some_denied))
        }
    }

    private fun getPermissionsToRequest(): List<String> {
        val permissions = mutableListOf<String>()

        permissions.add(Manifest.permission.CAMERA)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }

        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> {
                // Android 8+：读取 Wi‑Fi SSID / legacy AP 连接轮询依赖真实 SSID，需精确位置授权
                permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            else -> {
                permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        return permissions
    }

    private fun observeSavedDeviceBinding() {
        viewModelScope.launch {
            bluetoothDataManager.savedBluetoothAddress.collect { address ->
                isDeviceBound = !address.isNullOrBlank()
                if (isDeviceBound) {
                    bluetoothDataManager.getBluetoothName()?.let { savedName ->
                        _uiState.update { it.copy(connectedDeviceName = savedName) }
                    }
                    syncConnectionStateFromSdk()
                } else {
                    _uiState.update {
                        it.copy(
                            connectionState = ConnectionState.IDLE,
                            connectedDeviceName = null,
                            batteryLevel = -1,
                            isCharging = null,
                            btConnectionState = BtConnectionState.IDLE,
                            btFailureReason = null,
                        )
                    }
                }
            }
        }
    }

    private fun observeConnectionState() {
        viewModelScope.launch {
            GlassesManage.connectionStateFlow().collect { sdkState ->
                if (!isDeviceBound) return@collect
                applySdkConnectionState(sdkState)
            }
        }
    }

    private fun syncConnectionStateFromSdk() {
        if (!isDeviceBound) return
        applySdkConnectionState(GlassesManage.currentConnectionState())
    }

    private fun applySdkConnectionState(sdkState: GlassesConnectionState) {
        if (sdkState.bleState == SdkBleConnectionState.CONNECTING) {
            sawSdkConnecting = true
        }

        val mappedBle = sdkState.bleState.toUiConnectionState()
        val connectionState = when {
            pendingUserConnect && !sawSdkConnecting &&
                (sdkState.bleState == SdkBleConnectionState.IDLE ||
                    sdkState.bleState == SdkBleConnectionState.DISCONNECTED) ->
                ConnectionState.CONNECTING
            else -> mappedBle
        }

        when (sdkState.bleState) {
            SdkBleConnectionState.CONNECTED,
            SdkBleConnectionState.FAILED -> {
                pendingUserConnect = false
                sawSdkConnecting = false
            }
            SdkBleConnectionState.DISCONNECTED -> {
                if (sawSdkConnecting) {
                    pendingUserConnect = false
                    sawSdkConnecting = false
                }
            }
            else -> Unit
        }

        val wasBleConnected = _uiState.value.connectionState == ConnectionState.CONNECTED
        val btConnectionState = if (_uiState.value.showBtConnectionStatus) {
            sdkState.btState.toUiBtConnectionState()
        } else {
            BtConnectionState.IDLE
        }
        _uiState.update { ui ->
            ui.copy(
                connectionState = connectionState,
                btConnectionState = btConnectionState,
                btFailureReason = if (ui.showBtConnectionStatus) sdkState.btFailureReason else null,
                connectedDeviceName = sdkState.deviceName ?: ui.connectedDeviceName,
            )
        }
        if (sdkState.isBleConnected && !wasBleConnected) {
            refreshHomeDeviceSummary()
        }
        if (!sdkState.isBleConnected) {
            _uiState.update { it.copy(batteryLevel = -1) }
        }
    }

    private fun SdkBleConnectionState.toUiConnectionState(): ConnectionState = when (this) {
        SdkBleConnectionState.IDLE -> if (isDeviceBound) ConnectionState.DISCONNECTED else ConnectionState.IDLE
        SdkBleConnectionState.CONNECTING -> ConnectionState.CONNECTING
        SdkBleConnectionState.CONNECTED -> ConnectionState.CONNECTED
        SdkBleConnectionState.DISCONNECTED -> ConnectionState.DISCONNECTED
        SdkBleConnectionState.FAILED -> ConnectionState.DISCONNECTED
    }

    private fun SdkBtConnectionState.toUiBtConnectionState(): BtConnectionState = when (this) {
        SdkBtConnectionState.IDLE -> BtConnectionState.IDLE
        SdkBtConnectionState.BONDING -> BtConnectionState.BONDING
        SdkBtConnectionState.CONNECTING -> BtConnectionState.CONNECTING
        SdkBtConnectionState.CONNECTED -> BtConnectionState.CONNECTED
        SdkBtConnectionState.FAILED -> BtConnectionState.FAILED
        SdkBtConnectionState.DISCONNECTED -> BtConnectionState.DISCONNECTED
    }

    fun observeGlassesEvents() {
        viewModelScope.launch {
            AiAssistantClient.getInstance().aiAgentEventFlow().collect { event->
                when(event){
                    is AgentEvent.ReconnectRequired -> {
                        //上层业务自己判断 是否满足重连环境。网络 wifi 是否正常。
//                       AiAssistantClient.getInstance().manualReconnect()
                    }
                    else -> {}
                }
            }
        }
        viewModelScope.launch {
            GlassesManage.eventFlow().collect { events ->
                when (events) {
                    is ScanStateEvent.DeviceFound -> {
                        onDeviceFound(events)
                    }

                    is ScanStateEvent.ScanFinished -> {
                        _uiState.update {
                            it.copy(isScanning = false)
                        }
                    }

                    is ScanStateEvent.Error -> {
                        _uiState.update {
                            it.copy(isScanning = false)
                        }
                    }

                    is CmdResultEvent.DevicePower -> {
                        _uiState.update {
                            it.copy(
                                batteryLevel = events.value ?: it.batteryLevel,
                                isCharging = events.isCharging ?: it.isCharging
                            )
                        }
                    }

                    is CmdResultEvent.MediaFileCount -> {
                        _uiState.update { it.copy(pendingSyncPhotosCount = events.count) }
                        updateFeatures()
                    }

                    is CmdResultEvent.ActionSync -> {
                        when (events.type) {
                            ActionSyncType.SINGLE_TOUCH -> {
                                LogUtils.d("actionIndex", "单击事件 ${events.state}")
                            }
                            ActionSyncType.WEAR -> {
                                LogUtils.d("actionIndex", "佩戴状态发生变化${events.state}")
                            }
                            ActionSyncType.MUSIC -> {
                                // App 设备翻译时，App 自行处理逻辑，开启录音、关闭音乐等
                                LogUtils.d("actionIndex", "轻触设备，音乐状态发生变化${events.state}")
                            }
                            else -> Unit
                        }
                    }

                    else -> {}
                }
            }
        }
    }

    private fun refreshHomeDeviceSummary() {
        if (!GlassesManage.currentConnectionState().isBleConnected) {
            return
        }
        GlassesManage.getBatteryLevel()
        GlassesManage.getMediaFileCount()
        GlassesManage.setVoiceWakeUp(localOfflineEnabled = true, opusPushEnabled = true)
    }

    private suspend fun initGlassesSdkAndAiClient(
        deviceName: String? = null,
        channelType: GlassesConstant.ChannelType? = null,
    ) {
        val snapshot = AppConfigLoader.loadSnapshot(appDataManager)
        val resolvedName = deviceName
            ?: bluetoothDataManager.getBluetoothName()
        val productSeries = ProductSeriesResolver.fromDeviceName(resolvedName)
        val channel = channelType ?: SdkChannelResolver.loadForSdkInit(bluetoothDataManager, appDataManager)
        LogUtils.i(
            "HomeViewModel",
            "init SDK channel=${channel.name} productSeries=${productSeries.code} deviceName=$resolvedName"
        )
        GlassesManage.initialize(
            SdkConfig(
                true,
                context,
                channel,
                LogUtils.V,
                productSeries = productSeries,
            ),
        )
        _uiState.update {
            it.copy(showBtConnectionStatus = channel != GlassesConstant.ChannelType.RTK)
        }
        val localConfig = AppConfigLoader.localCustomEnvironment(snapshot)
        if (localConfig != null) {
            AiAssistantClient.getInstance().applyServerEnvironmentToGlobals(localConfig)
        } else {
            AiAssistantClient.getInstance().applyServerEnvironmentToGlobals(snapshot.selectedEnvironment)
        }
        AiAssistantClient.getInstance().initializeAiClient(
            AiAgentConfig(
                context = context,
                channel = channel,
                aiModelType = GlassesConstant.AiModelVendor.DEFAULT,
                serverEnvironment = snapshot.selectedEnvironment,
                customServerEnvironment = localConfig,
                enableDefaultPlaySimultaneousAudio = false,
                aiDialogueLanguage = AiDialogueLanguageDefaults.defaultLangType(),
            ),
        )
    }

    private fun onDeviceFound(result: ScanStateEvent.DeviceFound) {
//        FissionLogUtils.d("${result.data.bleDevice.name} - ${result.data.bleDevice.macAddress} - ${result.data.scanRecord.bytes.toHexStringUnsigned()}")
        val parsed = BleAdvertisementParser.parse(result.data) ?: return
        if (!parsed.isListDisplayable) return

        val mac = parsed.macAddress
        val current = _scannedDevices.value
        val existing = current.find { it.macAddress == mac }
        if (existing != null && abs(existing.rssi - parsed.rssi) < 5) return

        _scannedDevices.value = (current.filter { it.macAddress != mac } + parsed)
            .sortedByDescending { it.rssi }
    }

    fun startScanDevice() {
        startScanDeviceInternal()
    }

    private fun startScanDeviceInternal() {
        if (_uiState.value.isScanning) return
        _scannedDevices.value = emptyList()
        _uiState.update { it.copy(isScanning = true) }
        GlassesManage.startScanBleDevices(
            context = context,
            bleScanConfig = BleScanConfig(isContinuousScan = false, scanDuration = 120000),
            scanSettings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                .build(),
            scanFilters = arrayOf(ScanFilter.Builder().build())
        )
    }

    fun stopScanDevice() {
        GlassesManage.stopScanBleDevices(context)
        _uiState.update { it.copy(isScanning = false) }
    }

    fun connectDevice(device: ScannedBleDevice) {
        val channel = device.channelType
        if (device.macAddress.isNotEmpty() && channel == null) {
            ToastUtils.showLong(context.getString(R.string.scanned_device_channel_unrecognized))
            return
        }
        if (device.macAddress.isNotEmpty()) {
            markConnecting(device.deviceName)
        }
        viewModelScope.launch {
            if (device.macAddress.isEmpty()) {
                markReconnectingIfNeeded()
            }
            val deviceName = device.deviceName.ifBlank { bluetoothDataManager.getBluetoothName() }
            initGlassesSdkAndAiClient(deviceName, channel)
            connectDeviceInternal(device)
        }
    }

    fun connectDevice(mac: String, name: String) {
        connectDevice(
            ScannedBleDevice(
                macAddress = mac,
                deviceName = name,
                channelType = null,
                adaptationNumber = "",
                rssi = 0,
            )
        )
    }

    private fun markConnecting(deviceName: String) {
        isDeviceBound = true
        pendingUserConnect = true
        sawSdkConnecting = false
        _uiState.update {
            it.copy(
                connectionState = ConnectionState.CONNECTING,
                connectedDeviceName = deviceName.ifEmpty { it.connectedDeviceName },
            )
        }
    }

    private suspend fun markReconnectingIfNeeded() {
        val address = bluetoothDataManager.getBluetoothAddress()
        if (address.isNullOrBlank()) return
        isDeviceBound = true
        pendingUserConnect = true
        sawSdkConnecting = false
        _uiState.update {
            it.copy(
                connectionState = ConnectionState.CONNECTING,
                connectedDeviceName = bluetoothDataManager.getBluetoothName() ?: it.connectedDeviceName,
            )
        }
    }

    private suspend fun connectDeviceInternal(device: ScannedBleDevice) {
        stopScanDevice()
        if (device.macAddress.isEmpty()) {
            if (!bluetoothDataManager.getBluetoothAddress().isNullOrEmpty()) {
                connectDeviceInternal(
                    ScannedBleDevice(
                        macAddress = bluetoothDataManager.getBluetoothAddress()!!,
                        deviceName = bluetoothDataManager.getBluetoothName()!!,
                        channelType = null,
                        adaptationNumber = bluetoothDataManager.getBluetoothAdapter().orEmpty(),
                        rssi = 0,
                    )
                )
            }
            return
        }
        GlassesManage.connect(
            BleComConfig(
                context = context,
                mac = device.macAddress,
                isOtaMode = device.isOtaMode,
                deviceName = device.deviceName,
                adaptationNumber = device.adaptationNumber.takeIf { it.isNotBlank() }
                    ?: bluetoothDataManager.getBluetoothAdapter()?.takeIf { it.isNotBlank() },
            )
        )
        bluetoothDataManager.saveBluetoothDevice(
            address = device.macAddress,
            name = device.deviceName,
            sdkChannel = device.channelType?.name,
            adaptationNumber = device.adaptationNumber.takeIf { it.isNotBlank() },
        )
        _uiState.update {
            it.copy(
                connectedDeviceName = device.deviceName.ifEmpty {
                    bluetoothDataManager.getBluetoothName()!!
                },
            )
        }
    }

    fun reconnectBt() {
        if (!GlassesManage.currentConnectionState().isBleConnected) {
            ToastUtils.showShort(context.getString(R.string.bt_reconnect_ble_required))
            return
        }
        GlassesManage.reconnectBluetooth()
    }

    private fun updateFeatures() {
        _uiState.update { currentState ->
            currentState.copy(
                features = HomeUiState.initialFeatures(currentState.pendingSyncPhotosCount)
            )
        }
    }
}
