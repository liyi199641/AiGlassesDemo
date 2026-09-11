package com.lw.ai.glasses.ui.live

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.opengl.GLSurfaceView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.ScreenRotation
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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fission.wear.glasses.sdk.constant.GlassesConstant.LiveStreamingMode
import com.lw.ai.glasses.R
import com.lw.top.lib_core.utils.findActivity
import com.realsil.sdk.audioconnect.smartwear.live.view.RTKVideoView
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.launch
import kotlin.math.min


private const val LY_PREVIEW_ROTATION_DEGREES = 270f

@SuppressLint("UnusedBoxWithConstraintsScope")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveScreen(
    onNavigateBack: () -> Unit,
    viewModel: LiveViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewViewHolder = remember { arrayOfNulls<View>(1) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        viewModel.onLocationPermissionResult(
            permissions.values.all { it } && viewModel.hasLocationPermission(),
        )
    }

    LaunchedEffect(Unit) {
        launch {
            viewModel.requestLocationPermissionEvent.collect {
                locationPermissionLauncher.launch(viewModel.liveLocationPermissions())
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.requestLocationPermissionIfNeeded()
    }

    DisposableEffect(lifecycleOwner, uiState.isLyScheme) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> viewModel.onPreviewLifecycleStop()
                Lifecycle.Event.ON_RESUME -> {
                    (previewViewHolder[0] as? RTKVideoView)?.onResume()
                }
                Lifecycle.Event.ON_PAUSE -> {
                    (previewViewHolder[0] as? RTKVideoView)?.onPause()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            (previewViewHolder[0] as? RTKVideoView)?.onResume()
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            (previewViewHolder[0] as? RTKVideoView)?.onPause()
            viewModel.onPreviewLifecycleStop()
        }
    }

    DisposableEffect(activity) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            viewModel.detachPreviewView()
        }
    }

    BackHandler {
        when {
            uiState.isConnecting -> viewModel.cancelConnecting()
            uiState.screenPhase == LiveScreenPhase.LIVE -> viewModel.exitLiveScreen()
            else -> onNavigateBack()
        }
    }

    val previewAspectRatio = remember(uiState.targetResolution) {
        parseLiveResolutionAspectRatio(uiState.targetResolution)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            if (uiState.showsLocalPreview &&
                (!uiState.isLyScheme || uiState.screenPhase == LiveScreenPhase.LIVE)
            ) {
                val density = LocalDensity.current
                val maxWpx = with(density) { maxWidth.toPx() }
                val maxHpx = with(density) { maxHeight.toPx() }
                val degrees = uiState.previewRotation
                val layoutW = maxWpx
                val layoutH = layoutW / previewAspectRatio
                val scale = if (degrees == 90 || degrees == 270) {
                    min(maxWpx / layoutH, maxHpx / layoutW).coerceAtMost(1f)
                } else {
                    min(1f, maxHpx / layoutH)
                }
                if (uiState.isLyScheme) {
                    // 眼镜摄像头传感器固定旋转 90°，RTSP(H264) 流不带旋转元数据，且 LY 方案无
                    // 眼镜端旋转指令，只能在本地视图修正。PlayerView 已改用 TextureView，可可靠应用
                    // rotationZ；再按 90° 旋转后的包围盒等比缩放，保证画面完整不变形地居中显示。
                    // 若实测方向相反（画面倒了 180°），把 LY_PREVIEW_ROTATION_DEGREES 改成 270f 即可。
                    val lyAspect = 4f / 3f // 源视频 640x480
                    val lyLayoutW = maxWpx
                    val lyLayoutH = lyLayoutW / lyAspect
                    val lyScale = min(maxWpx / lyLayoutH, maxHpx / lyLayoutW).coerceAtMost(1f)
                    LyExoPlayerPreviewView(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(lyAspect)
                            .graphicsLayer {
                                rotationZ = LY_PREVIEW_ROTATION_DEGREES
                                scaleX = lyScale
                                scaleY = lyScale
                            },
                        onViewReady = { view ->
                            if (previewViewHolder[0] !== view) {
                                previewViewHolder[0] = view
                                viewModel.onPreviewViewReady(view)
                            }
                        },
                    )
                } else {
                    AndroidView(
                        factory = { ctx ->
                            RTKVideoView(ctx).apply {
                                setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(previewAspectRatio)
                            .graphicsLayer {
                                rotationZ = degrees.toFloat()
                                scaleX = scale
                                scaleY = scale
                            },
                        update = { view ->
                            if (previewViewHolder[0] == null) {
                                previewViewHolder[0] = view
                                viewModel.onPreviewViewReady(view)
                            }
                            if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                                view.onResume()
                            }
                        },
                    )
                }
            }
        }

        when (uiState.screenPhase) {
            LiveScreenPhase.CONFIG -> {
                LiveConfigScreen(
                    uiState = uiState,
                    onNavigateBack = {
                        if (uiState.isConnecting) viewModel.cancelConnecting()
                        else onNavigateBack()
                    },
                    onFpsChange = viewModel::updateFps,
                    onResolutionChange = viewModel::updateResolution,
                    onBitrateChange = viewModel::updateBitrateChange,
                    onStreamingModeChange = { mode ->
                        viewModel.updateStreamingMode(mode, activity)
                    },
                    onManualPushUrlChange = viewModel::updateManualPushUrl,
                    onConfirm = { activity?.let(viewModel::confirmAndStartLive) },
                )
            }

            LiveScreenPhase.LIVE -> {
                LiveFullscreenOverlay(
                    uiState = uiState,
                    onExitLive = viewModel::exitLiveScreen,
                    onCancelConnecting = viewModel::cancelConnecting,
                    onRetryDouyinBroadcast = { activity?.let(viewModel::retryDouyinBroadcast) },
                    onToggleMic = viewModel::togglePreviewMic,
                    onToggleRotation = viewModel::togglePreviewRotation,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LiveConfigScreen(
    uiState: LiveUiState,
    onNavigateBack: () -> Unit,
    onFpsChange: (Int) -> Unit,
    onResolutionChange: (String) -> Unit,
    onBitrateChange: (Int) -> Unit,
    onStreamingModeChange: (LiveStreamingMode) -> Unit,
    onManualPushUrlChange: (String) -> Unit,
    onConfirm: () -> Unit,
) {
    val resolutions = listOf("1280x720", "960x720", "720x480", "720x960", "480x720")
    var isResolutionExpanded by remember { mutableStateOf(false) }
    val canEdit = !uiState.isConnecting

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (uiState.isLyScheme) R.string.preview_title else R.string.live_title,
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.surface)
                .imePadding()
                .verticalScroll(rememberScrollState()),
        ) {
            if (uiState.showsParameterConfig) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                ),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.parameter_config),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OutlinedTextField(
                            value = if (uiState.targetFps == 0) "" else uiState.targetFps.toString(),
                            onValueChange = { newValue ->
                                if (newValue.all { it.isDigit() }) {
                                    onFpsChange(newValue.toIntOrNull() ?: 0)
                                }
                            },
                            label = { Text(stringResource(R.string.fps_label)) },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            enabled = canEdit,
                        )

                        ExposedDropdownMenuBox(
                            expanded = isResolutionExpanded && canEdit,
                            onExpandedChange = {
                                if (canEdit) isResolutionExpanded = !isResolutionExpanded
                            },
                            modifier = Modifier.weight(1.5f),
                        ) {
                            OutlinedTextField(
                                value = uiState.targetResolution,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(stringResource(R.string.resolution)) },
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = isResolutionExpanded)
                                },
                                modifier = Modifier.menuAnchor(),
                                enabled = canEdit,
                            )
                            ExposedDropdownMenu(
                                expanded = isResolutionExpanded,
                                onDismissRequest = { isResolutionExpanded = false },
                            ) {
                                resolutions.forEach { res ->
                                    DropdownMenuItem(
                                        text = { Text(res) },
                                        onClick = {
                                            onResolutionChange(res)
                                            isResolutionExpanded = false
                                        },
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
                                onBitrateChange(newValue.toIntOrNull() ?: 0)
                            }
                        },
                        label = { Text(stringResource(R.string.bitrate_label)) },
                        placeholder = { Text(stringResource(R.string.bitrate_default_hint)) },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        enabled = canEdit,
                        supportingText = { Text(stringResource(R.string.bitrate_suggested)) },
                    )
                }
            }
            } else {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    ),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.preview_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.live_mode_preview_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (uiState.showsStreamingModeOptions) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                ),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.live_mode_label),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    LiveStreamingMode.entries.forEach { mode ->
                        LiveModeOptionRow(
                            selected = uiState.streamingMode == mode,
                            enabled = canEdit,
                            title = stringResource(mode.titleRes()),
                            description = stringResource(mode.descriptionRes()),
                            onClick = { onStreamingModeChange(mode) },
                        )
                    }

                    if (uiState.requiresManualPushUrl) {
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = uiState.manualPushUrl,
                            onValueChange = onManualPushUrlChange,
                            label = { Text(stringResource(R.string.manual_push_url_label)) },
                            placeholder = { Text(stringResource(R.string.manual_push_url_hint)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            enabled = canEdit,
                        )
                    }
                }
            }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onConfirm,
                enabled = !uiState.isConnecting &&
                    (!uiState.requiresManualPushUrl || uiState.manualPushUrl.isNotBlank()),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(56.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    text = stringResource(
                        if (uiState.isLyScheme) {
                            R.string.confirm_start_preview
                        } else {
                            uiState.streamingMode.confirmButtonRes()
                        },
                    ),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun LiveFullscreenOverlay(
    uiState: LiveUiState,
    onExitLive: () -> Unit,
    onCancelConnecting: () -> Unit,
    onRetryDouyinBroadcast: () -> Unit,
    onToggleMic: () -> Unit,
    onToggleRotation: () -> Unit,
) {
    val showBlockingOverlay = uiState.isConnecting && when (uiState.connectPhase) {
        LiveConnectPhase.STOPPING,
        LiveConnectPhase.PREVIEW,
        -> true
        LiveConnectPhase.DOUYIN -> uiState.requiresDouyinPush
        LiveConnectPhase.NONE -> false
    }
    val onBack = if (uiState.isConnecting) onCancelConnecting else onExitLive
    val loadingText = when (uiState.connectPhase) {
        LiveConnectPhase.PREVIEW -> when (uiState.streamingMode) {
            LiveStreamingMode.PUSH -> stringResource(R.string.establishing_push)
            else -> stringResource(R.string.establishing_preview)
        }
        LiveConnectPhase.DOUYIN -> when (uiState.streamingMode) {
            LiveStreamingMode.PUSH -> stringResource(R.string.establishing_push)
            else -> stringResource(R.string.starting_douyin_live)
        }
        LiveConnectPhase.STOPPING -> stringResource(R.string.stopping_live)
        LiveConnectPhase.NONE -> stringResource(R.string.establishing_connection)
    }
    val titleText = when {
        uiState.isPushingToDouyin && uiState.streamingMode == LiveStreamingMode.PUSH ->
            stringResource(R.string.push_only_in_progress)
        uiState.isPushingToDouyin -> stringResource(R.string.live_in_progress)
        uiState.isPlayingLocal -> stringResource(R.string.preview_in_progress)
        else -> stringResource(R.string.live_title)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (showBlockingOverlay) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color.White)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = loadingText,
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back_to_config),
                        tint = Color.White,
                    )
                }
                Text(
                    text = titleText,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                LiveStatusCard(
                    modifier = Modifier.weight(1f),
                    title = stringResource(R.string.device_status),
                    statusText = if (uiState.isDeviceStreaming) {
                        stringResource(R.string.device_streaming_active)
                    } else {
                        stringResource(R.string.device_streaming_inactive)
                    },
                    active = uiState.isDeviceStreaming,
                    activeColor = Color(0xFF4CAF50),
                )
                if (uiState.showsPushStatus) {
                    LiveStatusCard(
                        modifier = Modifier.weight(1f),
                        title = stringResource(R.string.pushing_status),
                        statusText = if (uiState.isPushingToDouyin) {
                            stringResource(R.string.push_streaming_active)
                        } else {
                            stringResource(R.string.push_streaming_inactive)
                        },
                        active = uiState.isPushingToDouyin,
                        activeColor = Color.Red,
                    )
                }
            }

            if (uiState.connectPhase == LiveConnectPhase.DOUYIN &&
                uiState.isPlayingLocal &&
                !uiState.isPushingToDouyin &&
                uiState.streamingMode == LiveStreamingMode.PREVIEW_PUSH
            ) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.starting_douyin_live),
                    color = Color.White.copy(alpha = 0.9f),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = Color.Black.copy(alpha = 0.55f),
                            shape = RoundedCornerShape(8.dp),
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }

            if (uiState.errorMessage != null && !showBlockingOverlay) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = uiState.errorMessage,
                    color = Color(0xFFFFCDD2),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = Color.Black.copy(alpha = 0.55f),
                            shape = RoundedCornerShape(8.dp),
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }

        if (!showBlockingOverlay) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (uiState.isPlayingLocal &&
                    uiState.showsLocalPreview &&
                    uiState.showsPreviewControls
                ) {
                    LivePreviewControlRow(
                        isMicOn = uiState.isMicOn,
                        onToggleMic = onToggleMic,
                        onToggleRotation = onToggleRotation,
                    )
                }
                if (uiState.canRetryDouyinBroadcast) {
                    Button(
                        onClick = onRetryDouyinBroadcast,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(28.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.retry_start_douyin_live),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Button(
                    onClick = onExitLive,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text(
                        text = stringResource(
                            when {
                                uiState.streamingMode == LiveStreamingMode.PREVIEW &&
                                    uiState.isPlayingLocal &&
                                    !uiState.isPushingToDouyin -> R.string.stop_preview
                                else -> R.string.stop_live
                            },
                        ),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun LivePreviewControlRow(
    isMicOn: Boolean,
    onToggleMic: () -> Unit,
    onToggleRotation: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = Color.Black.copy(alpha = 0.55f),
                shape = RoundedCornerShape(28.dp),
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LivePreviewControlButton(
            icon = if (isMicOn) Icons.Default.Mic else Icons.Default.MicOff,
            label = stringResource(R.string.live_preview_mic),
            active = isMicOn,
            onClick = onToggleMic,
        )
        LivePreviewControlButton(
            icon = Icons.Default.ScreenRotation,
            label = stringResource(R.string.live_preview_rotate),
            active = true,
            onClick = onToggleRotation,
        )
    }
}

@Composable
private fun LivePreviewControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (active) Color.White else Color.White.copy(alpha = 0.45f),
            modifier = Modifier.size(28.dp),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.85f),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun LiveStatusCard(
    modifier: Modifier = Modifier,
    title: String,
    statusText: String,
    active: Boolean,
    activeColor: Color,
) {
    Column(
        modifier = modifier
            .background(
                color = Color.Black.copy(alpha = 0.55f),
                shape = RoundedCornerShape(10.dp),
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(
                        color = if (active) activeColor else Color.Gray,
                        shape = CircleShape,
                    ),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                color = Color.White.copy(alpha = 0.85f),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = statusText,
            color = if (active) activeColor else Color.White.copy(alpha = 0.65f),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

/** 解析分辨率字符串为宽高比，默认 16:9 横屏。 */
private fun parseLiveResolutionAspectRatio(resolution: String): Float {
    val normalized = resolution.replace('*', 'x')
    val parts = normalized.split('x', 'X').mapNotNull { it.trim().toIntOrNull() }
    if (parts.size >= 2 && parts[1] > 0) {
        return parts[0].toFloat() / parts[1].toFloat()
    }
    return 16f / 9f
}

@Composable
private fun LiveModeOptionRow(
    selected: Boolean,
    enabled: Boolean,
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onClick() }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            enabled = enabled,
        )
        Column(modifier = Modifier.padding(start = 4.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LyExoPlayerPreviewView(
    modifier: Modifier = Modifier,
    onViewReady: (PlayerView) -> Unit,
) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            // 用 XML 布局创建 surface_type="texture_view" 的 PlayerView：
            // 默认 SurfaceView 无法随 graphicsLayer 旋转，TextureView 才能正确显示旋转后的画面。
            LayoutInflater.from(ctx)
                .inflate(R.layout.view_ly_exo_player, null) as PlayerView
        },
        update = onViewReady,
    )
}

private fun LiveStreamingMode.titleRes(): Int = when (this) {
    LiveStreamingMode.PREVIEW -> R.string.live_mode_preview
    LiveStreamingMode.PUSH -> R.string.live_mode_push
    LiveStreamingMode.PREVIEW_PUSH -> R.string.live_mode_preview_push
    LiveStreamingMode.PREVIEW_PUSH_MANUAL -> R.string.live_mode_preview_push_manual
}

private fun LiveStreamingMode.descriptionRes(): Int = when (this) {
    LiveStreamingMode.PREVIEW -> R.string.live_mode_preview_desc
    LiveStreamingMode.PUSH -> R.string.live_mode_push_desc
    LiveStreamingMode.PREVIEW_PUSH -> R.string.live_mode_preview_push_desc
    LiveStreamingMode.PREVIEW_PUSH_MANUAL -> R.string.live_mode_preview_push_manual_desc
}

private fun LiveStreamingMode.confirmButtonRes(): Int = when (this) {
    LiveStreamingMode.PREVIEW -> R.string.confirm_start_preview
    LiveStreamingMode.PUSH -> R.string.confirm_start_live
    LiveStreamingMode.PREVIEW_PUSH -> R.string.live_preview_push
    LiveStreamingMode.PREVIEW_PUSH_MANUAL -> R.string.live_preview_push
}
