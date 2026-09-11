package com.lw.ai.glasses.ui.devicecontrol

import BaseViewModel
import android.content.Context
import androidx.lifecycle.viewModelScope
import com.blankj.utilcode.util.ToastUtils
import com.fission.wear.glasses.sdk.GlassesManage
import com.fission.wear.glasses.sdk.constant.GlassesConstant
import com.fission.wear.glasses.sdk.constant.GlassesConstant.ERROR_CODE_IMAGE_RECOGNITION
import com.fission.wear.glasses.sdk.events.CmdResultEvent
import com.lw.ai.glasses.R
import com.lw.ai.glasses.state.DeviceActionStateManager
import com.lw.ai.glasses.utils.titleRes
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DeviceControlViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val deviceActionStateManager: DeviceActionStateManager,
) : BaseViewModel() {

    private val _localState = MutableStateFlow(DeviceControlLocalState())

    val uiState: StateFlow<DeviceControlUiState> = combine(
        _localState,
        deviceActionStateManager.state,
    ) { local, action ->
        val aiPhotoResult = when {
            local.awaitingAiPhotoBleResult && action.isTakingPhoto ->
                local.aiPhotoBleResult.copy(status = AiPhotoBleStatus.Waiting)

            local.awaitingAiPhotoBleResult &&
                local.aiPhotoBleResult.status == AiPhotoBleStatus.Idle ->
                local.aiPhotoBleResult.copy(status = AiPhotoBleStatus.Transferring)

            else -> local.aiPhotoBleResult
        }
        DeviceControlUiState(
            systemVolume = local.systemVolume,
            mediaVolume = local.mediaVolume,
            callVolume = local.callVolume,
            featuresConfigInfo = local.featuresConfigInfo,
            featureSupportRows = local.featureSupportRows,
            isTakingPhoto = action.isTakingPhoto,
            isRecordingAudio = action.isRecordingAudio,
            isRecordingVideo = action.isRecordingVideo,
            isMusicPlaying = action.isMusicPlaying,
            isImporting = action.isImporting,
            isWearing = action.isWearing,
            aiPhotoBleResult = aiPhotoResult,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = DeviceControlUiState(),
    )

    init {
        observeGlassesEvents()
    }

    private fun observeGlassesEvents() {
        viewModelScope.launch {
            GlassesManage.eventFlow().collect { event ->
                when (event) {
                    is CmdResultEvent.DeviceVolumeState -> {
                        _localState.update {
                            it.copy(
                                systemVolume = event.systemVolume,
                                mediaVolume = event.mediaVolume,
                                callVolume = event.callVolume,
                            )
                        }
                    }

                    is CmdResultEvent.DeviceSupportedFeatures -> {
                        val config = event.featuresConfigInfo
                        _localState.update {
                            it.copy(
                                featuresConfigInfo = config,
                                featureSupportRows = DeviceFeaturesMapper.toFeatureSupportRows(config),
                            )
                        }
                    }

                    is CmdResultEvent.ImageData -> {
                        if (!_localState.value.awaitingAiPhotoBleResult) return@collect
                        _localState.update {
                            it.copy(
                                awaitingAiPhotoBleResult = false,
                                aiPhotoBleResult = AiPhotoBleResult(
                                    status = AiPhotoBleStatus.Success,
                                    imageBytesSize = event.data.size,
                                ),
                            )
                        }
                    }

                    is CmdResultEvent.ImageFile -> {
                        if (!_localState.value.awaitingAiPhotoBleResult &&
                            _localState.value.aiPhotoBleResult.status != AiPhotoBleStatus.Success
                        ) {
                            return@collect
                        }
                        val file = event.imageFile
                        _localState.update {
                            it.copy(
                                awaitingAiPhotoBleResult = false,
                                aiPhotoBleResult = AiPhotoBleResult(
                                    status = AiPhotoBleStatus.Success,
                                    imageFilePath = file?.absolutePath,
                                    imageBytesSize = file?.length()?.toInt()
                                        ?: it.aiPhotoBleResult.imageBytesSize,
                                    aiRecognitionWarning = it.aiPhotoBleResult.aiRecognitionWarning,
                                ),
                            )
                        }
                    }

                    is CmdResultEvent.Fail -> {
                        if (!isImageTransferError(event.code)) return@collect
                        val current = _localState.value
                        if (!current.awaitingAiPhotoBleResult &&
                            current.aiPhotoBleResult.status != AiPhotoBleStatus.Success
                        ) {
                            return@collect
                        }
                        if (event.code == ERROR_CODE_IMAGE_RECOGNITION &&
                            current.aiPhotoBleResult.status == AiPhotoBleStatus.Success
                        ) {
                            _localState.update {
                                it.copy(
                                    awaitingAiPhotoBleResult = false,
                                    aiPhotoBleResult = it.aiPhotoBleResult.copy(
                                        aiRecognitionWarning = event.msg,
                                    ),
                                )
                            }
                            return@collect
                        }
                        _localState.update {
                            it.copy(
                                awaitingAiPhotoBleResult = false,
                                aiPhotoBleResult = AiPhotoBleResult(
                                    status = AiPhotoBleStatus.Failed,
                                    errorMessage = event.msg,
                                ),
                            )
                        }
                    }

                    else -> Unit
                }
            }
        }
    }

    private fun isImageTransferError(code: Int): Boolean {
        return code in GlassesConstant.ERROR_CODE_IMAGE_PACKET_TOO_SHORT..
            GlassesConstant.ERROR_CODE_IMAGE_RECOGNITION
    }

    fun loadCurrentVolume() {
        GlassesManage.getVolume()
    }

    fun loadDeviceSupportedFeatures() {
        GlassesManage.getDeviceSupportedFeatures()
    }

    fun loadActionState() {
        deviceActionStateManager.refreshActionState()
    }

    fun setVolume(type: GlassesConstant.AudioVolumeType, volume: Int) {
        GlassesManage.setVolume(type, volume)
        _localState.update { state ->
            when (type) {
                GlassesConstant.AudioVolumeType.SYSTEM -> state.copy(systemVolume = volume)
                GlassesConstant.AudioVolumeType.MEDIA -> state.copy(mediaVolume = volume)
                GlassesConstant.AudioVolumeType.CALL -> state.copy(callVolume = volume)
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
        _localState.update {
            it.copy(
                awaitingAiPhotoBleResult = true,
                aiPhotoBleResult = AiPhotoBleResult(status = AiPhotoBleStatus.Waiting),
            )
        }
        GlassesManage.takePicture(takePhotoOnly = true)
    }

    fun takePictureToDevice() = sendCommand {
        GlassesManage.takePicture(takePhotoOnly = false)
    }

    fun answerPhoneCall() = sendCommand {
        GlassesManage.answerPhoneCall()
    }

    fun hangUpPhoneCall() = sendCommand {
        GlassesManage.hangUpPhoneCall()
    }

    fun refreshDeviceState() = sendCommand {
        GlassesManage.getBatteryLevel()
        deviceActionStateManager.refreshActionState()
        GlassesManage.getMediaFileCount()
        GlassesManage.getDeviceStorage()
    }

    private inline fun sendCommand(command: () -> Unit) {
        command()
    }
}

private data class DeviceControlLocalState(
    val systemVolume: Int = 0,
    val mediaVolume: Int = 0,
    val callVolume: Int = 0,
    val featuresConfigInfo: com.fission.wear.glasses.sdk.data.model.GlassesFeaturesConfigInfo? = null,
    val featureSupportRows: List<FeatureSupportRow> = emptyList(),
    val awaitingAiPhotoBleResult: Boolean = false,
    val aiPhotoBleResult: AiPhotoBleResult = AiPhotoBleResult(),
)
