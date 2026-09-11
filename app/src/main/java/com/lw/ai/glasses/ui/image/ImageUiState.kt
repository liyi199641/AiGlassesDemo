package com.lw.ai.glasses.ui.image

import com.lw.ai.glasses.state.MediaSyncState
import com.lw.top.lib_core.data.local.entity.MediaFilesEntity

data class ImageUiState(
    val images: List<MediaFilesEntity> = emptyList(),
    val syncState: MediaSyncState = MediaSyncState(),
    val selectedImageForZoom: MediaFilesEntity? = null,
)
