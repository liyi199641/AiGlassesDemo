package com.lw.ai.glasses.ui.call

import android.Manifest
import android.content.Intent
import android.view.TextureView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VideoCall
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.VoiceChat
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.fission.wear.glasses.sdk.AiAssistantClient
import com.lw.ai.glasses.R
import com.lw.ai.glasses.ui.common.WsConnectionTopNotification
import com.lw.ai.glasses.ui.translate.Language
import com.lw.ai.glasses.ui.translate.LanguageSelectionSheet
import kotlinx.coroutines.delay

@Composable
fun CallScreen(
    viewModel: CallViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        viewModel.onRecordAudioPermissionResult(isGranted)
    }

    LaunchedEffect(Unit) {
        viewModel.requestAudioPermissionEvent.collect {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            if (!uiState.isInCall) {
                CallSetupContent(
                    uiState = uiState,
                    viewModel = viewModel,
                    onNavigateBack = onNavigateBack,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                ActiveCallContent(
                    uiState = uiState,
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        WsConnectionTopNotification(
            state = uiState.wsConnection,
            onReconnect = viewModel::reconnectWebSocket,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

@Composable
private fun CallDurationBadge(
    durationSeconds: Int,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = Color.Black.copy(alpha = 0.72f),
        shape = RoundedCornerShape(20.dp),
        shadowElevation = 6.dp,
    ) {
        Text(
            text = formatCallDuration(durationSeconds),
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallSetupContent(
    uiState: CallUiState,
    viewModel: CallViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showLanguageSheet by remember { mutableStateOf(false) }
    var isSelectingSource by remember { mutableStateOf(true) }

    if (showLanguageSheet) {
        LanguageSelectionSheet(
            languages = uiState.allLanguages,
            onDismissRequest = { showLanguageSheet = false },
            onLanguageSelected = { language ->
                if (isSelectingSource) {
                    viewModel.setSourceLanguage(language)
                } else {
                    viewModel.setTargetLanguage(language)
                }
            },
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(stringResource(R.string.start_call), style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(32.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(
                onClick = { viewModel.setCallMode(CallMode.VIDEO) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (uiState.callMode == CallMode.VIDEO) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Icon(Icons.Default.VideoCall, contentDescription = null)
                Text(stringResource(R.string.video_call))
            }
            Button(
                onClick = { viewModel.setCallMode(CallMode.AUDIO) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (uiState.callMode == CallMode.AUDIO) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Icon(Icons.Default.VoiceChat, contentDescription = null)
                Text(stringResource(R.string.audio_call))
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = stringResource(R.string.call_language_pair_title),
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(modifier = Modifier.height(12.dp))
        CallLanguageTopBar(
            srcLang = uiState.srcLang,
            targetLang = uiState.targetLang,
            onSrcClick = {
                isSelectingSource = true
                showLanguageSheet = true
            },
            onTargetClick = {
                isSelectingSource = false
                showLanguageSheet = true
            },
            onSwapClick = { viewModel.swapLanguages() },
        )

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = { viewModel.startCall() },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            enabled = !uiState.isLoading,
            shape = RoundedCornerShape(12.dp)
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text(stringResource(R.string.start_call), fontSize = 18.sp)
            }
        }
    }
}

@Composable
private fun CallLanguageTopBar(
    srcLang: Language?,
    targetLang: Language?,
    onSrcClick: () -> Unit,
    onTargetClick: () -> Unit,
    onSwapClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.call_my_speech_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            TextButton(onClick = onSrcClick, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = srcLang?.name ?: stringResource(R.string.choose_language),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                )
            }
        }

        IconButton(onClick = onSwapClick) {
            Icon(
                imageVector = Icons.Default.SwapHoriz,
                contentDescription = stringResource(R.string.swap),
                tint = MaterialTheme.colorScheme.primary,
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.call_other_speech_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            TextButton(onClick = onTargetClick, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = targetLang?.name ?: stringResource(R.string.choose_language),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
fun ActiveCallContent(
    uiState: CallUiState,
    viewModel: CallViewModel,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val shareTitle = stringResource(R.string.share)
    val listState = rememberLazyListState()

    LaunchedEffect(uiState.translationLogs.size) {
        if (uiState.translationLogs.isNotEmpty()) {
            listState.animateScrollToItem(uiState.translationLogs.size - 1)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {

        // 1. 视频流层 / 语音等待层 (最底层)
        if (uiState.callMode == CallMode.VIDEO) {
            VideoOverlayLayout(
                isRemoteReady = uiState.isRemoteVideoReady,
                isVideoMuted = uiState.isVideoMuted,
                isRemoteVideoMuted = uiState.isRemoteVideoMuted
            )
        } else if (!uiState.isCallConnected) {
            WaitingRemoteJoinOverlay()
        }

        // 2. 翻译记录层 (中间层)
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 100.dp, bottom = 150.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(uiState.translationLogs) { log ->
                TranslationBubble(log)
            }
        }

        // 3. 视频控制按钮层 (右上/侧边悬浮，必须在渲染层之后定义以接收点击)
        if (uiState.callMode == CallMode.VIDEO) {
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                FloatingActionButton(
                    onClick = { viewModel.flipCamera() },
                    containerColor = Color.Black.copy(alpha = 0.5f),
                    contentColor = Color.White,
                    shape = CircleShape,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.FlipCameraAndroid, contentDescription = stringResource(R.string.switch_camera))
                }

                FloatingActionButton(
                    onClick = { viewModel.toggleVideo() },
                    containerColor = if (uiState.isVideoMuted) MaterialTheme.colorScheme.error.copy(alpha = 0.7f) else Color.Black.copy(alpha = 0.5f),
                    contentColor = Color.White,
                    shape = CircleShape,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = if (uiState.isVideoMuted) Icons.Default.VideocamOff else Icons.Default.Videocam,
                        contentDescription = stringResource(R.string.video_toggle)
                    )
                }

                FloatingActionButton(
                    onClick = { viewModel.toggleRemoteAudio() },
                    containerColor = if (uiState.isRemoteAudioMuted) MaterialTheme.colorScheme.error.copy(alpha = 0.7f) else Color.Black.copy(alpha = 0.5f),
                    contentColor = Color.White,
                    shape = CircleShape,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = if (uiState.isRemoteAudioMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                        contentDescription = stringResource(R.string.remote_audio_toggle)
                    )
                }


            }
        }

        // 4. 底部通话控制栏 (最顶层)
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (uiState.isCallConnected) {
                CallDurationBadge(
                    durationSeconds = uiState.callDurationSeconds,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = { viewModel.toggleMic() },
                    modifier = Modifier
                        .size(56.dp)
                        .background(
                            if (uiState.isMicMuted) MaterialTheme.colorScheme.error else Color.DarkGray.copy(alpha = 0.6f),
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = if (uiState.isMicMuted) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = stringResource(R.string.mute),
                        tint = Color.White
                    )
                }

                IconButton(
                    onClick = { viewModel.endCall() },
                    modifier = Modifier
                        .size(72.dp)
                        .background(MaterialTheme.colorScheme.error, CircleShape)
                ) {
                    Icon(
                        Icons.Default.CallEnd,
                        contentDescription = stringResource(R.string.hang_up),
                        tint = MaterialTheme.colorScheme.onError,
                        modifier = Modifier.size(32.dp)
                    )
                }

                IconButton(
                    onClick = { viewModel.toggleSpeaker() },
                    modifier = Modifier
                        .size(56.dp)
                        .background(
                            if (uiState.isSpeakerOn) MaterialTheme.colorScheme.primary else Color.DarkGray.copy(alpha = 0.6f),
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = if (uiState.isSpeakerOn) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                        contentDescription = stringResource(R.string.speaker),
                        tint = Color.White
                    )
                }
            }
        }

        val hostUrl = uiState.hostUrl
        if (!hostUrl.isNullOrBlank()) {
            FloatingActionButton(
                onClick = {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, hostUrl)
                    }
                    context.startActivity(Intent.createChooser(send, shareTitle))
                },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(start = 16.dp, top = 16.dp),
                containerColor = Color.Black.copy(alpha = 0.5f),
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Share, contentDescription = stringResource(R.string.share_room_link))
            }
        }
    }
}

@Composable
private fun WaitingRemoteJoinOverlay(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.waiting_remote_join),
                color = Color.White,
                fontSize = 14.sp,
            )
        }
    }
}

@Composable
fun VideoOverlayLayout(
    isRemoteReady: Boolean,
    isVideoMuted: Boolean,
    isRemoteVideoMuted: Boolean
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // 远端视频
        if (isRemoteReady && !isRemoteVideoMuted) {
            AndroidView(
                factory = { ctx ->
                    TextureView(ctx).also {
                        AiAssistantClient.getInstance().updateRemoteView(it)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        } else if (!isRemoteReady) {
            WaitingRemoteJoinOverlay()
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Person, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(64.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.remote_camera_off), color = Color.White, fontSize = 14.sp)
                }
            }
        }

        // 本地视频预览 (右上角小窗，贴顶)
        Card(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 16.dp)
                .size(width = 90.dp, height = 130.dp),
            shape = RoundedCornerShape(8.dp),
            elevation = CardDefaults.cardElevation(4.dp)
        ) {
            if (!isVideoMuted) {
                AndroidView(
                    factory = { ctx ->
                        TextureView(ctx).also {
                            AiAssistantClient.getInstance().updateLocalView(it)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.DarkGray),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Person, contentDescription = null, tint = Color.Gray)
                }
            }
        }
    }
}

@Composable
fun TranslationBubble(log: TranslationMessage) {
    val alignment = if (log.isFromMe) Alignment.CenterEnd else Alignment.CenterStart
    val bubbleColor = if (log.isFromMe) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
    
    val shape = if (log.isFromMe) {
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 0.dp)
    } else {
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 0.dp, bottomEnd = 16.dp)
    }

    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = alignment) {
        Surface(
            modifier = Modifier.widthIn(max = 280.dp),
            color = bubbleColor,
            shape = shape,
            shadowElevation = 1.dp
        ) {
            Text(
                text = log.text,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
