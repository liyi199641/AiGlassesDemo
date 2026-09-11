package com.lw.ai.glasses.state

import android.content.Context
import com.blankj.utilcode.util.LogUtils
import com.blankj.utilcode.util.ToastUtils
import com.fission.wear.glasses.sdk.GlassesManage
import com.fission.wear.glasses.sdk.constant.GlassesConstant
import com.fission.wear.glasses.sdk.constant.GlassesConstant.ActionSyncType
import com.fission.wear.glasses.sdk.events.CmdResultEvent
import com.fission.wear.glasses.sdk.events.ConnectionStateEvent
import com.fission.wear.glasses.sdk.events.FileSyncEvent
import com.lw.ai.glasses.R
import com.lw.ai.glasses.config.SdkChannelResolver
import com.lw.ai.glasses.ui.image.MediaFileUtils
import com.lw.ai.glasses.utils.SdkErrorMessages
import com.lw.top.lib_core.data.datastore.AppDataManager
import com.lw.top.lib_core.data.datastore.BluetoothDataManager
import com.lw.top.lib_core.data.local.entity.MediaFilesEntity
import com.lw.top.lib_core.data.repository.PhotoRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 同步过程中的预览项；[fpath] 与 SDK [FileSyncEvent.DownloadSuccess.fpath] 对应。 */
data class MediaSyncPreviewItem(
    val fpath: String,
    val index: Int,
    val thumbnailUrl: String,
    val filePath: String? = null,
    val fileSize: Long = 0,
    val fileModifiedTime: String = "",
    val isDownloaded: Boolean = false,
)

data class MediaSyncState(
    val isSyncing: Boolean = false,
    val syncProgress: Float = 0.0f,
    val currentFileIndex: Int = 0,
    val totalFilesToSync: Int = 0,
    val speed: String = "0 KB/s",
    val errorMessage: String? = null,
    val previewItems: List<MediaSyncPreviewItem> = emptyList(),
    /** 眼镜端待同步媒体数量；null 表示尚未查询或未连接。 */
    val pendingDeviceMediaCount: Int? = null,
)

/**
 * 全局媒体同步状态：应用启动后订阅 [FileSyncEvent] 与 [CmdResultEvent.ActionSync]，
 * 不依赖同步页生命周期；设备退出导入模式时同步结束 UI 状态。
 */
@Singleton
class MediaSyncStateManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val photoRepository: PhotoRepository,
    private val bluetoothDataManager: BluetoothDataManager,
    private val appDataManager: AppDataManager,
) {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val started = AtomicBoolean(false)
    @Volatile
    private var savedSdkChannel: GlassesConstant.ChannelType = SdkChannelResolver.defaultChannel

    private val _state = MutableStateFlow(MediaSyncState())
    val state: StateFlow<MediaSyncState> = _state.asStateFlow()

    fun start() {
        if (!started.compareAndSet(false, true)) return
        refreshSavedSdkChannel()
        observeFileSyncEvents()
    }

    private fun refreshSavedSdkChannel() {
        appScope.launch {
            savedSdkChannel = SdkChannelResolver.loadForSdkInit(
                bluetoothDataManager,
                appDataManager,
            )
        }
    }

    fun refreshPendingMediaCount() {
        refreshSavedSdkChannel()
        if (!GlassesManage.currentConnectionState().isBleConnected) {
            _state.update { it.copy(pendingDeviceMediaCount = null) }
            return
        }
        GlassesManage.getMediaFileCount()
    }

    fun syncAllMediaFile() {
        refreshSavedSdkChannel()
        if (_state.value.isSyncing) {
            ToastUtils.showLong(context.getString(R.string.file_syncing))
            return
        }
        val pending = _state.value.pendingDeviceMediaCount
        if (pending != null && pending <= 0) {
            return
        }
        _state.update {
            it.copy(
                isSyncing = true,
                errorMessage = null,
                syncProgress = 0f,
                currentFileIndex = 0,
                totalFilesToSync = 0,
                speed = "0 KB/s",
                previewItems = emptyList(),
            )
        }
        GlassesManage.syncAllMediaFile()
    }

    private fun observeFileSyncEvents() {
        appScope.launch {
            GlassesManage.eventFlow().collect { events ->
                when (events) {
                    is ConnectionStateEvent.Connected,
                    is ConnectionStateEvent.DeviceReady -> {
                        refreshSavedSdkChannel()
                        refreshPendingMediaCount()
                    }

                    is ConnectionStateEvent.Disconnected -> {
                        _state.update { it.copy(pendingDeviceMediaCount = null) }
                        if (_state.value.isSyncing) {
                            resetSyncState(context.getString(R.string.file_sync_failed_unknown))
                        }
                    }

                    is CmdResultEvent.MediaFileCount -> {
                        _state.update { it.copy(pendingDeviceMediaCount = events.count) }
                    }

                    is CmdResultEvent.ActionSync -> {
                        if (events.type == ActionSyncType.IMPORTING &&
                            !events.state &&
                            _state.value.isSyncing
                        ) {
                            resetSyncState()
                        }
                    }

                    is FileSyncEvent.ThumbnailsReady -> {
                        if (!_state.value.isSyncing) return@collect
                        _state.update {
                            it.copy(
                                isSyncing = true,
                                previewItems = events.thumbnails.map { thumb ->
                                    MediaSyncPreviewItem(
                                        fpath = thumb.fpath,
                                        index = thumb.index,
                                        thumbnailUrl = thumb.thumbnailUrl,
                                    )
                                },
                                totalFilesToSync = events.totalFileCount,
                                currentFileIndex = 0,
                                syncProgress = 0f,
                            )
                        }
                    }

                    is FileSyncEvent.DownloadProgress -> {
                        if (!_state.value.isSyncing) return@collect
                        _state.update { state ->
                            state.copy(
                                syncProgress = events.progress / 100f,
                                currentFileIndex = events.curFileIndex + 1,
                                totalFilesToSync = events.totalFileCount,
                                speed = events.speed,
                            )
                        }
                    }

                    is FileSyncEvent.DownloadSuccess -> {
                        if (!_state.value.isSyncing) return@collect
                        if (events.filePath.isEmpty()) return@collect
                        _state.update { state ->
                            state.copy(
                                previewItems = if (usesLySyncPlaceholders()) {
                                    state.previewItems.map { item ->
                                        if (item.fpath == events.fpath) {
                                            item.copy(
                                                filePath = events.filePath,
                                                fileSize = events.fileSizeInBytes,
                                                fileModifiedTime = events.fileModifiedTime,
                                                isDownloaded = true,
                                            )
                                        } else {
                                            item
                                        }
                                    }
                                } else {
                                    appendDownloadedPreviewItem(state.previewItems, events)
                                },
                            )
                        }
                        val newFileEntity = MediaFilesEntity(
                            filePath = events.filePath,
                            type = MediaFileUtils.typeString(events.filePath),
                            createdAt = MediaFileUtils.resolveCreatedAt(
                                events.filePath,
                                events.fileModifiedTime,
                            ),
                            size = events.fileSizeInBytes,
                        )
                        appScope.launch {
                            photoRepository.insertPhoto(newFileEntity)
                        }
                    }

                    is FileSyncEvent.DownloadSkipped -> {
                        if (!_state.value.isSyncing) return@collect
                        _state.update { state ->
                            state.copy(
                                previewItems = state.previewItems.filter { it.fpath != events.fpath },
                            )
                        }
                    }

                    is FileSyncEvent.BatchDownloadFinished -> {
                        resetSyncState()
                        refreshPendingMediaCount()
                    }

                    is FileSyncEvent.Failed -> {
                        val reason = SdkErrorMessages.forFileSync(
                            context,
                            events.code,
                            events.reason,
                        )
                        LogUtils.e(
                            "MediaSyncStateManager",
                            "FileSync Failed code=${events.code} reason=$reason",
                        )
                        resetSyncState(reason)
                        refreshPendingMediaCount()
                        ToastUtils.showLong(reason)
                    }

                    else -> Unit
                }
            }
        }
    }

    /** LY 通过 ThumbnailsReady 预占位；RTK / TB 无预览图，下载完成后再追加条目。 */
    private fun usesLySyncPlaceholders(): Boolean {
        return savedSdkChannel == GlassesConstant.ChannelType.LY
    }

    private fun appendDownloadedPreviewItem(
        current: List<MediaSyncPreviewItem>,
        events: FileSyncEvent.DownloadSuccess,
    ): List<MediaSyncPreviewItem> {
        val key = events.fpath.ifBlank { events.filePath }
        val existingIndex = current.indexOfFirst { item ->
            item.fpath == key || item.filePath == events.filePath || item.index == events.curFileIndex
        }
        val updated = MediaSyncPreviewItem(
            fpath = key,
            index = events.curFileIndex,
            thumbnailUrl = "",
            filePath = events.filePath,
            fileSize = events.fileSizeInBytes,
            fileModifiedTime = events.fileModifiedTime,
            isDownloaded = true,
        )
        return if (existingIndex >= 0) {
            current.toMutableList().apply { set(existingIndex, updated) }
        } else {
            current + updated
        }
    }

    /** 同步结束或失败：恢复空闲状态，丢弃未下载完成的预览项，已下载项由数据库展示。 */
    private fun resetSyncState(errorMessage: String? = null) {
        _state.update { current ->
            current.copy(
                isSyncing = false,
                syncProgress = 0f,
                currentFileIndex = 0,
                totalFilesToSync = 0,
                speed = "0 KB/s",
                errorMessage = errorMessage,
                previewItems = emptyList(),
            )
        }
    }
}
