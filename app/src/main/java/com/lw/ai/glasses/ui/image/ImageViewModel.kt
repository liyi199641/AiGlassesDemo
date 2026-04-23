package com.lw.ai.glasses.ui.image

import BaseViewModel
import androidx.lifecycle.viewModelScope
import com.blankj.utilcode.util.ToastUtils
import com.fission.wear.glasses.sdk.GlassesManage
import com.fission.wear.glasses.sdk.constant.GlassesConstant
import com.fission.wear.glasses.sdk.events.FileSyncEvent
import com.lw.top.lib_core.data.local.entity.MediaFilesEntity
import com.lw.top.lib_core.data.repository.PhotoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import jakarta.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class ImageViewModel @Inject constructor(
    private val photoRepository: PhotoRepository
) : BaseViewModel() {

    private val _syncState = MutableStateFlow(SyncState())
    private val _uiEvents = MutableStateFlow<ImageUiEvent>(ImageUiEvent.None)

    val uiState: StateFlow<ImageUiState> = combine(
        photoRepository.getSyncedPhotosFlow(),
        _syncState,
        _uiEvents
    ) { photos, syncState, event ->
        val currentSelected = uiState.value.selectedImageForZoom
        val newSelectedImage = when (event) {
            is ImageUiEvent.SelectImage -> event.image
            is ImageUiEvent.DismissImage -> null
            ImageUiEvent.None -> currentSelected
        }

        ImageUiState(
            images = photos,
            syncState = syncState,
            selectedImageForZoom = newSelectedImage
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = ImageUiState()
    )

    init {
        observeGlassesEvents()
    }

    fun onEvent(event: ImageUiEvent) {
        _uiEvents.value = event
        if (event is ImageUiEvent.SelectImage || event is ImageUiEvent.DismissImage) {
            _uiEvents.value = ImageUiEvent.None
        }
    }


    fun clearAllPhotos() {//App 清理的时候。根据文件地址路径。清除下缓存
        viewModelScope.launch {
            photoRepository.clearAllPhotos()
        }
    }

    fun syncAllMediaFile() {
        if (_syncState.value.isSyncing) {
            ToastUtils.showLong("文件正在同步中")
            return
        }
        _syncState.value = SyncState(isSyncing = true)
        GlassesManage.syncAllMediaFile(GlassesConstant.WifiMode.P2P_MODE)
    }

    fun observeGlassesEvents() {
        viewModelScope.launch {
            GlassesManage.eventFlow().collect { events ->
                when (events) {

                    is FileSyncEvent.ConnectSuccess -> {

                    }

                    is FileSyncEvent.DownloadProgress -> {
                        _syncState.update {
                            it.copy(
                                syncProgress = events.progress / 100f,
                                currentFileIndex = events.curFileIndex,
                                totalFilesToSync = events.totalFileCount,
                                speed = events.speed,
                            )
                        }
                    }

                    is FileSyncEvent.DownloadSuccess -> {
                        val newFileEntity = MediaFilesEntity(
                            filePath = events.filePath,
                            type = "IMAGE",
                            createdAt = System.currentTimeMillis(),
                            size = events.fileSizeInBytes,
                        )
                        photoRepository.insertPhoto(newFileEntity)
                        val isLastFile = (events.curFileIndex + 1) == events.totalFileCount
                        if (isLastFile) {
                            _syncState.value = SyncState()
                        }
                    }

                    is FileSyncEvent.Failed -> {
                        _syncState.value = SyncState()
                    }

                    else -> {

                    }
                }
            }
        }
    }

}