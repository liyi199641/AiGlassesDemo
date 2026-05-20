package com.lw.ai.glasses.ui.home

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fission.wear.glasses.sdk.constant.GlassesConstant
import com.lw.ai.glasses.R
import com.lw.ai.glasses.ui.base.screen.popup.CenteredFadeInPopup
import com.lw.ai.glasses.utils.titleRes
import com.polidea.rxandroidble3.scan.ScanResult
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigate: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    var showScanningDevices by remember { mutableStateOf(false) }
    var showWsDebugDialog by remember { mutableStateOf(false) }
    
    val context = LocalContext.current

    if (showWsDebugDialog) {
        var localWsInput by remember(uiState.localEnvironmentWsUrl) {
            mutableStateOf(uiState.localEnvironmentWsUrl)
        }
        AlertDialog(
            onDismissRequest = { showWsDebugDialog = false },
            title = { Text(stringResource(R.string.environment_switch)) },
            text = {
                Column {
                    Text(stringResource(R.string.environment_switch_hint), style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(12.dp))
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        items(GlassesConstant.ServerEnvironment.entries) { env ->
                            val isLocalEnv = env == GlassesConstant.ServerEnvironment.LOCAL
                            val applyEnvironment = {
                                if (isLocalEnv) {
                                    val trimmedWsUrl = localWsInput.trim()
                                    viewModel.updateEnvironment(env, trimmedWsUrl)
                                    if (
                                        trimmedWsUrl.startsWith("ws://") ||
                                        trimmedWsUrl.startsWith("wss://")
                                    ) {
                                        showWsDebugDialog = false
                                    }
                                } else {
                                    viewModel.updateEnvironment(env)
                                    showWsDebugDialog = false
                                }
                            }
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { applyEnvironment() }
                                        .padding(vertical = 8.dp)
                                ) {
                                    RadioButton(
                                        selected = uiState.selectedEnvironment == env,
                                        onClick = { applyEnvironment() }
                                    )
                                    Text(
                                        text = stringResource(env.titleRes()),
                                        modifier = Modifier.padding(start = 8.dp)
                                    )
                                }
                                if (isLocalEnv) {
                                    OutlinedTextField(
                                        value = localWsInput,
                                        onValueChange = { localWsInput = it },
                                        label = { Text(stringResource(R.string.local_ws_address)) },
                                        placeholder = { Text(stringResource(R.string.local_ws_url_placeholder)) },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 48.dp, bottom = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmedWsUrl = localWsInput.trim()
                        viewModel.updateEnvironment(
                            GlassesConstant.ServerEnvironment.LOCAL,
                            trimmedWsUrl
                        )
                        if (
                            trimmedWsUrl.startsWith("ws://") ||
                            trimmedWsUrl.startsWith("wss://")
                        ) {
                            showWsDebugDialog = false
                        }
                    }
                ) {
                    Text(stringResource(R.string.save_local_environment))
                }
            },
            dismissButton = {
                TextButton(onClick = { showWsDebugDialog = false }) {
                    Text(stringResource(R.string.close))
                }
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        viewModel.onPermissionResult(allGranted)
        checkAndRequestManageStoragePermission(context)
    }

    LaunchedEffect(viewModel.permissionEvent) {
        viewModel.permissionEvent.collect { permissions ->
            if (permissions.isNotEmpty()) {
                permissionLauncher.launch(permissions.toTypedArray())
            }
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        viewModel.onRecordAudioPermissionResult(isGranted)
    }

    val liveStreamingPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        viewModel.onLiveStreamingPermissionResult(permissions)
    }

    LaunchedEffect(Unit) {
        launch {
            viewModel.requestAudioPermissionEvent.collect {
                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
        launch {
            viewModel.requestLiveStreamingPermissionEvent.collect { permissions ->
                liveStreamingPermissionLauncher.launch(permissions.toTypedArray())
            }
        }
        launch {
            viewModel.navigationEvent.collect { route ->
                onNavigate(route)
            }
        }
    }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.home_title)) },
                actions = {
                    IconButton(onClick = { showWsDebugDialog = true }) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.environment_settings))
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            DeviceStatusCard(
                uiState = uiState,
                onConnectClick = {
                    if (showScanningDevices) {
                        showScanningDevices = false
                        viewModel.stopScanDevice()
                    } else {
                        showScanningDevices = true
                        viewModel.startScanDevice()
                    }
                },
                onReconnectClick ={
                    viewModel.connectDevice("","")
                }
            )

            FeatureGrid(
                features = uiState.features,
                onFeatureClick = { viewModel.onFeatureClick(it) }
            )
        }
    }

    if (showScanningDevices) {
        ScannedDevicesPopup(
            viewModel = viewModel,
            onDismiss = {
                showScanningDevices = false
                viewModel.stopScanDevice()
            },
            onDeviceSelected = { mac, name ->
                showScanningDevices = false
                viewModel.connectDevice(mac, name)
            }
        )
    }
}

@Composable
private fun ScannedDevicesPopup(
    viewModel: HomeViewModel,
    onDismiss: () -> Unit,
    onDeviceSelected: (mac: String, name: String) -> Unit,
) {
    val scannedDevices by viewModel.scannedDevices.collectAsState()

    CenteredFadeInPopup(
        visible = true,
        onDismissRequest = onDismiss,
        modifier = Modifier.padding(horizontal = 40.dp)
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(500.dp)
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.scanned_devices),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                if (scannedDevices.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(stringResource(R.string.no_devices_found))
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f)
                    ) {
                        items(
                            items = scannedDevices,
                            key = { result -> result.bleDevice.macAddress }
                        ) { result ->
                            ScannedDeviceItem(
                                scanResult = result,
                                onClick = {
                                    onDeviceSelected(
                                        result.bleDevice.macAddress,
                                        result.bleDevice.name.orEmpty()
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}


@Composable
fun ScannedDeviceItem(
    scanResult: ScanResult,
    onClick: () -> Unit
) {
    val unknownDevice = stringResource(R.string.unknown_device)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Bluetooth,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = scanResult.bleDevice.name ?: unknownDevice,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = scanResult.bleDevice.macAddress,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "${scanResult.rssi} dBm",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun DeviceStatusCard(
    uiState: HomeUiState,
    onConnectClick: () -> Unit,
    onReconnectClick: () -> Unit,
) {
    val unknownDevice = stringResource(R.string.unknown_device)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .height(100.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    enabled = uiState.connectionState == ConnectionState.IDLE ||
                            uiState.connectionState == ConnectionState.DISCONNECTED
                ) {
                    when (uiState.connectionState) {
                        ConnectionState.IDLE -> onConnectClick()
                        ConnectionState.DISCONNECTED -> onReconnectClick()
                        else -> {}
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            when (uiState.connectionState) {
                // 1. 已连接
                ConnectionState.CONNECTED -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Bluetooth,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = uiState.connectedDeviceName ?: unknownDevice,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = stringResource(R.string.connected),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (uiState.isCharging == true) {
                                Icon(
                                    imageVector = Icons.Default.Bolt,
                                    contentDescription = null,
                                    tint = Color.Yellow,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Text(
                                text = "${uiState.batteryLevel}%",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = if (uiState.batteryLevel > 20) Icons.Default.BatteryFull else Icons.Default.BatteryStd,
                                contentDescription = null,
                                tint = if (uiState.batteryLevel > 20) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                // 2. 连接中
                ConnectionState.CONNECTING -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.connecting_device),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                // 3. 断开状态 (显示设备名，红色/灰色提示，点击重连)
                ConnectionState.DISCONNECTED -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Icon(
                            imageVector = Icons.Default.BluetoothDisabled,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            // 依然显示之前的设备名称
                            Text(
                                text = uiState.connectedDeviceName ?: unknownDevice,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                            )
                            Text(
                                text = stringResource(R.string.disconnected_tap_reconnect),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                // 4. 闲置/初始状态 (无设备，点击扫描)
                ConnectionState.IDLE -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.BluetoothSearching,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f),
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.device_not_connected),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(R.string.tap_scan_connect),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }

        }
    }
}

@Composable
fun FeatureGrid(
    features: List<Feature>,
    onFeatureClick: (Feature) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(features) { feature ->
            FeatureItem(
                feature = feature,
                onClick = { onFeatureClick(feature) }
            )
        }
    }
}

@Composable
fun FeatureItem(
    feature: Feature,
    onClick: () -> Unit
) {
    val featureName = stringResource(feature.nameRes)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.2f)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = feature.icon,
                    contentDescription = featureName,
                    modifier = Modifier.size(36.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = featureName,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (feature.badgeCount != null && feature.badgeCount > 0) {
                Badge(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp),
                    containerColor = MaterialTheme.colorScheme.error
                ) {
                    Text(
                        text = if (feature.badgeCount > 99) "99+" else feature.badgeCount.toString(),
                        color = Color.White
                    )
                }
            }
        }
    }
}

private fun checkAndRequestManageStoragePermission(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        if (!Environment.isExternalStorageManager()) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:${context.packageName}")
                context.startActivity(intent)
            } catch (e: Exception) {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                context.startActivity(intent)
            }
        }
    }
}