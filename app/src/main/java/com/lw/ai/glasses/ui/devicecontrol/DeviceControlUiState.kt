package com.lw.ai.glasses.ui.devicecontrol

import androidx.annotation.StringRes
import com.fission.wear.glasses.sdk.data.model.GlassesFeaturesConfigInfo

enum class AiPhotoBleStatus {
    Idle,
    Waiting,
    Transferring,
    Success,
    Failed,
}

data class AiPhotoBleResult(
    val status: AiPhotoBleStatus = AiPhotoBleStatus.Idle,
    val imageFilePath: String? = null,
    val imageBytesSize: Int? = null,
    val errorMessage: String? = null,
    val aiRecognitionWarning: String? = null,
)

data class DeviceControlUiState(
    val systemVolume: Int = 0,
    val mediaVolume: Int = 0,
    val callVolume: Int = 0,
    val featuresConfigInfo: GlassesFeaturesConfigInfo? = null,
    val featureSupportRows: List<FeatureSupportRow> = emptyList(),
    val isTakingPhoto: Boolean = false,
    val isRecordingAudio: Boolean = false,
    val isRecordingVideo: Boolean = false,
    val isMusicPlaying: Boolean = false,
    val isImporting: Boolean = false,
    val isWearing: Boolean? = null,
    val aiPhotoBleResult: AiPhotoBleResult = AiPhotoBleResult(),
)

data class FeatureSupportRow(
    @StringRes val labelRes: Int,
    val supported: Boolean,
)
