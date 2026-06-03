package com.lw.ai.glasses.ui.devicecontrol

import com.lw.ai.glasses.ui.base.viewmodel.BaseViewModel
import android.content.Context
import androidx.lifecycle.viewModelScope
import com.blankj.utilcode.util.ToastUtils
import com.fission.wear.glasses.sdk.GlassesManage
import com.fission.wear.glasses.sdk.constant.GlassesConstant
import com.fission.wear.glasses.sdk.constant.GlassesConstant.ActionSyncType
import com.fission.wear.glasses.sdk.constant.LyCmdConstant
import com.fission.wear.glasses.sdk.events.CmdResultEvent
import com.lw.ai.glasses.R
import com.lw.ai.glasses.utils.titleRes
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DeviceControlViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : BaseViewModel() {

    private val _uiState = MutableStateFlow(DeviceControlUiState())
    val uiState = _uiState.asStateFlow()

    init {
        observeGlassesEvents()
    }

    private fun observeGlassesEvents() {
        viewModelScope.launch {
            GlassesManage.eventFlow().collect { event ->
                when (event) {
                    is CmdResultEvent.DeviceVolumeState -> {
                        _uiState.update {
                            it.copy(
                                systemVolume = event.systemVolume,
                                mediaVolume = event.mediaVolume,
                                callVolume = event.callVolume,
                            )
                        }
                    }

                    is CmdResultEvent.DeviceSupportedFeatures -> {
                        val config = event.featuresConfigInfo
                        _uiState.update {
                            it.copy(
                                featuresConfigInfo = config,
                                featureSupportRows = DeviceFeaturesMapper.toFeatureSupportRows(config),
                            )
                        }
                    }

                    is CmdResultEvent.ActionSync -> {
                        _uiState.update { state ->
                            when (event.type) {
                                ActionSyncType.TAKE_PHOTO -> state.copy(isTakingPhoto = event.state)
                                ActionSyncType.RECORD_AUDIO -> state.copy(isRecordingAudio = event.state)
                                ActionSyncType.RECORD_VIDEO -> state.copy(isRecordingVideo = event.state)
                                ActionSyncType.MUSIC -> state.copy(isMusicPlaying = event.state)
                                ActionSyncType.IMPORTING -> state.copy(isImporting = event.state)
                                ActionSyncType.WEAR -> state.copy(isWearing = event.state)
                                else -> state
                            }
                        }
                    }

                    else -> Unit
                }
            }
        }
    }

    fun loadCurrentVolume() {
        GlassesManage.getVolume()
    }

    fun loadDeviceSupportedFeatures() {
        GlassesManage.getDeviceSupportedFeatures()
    }

    fun loadActionState() {
        GlassesManage.getActionState()
    }

    fun setVolume(type: LyCmdConstant.AudioVolumeType, volume: Int) {
        GlassesManage.setVolume(type, volume)
        _uiState.update { state ->
            when (type) {
                LyCmdConstant.AudioVolumeType.SYSTEM -> state.copy(systemVolume = volume)
                LyCmdConstant.AudioVolumeType.MEDIA -> state.copy(mediaVolume = volume)
                LyCmdConstant.AudioVolumeType.CALL -> state.copy(callVolume = volume)
            }
        }
        ToastUtils.showShort(
            context.getString(R.string.volume_set_to, context.getString(type.titleRes()), volume)
        )
    }

    fun getVolume() = sendCommand {
        loadCurrentVolume()
    }

    fun upVolume() = sendCommand {
        GlassesManage.upVolume()
    }

    fun downVolume() = sendCommand {
        GlassesManage.downVolume()
    }

    fun playMusic() = sendCommand {
        GlassesManage.controlMusic(true)
    }

    fun pauseMusic() = sendCommand {
        GlassesManage.controlMusic(false)
    }

    fun previousMusic() = sendCommand {
        GlassesManage.switchMusic(GlassesConstant.MusicSwitchAction.PREVIOUS)
    }

    fun nextMusic() = sendCommand {
        GlassesManage.switchMusic(GlassesConstant.MusicSwitchAction.NEXT)
    }

    fun startRecording() = sendCommand {
        GlassesManage.startDeviceRecording()
    }

    fun stopRecording() = sendCommand {
        GlassesManage.stopDeviceRecording()
    }

    fun startVideoRecording() = sendCommand {
        GlassesManage.startDeviceVideoRecording()
    }

    fun stopVideoRecording() = sendCommand {
        GlassesManage.stopDeviceVideoRecording()
    }

    fun takePictureForAi() = sendCommand {
        GlassesManage.takePicture(takePhotoOnly = true)
    }

    fun takePictureToDevice() = sendCommand {
        GlassesManage.takePicture(takePhotoOnly = false)
    }

    fun startAiAssistant() = sendCommand {
        GlassesManage.startAiAssistant()
    }

    fun stopAiAssistant() = sendCommand {
        GlassesManage.stopAiAssistant()
    }

    fun interruptAiAssistant() = sendCommand {
        GlassesManage.interruptAiAssistant()
    }

    fun answerPhoneCall() = sendCommand {
        GlassesManage.answerPhoneCall()
    }

    fun hangUpPhoneCall() = sendCommand {
        GlassesManage.hangUpPhoneCall()
    }

    fun refreshDeviceState() = sendCommand {
        GlassesManage.getBatteryLevel()
        GlassesManage.getActionState()
        GlassesManage.getMediaFileCount()
        GlassesManage.getDeviceStorage()
    }

    private inline fun sendCommand(command: () -> Unit) {
        command()
    }
}
