package com.lw.ai.glasses.ui.setting

import com.lw.ai.glasses.ui.base.viewmodel.BaseViewModel
import android.content.Context
import androidx.lifecycle.viewModelScope
import com.blankj.utilcode.util.LogUtils
import com.fission.wear.glasses.sdk.GlassesManage
import com.fission.wear.glasses.sdk.AiAssistantClient
import com.fission.wear.glasses.sdk.constant.LyCmdConstant
import com.fission.wear.glasses.sdk.data.dto.DeviceSettingsStateDTO
import com.fission.wear.glasses.sdk.data.dto.DeviceVersionInfoDTO
import com.fission.wear.glasses.sdk.events.CmdResultEvent
import com.lw.ai.glasses.R
import com.lw.ai.glasses.ui.home.ConnectionState
import com.lw.top.lib_core.data.datastore.BluetoothDataManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.collections.filterIsInstance

@HiltViewModel
class SettingViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
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
                                isSupportLiveSteaming = events.featuresConfigInfo.supportLiveStreaming
                            )
                        }
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
        GlassesManage.getVoiceWakeUp()
        GlassesManage.getDeviceSupportedFeatures()
    }

    fun onRecordDurationChanged(duration: Int) {
        GlassesManage.setVideoDuration(duration)
        _uiState.update { currentState ->
            val newItems = currentState.settingItems.map { item ->
                if (item is SettingItem.ActionItem && item.id == "record_duration") {
                    item.copy(summary = context.getString(R.string.duration_seconds, duration))
                } else {
                    item
                }
            }
            currentState.copy(settingItems = newItems)
        }
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

    fun setWearDetectionEnabled(enabled: Boolean) {
        GlassesManage.setWearDetection(
            if (enabled) LyCmdConstant.WearDetectionState.ON else LyCmdConstant.WearDetectionState.OFF
        )
        _uiState.update { state ->
            state.copy(
                settingItems = state.settingItems.map { item ->
                    if (item is SettingItem.SwitchItem && item.id == "wear_detection") {
                        item.copy(isChecked = enabled)
                    } else {
                        item
                    }
                }
            )
        }
    }

    /** 开关为「启用」：开启 = 设备可使用本地离线语音指令 */
    fun setLocalOfflineVoiceEnabled(enabled: Boolean) {
        localOfflineVoiceEnabled = enabled
        GlassesManage.setVoiceWakeUp(
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
        GlassesManage.setVoiceWakeUp(
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
            SettingItem.InfoItem(context.getString(R.string.firmware_version), versionInfo.firmwareVersion),
            SettingItem.InfoItem(context.getString(R.string.wifi_version), versionInfo.wifiVersion),
            SettingItem.InfoItem(context.getString(R.string.hardware_version), versionInfo.hardwareVersion)
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

    private fun mapDtoToUiState(dto: DeviceSettingsStateDTO): SettingUiState {
        val items = mutableListOf<SettingItem>()

        items.add(
            SettingItem.DropdownItem(
            id = "led_brightness",
            title = context.getString(R.string.led_brightness),
            selectedOption = SettingMapper.toLedBrightnessOptions(context)
                .find { it.value == dto.ledBrightness } ?: SettingMapper.toLedBrightnessOptions(context)
                .first(),
            options = SettingMapper.toLedBrightnessOptions(context)
        ))

        items.add(
            SettingItem.ActionItem(
                id = "record_duration",
                title = context.getString(R.string.video_recording),
                summary = context.getString(
                    R.string.duration_value_seconds,
                    dto.recordDuration?.toString() ?: context.getString(R.string.not_set)
                )
            )
        )

        items.add(
            SettingItem.SwitchItem(
                id = "wear_detection",
                title = context.getString(R.string.wear_detection),
                isChecked = dto.wearDetectionEnabled != LyCmdConstant.WearDetectionState.OFF
            )
        )

        items.add(
            SettingItem.SwitchItem(
                id = "voice_disable_local",
                title = context.getString(R.string.local_offline_voice_command),
                isChecked = localOfflineVoiceEnabled,
                summary = context.getString(R.string.local_offline_voice_command_summary)
            )
        )
        items.add(
            SettingItem.SwitchItem(
                id = "voice_disable_opus",
                title = context.getString(R.string.ai_wakeup_opus_push),
                isChecked = opusStreamPushEnabled,
                summary = context.getString(R.string.ai_wakeup_opus_push_summary)
            )
        )

        val gestureActionOptions = SettingMapper.toGestureActionOptions(context)
        LyCmdConstant.GestureType.entries.forEach { gestureType ->
            val currentAction = dto.gestureSettings?.get(gestureType)
            items.add(
                SettingItem.DropdownItem(
                    id = "gesture_${gestureType.name}",
                title = SettingMapper.toGestureTypeTitle(context, gestureType),
                selectedOption = gestureActionOptions.find { it.value == currentAction }
                    ?: gestureActionOptions.first(),
                options = gestureActionOptions
            ))
        }

        items.add(
            SettingItem.ActionItem(
                id = "burst_photo_count",
                title = context.getString(R.string.burst_photo_count),
                summary = dto.burstPhotoCount?.toString() ?: context.getString(R.string.not_set)
            )
        )

        items.add(
            SettingItem.DropdownItem(
                id = "screen_orientation",
            title = context.getString(R.string.screen_orientation),
            selectedOption = SettingMapper.toScreenOrientationOptions(context)
                .find { it.value == dto.orientation } ?: SettingMapper.toScreenOrientationOptions(context)
                .first(),
            options = SettingMapper.toScreenOrientationOptions(context)
        ))

        items.add(
            SettingItem.ActionItem(
                id = "reboot_device",
                title = context.getString(R.string.reboot_device),
                summary = context.getString(R.string.reboot_device_summary)
            )
        )
        items.add(
            SettingItem.ActionItem(
                id = "restore_factory",
                title = context.getString(R.string.restore_factory),
                summary = context.getString(R.string.restore_factory_summary)
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
                 AiAssistantClient.getInstance().disconnect()
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
