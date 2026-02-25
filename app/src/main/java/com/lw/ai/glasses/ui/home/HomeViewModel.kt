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
import com.fission.wear.glasses.sdk.GlassesManage
import com.fission.wear.glasses.sdk.config.BleComConfig
import com.fission.wear.glasses.sdk.config.BleScanConfig
import com.fission.wear.glasses.sdk.constant.GlassesConstant
import com.fission.wear.glasses.sdk.constant.GlassesConstant.ACTION_INDEX_MUSIC
import com.fission.wear.glasses.sdk.constant.GlassesConstant.ACTION_INDEX_WEAR
import com.fission.wear.glasses.sdk.events.AiAssistantEvent
import com.fission.wear.glasses.sdk.events.CmdResultEvent
import com.fission.wear.glasses.sdk.events.ConnectionStateEvent
import com.fission.wear.glasses.sdk.events.ScanStateEvent
import com.lw.ai.glasses.service.AiAssistantService
import com.lw.top.lib_core.data.datastore.AppDataManager
import com.lw.top.lib_core.data.datastore.BluetoothDataManager
import com.polidea.rxandroidble3.exceptions.BleDisconnectedException
import com.polidea.rxandroidble3.exceptions.BleGattException
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

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bluetoothDataManager: BluetoothDataManager,
    private val appDataManager: AppDataManager
) : BaseViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState = _uiState.asStateFlow()

    private val _permissionEvent = MutableSharedFlow<List<String>>(replay = 1)
    val permissionEvent = _permissionEvent.asSharedFlow()

    private val _requestAudioPermissionEvent = MutableSharedFlow<Unit>()
    val requestAudioPermissionEvent = _requestAudioPermissionEvent.asSharedFlow()

    private val _navigationEvent = MutableSharedFlow<String>()
    val navigationEvent = _navigationEvent.asSharedFlow()

    init {
        viewModelScope.launch {
            bluetoothDataManager.savedBluetoothState.collect { stateInt ->
                val newState =
                    ConnectionState.entries.find { it.value == stateInt } ?: ConnectionState.IDLE

                if (newState == ConnectionState.IDLE) {
                    _uiState.update {
                        it.copy(
                            connectionState = ConnectionState.IDLE,
                        )
                    }
                } else {
                    val currentUiState = _uiState.value.connectionState
                    if (currentUiState == ConnectionState.IDLE || currentUiState == ConnectionState.DISCONNECTED) {
                        _uiState.update { it.copy(connectionState = newState) }
                    }
                }
            }
        }

        viewModelScope.launch {
            val connectionState =
                ConnectionState.fromValue(bluetoothDataManager.getBluetoothState())
            if (connectionState != ConnectionState.IDLE) {
                if (!bluetoothDataManager.getBluetoothAddress().isNullOrEmpty()) {
                    connectDevice(
                        bluetoothDataManager.getBluetoothAddress()!!,
                        bluetoothDataManager.getBluetoothName()!!
                    )
                    _uiState.update {
                        it.copy(
                            connectedDeviceName = bluetoothDataManager.getBluetoothName()!!
                        )
                    }
                }
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

            else -> {
                viewModelScope.launch {
                    _navigationEvent.emit(feature.route)
                }
            }
        }
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
            ToastUtils.showLong("需要录音权限才能使用翻译功能")
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
            ToastUtils.showLong("部分权限被拒绝，功能可能受限")
        }
    }

    private fun getPermissionsToRequest(): List<String> {
        val permissions = mutableListOf<String>()

        permissions.add(Manifest.permission.CAMERA)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }else{
            permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
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
            GlassesManage.eventFlow().collect { events ->
                when (events) {
                    is ScanStateEvent.DeviceFound -> {
                        _uiState.update { state ->
                            state.copy(
                                scannedDevices = state.scannedDevices
                                    .plus(events.data)
                                    .distinctBy { it.bleDevice.macAddress }
                                    .sortedByDescending { it.rssi }
                                    .filter {
                                        val name = it.bleDevice.name ?: ""
                                        name.contains("Glass", ignoreCase = true)
                                                || name.contains("AG66", ignoreCase = true)
                                                || name.contains("Tesee", ignoreCase = true)
                                                || name.contains("AG188", ignoreCase = true)
                                    }
                            )
                        }
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

                        viewModelScope.launch {
                            bluetoothDataManager.saveBluetoothState(ConnectionState.CONNECTING.value)
                        }

                        _uiState.update {
                            it.copy(
                                connectionState = ConnectionState.CONNECTING,
                            )
                        }
                    }

                    is ConnectionStateEvent.Connected -> {
                        _uiState.update {
                            it.copy(
                                connectionState = ConnectionState.CONNECTED,
                            )
                        }
                        viewModelScope.launch {
                            bluetoothDataManager.saveBluetoothState(ConnectionState.CONNECTED.value)
                        }
                        
                        // 启动 AI 助手前台保活服务
                        AiAssistantService.start(context)

                        GlassesManage.getBatteryLevel()
                        GlassesManage.getMediaFileCount()
                        GlassesManage.connectAiAssistant(
                            bluetoothDataManager.getBluetoothAddress()!!,
                            bluetoothDataManager.getBluetoothName()!!,
                            "6600",
                            "ukuSPzMnpLvLS2TTLL9S8PvUJzfTCHnu",
                            "tz5dgRLm6tXS8gRr"
                        )
                        GlassesManage.getActionState()

                    }

                    is ConnectionStateEvent.Disconnected -> {
                        _uiState.update {
                            it.copy(
                                connectionState = ConnectionState.DISCONNECTED,
                                batteryLevel = -1
                            )
                        }

                        if (error is BleDisconnectedException || error is BleGattException) {
                            _uiState.update {
                                it.copy(connectionState = ConnectionState.DISCONNECTED)
                            }
                        }

                        AiAssistantService.stop(context)

                        viewModelScope.launch {
                            bluetoothDataManager.saveBluetoothState(ConnectionState.DISCONNECTED.value)
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

                            ACTION_INDEX_WEAR -> {
                                LogUtils.d("佩戴状态发生变化${events.state}")
                            }

                            ACTION_INDEX_MUSIC -> {
                                //App 设备翻译时，App自行处理逻辑，开启录音。关闭音乐等逻辑
                                LogUtils.d("轻触设备，音乐状态发生变化${events.state}")
                            }

                            else -> {

                            }

                        }
                    }

                    is AiAssistantEvent.ReconnectRequired -> {
                        //上层业务自己判断 是否满足重连环境。网络 wifi 是否正常。
//                        GlassesManage.manualReconnect()
                    }

                    else -> {}
                }
            }
        }
    }

    fun startScanDevice() {
        GlassesManage.initialize(context, GlassesConstant.GLASSES_CHANNEL_LY)
        if (_uiState.value.isScanning) return
        GlassesManage.startScanBleDevices(
            bleScanConfig = BleScanConfig(isContinuousScan = false, scanDuration = 120000),
            scanSettings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                .build(),
            scanFilters = arrayOf(ScanFilter.Builder().build())
        )
    }

    fun connectDevice(mac: String, name: String) {
        GlassesManage.initialize(context, GlassesConstant.GLASSES_CHANNEL_LY)
        GlassesManage.stopScanBleDevices(context)
        if (mac.isEmpty()) {
            viewModelScope.launch {
                if (!bluetoothDataManager.getBluetoothAddress().isNullOrEmpty()) {
                    connectDevice(
                        bluetoothDataManager.getBluetoothAddress()!!,
                        bluetoothDataManager.getBluetoothName()!!
                    )
                }
            }
        } else {
            GlassesManage.connect(BleComConfig(context, mac))
            viewModelScope.launch {
                bluetoothDataManager.saveBluetoothDevice(mac, name)
            }
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    connectedDeviceName = name.ifEmpty { bluetoothDataManager.getBluetoothName()!! },
                )
            }
        }

    }

    fun updateEnvironment(env: GlassesConstant.ServerEnvironment) {
        GlassesManage.updateEnvironment(env)
        viewModelScope.launch {
            appDataManager.saveEnvironment(env.name)
        }
    }

    private fun updateFeatures() {
        _uiState.update { currentState ->
            currentState.copy(
                features = HomeUiState.initialFeatures(currentState.pendingSyncPhotosCount)
            )
        }
    }
}
