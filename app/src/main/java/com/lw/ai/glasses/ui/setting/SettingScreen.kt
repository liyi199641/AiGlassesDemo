package com.lw.ai.glasses.ui.setting

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.lw.ai.glasses.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingScreen(
    viewModel: SettingViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var showInputDialog by remember { mutableStateOf(false) }
    var showRebootConfirm by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }
    if (showRebootConfirm) {
        AlertDialog(
            onDismissRequest = { showRebootConfirm = false },
            title = { Text(stringResource(R.string.reboot_device)) },
            text = { Text(stringResource(R.string.reboot_device_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.rebootDevice()
                    showRebootConfirm = false
                }) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showRebootConfirm = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text(stringResource(R.string.restore_factory)) },
            text = { Text(stringResource(R.string.restore_factory_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.restoreFactorySettings()
                    showResetConfirm = false
                }) { Text(stringResource(R.string.confirm), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        },
        bottomBar = {
            Button(
                onClick = viewModel::onDisconnect,
                enabled = uiState.disconnectAction.isEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text(stringResource(uiState.disconnectAction.titleRes))
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                itemsIndexed(
                    items = uiState.settingItems,
                    key = { index, item ->
                        when (item) {
                            is SettingItem.ActionItem -> "action_${item.id}"
                            is SettingItem.DropdownItem<*> -> "dropdown_${item.id}"
                            is SettingItem.InfoItem -> "info_${item.title}"
                            is SettingItem.SwitchItem -> "switch_${item.id}"
                            is SettingItem.Divider -> "divider_$index"
                        }
                    }
                ) { _, item ->
                    when (item) {
                        is SettingItem.DropdownItem<*> -> {
                            DropdownSettingItem(item = item, onItemSelected = viewModel::onSettingSelected)
                        }
                        is SettingItem.SwitchItem -> {
                            SwitchSettingItem(
                                item = item,
                                onCheckedChange = { isChecked ->
                                    when (item.id) {
                                        "wear_detection" -> viewModel.setWearDetectionEnabled(isChecked)
                                        "voice_disable_local" -> viewModel.setLocalOfflineVoiceEnabled(isChecked)
                                        "voice_disable_opus" -> viewModel.setOpusStreamPushEnabled(isChecked)
                                    }
                                }
                            )
                        }
                        is SettingItem.ActionItem -> {
                            ActionSettingItem(item = item) {
                                when (item.id) {
                                    "record_duration" -> showInputDialog = true
                                    "reboot_device" -> showRebootConfirm = true
                                    "restore_factory" -> showResetConfirm = true
                                }
                            }
                        }
                        is SettingItem.InfoItem -> {
                            InfoSettingItem(item = item)
                        }
                        is SettingItem.Divider -> {
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        }
                    }
                }
            }

            if (showInputDialog) {
                RecordDurationInputDialog(
                    onDismiss = { showInputDialog = false },
                    onConfirm = { duration ->
                        viewModel.onRecordDurationChanged(duration)
                        showInputDialog = false
                    }
                )
            }

            if (uiState.isUnbinding) {
                LoadingDialog(text = stringResource(R.string.unbinding))
            }
        }
    }
}

@Composable
private fun RecordDurationInputDialog(
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.set_record_duration)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.filter { char -> char.isDigit() } },
                label = { Text(stringResource(R.string.duration_seconds_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        },
        confirmButton = {
            Button(
                onClick = { text.toIntOrNull()?.let(onConfirm) },
                enabled = text.isNotBlank()
            ) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
fun <T> DropdownSettingItem(
    item: SettingItem.DropdownItem<T>,
    onItemSelected: (String, T) -> Unit
) {
    var isDropdownExpanded by remember { mutableStateOf(false) }

    Box {
        BaseSettingItem(
            title = item.title,
            summary = item.selectedOption.title,
            isEnabled = item.isEnabled,
            onClick = { if (item.isEnabled) isDropdownExpanded = true }
        ) {
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = stringResource(R.string.select),
                tint = MaterialTheme.colorScheme.primary
            )
        }

        DropdownMenu(
            expanded = isDropdownExpanded,
            onDismissRequest = { isDropdownExpanded = false }
        ) {
            item.options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.title) },
                    onClick = {
                        isDropdownExpanded = false
                        onItemSelected(item.id, option.value)
                    }
                )
            }
        }
    }
}

@Composable
fun SwitchSettingItem(
    item: SettingItem.SwitchItem,
    onCheckedChange: (Boolean) -> Unit
) {
    BaseSettingItem(
        title = item.title,
        summary = item.summary,
        isEnabled = item.isEnabled,
        onClick = { if (item.isEnabled) onCheckedChange(!item.isChecked) }
    ) {
        Switch(
            checked = item.isChecked,
            onCheckedChange = onCheckedChange,
            enabled = item.isEnabled
        )
    }
}

@Composable
fun ActionSettingItem(item: SettingItem.ActionItem, onClick: () -> Unit) {
    BaseSettingItem(
        title = item.title,
        summary = item.summary,
        isEnabled = item.isEnabled,
        onClick = onClick
    )
}

@Composable
fun InfoSettingItem(item: SettingItem.InfoItem) {
    BaseSettingItem(
        title = item.title,
        summary = item.value,
        isEnabled = true,
        onClick = {}
    )
}

@Composable
private fun BaseSettingItem(
    title: String,
    summary: String?,
    isEnabled: Boolean,
    onClick: () -> Unit,
    trailingContent: (@Composable () -> Unit)? = null
) {
    val titleColor = if (isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    val summaryColor = if (isEnabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = isEnabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontWeight = FontWeight.Medium, fontSize = 16.sp, color = titleColor)
            if (summary != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = summary, fontSize = 14.sp, color = summaryColor)
            }
        }
        if (trailingContent != null) {
            Spacer(modifier = Modifier.width(16.dp))
            trailingContent()
        }
    }
}

@Composable
fun LoadingDialog(
    text: String = ""
) {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .background(
                    color = Color.Black.copy(alpha = 0.8f),
                    shape = RoundedCornerShape(16.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.size(36.dp),
                    strokeWidth = 3.dp
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = text,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}