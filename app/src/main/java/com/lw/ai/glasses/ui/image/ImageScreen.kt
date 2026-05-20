@file:Suppress("OPT_IN_ARGUMENT_IS_NOT_MARKER")

package com.lw.ai.glasses.ui.image

import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import com.lw.ai.glasses.R
import com.lw.top.lib_core.data.local.entity.MediaFilesEntity
import java.io.File
import kotlin.math.log10
import kotlin.math.pow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageScreen(
    viewModel: ImageViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.sync_album_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                actions = {
                    TextButton(onClick = {
                        viewModel.clearAllPhotos()
                    }) {
                        Text(stringResource(R.string.clear_records))
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            SyncStatusHeader(
                progress = uiState.syncState.syncProgress,
                currentFileIndex = uiState.syncState.currentFileIndex,
                totalFiles = uiState.syncState.totalFilesToSync,
                isSyncing = uiState.syncState.isSyncing,
                speed = uiState.syncState.speed,
                onSyncClick = { viewModel.syncAllMediaFile() }
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (uiState.images.isEmpty() && !uiState.syncState.isSyncing) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(stringResource(R.string.empty_images_hint))
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(items = uiState.images, key = { it.id }) { mediaFile ->
                        val videoExtensions = setOf("mp4", "mov", "avi", "mkv", "webm")
                        val isVideo = videoExtensions.contains(File(mediaFile.filePath).extension.lowercase())

                        Card(
                            modifier = Modifier.aspectRatio(1f),
                            shape = RoundedCornerShape(8.dp),
                            onClick = { viewModel.onEvent(ImageUiEvent.SelectImage(mediaFile)) }
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                AsyncImage(
                                    model = mediaFile.filePath,
                                    contentDescription = mediaFile.type,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )

                                if (isVideo) {
                                    Icon(
                                        imageVector = Icons.Default.PlayCircleOutline,
                                        contentDescription = stringResource(R.string.play_video),
                                        tint = Color.White.copy(alpha = 0.8f), // 使用带透明度的白色
                                        modifier = Modifier
                                            .align(Alignment.Center)
                                            .size(48.dp) // 给图标一个合适的大小
                                    )
                                }

                                Text(
                                    text = formatFileSize(mediaFile.size), // 使用辅助函数格式化大小
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    textAlign = TextAlign.End,
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(8.dp)
                                        .background(
                                            Color.Black.copy(alpha = 0.3f), // 加个半透明背景，确保文字清晰
                                            RoundedCornerShape(4.dp)
                                        )
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    uiState.selectedImageForZoom?.let { mediaFile ->
        MediaPreviewDialog(
            mediaFile = mediaFile,
            onDismiss = { viewModel.onEvent(ImageUiEvent.DismissImage) }
        )
    }
}

@Composable
private fun SyncStatusHeader(
    progress: Float,
    currentFileIndex: Int,
    totalFiles: Int,
    isSyncing: Boolean,
    speed: String,
    onSyncClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {

        Box(
            modifier = Modifier
                .weight(1f)
        ) {

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(22.dp)
                    .clip(RoundedCornerShape(6.dp)),
            )

            Text(
                text = speed,
                fontSize = 14.sp,
                color = Color.White,
                modifier = Modifier.align(Alignment.Center),
                fontWeight = FontWeight.Medium
            )


        }



        Spacer(modifier = Modifier.width(12.dp))

        if (isSyncing) {
            Text(
                text = "$currentFileIndex/$totalFiles",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        } else {
            Button(
                onClick = onSyncClick,
                enabled = !isSyncing,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(stringResource(R.string.sync))
            }
        }
    }
}

@Composable
private fun MediaPreviewDialog(mediaFile: MediaFilesEntity, onDismiss: () -> Unit) {
    val videoExtensions = setOf("mp4", "mov", "avi", "mkv", "webm")
    val isVideo = videoExtensions.contains(File(mediaFile.filePath).extension.lowercase())

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false) // 全屏
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            if (isVideo) {
                VideoPlayer(videoPath = mediaFile.filePath)
            } else {
                ZoomableImage(imagePath = mediaFile.filePath)
            }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(UnstableApi::class)
@Composable
private fun VideoPlayer(videoPath: String) {
    val context = LocalContext.current

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true // 准备好后自动播放
        }
    }

    LaunchedEffect(videoPath) {
        val videoUri = Uri.fromFile(File(videoPath))
        val mediaItem = MediaItem.fromUri(videoUri)
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        }
    )
}

/**
 * 用于显示和处理可缩放、可平移的图片。
 */
@Composable
private fun ZoomableImage(imagePath: String) {
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = kotlin.math.max(1f, scale * zoom)
                    // 限制图片移动边界，防止移出屏幕
                    val boundsX = (size.width * (scale - 1)) / 2f
                    val boundsY = (size.height * (scale - 1)) / 2f
                    offsetX = (offsetX + pan.x).coerceIn(-boundsX, boundsX)
                    offsetY = (offsetY + pan.y).coerceIn(-boundsY, boundsY)
                }
            }
    ) {
        AsyncImage(
            model = imagePath,
            contentDescription = stringResource(R.string.zoomable_image),
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY
                )
        )
    }
}

private fun formatFileSize(sizeInBytes: Long): String {
    if (sizeInBytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (log10(sizeInBytes.toDouble()) / log10(1024.0)).toInt()
    return String.format("%.1f %s", sizeInBytes / 1024.0.pow(digitGroups.toDouble()), units[digitGroups])
}
