package com.lw.ai.glasses.ui.home

import com.lw.ai.glasses.ui.base.viewmodel.BaseViewModel
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
import com.fission.wear.glasses.sdk.events.ConnectionStateEvent
import com.fission.wear.glasses.sdk.events.ScanStateEvent
import com.lw.ai.glasses.R
import com.lw.ai.glasses.config.AppConfigLoader
import com.lw.top.lib_core.data.datastore.AppDataManager
import com.lw.top.lib_core.data.datastore.BluetoothDataManager
import com.polidea.rxandroidble3.scan.ScanFilter
import com.polidea.rxandroidble3.scan.ScanResult
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

    private val _scannedDevices = MutableStateFlow<List<ScanResult>>(emptyList())
    val scannedDevices = _scannedDevices.asStateFlow()

    private val _permissionEvent = MutableSharedFlow<List<String>>(replay = 1)
    val permissionEvent = _permissionEvent.asSharedFlow()

    private val _requestAudioPermissionEvent = MutableSharedFlow<Unit>()
    val requestAudioPermissionEvent = _requestAudioPermissionEvent.asSharedFlow()

    private val _requestLiveStreamingPermissionEvent = MutableSharedFlow<List<String>>()
    val requestLiveStreamingPermissionEvent = _requestLiveStreamingPermissionEvent.asSharedFlow()

    private val _navigationEvent = MutableSharedFlow<String>()
    val navigationEvent = _navigationEvent.asSharedFlow()

    init {
        viewModelScope.launch {
            bluetoothDataManager.getBluetoothName()?.let { savedName ->
                _uiState.update { it.copy(connectedDeviceName = savedName) }
            }
        }
        checkAndRequestPermissions()
        observeGlassesEvents()
        updateFeatures()
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
        }
        permissionsNeeded.add(Manifest.permission.ACCESS_WIFI_STATE)
        permissionsNeeded.add(Manifest.permission.CHANGE_WIFI_STATE)
        permissionsNeeded.add(Manifest.permission.INTERNET)
        permissionsNeeded.add(Manifest.permission.ACCESS_NETWORK_STATE)
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
                        onDeviceFound(events.data)
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

                    is ConnectionStateEvent.Connecting -> {
                        _uiState.update {
                            it.copy(connectionState = ConnectionState.CONNECTING)
                        }
                    }

                    is ConnectionStateEvent.Connected -> {
                        _uiState.update {
                            it.copy(connectionState = ConnectionState.CONNECTED)
                        }
                        refreshHomeDeviceSummary()
                    }

                    is ConnectionStateEvent.Disconnected -> {
                        _uiState.update {
                            it.copy(
                                connectionState = ConnectionState.DISCONNECTED,
                                batteryLevel = -1,
                            )
                        }
                    }

                    is ConnectionStateEvent.Idle -> {
                        _uiState.update {
                            it.copy(connectionState = ConnectionState.IDLE)
                        }
                    }

                    is ConnectionStateEvent.Failed -> {
                        _uiState.update {
                            it.copy(connectionState = ConnectionState.DISCONNECTED)
                        }
                    }

                    is CmdResultEvent.DevicePower -> {
                        _uiState.update {
                            it.copy(
                                batteryLevel = events.value ?: 0,
                                isCharging = events.isCharging
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
        if (_uiState.value.connectionState != ConnectionState.CONNECTED) {
            return
        }
        GlassesManage.getBatteryLevel()
        GlassesManage.getMediaFileCount()
        GlassesManage.setVoiceWakeUp(localOfflineEnabled = true, opusPushEnabled = true)
    }

    private suspend fun initGlassesSdkAndAiClient() {
        val snapshot = AppConfigLoader.loadSnapshot(appDataManager)
        GlassesManage.initialize(
            SdkConfig(true, context, snapshot.selectedChannel, LogUtils.V),
        )
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

    private fun isTargetGlassesDevice(name: String): Boolean {
        return name.contains("Glass", ignoreCase = true)
            || name.contains("AG66", ignoreCase = true)
            || name.contains("AG19", ignoreCase = true)
            || name.contains("Tesee", ignoreCase = true)
            || name.contains("AG188", ignoreCase = true)
            || name.contains("Xinmo G1", ignoreCase = true)
    }

    private fun onDeviceFound(result: ScanResult) {
        val name = result.bleDevice.name ?: return
        if (!isTargetGlassesDevice(name)) return

        val mac = result.bleDevice.macAddress
        val current = _scannedDevices.value
        val existing = current.find { it.bleDevice.macAddress == mac }
        if (existing != null && abs(existing.rssi - result.rssi) < 5) return

        _scannedDevices.value = (current.filter { it.bleDevice.macAddress != mac } + result)
            .sortedByDescending { it.rssi }
    }

    fun startScanDevice() {
        viewModelScope.launch {
            initGlassesSdkAndAiClient()
            startScanDeviceInternal()
        }
    }

    private fun startScanDeviceInternal() {
        if (_uiState.value.isScanning) return
        _scannedDevices.value = emptyList()
        _uiState.update { it.copy(isScanning = true) }
        GlassesManage.startScanBleDevices(
            bleScanConfig = BleScanConfig(isContinuousScan = false, scanDuration = 120000),
            scanSettings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
                .setCallbackType(ScanSettings.CALLBACK_TYPE_FIRST_MATCH)
                .build(),
            scanFilters = arrayOf(ScanFilter.Builder().build())
        )
    }

    fun stopScanDevice() {
        GlassesManage.stopScanBleDevices(context)
        _uiState.update { it.copy(isScanning = false) }
    }

    fun connectDevice(mac: String, name: String) {
        viewModelScope.launch {
            initGlassesSdkAndAiClient()
            connectDeviceInternal(mac, name)
        }
    }

    private suspend fun connectDeviceInternal(mac: String, name: String) {
        stopScanDevice()
        if (mac.isEmpty()) {
            if (!bluetoothDataManager.getBluetoothAddress().isNullOrEmpty()) {
                connectDeviceInternal(
                    bluetoothDataManager.getBluetoothAddress()!!,
                    bluetoothDataManager.getBluetoothName()!!,
                )
            }
            return
        }
        GlassesManage.connect(BleComConfig(context, mac, false))
        bluetoothDataManager.saveBluetoothDevice(mac, name)
        _uiState.update {
            it.copy(
                connectedDeviceName = name.ifEmpty { bluetoothDataManager.getBluetoothName()!! },
            )
        }
    }

    fun reconnectBt() {
        if (_uiState.value.connectionState != ConnectionState.CONNECTED) {
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
