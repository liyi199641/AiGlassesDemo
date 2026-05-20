package com.lw.ai.glasses.ui.update

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fission.wear.glasses.sdk.constant.GlassesConstant
import com.lw.ai.glasses.R
import com.lw.ai.glasses.utils.titleRes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateScreen(
    onNavigateBack: () -> Unit,
    viewModel: UpdateViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri -> viewModel.onFileAdded(uri, context) }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ota_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { filePickerLauncher.launch("*/*") }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_firmware))
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            // 状态和进度区域
            StatusCard(
                statusText = uiState.statusText,
                otaStatus = uiState.otaStatus,
                progress = uiState.progress
            )

            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.recent_firmware), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            // 文件列表区域
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (uiState.recentFiles.isEmpty()) {
                    item {
                        EmptyState()
                    }
                } else {
                    items(uiState.recentFiles, key = { it.id }) { file ->
                        FirmwareFileItem(
                            file = file,
                            isSelected = file.id == uiState.selectedFileId,
                            onClick = { viewModel.onFileSelectionChanged(file.id) }
                        )
                    }
                }
            }

            OtaTypeSelection(
                availableTypes = uiState.availableOtaTypes,
                selectedType = uiState.selectedOtaType,
                onTypeSelected = { viewModel.onOtaTypeChanged(it) }
            )

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { viewModel.startOtaUpgrade() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                enabled = uiState.selectedFileId != null && uiState.otaStatus != OtaStatus.UPGRADING
            ) {
                Text(
                    if (uiState.otaStatus == OtaStatus.UPGRADING) {
                        stringResource(R.string.upgrading)
                    } else {
                        stringResource(R.string.start_upgrade)
                    }
                )
            }
        }

    }
}

@Composable
private fun StatusCard(statusText: String, otaStatus: OtaStatus, progress: Int) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stringResource(R.string.upgrade_status), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyLarge,
                color = when (otaStatus) {
                    OtaStatus.SUCCESS -> Color(0xFF008000)
                    OtaStatus.FAILED -> MaterialTheme.colorScheme.error
                    else -> LocalContentColor.current
                }
            )
            AnimatedVisibility(visible = otaStatus == OtaStatus.UPGRADING) {
                Column {
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = progress / 100f,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun FirmwareFileItem(
    file: FirmwareFile,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        colors = if (isSelected) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        else CardDefaults.cardColors(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = isSelected,
                onClick = onClick
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(file.name, fontWeight = FontWeight.Bold)
                Text(
                    text = stringResource(R.string.firmware_file_size_mb, file.sizeInMb),
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalContentColor.current.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.NoteAdd, contentDescription = null, modifier = Modifier.size(48.dp), tint = Color.Gray)
            Text(stringResource(R.string.empty_firmware_files), color = Color.Gray)
            Text(stringResource(R.string.tap_add_firmware), fontSize = 12.sp, color = Color.Gray)
        }
    }
}

@Composable
fun OtaTypeSelection(
    availableTypes: List<GlassesConstant.OtaType>,
    selectedType: GlassesConstant.OtaType,
    onTypeSelected: (GlassesConstant.OtaType) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(stringResource(R.string.upgrade_type), style = MaterialTheme.typography.bodyLarge)

        availableTypes.forEach { otaType ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .clickable { onTypeSelected(otaType) }
            ) {
                RadioButton(
                    selected = (otaType == selectedType),
                    onClick = { onTypeSelected(otaType) }
                )
                Text(
                    text = stringResource(otaType.titleRes()),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }
    }
}

