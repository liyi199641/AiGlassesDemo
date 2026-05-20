package com.lw.ai.glasses.ui.update

import BaseViewModel
import android.content.Context
import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.fission.wear.glasses.sdk.GlassesManage
import com.fission.wear.glasses.sdk.constant.GlassesConstant
import com.fission.wear.glasses.sdk.events.OTAEvent
import com.lw.ai.glasses.R
import com.lw.ai.glasses.utils.getFileNameFromUri
import com.lw.top.lib_core.data.repository.UpdateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

// 临时的模拟OtaState
sealed class OtaState {
    data object Idle : OtaState()
    data class InProgress(val progress: Int) : OtaState()
    data object Success : OtaState()
    data class Error(val message: String) : OtaState()
}

@HiltViewModel
class UpdateViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val updateRepository: UpdateRepository
) : BaseViewModel() {

    private val _uiState = MutableStateFlow(UpdateUiState())
    val uiState = _uiState.asStateFlow()

    companion object {
        private const val MAX_RECENT_FILES = 3
    }

    init {
        _uiState.update {
            it.copy(statusText = context.getString(R.string.ota_status_choose_or_add))
        }
        loadRecentFiles()
        observeOtaState()
    }

    private fun loadRecentFiles() {
        viewModelScope.launch {
            // 1. 从Repository获取JSON字符串
            val filesJson = updateRepository.getRecentFilesJson()
            // 2. 在ViewModel中反序列化为模型对象
            val recentFiles = filesJson.mapNotNull { FirmwareFile.fromJson(it) }
                .sortedByDescending { it.addedTime }

            _uiState.update {
                it.copy(
                    recentFiles = recentFiles,
                    selectedFileId = recentFiles.firstOrNull()?.id
                )
            }
        }
    }

    fun onFileAdded(uri: Uri?, context: Context) {
        if (uri == null) return

        viewModelScope.launch {
            copyFileToCache(context, uri)?.let { newFile ->
                _uiState.update { currentState ->
                    val updatedList = (listOf(newFile) + currentState.recentFiles)
                        .distinctBy { it.id }
                        .sortedByDescending { it.addedTime }
                        .take(MAX_RECENT_FILES)

                    // 在协程中保存
                    launch {
                        // 3. 将模型对象序列化为JSON字符串，再传给Repository
                        val filesJson = updatedList.map { it.toJson() }.toSet()
                        updateRepository.saveRecentFilesJson(filesJson)
                    }

                    currentState.copy(
                        recentFiles = updatedList,
                        selectedFileId = newFile.id,
                        otaStatus = OtaStatus.READY_TO_UPGRADE,
                        statusText = context.getString(R.string.ota_file_ready, newFile.name)
                    )
                }
            }
        }
    }

    fun onFileSelectionChanged(fileId: String) {
        _uiState.update { currentState ->
            currentState.copy(
                selectedFileId = fileId,
                otaStatus = OtaStatus.READY_TO_UPGRADE,
                statusText = context.getString(
                    R.string.ota_file_ready,
                    currentState.recentFiles.find { it.id == fileId }?.name.orEmpty()
                )
            )
        }
    }

    fun onOtaTypeChanged(otaType: GlassesConstant.OtaType) {
        _uiState.update { it.copy(selectedOtaType = otaType) }
    }

    fun startOtaUpgrade() {
        val currentState = _uiState.value
        val selectedId = currentState.selectedFileId ?: run {
            _uiState.update { it.copy(statusText = context.getString(R.string.ota_error_choose_file)) }
            return
        }

        val fileToUpgrade = currentState.recentFiles.find { it.id == selectedId }
        if (fileToUpgrade != null) {
            // 【关键修改】从UI状态中获取当前选中的OTA类型
            val selectedType = currentState.selectedOtaType

            // 使用选中的文件路径和OTA类型来启动升级
            GlassesManage.startOTA(fileToUpgrade.path, selectedType, "3.20.1.32")

            _uiState.update {
                it.copy(
                    otaStatus = OtaStatus.UPGRADING,
                    statusText = context.getString(R.string.ota_prepare_upgrade, fileToUpgrade.name)
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    otaStatus = OtaStatus.FAILED,
                    statusText = context.getString(R.string.ota_error_file_not_found)
                )
            }
        }
    }

    private fun copyFileToCache(context: Context, uri: Uri): FirmwareFile? {
        return try {
            val fileName = getFileNameFromUri(context, uri)
            val destinationFile = File(context.cacheDir, fileName)

            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                FileOutputStream(destinationFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }

            val sizeInMb = destinationFile.length() / (1024f * 1024f)

            FirmwareFile(
                id = destinationFile.absolutePath,
                name = fileName,
                path = destinationFile.absolutePath,
                sizeInMb = sizeInMb,
                addedTime = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun observeOtaState() {
        viewModelScope.launch {
            GlassesManage.eventFlow().collect { events ->
                _uiState.update { currentState ->
                    when (events) {
                        is OTAEvent.Start -> currentState.copy(
                            otaStatus = OtaStatus.UPGRADING,
                            statusText = context.getString(R.string.ota_started),
                            progress = 0
                        )

                        is OTAEvent.Progress -> {
                            val stageText = if (events.type == GlassesConstant.OTAStage.VERIFY) {
                                context.getString(R.string.ota_verifying)
                            } else {
                                context.getString(R.string.ota_upgrading)
                            }
                            currentState.copy(
                                otaStatus = OtaStatus.UPGRADING,
                                statusText = context.getString(R.string.ota_progress, stageText, events.percent),
                                progress = events.percent
                            )
                        }

                        is OTAEvent.DeviceRebooting -> currentState.copy(
                            otaStatus = OtaStatus.UPGRADING,
                            statusText = context.getString(R.string.ota_waiting_reboot)
                        )

                        is OTAEvent.Success -> currentState.copy(
                            otaStatus = OtaStatus.SUCCESS,
                            statusText = context.getString(R.string.ota_success),
                            progress = 100
                        )

                        is OTAEvent.Failed -> currentState.copy(
                            otaStatus = OtaStatus.FAILED,
                            statusText = context.getString(R.string.ota_failed, events.reason)
                        )

                        is OTAEvent.Cancelled -> currentState.copy(
                            otaStatus = OtaStatus.IDLE,
                            statusText = context.getString(R.string.ota_cancelled),
                            progress = 0
                        )

                        is OTAEvent.Idle -> currentState.copy(
                            otaStatus = OtaStatus.IDLE,
                            statusText = context.getString(R.string.ota_idle),
                            progress = 0
                        )

                        else -> currentState
                    }
                }

            }
        }

    }

}
