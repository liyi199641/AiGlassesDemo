@file:Suppress("OPT_IN_ARGUMENT_IS_NOT_MARKER")

package com.lw.ai.glasses.ui.image

import TextRed
import TextRedBackground
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
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PauseCircleOutline
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import com.lw.ai.glasses.state.MediaSyncPreviewItem
import com.lw.top.lib_core.data.local.entity.MediaFilesEntity
import java.io.File
import java.text.DateFormat
import java.util.Date
import kotlin.math.log10
import kotlin.math.pow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageScreen(
    viewModel: ImageViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.refreshPendingMediaCount()
    }

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
                pendingDeviceMediaCount = uiState.syncState.pendingDeviceMediaCount,
                onSyncClick = { viewModel.syncAllMediaFile() },
            )

            uiState.syncState.errorMessage?.takeIf { !uiState.syncState.isSyncing }?.let { message ->
                Spacer(modifier = Modifier.height(8.dp))
                SyncErrorBubble(message = message)
            }

            Spacer(modifier = Modifier.height(16.dp))

            val previewItems = uiState.syncState.previewItems
            val hasPreviewItems = previewItems.isNotEmpty()
            val showEmptyHint = uiState.images.isEmpty() &&
                !hasPreviewItems &&
                !uiState.syncState.isSyncing

            if (showEmptyHint) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(stringResource(R.string.empty_images_hint))
                }
            } else {
                val previewLocalPaths = remember(previewItems) {
                    previewItems.mapNotNull { it.filePath }.toSet()
                }
                val groupedMedia = remember(uiState.images, previewLocalPaths) {
                    uiState.images
                        .filter { it.filePath !in previewLocalPaths }
                        .groupBy { MediaFileUtils.startOfDay(it.createdAt) }
                        .entries
                        .sortedByDescending { it.key }
                }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 16.dp),
                ) {
                    if (hasPreviewItems) {
                        item(key = "sync-preview-header", span = { GridItemSpan(2) }) {
                            Text(
                                text = stringResource(R.string.sync_in_progress_section),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp, bottom = 4.dp),
                            )
                        }
                        items(items = previewItems, key = { it.fpath }) { previewItem ->
                            SyncPreviewGridItem(
                                item = previewItem,
                                isActiveDownload = uiState.syncState.isSyncing &&
                                    !previewItem.isDownloaded &&
                                    uiState.syncState.currentFileIndex == previewItem.index + 1,
                                onOpenDownloaded = { mediaFile ->
                                    viewModel.onEvent(ImageUiEvent.SelectImage(mediaFile))
                                },
                            )
                        }
                    }

                    groupedMedia.forEach { (dayStart, dayItems) ->
                        item(key = "header-$dayStart", span = { GridItemSpan(2) }) {
                            DateSectionHeader(dayStartMillis = dayStart)
                        }
                        items(items = dayItems, key = { it.id }) { mediaFile ->
                            MediaGridItem(
                                mediaFile = mediaFile,
                                onClick = { viewModel.onEvent(ImageUiEvent.SelectImage(mediaFile)) },
                            )
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
private fun SyncPreviewGridItem(
    item: MediaSyncPreviewItem,
    isActiveDownload: Boolean,
    onOpenDownloaded: (MediaFilesEntity) -> Unit,
) {
    val mediaType = MediaFileUtils.typeOf(item.fpath)
    val canOpen = item.isDownloaded && !item.filePath.isNullOrBlank()

    if (canOpen) {
        Card(
            modifier = Modifier.aspectRatio(1f),
            shape = RoundedCornerShape(8.dp),
            onClick = {
                onOpenDownloaded(
                    MediaFilesEntity(
                        filePath = item.filePath!!,
                        type = MediaFileUtils.typeString(item.filePath),
                        createdAt = MediaFileUtils.resolveCreatedAt(
                            item.filePath,
                            item.fileModifiedTime,
                        ),
                        size = item.fileSize,
                    ),
                )
            },
        ) {
            SyncPreviewGridContent(
                item = item,
                mediaType = mediaType,
                useDownloadedFile = true,
                isActiveDownload = false,
            )
        }
    } else {
        Card(
            modifier = Modifier.aspectRatio(1f),
            shape = RoundedCornerShape(8.dp),
        ) {
            SyncPreviewGridContent(
                item = item,
                mediaType = mediaType,
                useDownloadedFile = false,
                isActiveDownload = isActiveDownload,
            )
        }
    }
}

@Composable
private fun SyncPreviewGridContent(
    item: MediaSyncPreviewItem,
    mediaType: MediaFileType,
    useDownloadedFile: Boolean,
    isActiveDownload: Boolean,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        when {
            useDownloadedFile && mediaType == MediaFileType.AUDIO -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF37474F)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.VolumeUp,
                        contentDescription = stringResource(R.string.play_audio),
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(48.dp),
                    )
                }
            }

            useDownloadedFile -> {
                AsyncImage(
                    model = item.filePath,
                    contentDescription = item.fpath,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }

            mediaType == MediaFileType.AUDIO -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF37474F)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.VolumeUp,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(48.dp),
                    )
                }
            }

            else -> {
                AsyncImage(
                    model = item.thumbnailUrl,
                    contentDescription = item.fpath,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }

        if (!useDownloadedFile) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center,
            ) {
                if (isActiveDownload) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        color = Color.White,
                        strokeWidth = 3.dp,
                    )
                }
            }
        }

        if (mediaType == MediaFileType.VIDEO) {
            Icon(
                imageVector = Icons.Default.PlayCircleOutline,
                contentDescription = stringResource(R.string.play_video),
                tint = Color.White.copy(alpha = 0.8f),
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(48.dp),
            )
        }

        if (useDownloadedFile && item.fileSize > 0) {
            Text(
                text = formatFileSize(item.fileSize),
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.End,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .background(
                        Color.Black.copy(alpha = 0.3f),
                        RoundedCornerShape(4.dp),
                    )
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun DateSectionHeader(dayStartMillis: Long) {
    val todayStart = remember { MediaFileUtils.startOfDay(System.currentTimeMillis()) }
    val yesterdayStart = todayStart - 24 * 60 * 60 * 1000
    val formattedDate = remember(dayStartMillis) {
        DateFormat.getDateInstance(DateFormat.MEDIUM, java.util.Locale.getDefault())
            .format(Date(dayStartMillis))
    }
    val label = when (dayStartMillis) {
        todayStart -> stringResource(R.string.date_today)
        yesterdayStart -> stringResource(R.string.date_yesterday)
        else -> formattedDate
    }

    Text(
        text = label,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp),
    )
}

@Composable
private fun MediaGridItem(
    mediaFile: MediaFilesEntity,
    onClick: () -> Unit,
) {
    val mediaType = MediaFileUtils.typeOf(mediaFile.filePath)

    Card(
        modifier = Modifier.aspectRatio(1f),
        shape = RoundedCornerShape(8.dp),
        onClick = onClick,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            when (mediaType) {
                MediaFileType.AUDIO -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF37474F)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = stringResource(R.string.play_audio),
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(48.dp),
                        )
                    }
                }

                else -> {
                    AsyncImage(
                        model = mediaFile.filePath,
                        contentDescription = mediaFile.type,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
            }

            if (mediaType == MediaFileType.VIDEO) {
                Icon(
                    imageVector = Icons.Default.PlayCircleOutline,
                    contentDescription = stringResource(R.string.play_video),
                    tint = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(48.dp),
                )
            }

            Text(
                text = formatFileSize(mediaFile.size),
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.End,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .background(
                        Color.Black.copy(alpha = 0.3f),
                        RoundedCornerShape(4.dp),
                    )
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun SyncErrorBubble(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = TextRedBackground,
    ) {
        Text(
            text = message,
            color = TextRed,
            fontSize = 14.sp,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
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
    pendingDeviceMediaCount: Int?,
    onSyncClick: () -> Unit,
) {
    val showSyncButton = !isSyncing && pendingDeviceMediaCount != null && pendingDeviceMediaCount > 0

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(
                modifier = Modifier.weight(1f),
            ) {
                if (isSyncing) {
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
                        fontWeight = FontWeight.Medium,
                    )
                } else {
                    Text(
                        text = when (pendingDeviceMediaCount) {
                            null -> stringResource(R.string.pending_media_count_checking)
                            0 -> stringResource(R.string.no_pending_media)
                            else -> stringResource(
                                R.string.pending_media_count,
                                pendingDeviceMediaCount,
                            )
                        },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF424242),
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            when {
                isSyncing -> {
                    Text(
                        text = "$currentFileIndex/$totalFiles",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }

                showSyncButton -> {
                    Button(
                        onClick = onSyncClick,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Text(stringResource(R.string.sync))
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaPreviewDialog(mediaFile: MediaFilesEntity, onDismiss: () -> Unit) {
    val mediaType = MediaFileUtils.typeOf(mediaFile.filePath)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            when (mediaType) {
                MediaFileType.VIDEO -> VideoPlayer(videoPath = mediaFile.filePath)
                MediaFileType.AUDIO -> AudioPlayer(audioPath = mediaFile.filePath)
                else -> ZoomableImage(imagePath = mediaFile.filePath)
            }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(UnstableApi::class)
@Composable
private fun AudioPlayer(audioPath: String) {
    val context = LocalContext.current
    var isPlaying by remember(audioPath) { mutableStateOf(true) }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
        }
    }

    LaunchedEffect(audioPath) {
        val audioUri = Uri.fromFile(File(audioPath))
        exoPlayer.setMediaItem(MediaItem.fromUri(audioUri))
        exoPlayer.prepare()
        isPlaying = true
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
        modifier = Modifier.padding(horizontal = 32.dp),
    ) {
        Icon(
            imageVector = Icons.Default.VolumeUp,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.9f),
            modifier = Modifier.size(72.dp),
        )

        Text(
            text = File(audioPath).name,
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )

        IconButton(
            onClick = {
                if (exoPlayer.isPlaying) {
                    exoPlayer.pause()
                    isPlaying = false
                } else {
                    exoPlayer.play()
                    isPlaying = true
                }
            },
            modifier = Modifier.size(72.dp),
        ) {
            Icon(
                imageVector = if (isPlaying) {
                    Icons.Default.PauseCircleOutline
                } else {
                    Icons.Default.PlayCircleOutline
                },
                contentDescription = stringResource(
                    if (isPlaying) R.string.pause_audio else R.string.play_audio,
                ),
                tint = Color.White,
                modifier = Modifier.fillMaxSize(),
            )
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
