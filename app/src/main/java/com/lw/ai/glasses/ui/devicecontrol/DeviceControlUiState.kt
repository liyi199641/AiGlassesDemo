package com.lw.ai.glasses.ui.devicecontrol

import androidx.annotation.StringRes
import com.fission.wear.glasses.sdk.data.model.GlassesFeaturesConfigInfo
import com.fission.wear.glasses.sdk.data.model.ThumbnailPhotoConfig

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
    /** 回传图片实际宽高（解码得到）。 */
    val imageWidth: Int? = null,
    val imageHeight: Int? = null,
    /** 本次请求的缩略图宽高。 */
    val requestedWidth: Int? = null,
    val requestedHeight: Int? = null,
    val errorMessage: String? = null,
    val aiRecognitionWarning: String? = null,
)

data class DeviceControlUiState(
    val systemVolume: Int = 0,
    val mediaVolume: Int = 0,
    val callVolume: Int = 0,
    /** AI 识图缩略图参数；仅 RTK takePhotoOnly 生效。 */
    val thumbnailPhotoConfig: ThumbnailPhotoConfig = ThumbnailPhotoConfig.DEFAULT,
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
