package com.lw.ai.glasses.ui.setting

import BaseViewModel
import android.content.Context
import androidx.lifecycle.viewModelScope
import com.fission.wear.glasses.sdk.GlassesManage
import com.fission.wear.glasses.sdk.AiAssistantClient
import com.fission.wear.glasses.sdk.constant.GlassesConstant
import com.fission.wear.glasses.sdk.data.dto.DeviceSettingsStateDTO
import com.fission.wear.glasses.sdk.data.dto.DeviceVersionInfoDTO
import com.fission.wear.glasses.sdk.events.CmdResultEvent
import com.lw.ai.glasses.R
import com.lw.ai.glasses.config.AppConfigLoader
import com.lw.ai.glasses.config.SdkChannelResolver
import com.lw.top.lib_core.data.datastore.AppDataManager
import com.lw.top.lib_core.data.datastore.BluetoothDataManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bluetoothDataManager: BluetoothDataManager,
    private val appDataManager: AppDataManager,
) : BaseViewModel() {

    private var localOfflineVoiceEnabled: Boolean = true
    private var opusStreamPushEnabled: Boolean = true
    private var latestDeviceVersionInfo: DeviceVersionInfoDTO? = null
    private var latestDeviceSettingsState: DeviceSettingsStateDTO = DeviceSettingsStateDTO()
    private var hasLoadedDeviceSettings: Boolean = false
    private var currentChannel: GlassesConstant.ChannelType = GlassesConstant.ChannelType.LY

    private val _uiState = MutableStateFlow(SettingUiState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            currentChannel = SdkChannelResolver.loadForSdkInit(bluetoothDataManager, appDataManager)
            if (hasLoadedDeviceSettings) {
                _uiState.update { currentState ->
                    currentState.copy(
                        settingItems = appendVersionInfoItems(
                            mapDtoToUiState(latestDeviceSettingsState).settingItems
                        )
                    )
                }
            }
        }
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
                        latestDeviceSettingsState =
                            latestDeviceSettingsState.mergeWith(events.data)
                        events.data.voiceCommandEnabled?.let { enabled ->
                            localOfflineVoiceEnabled = enabled
                        }
                        _uiState.update { currentState ->
                            val newSettingItems = appendVersionInfoItems(
                                mapDtoToUiState(latestDeviceSettingsState).settingItems
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

    /** 每次进入设置页时刷新设备侧配置（ViewModel 可能仍存活）。 */
    fun onScreenVisible() {
        loadInitialSettings()
    }

    fun onRecordDurationChanged(duration: Int) {
        GlassesManage.setVideoDuration(duration)
        patchDurationSummary("record_duration", duration)
    }

    fun onAudioRecordDurationChanged(duration: Int) {
        GlassesManage.setVoiceDuration(duration)
        patchDurationSummary("audio_record_duration", duration)
    }

    private fun patchDurationSummary(settingId: String, duration: Int) {
        _uiState.update { currentState ->
            val newItems = currentState.settingItems.map { item ->
                if (item is SettingItem.ActionItem && item.id == settingId) {
                    item.copy(summary = context.getString(R.string.duration_seconds, duration))
                } else {
                    item
                }
            }
            currentState.copy(settingItems = newItems)
        }
    }

    fun <T> onSettingSelected(settingId: String, selectedValue: T) {
        when (settingId) {
            "led_brightness" -> {
                if (selectedValue is GlassesConstant.LedBrightnessLevel) {
                    GlassesManage.setLedBrightness(selectedValue)
                }
            }

            "screen_orientation" -> {
                if (selectedValue is GlassesConstant.ScreenOrientation) {
                    GlassesManage.setScreenOrientation(selectedValue)
                    latestDeviceSettingsState =
                        latestDeviceSettingsState.copy(orientation = selectedValue)
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
            if (enabled) GlassesConstant.WearDetectionState.ON else GlassesConstant.WearDetectionState.OFF
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
            SettingItem.InfoItem(
                context.getString(R.string.firmware_version),
                versionInfo.firmwareVersion.ifBlank { context.getString(R.string.not_set) }
            ),
            SettingItem.InfoItem(
                context.getString(R.string.wifi_version),
                versionInfo.wifiVersion.ifBlank { context.getString(R.string.not_set) }
            ),
            SettingItem.InfoItem(
                context.getString(R.string.hardware_version),
                versionInfo.hardwareVersion.ifBlank { context.getString(R.string.not_set) }
            )
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
        val ledOptions = SettingMapper.toLedBrightnessOptions(context)
        val orientationOptions = SettingMapper.toScreenOrientationOptions(context)

        val items = buildList {
            if (isSettingSupported("led_brightness")) {
                add(
                    SettingItem.DropdownItem(
                        id = "led_brightness",
                        title = context.getString(R.string.led_brightness),
                        selectedOption = ledOptions.find { it.value == dto.ledBrightness }
                            ?: ledOptions.first(),
                        options = ledOptions,
                    )
                )
            }

            if (isSettingSupported("record_duration")) {
                add(
                    SettingItem.ActionItem(
                        id = "record_duration",
                        title = context.getString(R.string.video_record_duration),
                        summary = context.getString(
                            R.string.duration_value_seconds,
                            dto.recordDuration?.toString() ?: context.getString(R.string.not_set)
                        )
                    )
                )
            }

            if (isSettingSupported("audio_record_duration")) {
                add(
                    SettingItem.ActionItem(
                        id = "audio_record_duration",
                        title = context.getString(R.string.audio_record_duration),
                        summary = context.getString(
                            R.string.duration_value_seconds,
                            dto.audioRecordDuration?.toString() ?: context.getString(R.string.not_set)
                        )
                    )
                )
            }

            if (isSettingSupported("wear_detection")) {
                add(
                    SettingItem.SwitchItem(
                        id = "wear_detection",
                        title = context.getString(R.string.wear_detection),
                        isChecked = dto.wearDetectionEnabled == GlassesConstant.WearDetectionState.ON
                    )
                )
            }

            if (isSettingSupported("voice_disable_local")) {
                add(
                    SettingItem.SwitchItem(
                        id = "voice_disable_local",
                        title = context.getString(R.string.local_offline_voice_command),
                        isChecked = localOfflineVoiceEnabled,
                        summary = context.getString(R.string.local_offline_voice_command_summary)
                    )
                )
            }

            if (isSettingSupported("voice_disable_opus")) {
                add(
                    SettingItem.SwitchItem(
                        id = "voice_disable_opus",
                        title = context.getString(R.string.ai_wakeup_opus_push),
                        isChecked = opusStreamPushEnabled,
                        summary = context.getString(R.string.ai_wakeup_opus_push_summary)
                    )
                )
            }

            if (isSettingSupported("burst_photo_count")) {
                add(
                    SettingItem.ActionItem(
                        id = "burst_photo_count",
                        title = context.getString(R.string.burst_photo_count),
                        summary = dto.burstPhotoCount?.toString() ?: context.getString(R.string.not_set)
                    )
                )
            }

            if (isSettingSupported("screen_orientation")) {
                add(
                    SettingItem.DropdownItem(
                        id = "screen_orientation",
                        title = context.getString(R.string.screen_orientation),
                        selectedOption = orientationOptions.find { it.value == dto.orientation }
                            ?: orientationOptions.first(),
                        options = orientationOptions,
                    )
                )
            }

            add(
                SettingItem.ActionItem(
                    id = "reboot_device",
                    title = context.getString(R.string.reboot_device),
                    summary = context.getString(R.string.reboot_device_summary)
                )
            )
            add(
                SettingItem.ActionItem(
                    id = "restore_factory",
                    title = context.getString(R.string.restore_factory),
                    summary = context.getString(R.string.restore_factory_summary)
                )
            )
        }

        val itemsWithDividers = items.flatMapIndexed { index, item ->
            if (index < items.size - 1) {
                listOf(item, SettingItem.Divider)
            } else {
                listOf(item)
            }
        }

        return SettingUiState(settingItems = itemsWithDividers)
    }

    /**
     * TB 方案 [DeviceSettingsStateDTO] 读回能力（写接口另行标注）：
     * - 支持读：recordDuration、audioRecordDuration（isSupportAudio==1 时，设备侧单位为分钟）、orientation、voiceCommandEnabled（本地离线语音）
     * - 仅支持写：ledBrightness、wearDetection
     * - 不支持读：gestureSettings、burstPhotoCount；Opus 推送开关无独立读回
     */
    private fun isSettingSupported(settingId: String): Boolean {
        return when (currentChannel) {
            GlassesConstant.ChannelType.LY -> settingId != "audio_record_duration"
            GlassesConstant.ChannelType.TB -> when (settingId) {
                "voice_disable_opus" -> false
                "record_duration", "audio_record_duration" ->
                    latestDeviceSettingsState.recordingLimitSupported == true
                else -> true
            }
            else -> true
        }
    }

    /**
     * RTK 侧部分回调是“增量设置”，未携带的字段会是 null。
     * 这里按字段合并到最近一次完整快照，避免单项设置把录像时长等其它项覆盖掉。
     */
    private fun DeviceSettingsStateDTO.mergeWith(update: DeviceSettingsStateDTO): DeviceSettingsStateDTO {
        return DeviceSettingsStateDTO(
            ledBrightness = update.ledBrightness ?: ledBrightness,
            recordDuration = update.recordDuration ?: recordDuration,
            audioRecordDuration = update.audioRecordDuration ?: audioRecordDuration,
            systemVolume = update.systemVolume ?: systemVolume,
            mediaVolume = update.mediaVolume ?: mediaVolume,
            callVolume = update.callVolume ?: callVolume,
            wearDetectionEnabled = update.wearDetectionEnabled ?: wearDetectionEnabled,
            voiceCommandEnabled = update.voiceCommandEnabled ?: voiceCommandEnabled,
            gestureSettings = update.gestureSettings ?: gestureSettings,
            burstPhotoCount = update.burstPhotoCount ?: burstPhotoCount,
            orientation = update.orientation ?: orientation,
            recordingLimitSupported = update.recordingLimitSupported ?: recordingLimitSupported,
        )
    }

    fun rebootDevice() {
        GlassesManage.rebootDevice()
    }

    fun restoreFactorySettings() {
        GlassesManage.restoreFactorySettings()
    }

    private fun <T> SettingItem.DropdownItem<T>.withNewSelection(value: Any?): SettingItem.DropdownItem<T> {
        val newSelectedOption = this.options.find { it.value == value }
        return if (newSelectedOption != null) this.copy(selectedOption = newSelectedOption) else this
    }

    fun onDisconnect() {
        viewModelScope.launch {
            _uiState.update { it.copy(isUnbinding = true) }

            bluetoothDataManager.clearBluetoothDevice()
            GlassesManage.disConnect()
            AiAssistantClient.getInstance().disconnect()

            _uiState.update {
                it.copy(
                    isUnbinding = false,
                    disconnectAction = it.disconnectAction.copy(isEnabled = false)
                )
            }
        }
    }
}
