package com.lw.ai.glasses.ui.update

import BaseViewModel
import android.content.Context
import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.fission.wear.glasses.sdk.GlassesManage
import com.fission.wear.glasses.sdk.constant.GlassesConstant
import com.fission.wear.glasses.sdk.events.CmdResultEvent
import com.fission.wear.glasses.sdk.events.OTAEvent
import com.lw.ai.glasses.R
import com.lw.ai.glasses.config.SdkChannelResolver
import com.lw.ai.glasses.utils.SdkErrorMessages
import com.lw.ai.glasses.utils.getFileNameFromUri
import com.lw.top.lib_core.data.datastore.AppDataManager
import com.lw.top.lib_core.data.datastore.BluetoothDataManager
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

@HiltViewModel
class UpdateViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val updateRepository: UpdateRepository,
    private val appDataManager: AppDataManager,
    private val bluetoothDataManager: BluetoothDataManager,
) : BaseViewModel() {

    private val _uiState = MutableStateFlow(UpdateUiState())
    val uiState = _uiState.asStateFlow()

    companion object {
        private const val MAX_RECENT_FILES = 3
        /** 匹配可选 v/V 前缀的三/四段式版本号，如 v3.3.7 / 1.4.2 / 3.20.1.53 */
        private val FIRMWARE_VERSION_REGEX = Regex("""[vV]?(\d+\.\d+\.\d+(?:\.\d+)?)""")
    }

    init {
        _uiState.update {
            it.copy(statusText = context.getString(R.string.ota_status_choose_or_add))
        }
        loadSdkChannel()
        loadRecentFiles()
        observeGlassesEvents()
        requestDeviceVersionInfo()
    }

    /** 每次进入固件升级页时刷新设备版本信息。 */
    fun onScreenVisible() {
        requestDeviceVersionInfo()
    }

    private fun requestDeviceVersionInfo() {
        GlassesManage.requestDeviceVersionInfo()
    }

    private fun loadSdkChannel() {
        viewModelScope.launch {
            val channel = SdkChannelResolver.loadForSdkInit(bluetoothDataManager, appDataManager)
            _uiState.update { it.copy(currentChannel = channel) }
        }
    }

    private fun loadRecentFiles() {
        viewModelScope.launch {
            // 1. 从Repository获取JSON字符串
            val filesJson = updateRepository.getRecentFilesJson()
            // 2. 在ViewModel中反序列化为模型对象
            val recentFiles = filesJson.mapNotNull { FirmwareFile.fromJson(it) }
                .sortedByDescending { it.addedTime }

            _uiState.update {
                val selectedId = recentFiles.firstOrNull()?.id
                it.copy(
                    recentFiles = recentFiles,
                    selectedFileId = selectedId,
                    firmwareVersion = recentFiles.firstOrNull()?.name
                        ?.let(::extractFirmwareVersion)
                        .orEmpty()
                )
            }
        }
    }

    fun onFileAdded(uri: Uri?, context: Context) {
        if (uri == null) return

        viewModelScope.launch {
            copyFileToCache(context, uri)?.let { newFile ->
                addRecentEntry(newFile, context)
            }
        }
    }

    /** RTK WiFi 升级：选择压缩包（内含 ota.json），复制到本地缓存后加入列表，由 SDK 负责解压。 */
    fun onWifiZipAdded(uri: Uri?, context: Context) {
        if (uri == null) return

        viewModelScope.launch {
            copyFileToCache(context, uri)?.copy(isWifiZip = true)?.let { newZip ->
                addRecentEntry(newZip, context)
            }
        }
    }

    private fun addRecentEntry(newEntry: FirmwareFile, context: Context) {
        _uiState.update { currentState ->
            val updatedList = (listOf(newEntry) + currentState.recentFiles)
                .distinctBy { it.id }
                .sortedByDescending { it.addedTime }
                .take(MAX_RECENT_FILES)

            // 在协程中保存
            viewModelScope.launch {
                val filesJson = updatedList.map { it.toJson() }.toSet()
                updateRepository.saveRecentFilesJson(filesJson)
            }

            val readyText = if (newEntry.isWifiZip) {
                context.getString(R.string.ota_zip_ready, newEntry.name)
            } else {
                context.getString(R.string.ota_file_ready, newEntry.name)
            }
            currentState.copy(
                recentFiles = updatedList,
                selectedFileId = if (newEntry.isWifiZip) currentState.selectedFileId else newEntry.id,
                selectedWifiZipId = if (newEntry.isWifiZip) newEntry.id else currentState.selectedWifiZipId,
                firmwareVersion = if (newEntry.isWifiZip) currentState.firmwareVersion
                else extractFirmwareVersion(newEntry.name),
                otaStatus = OtaStatus.READY_TO_UPGRADE,
                statusText = readyText
            )
        }
    }

    fun onFileSelectionChanged(fileId: String) {
        _uiState.update { currentState ->
            val selected = currentState.recentFiles.find { it.id == fileId } ?: return@update currentState
            if (selected.isWifiZip) {
                // RTK WiFi 压缩包：单独记录选中状态，不影响 BT 文件选择
                currentState.copy(
                    selectedWifiZipId = fileId,
                    otaStatus = OtaStatus.READY_TO_UPGRADE,
                    statusText = context.getString(R.string.ota_zip_ready, selected.name)
                )
            } else {
                currentState.copy(
                    selectedFileId = fileId,
                    firmwareVersion = extractFirmwareVersion(selected.name),
                    otaStatus = OtaStatus.READY_TO_UPGRADE,
                    statusText = context.getString(R.string.ota_file_ready, selected.name)
                )
            }
        }
    }

    fun onOtaTypeChanged(otaType: GlassesConstant.OtaType) {
        _uiState.update { it.copy(selectedOtaType = otaType) }
    }

    fun onFirmwareVersionChanged(version: String) {
        _uiState.update { it.copy(firmwareVersion = version) }
    }

    fun startOtaUpgrade() {
        val currentState = _uiState.value
        if (currentState.isRtk) {
            startRtkOtaUpgrade(currentState)
            return
        }

        val selectedId = currentState.selectedFileId ?: run {
            _uiState.update { it.copy(statusText = context.getString(R.string.ota_error_choose_file)) }
            return
        }

        val fileToUpgrade = currentState.recentFiles.find { it.id == selectedId }
        if (fileToUpgrade != null) {
            val selectedType = currentState.selectedOtaType
            val version = currentState.firmwareVersion.trim().ifBlank {
                extractFirmwareVersion(fileToUpgrade.name)
            }
            GlassesManage.startOTA(fileToUpgrade.path, selectedType, version)

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

    /**
     * RTK 方案升级：BT 文件 + WiFi 压缩包可同时升级，任一未选则仅升级另一种。
     */
    private fun startRtkOtaUpgrade(state: UpdateUiState) {
        val btFile = state.recentFiles.find { it.id == state.selectedFileId && !it.isWifiZip }
        val wifiZip = state.recentFiles.find { it.id == state.selectedWifiZipId && it.isWifiZip }
        if (btFile == null && wifiZip == null) {
            _uiState.update { it.copy(statusText = context.getString(R.string.ota_error_choose_file)) }
            return
        }

        val btVersion = state.firmwareVersion.trim().ifBlank {
            btFile?.name?.let(::extractFirmwareVersion).orEmpty()
        }
        GlassesManage.startRtkOta(
            btPath = btFile?.path,
            btVersion = btVersion.ifBlank { null },
            wifiZipPath = wifiZip?.path
        )

        val preparingName = buildList {
            btFile?.let { add(it.name) }
            wifiZip?.let { add(it.name) }
        }.joinToString(" + ")
        _uiState.update {
            it.copy(
                otaStatus = OtaStatus.UPGRADING,
                statusText = context.getString(R.string.ota_prepare_upgrade, preparingName)
            )
        }
    }

    /**
     * 从固件文件名中解析版本号（不含 v 前缀），解析失败返回空字符串。
     *
     * 示例：
     * - `tk8-ag19-isp-v3.3.7-no-marge-0803-20260806.bin` → `3.3.7`
     * - `tk8-ag19-isp-3.3.7-no-marge-0803-20260806.bin` → `3.3.7`
     * - `s3-AG66-bt-1.3.8-b4717b49-20260316.ufw` → `1.3.8`
     * - `s3-lingwei-ag66-bt-1.4.2-fb6c74d9-20260423.ufw` → `1.4.2`
     */
    fun extractFirmwareVersion(fileName: String): String {
        val name = fileName.substringAfterLast('/').substringBeforeLast('.')
        val match = FIRMWARE_VERSION_REGEX.find(name) ?: return ""
        return match.groupValues[1]
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

    private fun observeGlassesEvents() {
        viewModelScope.launch {
            GlassesManage.eventFlow().collect { events ->
                when (events) {
                    is CmdResultEvent.DeviceVersionInfoEvent -> {
                        _uiState.update { it.copy(deviceVersionInfo = events.data) }
                    }

                    else -> _uiState.update { currentState ->
                        mapOtaEvent(currentState, events)
                    }
                }
            }
        }
    }

    private fun mapOtaEvent(currentState: UpdateUiState, events: Any): UpdateUiState {
        return when (events) {
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

            is OTAEvent.Success -> {
                requestDeviceVersionInfo()
                currentState.copy(
                    otaStatus = OtaStatus.SUCCESS,
                    statusText = context.getString(R.string.ota_success),
                    progress = 100
                )
            }

            is OTAEvent.Failed -> {
                val message = SdkErrorMessages.forOta(
                    context,
                    events.code,
                    events.reason,
                )
                currentState.copy(
                    otaStatus = OtaStatus.FAILED,
                    statusText = context.getString(R.string.ota_failed, message),
                )
            }

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
