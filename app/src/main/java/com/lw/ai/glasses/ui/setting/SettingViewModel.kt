package com.lw.ai.glasses.ui.setting

import BaseViewModel
import androidx.lifecycle.viewModelScope
import com.blankj.utilcode.util.LogUtils
import com.fission.wear.glasses.sdk.GlassesManage
import com.fission.wear.glasses.sdk.constant.LyCmdConstant
import com.fission.wear.glasses.sdk.data.dto.DeviceSettingsStateDTO
import com.fission.wear.glasses.sdk.data.dto.DeviceVersionInfoDTO
import com.fission.wear.glasses.sdk.events.CmdResultEvent
import com.lw.ai.glasses.ui.home.ConnectionState
import com.lw.top.lib_core.data.datastore.BluetoothDataManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.collections.filterIsInstance

@HiltViewModel
class SettingViewModel @Inject constructor(
    private val bluetoothDataManager: BluetoothDataManager
) : BaseViewModel() {

    private var localOfflineVoiceEnabled: Boolean = true
    private var opusStreamPushEnabled: Boolean = true
    private var latestDeviceVersionInfo: DeviceVersionInfoDTO? = null
    private var hasLoadedDeviceSettings: Boolean = false

    private val _uiState = MutableStateFlow(SettingUiState())
    val uiState = _uiState.asStateFlow()

    init {
        observeGlassesEvents()
        loadInitialSettings()
    }

    private fun observeGlassesEvents() {
        viewModelScope.launch {
            GlassesManage.eventFlow().collect { events ->
                when (events) {
                    is CmdResultEvent.DeviceSupportedFeatures -> {
                        _uiState.update {
                            it.copy(
                                isSupportLiveSteaming = events.featuresConfigInfo.supportLiveStreaming,
                                isSupportsQuickVolumeAdjust = events.featuresConfigInfo.supportQuicklyAdjustVolume
                            )
                        }

                        GlassesManage.getVolume()
                    }
                    is CmdResultEvent.DeviceVolumeState ->{
                        addVolumeSetting(events.systemVolume,events.mediaVolume,events.callVolume)
                    }
                    is CmdResultEvent.DeviceVersionInfoEvent -> {
                        latestDeviceVersionInfo = events.data
                        if (hasLoadedDeviceSettings) {
                            _uiState.update { state ->
                                state.copy(
                                    settingItems = appendVersionInfoItems(state.settingItems)
                                )
                            }
                        }
                    }

                    is CmdResultEvent.DeviceSettingsStateEvent -> {
                        hasLoadedDeviceSettings = true
                        _uiState.update { currentState ->
                            val newSettingItems = appendVersionInfoItems(
                                mapDtoToUiState(events.data).settingItems
                            )
                            currentState.copy(
                                settingItems = newSettingItems,
                                disconnectAction = currentState.disconnectAction.copy(isEnabled = true)
                            )
                        }
                    }

                    is CmdResultEvent.VoiceCommandDisableState -> {
                        localOfflineVoiceEnabled = !events.localOfflineVoiceDisabled
                        opusStreamPushEnabled = !events.opusStreamPushDisabled
                        patchVoiceCommandDisableSwitches(
                            localOfflineEnabled = localOfflineVoiceEnabled,
                            opusPushEnabled = opusStreamPushEnabled
                        )
                    }

                    else -> {}
                }
            }
        }
    }

    private fun loadInitialSettings() {
        GlassesManage.requestDeviceVersionInfo()
        GlassesManage.getDeviceSettingsState()
        GlassesManage.getVoiceCommandDisableState()
        GlassesManage.getDeviceSupportedFeatures()
    }

    fun onRecordDurationChanged(duration: Int) {
        GlassesManage.setVideoDuration(duration)
        _uiState.update { currentState ->
            val newItems = currentState.settingItems.map { item ->
                if (item is SettingItem.ActionItem && item.id == "record_duration") {
                    item.copy(summary = "$duration 秒")
                } else {
                    item
                }
            }
            currentState.copy(settingItems = newItems)
        }
    }

    fun onVolumeChanged(type: Int, volume:Int){
        val volumeType =   when(type){
            0 -> LyCmdConstant.AudioVolumeType.SYSTEM
            1 -> LyCmdConstant.AudioVolumeType.MEDIA
            else -> LyCmdConstant.AudioVolumeType.CALL
        }
        GlassesManage.setVolume(volumeType,volume)
    }

    fun <T> onSettingSelected(settingId: String, selectedValue: T) {
        if (settingId.startsWith("gesture_")) {
            val gestureTypeName = settingId.removePrefix("gesture_")
            try {
                val gestureType = LyCmdConstant.GestureType.valueOf(gestureTypeName)
                if (selectedValue is LyCmdConstant.GestureAction) {
                    GlassesManage.setGestureShortcut(gestureType, selectedValue)
                }
            } catch (e: IllegalArgumentException) {
                LogUtils.e("无效的手势类型: $gestureTypeName")
            }
        } else {
            when (settingId) {
                "led_brightness" -> {
                    if (selectedValue is LyCmdConstant.LedBrightnessLevel) {
                        GlassesManage.setLedBrightness(selectedValue)
                    }
                }

                "screen_orientation" -> {
                    if (selectedValue is LyCmdConstant.ScreenOrientation) {
                        GlassesManage.setScreenOrientation(selectedValue)
                    }
                }

                "wear_detection" -> {
                    if (selectedValue is LyCmdConstant.WearDetectionState) {
                        GlassesManage.setWearDetection(selectedValue)
                    }
                }

                "voice_command" -> {
                    if (selectedValue is Boolean) {
                        setVoiceWakeUp(selectedValue)
                    }
                }
            }
        }

        _uiState.update { currentState ->
            val newItems = currentState.settingItems.map { item ->
                if (item is SettingItem.DropdownItem<*> && item.id == settingId) {
                    item.withNewSelection(selectedValue)
                } else {
                    item
                }
            }
            currentState.copy(settingItems = newItems)
        }
    }

    fun setVoiceWakeUp(enable: Boolean){
        GlassesManage.setVoiceWakeUp(enable = enable)

        _uiState.update { currentState ->
            val updatedItems = currentState.settingItems.map { item ->
                if (item is SettingItem.SwitchItem && item.id == "voice_command") {
                    item.copy(isChecked = enable)
                } else {
                    item
                }
            }
            currentState.copy(settingItems = updatedItems)
        }
    }

    /** 开关为「启用」：开启 = 设备可使用本地离线语音指令 */
    fun setLocalOfflineVoiceEnabled(enabled: Boolean) {
        localOfflineVoiceEnabled = enabled
        GlassesManage.setVoiceCommandDisableState(
            localOfflineEnabled = localOfflineVoiceEnabled,
            opusPushEnabled = opusStreamPushEnabled
        )
        patchVoiceCommandDisableSwitches(
            localOfflineEnabled = localOfflineVoiceEnabled,
            opusPushEnabled = opusStreamPushEnabled
        )
    }

    /** 开关为「启用」：开启 = 允许 AI 唤醒与 Opus 音频推送 */
    fun setOpusStreamPushEnabled(enabled: Boolean) {
        opusStreamPushEnabled = enabled
        GlassesManage.setVoiceCommandDisableState(
            localOfflineEnabled = localOfflineVoiceEnabled,
            opusPushEnabled = opusStreamPushEnabled
        )
        patchVoiceCommandDisableSwitches(
            localOfflineEnabled = localOfflineVoiceEnabled,
            opusPushEnabled = opusStreamPushEnabled
        )
    }

    private fun patchVoiceCommandDisableSwitches(
        localOfflineEnabled: Boolean,
        opusPushEnabled: Boolean
    ) {
        _uiState.update { state ->
            state.copy(
                settingItems = state.settingItems.map { item ->
                    when {
                        item is SettingItem.SwitchItem && item.id == "voice_disable_local" ->
                            item.copy(isChecked = localOfflineEnabled)
                        item is SettingItem.SwitchItem && item.id == "voice_disable_opus" ->
                            item.copy(isChecked = opusPushEnabled)
                        else -> item
                    }
                }
            )
        }
    }

    private fun appendVersionInfoItems(items: List<SettingItem>): List<SettingItem> {
        val versionInfo = latestDeviceVersionInfo ?: return stripVersionInfoItems(items)
        val baseItems = stripVersionInfoItems(items)
        val versionItems = listOf(
            SettingItem.InfoItem("固件版本", versionInfo.firmwareVersion),
            SettingItem.InfoItem("Wifi版本", versionInfo.wifiVersion),
            SettingItem.InfoItem("硬件版本", versionInfo.hardwareVersion)
        )

        return buildList {
            addAll(baseItems)
            if (baseItems.isNotEmpty()) {
                add(SettingItem.Divider)
            }
            versionItems.forEachIndexed { index, item ->
                add(item)
                if (index < versionItems.lastIndex) {
                    add(SettingItem.Divider)
                }
            }
        }
    }

    private fun stripVersionInfoItems(items: List<SettingItem>): List<SettingItem> {
        return items.filterIndexed { index, item ->
            when {
                item is SettingItem.InfoItem -> false
                item is SettingItem.Divider -> {
                    val previousItem = items.getOrNull(index - 1)
                    val nextItem = items.getOrNull(index + 1)
                    previousItem !is SettingItem.InfoItem && nextItem !is SettingItem.InfoItem
                }
                else -> true
            }
        }
    }

    private fun addVolumeSetting(systemVolume:Int ,mediaVolume:Int,callVolume:Int){
        val items = mutableListOf<SettingItem>()
        items.add(
            SettingItem.ActionItem(
                id = "volume_system",
                title = "系统音量",
                summary = "$systemVolume",
            )
        )

        items.add(
            SettingItem.ActionItem(
                id = "volume_media",
                title = "媒体音量",
                summary = "$mediaVolume"
            )
        )

        items.add(
            SettingItem.ActionItem(
                id = "volume_call",
                title = "通话音量",
                summary = "$callVolume"
            )
        )


        _uiState.update { state ->
            val currentItems = state.settingItems.toMutableList()
            currentItems.removeIf { it is SettingItem.ActionItem && it.id.startsWith("volume_")}
            val rebootItem = currentItems.filterIsInstance<SettingItem.ActionItem>()
                    .first { it.id == "reboot_device" }

            val targetIndex = currentItems.indexOf(rebootItem)
            currentItems.addAll(targetIndex,items)
            state.copy(settingItems = currentItems)
        }


    }

    private fun mapDtoToUiState(dto: DeviceSettingsStateDTO): SettingUiState {
        val items = mutableListOf<SettingItem>()

        items.add(
            SettingItem.DropdownItem(
                id = "led_brightness",
                title = "LED 亮度",
                selectedOption = SettingMapper.toLedBrightnessOptions()
                                         .find { it.value == dto.ledBrightness } ?: SettingMapper.toLedBrightnessOptions()
                                         .first(),
                options = SettingMapper.toLedBrightnessOptions()
            ))

        items.add(
            SettingItem.ActionItem(
                id = "record_duration",
                title = "录像时长",
                summary = "${dto.recordDuration ?: "未设置"} 秒"
            )
        )

        items.add(
            SettingItem.DropdownItem(
                id = "wear_detection",
                title = "佩戴检测",
                selectedOption = SettingMapper.toWearDetectionOptions()
                                         .find { it.value == dto.wearDetectionEnabled }
                                 ?: SettingMapper.toWearDetectionOptions().first(),
                options = SettingMapper.toWearDetectionOptions()
            ))

        items.add(
            SettingItem.SwitchItem(
                id = "voice_command",
                title = "语音指令",
                isChecked = dto.voiceCommandEnabled ?: true,
                summary = "开启后可使用语音控制"
            )
        )

        items.add(
            SettingItem.SwitchItem(
                id = "voice_disable_local",
                title = "本地离线语音指令",
                isChecked = localOfflineVoiceEnabled,
                summary = "关闭后禁用设备端离线语音控制（协议 bit0）"
            )
        )
        items.add(
            SettingItem.SwitchItem(
                id = "voice_disable_opus",
                title = "AI 唤醒（Opus 推送）",
                isChecked = opusStreamPushEnabled,
                summary = "关闭后禁用 AI 唤醒与 Opus 音频推流（协议 bit1）"
            )
        )

        val gestureActionOptions = SettingMapper.toGestureActionOptions()
        LyCmdConstant.GestureType.entries.forEach { gestureType ->
            val currentAction = dto.gestureSettings?.get(gestureType)
            items.add(
                SettingItem.DropdownItem(
                    id = "gesture_${gestureType.name}",
                    title = SettingMapper.toGestureTypeTitle(gestureType),
                    selectedOption = gestureActionOptions.find { it.value == currentAction }
                                     ?: gestureActionOptions.first(),
                    options = gestureActionOptions
                ))
        }

        items.add(
            SettingItem.ActionItem(
                id = "burst_photo_count",
                title = "连拍张数",
                summary = dto.burstPhotoCount?.toString() ?: "未设置"
            )
        )

        items.add(
            SettingItem.DropdownItem(
                id = "screen_orientation",
                title = "屏幕方向",
                selectedOption = SettingMapper.toScreenOrientationOptions()
                                         .find { it.value == dto.orientation } ?: SettingMapper.toScreenOrientationOptions()
                                         .first(),
                options = SettingMapper.toScreenOrientationOptions()
            ))

        items.add(
            SettingItem.ActionItem(
                id = "reboot_device",
                title = "重启设备",
                summary = "重启动力眼镜"
            )
        )
        items.add(
            SettingItem.ActionItem(
                id = "restore_factory",
                title = "恢复出厂设置",
                summary = "清除所有用户数据并重启"
            )
        )

        val itemsWithDividers = items.flatMapIndexed { index, item ->
            if (index < items.size - 1) {
                listOf(item, SettingItem.Divider)
            } else {
                listOf(item)
            }
        }

        return SettingUiState(settingItems = itemsWithDividers)
    }

    fun rebootDevice() {
        GlassesManage.rebootDevice()
    }

    fun restoreFactorySettings() {
        GlassesManage.restoreFactorySettings()
    }

    private fun SettingUiState.getLatestGestureSettingsMap(): Map<LyCmdConstant.GestureType, LyCmdConstant.GestureAction> {
        return this.settingItems
                .filterIsInstance<SettingItem.DropdownItem<*>>()
                .filter { it.id.startsWith("gesture_") }
                .mapNotNull { item ->
                    try {
                        val typeName = item.id.removePrefix("gesture_")
                        val gestureType = LyCmdConstant.GestureType.valueOf(typeName)
                        val gestureAction = item.selectedOption.value as LyCmdConstant.GestureAction
                        gestureType to gestureAction
                    } catch (e: Exception) {
                        null
                    }
                }.toMap()
    }

    private fun <T> SettingItem.DropdownItem<T>.withNewSelection(value: Any?): SettingItem.DropdownItem<T> {
        val newSelectedOption = this.options.find { it.value == value }
        return if (newSelectedOption != null) this.copy(selectedOption = newSelectedOption) else this
    }

    fun onDisconnect() {
        viewModelScope.launch {
            val currentStateValue = bluetoothDataManager.getBluetoothState()
            val connectionState = ConnectionState.fromValue(currentStateValue)

            _uiState.update { it.copy(isUnbinding = true) }

//             if (connectionState == ConnectionState.CONNECTED) {
            GlassesManage.disConnect()
            delay(1000)
//             }

            bluetoothDataManager.clearBluetoothDevice()

            _uiState.update {
                it.copy(
                    isUnbinding = false,
                    disconnectAction = it.disconnectAction.copy(isEnabled = false)
                )
            }
        }
    }
}
