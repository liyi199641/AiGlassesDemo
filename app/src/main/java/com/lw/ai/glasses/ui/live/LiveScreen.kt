package com.lw.ai.glasses.ui.live

import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fission.wear.glasses.sdk.constant.GlassesConstant
import com.lw.ai.glasses.R
import com.lw.top.lib_core.utils.findActivity
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveScreen(
    onNavigateBack: () -> Unit,
    viewModel: LiveViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val vlcPlayer = viewModel.vlcPlayer
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val canSwitchTab = !uiState.isConnecting &&
            !uiState.isPlayingLocal &&
            !uiState.isDeviceStreaming &&
            !uiState.isPushingToDouyin

    DisposableEffect(activity) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        activity?.let {
            viewModel.initDouyinSdk(activity)
        }
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(stringResource(R.string.live_title)) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
                TabRow(selectedTabIndex = uiState.currentFunction.ordinal) {
                    Tab(
                        selected = uiState.currentFunction == AppFunctionMode.LOCAL_PLAYER,
                        onClick = { viewModel.switchFunction(AppFunctionMode.LOCAL_PLAYER) },
                        text = { Text(stringResource(R.string.local_preview)) },
                        enabled = canSwitchTab
                    )
                    Tab(
                        selected = uiState.currentFunction == AppFunctionMode.DOUYIN_PUSHER,
                        onClick = { viewModel.switchFunction(AppFunctionMode.DOUYIN_PUSHER) },
                        text = { Text(stringResource(R.string.douyin_push)) },
                        enabled = canSwitchTab
                    )
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            when (uiState.currentFunction) {
                AppFunctionMode.LOCAL_PLAYER -> {
                    LocalPlayerContent(
                        uiState = uiState,
                        vlcPlayer = vlcPlayer,
                        onStart = viewModel::startStreaming,
                        onStop = viewModel::stopStreaming,
                        onFpsChange = viewModel::updateFps,
                        onResolutionChange = viewModel::updateResolution,
                        onBitrateChange = viewModel::updateBitrateChange,
                        onLiveChannelChange = viewModel::updateLiveChannel
                    )
                }

                AppFunctionMode.DOUYIN_PUSHER -> {
                    DouyinPusherContent(
                        uiState = uiState,
                        onAuth = viewModel::douyinAuth,
                        onStart = { activity?.let(viewModel::startLiveStreaming) },
                        onStop = viewModel::closeBroadcast,
                    )
                }
            }
        }
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalPlayerContent(
    uiState: LiveUiState,
    vlcPlayer: MediaPlayer?,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onFpsChange: (Int) -> Unit,
    onResolutionChange: (String) -> Unit,
    onBitrateChange: (Int) -> Unit, // <--- 新增回调：码率变化
    onLiveChannelChange: (GlassesConstant.LiveChannel) -> Unit
) {
    val resolutions = listOf("1280x720", "960x720","720x960","720*480","480*720")
    val liveChannels = GlassesConstant.LiveChannel.entries
    var isResolutionExpanded by remember { mutableStateOf(false) }
    var isLiveChannelExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .imePadding() // <--- 关键：自动避让键盘，配合下面的 verticalScroll 使用
            .verticalScroll(rememberScrollState())
    ) {

        // --- 视频播放区域 ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(400.dp)
                .padding(16.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black)
        ) {
            if (vlcPlayer != null && uiState.isPlayingLocal) {
                AndroidView(
                    factory = { ctx ->
                        VLCVideoLayout(ctx).apply {
                            runCatching { vlcPlayer.detachViews() }
                            vlcPlayer.attachViews(this, null, false, false)
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    onRelease = {
                        runCatching { vlcPlayer.detachViews() }
                    }
                )
            }

            // 遮罩层逻辑
            if (!uiState.isPlayingLocal) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Default.VideocamOff,
                            contentDescription = null,
                            tint = Color.Gray,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.waiting_device_connection), color = Color.White)
                        if (uiState.isConnecting) {
                            Spacer(Modifier.height(16.dp))
                            CircularProgressIndicator(color = Color.White)
                        }
                    }
                }
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(
                    alpha = 0.5f
                )
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    stringResource(R.string.parameter_config),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))

                // --- 第一行：帧率 & 分辨率 ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 帧率输入
                    OutlinedTextField(
                        value = if (uiState.targetFps == 0) "" else uiState.targetFps.toString(),
                        onValueChange = { newValue ->
                            if (newValue.all { it.isDigit() }) {
                                val intValue = newValue.toIntOrNull() ?: 0
                                onFpsChange(intValue)
                            }
                        },
                        label = { Text(stringResource(R.string.fps_label)) },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        enabled = !uiState.isPlayingLocal
                    )

                    // 分辨率选择
                    ExposedDropdownMenuBox(
                        expanded = isResolutionExpanded && !uiState.isPlayingLocal,
                        onExpandedChange = {
                            if (!uiState.isPlayingLocal) isResolutionExpanded = !isResolutionExpanded
                        },
                        modifier = Modifier.weight(1.5f)
                    ) {
                        OutlinedTextField(
                            value = uiState.targetResolution,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.resolution)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isResolutionExpanded) },
                            modifier = Modifier.menuAnchor(),
                            enabled = !uiState.isPlayingLocal
                        )
                        ExposedDropdownMenu(
                            expanded = isResolutionExpanded,
                            onDismissRequest = { isResolutionExpanded = false }
                        ) {
                            resolutions.forEach { res ->
                                DropdownMenuItem(
                                    text = { Text(res) },
                                    onClick = {
                                        onResolutionChange(res)
                                        isResolutionExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = if (uiState.bitrate == 0) "" else uiState.bitrate.toString(),
                    onValueChange = { newValue ->
                        if (newValue.all { it.isDigit() }) {
                            // 码率可能是个大数字，但 Int (20亿) 足够容纳 1000000
                            val intValue = newValue.toIntOrNull() ?: 0
                            onBitrateChange(intValue)
                        }
                    },
                    label = { Text(stringResource(R.string.bitrate_label)) },
                    placeholder = { Text(stringResource(R.string.bitrate_default_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    enabled = !uiState.isPlayingLocal,
                    supportingText = { Text(stringResource(R.string.bitrate_suggested)) } // 友好的提示文本
                )

                Spacer(modifier = Modifier.height(12.dp))

                ExposedDropdownMenuBox(
                    expanded = isLiveChannelExpanded && !uiState.isPlayingLocal,
                    onExpandedChange = {
                        if (!uiState.isPlayingLocal) {
                            isLiveChannelExpanded = !isLiveChannelExpanded
                        }
                    }
                ) {
                    OutlinedTextField(
                        value = uiState.liveChannel.displayName(),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.live_channel_label)) },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = isLiveChannelExpanded)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        enabled = !uiState.isPlayingLocal
                    )
                    ExposedDropdownMenu(
                        expanded = isLiveChannelExpanded,
                        onDismissRequest = { isLiveChannelExpanded = false }
                    ) {
                        liveChannels.forEach { channel ->
                            DropdownMenuItem(
                                text = { Text(channel.displayName()) },
                                onClick = {
                                    onLiveChannelChange(channel)
                                    isLiveChannelExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // --- 底部按钮 ---
        val isRunning = uiState.isPlayingLocal
        val isConnecting = uiState.isConnecting

        Button(
            onClick = { if (isRunning) onStop() else onStart() },
            enabled = !isConnecting,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(56.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
        ) {
            if (isConnecting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.establishing_connection))
            } else {
                Text(
                    text = if (isRunning) {
                        stringResource(R.string.disconnect_device)
                    } else {
                        stringResource(R.string.connect_glasses_preview)
                    },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // 底部留白
        Spacer(modifier = Modifier.height(32.dp))
    }
}

private fun GlassesConstant.LiveChannel.displayName(): String {
    return when (this) {
        GlassesConstant.LiveChannel.WIFI_AP -> "WIFI_AP"
        GlassesConstant.LiveChannel.WIFI_STATION -> "WIFI_STATION"
        GlassesConstant.LiveChannel.BT -> "BT"
    }
}

@Composable
private fun DouyinPusherContent(
    uiState: LiveUiState,
    onAuth: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    val isRunning = uiState.isPushingToDouyin
    val isConnecting = uiState.isConnecting
    val isDeviceStreaming = uiState.isDeviceStreaming

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.device_status),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.weight(1f))
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(
                                color = if (isDeviceStreaming) Color.Green else Color.Gray,
                                shape = CircleShape
                            )
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (isDeviceStreaming) stringResource(R.string.streaming) else stringResource(R.string.not_streaming),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Spacer(Modifier.height(12.dp))
//                VerticalDivider()
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.pushing_status),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.weight(1f))
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(
                                color = if (isRunning) Color.Red else Color.Gray,
                                shape = CircleShape
                            )
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (isRunning) stringResource(R.string.live_now) else stringResource(R.string.idle),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        Text(
            text = stringResource(R.string.parameter_config),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )



        Spacer(Modifier.height(16.dp))

        Spacer(Modifier.weight(1f))

        Button(
            onClick = onAuth,
            enabled = !isConnecting && !isRunning,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(stringResource(R.string.douyin_auth))
        }

        Spacer(Modifier.height(12.dp))

        Button(
            onClick = { if (isRunning) onStop() else onStart() },
            enabled = !isConnecting,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
        ) {
            if (isConnecting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.initializing))
            } else {
                Text(
                    text = if (isRunning) {
                        stringResource(R.string.disconnect_glasses_stop_push)
                    } else {
                        stringResource(R.string.connect_glasses_start_push)
                    },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}
