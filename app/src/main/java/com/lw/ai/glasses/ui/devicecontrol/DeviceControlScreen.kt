package com.lw.ai.glasses.ui.devicecontrol

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fission.wear.glasses.sdk.constant.GlassesConstant
import com.fission.wear.glasses.sdk.data.model.ThumbnailPhotoConfig
import com.lw.ai.glasses.R
import kotlin.math.roundToInt

/** AI 识图分辨率预设：仅保留实机确认可行的档位（photoWidth ≤ 2048）。 */
private data class ThumbnailResolutionPreset(
    val width: Int,
    val height: Int,
    val label: String,
)

private val ThumbnailPhotoResolutionPresets = listOf(
    // 4:3
    ThumbnailResolutionPreset(640, 480, "640×480 · 4:3"),
    ThumbnailResolutionPreset(960, 720, "960×720 · 4:3"),
    ThumbnailResolutionPreset(1280, 960, "1280×960 · 4:3"),
    ThumbnailResolutionPreset(1600, 1200, "1600×1200 · 4:3"),
    ThumbnailResolutionPreset(1920, 1440, "1920×1440 · 4:3"),
    ThumbnailResolutionPreset(2048, 1536, "2048×1536 · 4:3"),
    // 16:9
    ThumbnailResolutionPreset(1280, 720, "1280×720 · 16:9"),
    ThumbnailResolutionPreset(1920, 1080, "1920×1080 · 16:9"),
    ThumbnailResolutionPreset(2048, 1152, "2048×1152 · 16:9"),
    // 其它横屏
    ThumbnailResolutionPreset(1080, 960, "1080×960"),
    ThumbnailResolutionPreset(1920, 960, "1920×960 · 2:1"),
    ThumbnailResolutionPreset(1920, 1200, "1920×1200 · 16:10"),
    ThumbnailResolutionPreset(2048, 1080, "2048×1080"),
    ThumbnailResolutionPreset(2048, 1280, "2048×1280 · 8:5"),
    ThumbnailResolutionPreset(2048, 1296, "2048×1296"),
    // 竖屏（已确认）
    ThumbnailResolutionPreset(720, 1280, "720×1280 · 9:16"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceControlScreen(
    onNavigateBack: () -> Unit,
    viewModel: DeviceControlViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadCurrentVolume()
        viewModel.loadDeviceSupportedFeatures()
        viewModel.loadActionState()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.device_control_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                VolumeControlCard(
                    viewModel = viewModel,
                    systemVolume = uiState.systemVolume,
                    mediaVolume = uiState.mediaVolume,
                    callVolume = uiState.callVolume,
                )
            }
            item {
                ControlSectionCard(
                    title = stringResource(R.string.music_control),
                    summary = stringResource(R.string.music_control_summary),
                    statusText = stringResource(
                        if (uiState.isMusicPlaying) {
                            R.string.action_status_music_playing
                        } else {
                            R.string.action_status_music_idle
                        }
                    ),
                    statusActive = uiState.isMusicPlaying,
                    statusVisual = ActionStatusVisual.MUSIC,
                ) {
                    ControlButtonRow(
                        primaryText = stringResource(R.string.play),
                        onPrimaryClick = viewModel::playMusic,
                        secondaryText = stringResource(R.string.pause),
                        onSecondaryClick = viewModel::pauseMusic
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    ControlButtonRow(
                        primaryText = stringResource(R.string.previous_track),
                        onPrimaryClick = viewModel::previousMusic,
                        secondaryText = stringResource(R.string.next_track),
                        onSecondaryClick = viewModel::nextMusic
                    )
                }
            }
            item {
                ControlSectionCard(
                    title = stringResource(R.string.recording),
                    summary = stringResource(R.string.recording_summary),
                    statusText = stringResource(
                        if (uiState.isRecordingAudio) {
                            R.string.action_status_recording_audio
                        } else {
                            R.string.action_status_recording_audio_idle
                        }
                    ),
                    statusActive = uiState.isRecordingAudio,
                    statusVisual = ActionStatusVisual.RECORD_AUDIO,
                ) {
                    ControlButtonRow(
                        primaryText = stringResource(R.string.start_recording),
                        onPrimaryClick = viewModel::startRecording,
                        secondaryText = stringResource(R.string.stop_recording),
                        onSecondaryClick = viewModel::stopRecording
                    )
                }
            }
            item {
                ControlSectionCard(
                    title = stringResource(R.string.video_recording),
                    summary = stringResource(R.string.video_recording_summary),
                    statusText = stringResource(
                        if (uiState.isRecordingVideo) {
                            R.string.action_status_recording_video
                        } else {
                            R.string.action_status_recording_video_idle
                        }
                    ),
                    statusActive = uiState.isRecordingVideo,
                    statusVisual = ActionStatusVisual.RECORD_VIDEO,
                ) {
                    ControlButtonRow(
                        primaryText = stringResource(R.string.start_video_recording),
                        onPrimaryClick = viewModel::startVideoRecording,
                        secondaryText = stringResource(R.string.stop_video_recording),
                        onSecondaryClick = viewModel::stopVideoRecording
                    )
                }
            }
            item {
                ControlSectionCard(
                    title = stringResource(R.string.photo_and_ai),
                    summary = stringResource(R.string.photo_and_ai_summary),
                    statusText = stringResource(
                        if (uiState.isTakingPhoto) {
                            R.string.action_status_taking_photo
                        } else {
                            R.string.action_status_photo_idle
                        }
                    ),
                    statusActive = uiState.isTakingPhoto,
                    statusVisual = ActionStatusVisual.PHOTO,
                ) {
                    AiThumbnailPhotoGroup(
                        config = uiState.thumbnailPhotoConfig,
                        onConfigChange = viewModel::updateThumbnailPhotoConfig,
                        onTakeAiPhoto = viewModel::takePictureForAi,
                        aiPhotoBleResult = uiState.aiPhotoBleResult,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.take_picture_to_device_section),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(R.string.take_picture_to_device_section_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
                    )
                    FullWidthButton(
                        text = stringResource(R.string.take_picture_to_device),
                        onClick = viewModel::takePictureToDevice,
                    )
                }
            }
            item {
                ControlSectionCard(
                    title = stringResource(R.string.call_control),
                    summary = stringResource(R.string.call_control_summary)
                ) {
                    ControlButtonRow(
                        primaryText = stringResource(R.string.answer),
                        onPrimaryClick = viewModel::answerPhoneCall,
                        secondaryText = stringResource(R.string.hang_up),
                        onSecondaryClick = viewModel::hangUpPhoneCall
                    )
                }
            }
            item {
                ControlSectionCard(
                    title = stringResource(R.string.device_status),
                    summary = stringResource(R.string.device_status_summary),
                    statusText = stringResource(
                        if (uiState.isImporting) {
                            R.string.action_status_importing
                        } else {
                            R.string.action_status_import_idle
                        }
                    ),
                    statusActive = uiState.isImporting,
                    statusVisual = ActionStatusVisual.IMPORT,
                ) {
                    val wearStatusText = when (uiState.isWearing) {
                        true -> stringResource(R.string.action_status_wearing)
                        false -> stringResource(R.string.action_status_not_wearing)
                        null -> stringResource(R.string.action_status_wear_unknown)
                    }
                    ActionStatusIndicator(
                        text = stringResource(R.string.device_action_status_label, wearStatusText),
                        active = uiState.isWearing == true,
                        visual = ActionStatusVisual.WEAR,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FullWidthButton(text = stringResource(R.string.refresh_device_status), onClick = viewModel::refreshDeviceState)
                }
            }
            item {
                DeviceSupportedFeaturesCard(
                    featureRows = uiState.featureSupportRows,
                    onRefresh = viewModel::loadDeviceSupportedFeatures
                )
            }
        }
    }
}

@Composable
private fun AiPhotoBleResultSection(result: AiPhotoBleResult) {
    if (result.status == AiPhotoBleStatus.Idle) return

    Spacer(modifier = Modifier.height(12.dp))
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.ai_photo_ble_result_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(
                when (result.status) {
                    AiPhotoBleStatus.Waiting -> R.string.ai_photo_ble_status_waiting
                    AiPhotoBleStatus.Transferring -> R.string.ai_photo_ble_status_transferring
                    AiPhotoBleStatus.Success -> R.string.ai_photo_ble_status_success
                    AiPhotoBleStatus.Failed -> R.string.ai_photo_ble_status_failed
                    AiPhotoBleStatus.Idle -> R.string.ai_photo_ble_status_waiting
                }
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = when (result.status) {
                AiPhotoBleStatus.Success -> MaterialTheme.colorScheme.primary
                AiPhotoBleStatus.Failed -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        if (result.status == AiPhotoBleStatus.Success) {
            val width = result.imageWidth
            val height = result.imageHeight
            val requestedWidth = result.requestedWidth
            val requestedHeight = result.requestedHeight
            when {
                width != null && height != null &&
                    requestedWidth != null && requestedHeight != null -> {
                    val mismatched = width != requestedWidth || height != requestedHeight
                    Text(
                        text = stringResource(
                            R.string.ai_photo_ble_resolution_vs_request,
                            width,
                            height,
                            requestedWidth,
                            requestedHeight,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (mismatched) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                    )
                    if (mismatched) {
                        Text(
                            text = stringResource(R.string.ai_photo_ble_resolution_mismatch),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                width != null && height != null -> {
                    Text(
                        text = stringResource(
                            R.string.ai_photo_ble_resolution,
                            width,
                            height,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                else -> {
                    Text(
                        text = stringResource(R.string.ai_photo_ble_resolution_unknown),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        result.imageBytesSize?.let { size ->
            Text(
                text = stringResource(R.string.ai_photo_ble_size, formatBytes(size)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        result.imageFilePath?.let { path ->
            Text(
                text = stringResource(R.string.ai_photo_ble_file_path, path),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        result.errorMessage?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        result.aiRecognitionWarning?.let { warning ->
            Text(
                text = stringResource(R.string.ai_photo_ble_ai_warning, warning),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
        result.imageFilePath?.let { path ->
            val bitmap = remember(path) { BitmapFactory.decodeFile(path)?.asImageBitmap() }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = stringResource(R.string.ai_photo_ble_preview),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                }
            }
        }
    }
}

private fun formatBytes(bytes: Int): String {
    return when {
        bytes >= 1024 * 1024 -> "${(bytes / (1024f * 1024f)).roundToInt()} MB"
        bytes >= 1024 -> "${(bytes / 1024f).roundToInt()} KB"
        else -> "$bytes B"
    }
}

@Composable
private fun DeviceSupportedFeaturesCard(
    featureRows: List<FeatureSupportRow>,
    onRefresh: () -> Unit,
) {
    ControlSectionCard(
        title = stringResource(R.string.device_supported_features),
        summary = stringResource(R.string.device_supported_features_summary)
    ) {
        FullWidthButton(
            text = stringResource(R.string.refresh_device_supported_features),
            onClick = onRefresh
        )
        Spacer(modifier = Modifier.height(12.dp))
        if (featureRows.isEmpty()) {
            Text(
                text = stringResource(R.string.device_supported_features_not_loaded),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            featureRows.forEach { row ->
                FeatureSupportRowItem(row = row)
            }
        }
    }
}

@Composable
private fun FeatureSupportRowItem(row: FeatureSupportRow) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(row.labelRes),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = stringResource(
                if (row.supported) R.string.feature_supported else R.string.feature_not_supported
            ),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = if (row.supported) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

@Composable
private fun VolumeControlCard(
    viewModel: DeviceControlViewModel,
    systemVolume: Int,
    mediaVolume: Int,
    callVolume: Int,
) {
    ControlSectionCard(
        title = stringResource(R.string.volume_adjust),
        summary = stringResource(R.string.volume_adjust_summary)
    ) {
        ControlButtonRow(
            primaryText = stringResource(R.string.volume_down),
            onPrimaryClick = viewModel::downVolume,
            secondaryText = stringResource(R.string.volume_up),
            onSecondaryClick = viewModel::upVolume
        )
        Spacer(modifier = Modifier.height(8.dp))
        FullWidthButton(text = stringResource(R.string.get_current_volume), onClick = viewModel::getVolume)
        Spacer(modifier = Modifier.height(12.dp))
        VolumeSlider(
            title = stringResource(R.string.system_volume),
            maxVolume = 15,
            currentVolume = systemVolume,
            onVolumeSelected = { viewModel.setVolume(GlassesConstant.AudioVolumeType.SYSTEM, it) }
        )
        VolumeSlider(
            title = stringResource(R.string.media_volume),
            maxVolume = 16,
            currentVolume = mediaVolume,
            onVolumeSelected = { viewModel.setVolume(GlassesConstant.AudioVolumeType.MEDIA, it) }
        )
        VolumeSlider(
            title = stringResource(R.string.call_volume),
            maxVolume = 15,
            currentVolume = callVolume,
            onVolumeSelected = { viewModel.setVolume(GlassesConstant.AudioVolumeType.CALL, it) }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AiThumbnailPhotoGroup(
    config: ThumbnailPhotoConfig,
    onConfigChange: (ThumbnailPhotoConfig) -> Unit,
    onTakeAiPhoto: () -> Unit,
    aiPhotoBleResult: AiPhotoBleResult,
) {
    var widthText by remember(config.photoWidth) { mutableStateOf(config.photoWidth.toString()) }
    var heightText by remember(config.photoHeight) { mutableStateOf(config.photoHeight.toString()) }
    var quality by remember(config.photoQuality) { mutableFloatStateOf(config.photoQuality.toFloat()) }

    LaunchedEffect(config.photoWidth) { widthText = config.photoWidth.toString() }
    LaunchedEffect(config.photoHeight) { heightText = config.photoHeight.toString() }
    LaunchedEffect(config.photoQuality) { quality = config.photoQuality.toFloat() }

    val outlineShape = RoundedCornerShape(12.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline,
                shape = outlineShape,
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.thumbnail_photo_config_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = stringResource(R.string.thumbnail_photo_config_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.thumbnail_photo_play_sound),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = config.playTakePhotoSound,
                onCheckedChange = { enabled ->
                    onConfigChange(config.copy(playTakePhotoSound = enabled))
                },
            )
        }
        Text(
            text = stringResource(R.string.thumbnail_photo_presets),
            style = MaterialTheme.typography.bodyMedium,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            ThumbnailPhotoResolutionPresets.forEach { preset ->
                val selected =
                    config.photoWidth == preset.width &&
                        config.photoHeight == preset.height
                FilterChip(
                    selected = selected,
                    onClick = {
                        onConfigChange(
                            config.copy(
                                photoWidth = preset.width,
                                photoHeight = preset.height,
                            ),
                        )
                    },
                    label = { Text(preset.label) },
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = widthText,
                onValueChange = { raw ->
                    widthText = raw.filter { it.isDigit() }.take(5)
                    val width = widthText.toIntOrNull()?.coerceAtLeast(1) ?: return@OutlinedTextField
                    onConfigChange(config.copy(photoWidth = width))
                },
                label = { Text(stringResource(R.string.thumbnail_photo_width)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = heightText,
                onValueChange = { raw ->
                    heightText = raw.filter { it.isDigit() }.take(5)
                    val height = heightText.toIntOrNull()?.coerceAtLeast(1)
                        ?: return@OutlinedTextField
                    onConfigChange(config.copy(photoHeight = height))
                },
                label = { Text(stringResource(R.string.thumbnail_photo_height)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.thumbnail_photo_quality),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = quality.roundToInt().toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
        }
        Slider(
            value = quality,
            onValueChange = { quality = it },
            valueRange = ThumbnailPhotoConfig.PHOTO_QUALITY_MIN.toFloat()..
                ThumbnailPhotoConfig.PHOTO_QUALITY_MAX.toFloat(),
            steps = ThumbnailPhotoConfig.PHOTO_QUALITY_MAX -
                ThumbnailPhotoConfig.PHOTO_QUALITY_MIN - 1,
            onValueChangeFinished = {
                onConfigChange(config.copy(photoQuality = quality.roundToInt()))
            },
        )
        FullWidthButton(
            text = stringResource(R.string.take_picture_for_ai),
            onClick = onTakeAiPhoto,
        )
        AiPhotoBleResultSection(result = aiPhotoBleResult)
    }
}

@Composable
private fun VolumeSlider(
    title: String,
    maxVolume: Int,
    currentVolume: Int,
    onVolumeSelected: (Int) -> Unit
) {
    var volume by remember(currentVolume) { mutableFloatStateOf(currentVolume.toFloat()) }

    LaunchedEffect(currentVolume) {
        volume = currentVolume.toFloat()
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = volume.toInt().toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }
        Slider(
            value = volume,
            onValueChange = { volume = it },
            valueRange = 0f..maxVolume.toFloat(),
            steps = maxVolume - 1,
            onValueChangeFinished = { onVolumeSelected(volume.toInt()) }
        )
    }
}

@Composable
private fun ControlSectionCard(
    title: String,
    summary: String,
    statusText: String? = null,
    statusActive: Boolean = false,
    statusVisual: ActionStatusVisual? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
            )
            if (statusText != null && statusVisual != null) {
                ActionStatusIndicator(
                    text = statusText,
                    active = statusActive,
                    visual = statusVisual,
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            content()
        }
    }
}

@Composable
private fun ControlButtonRow(
    primaryText: String,
    onPrimaryClick: () -> Unit,
    secondaryText: String,
    onSecondaryClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            onClick = onPrimaryClick,
            modifier = Modifier.weight(1f)
        ) {
            Text(primaryText)
        }
        OutlinedButton(
            onClick = onSecondaryClick,
            modifier = Modifier.weight(1f)
        ) {
            Text(secondaryText)
        }
    }
}

@Composable
private fun FullWidthButton(
    text: String,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(text)
    }
}
