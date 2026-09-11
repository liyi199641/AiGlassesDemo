package com.lw.ai.glasses.ui.image

import BaseViewModel
import androidx.lifecycle.viewModelScope
import com.lw.ai.glasses.state.MediaSyncStateManager
import com.lw.top.lib_core.data.repository.PhotoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import jakarta.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class ImageViewModel @Inject constructor(
    private val photoRepository: PhotoRepository,
    private val mediaSyncStateManager: MediaSyncStateManager,
) : BaseViewModel() {

    private val _uiEvents = MutableStateFlow<ImageUiEvent>(ImageUiEvent.None)

    val uiState: StateFlow<ImageUiState> = combine(
        photoRepository.getSyncedPhotosFlow(),
        mediaSyncStateManager.state,
        _uiEvents,
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
            selectedImageForZoom = newSelectedImage,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = ImageUiState(),
    )

    fun onEvent(event: ImageUiEvent) {
        _uiEvents.value = event
        if (event is ImageUiEvent.SelectImage || event is ImageUiEvent.DismissImage) {
            _uiEvents.value = ImageUiEvent.None
        }
    }

    fun clearAllPhotos() {
        viewModelScope.launch {
            photoRepository.clearAllPhotos()
        }
    }

    fun refreshPendingMediaCount() {
        mediaSyncStateManager.refreshPendingMediaCount()
    }

    fun syncAllMediaFile() {
        mediaSyncStateManager.syncAllMediaFile()
    }
}
