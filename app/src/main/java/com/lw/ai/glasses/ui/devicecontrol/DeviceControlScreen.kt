package com.lw.ai.glasses.ui.devicecontrol

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fission.wear.glasses.sdk.constant.LyCmdConstant
import com.lw.ai.glasses.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceControlScreen(
    onNavigateBack: () -> Unit,
    viewModel: DeviceControlViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadCurrentVolume()
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
                    summary = stringResource(R.string.music_control_summary)
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
                    summary = stringResource(R.string.recording_summary)
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
                    summary = stringResource(R.string.video_recording_summary)
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
                    summary = stringResource(R.string.photo_and_ai_summary)
                ) {
                    ControlButtonRow(
                        primaryText = stringResource(R.string.take_picture_for_ai),
                        onPrimaryClick = viewModel::takePictureForAi,
                        secondaryText = stringResource(R.string.take_picture_to_device),
                        onSecondaryClick = viewModel::takePictureToDevice
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    ControlButtonRow(
                        primaryText = stringResource(R.string.start_ai),
                        onPrimaryClick = viewModel::startAiAssistant,
                        secondaryText = stringResource(R.string.stop_ai),
                        onSecondaryClick = viewModel::stopAiAssistant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FullWidthButton(text = stringResource(R.string.interrupt_ai_chat), onClick = viewModel::interruptAiAssistant)
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
                    summary = stringResource(R.string.device_status_summary)
                ) {
                    FullWidthButton(text = stringResource(R.string.refresh_device_status), onClick = viewModel::refreshDeviceState)
                }
            }
        }
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
            onVolumeSelected = { viewModel.setVolume(LyCmdConstant.AudioVolumeType.SYSTEM, it) }
        )
        VolumeSlider(
            title = stringResource(R.string.media_volume),
            maxVolume = 16,
            currentVolume = mediaVolume,
            onVolumeSelected = { viewModel.setVolume(LyCmdConstant.AudioVolumeType.MEDIA, it) }
        )
        VolumeSlider(
            title = stringResource(R.string.call_volume),
            maxVolume = 15,
            currentVolume = callVolume,
            onVolumeSelected = { viewModel.setVolume(LyCmdConstant.AudioVolumeType.CALL, it) }
        )
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
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
            )
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
