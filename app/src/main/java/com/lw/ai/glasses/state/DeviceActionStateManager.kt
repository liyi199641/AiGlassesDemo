package com.lw.ai.glasses.state

import com.fission.wear.glasses.sdk.GlassesManage
import com.fission.wear.glasses.sdk.constant.GlassesConstant.ActionSyncType
import com.fission.wear.glasses.sdk.events.CmdResultEvent
import com.fission.wear.glasses.sdk.events.ConnectionStateEvent
import com.fission.wear.glasses.sdk.events.FileSyncEvent
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

data class DeviceActionState(
    val isTakingPhoto: Boolean = false,
    val isRecordingAudio: Boolean = false,
    val isRecordingVideo: Boolean = false,
    val isMusicPlaying: Boolean = false,
    val isImporting: Boolean = false,
    val isWearing: Boolean? = null,
)

/**
 * 全局设备动作状态：订阅 [CmdResultEvent.ActionSync]，并在连接后主动拉取 [GlassesManage.getActionState]。
 */
@Singleton
class DeviceActionStateManager @Inject constructor() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val started = AtomicBoolean(false)

    private val _state = MutableStateFlow(DeviceActionState())
    val state: StateFlow<DeviceActionState> = _state.asStateFlow()

    fun start() {
        if (!started.compareAndSet(false, true)) return
        observeGlassesEvents()
    }

    fun refreshActionState() {
        if (!GlassesManage.currentConnectionState().isBleConnected) return
        GlassesManage.getActionState()
    }

    private fun observeGlassesEvents() {
        appScope.launch {
            GlassesManage.eventFlow().collect { event ->
                when (event) {
                    is ConnectionStateEvent.Connected -> refreshActionState()

                    is ConnectionStateEvent.Disconnected -> {
                        _state.value = DeviceActionState()
                    }

                    is CmdResultEvent.ActionSync -> {
                        _state.update { state ->
                            when (event.type) {
                                ActionSyncType.TAKE_PHOTO -> state.copy(isTakingPhoto = event.state)
                                ActionSyncType.RECORD_AUDIO -> state.copy(isRecordingAudio = event.state)
                                ActionSyncType.RECORD_VIDEO -> state.copy(isRecordingVideo = event.state)
                                ActionSyncType.MUSIC -> state.copy(isMusicPlaying = event.state)
                                ActionSyncType.IMPORTING -> state.copy(isImporting = event.state)
                                ActionSyncType.WEAR -> state.copy(isWearing = event.state)
                                else -> state
                            }
                        }
                    }

                    is FileSyncEvent.Failed,
                    is FileSyncEvent.BatchDownloadFinished -> {
                        _state.update { state ->
                            if (state.isImporting) state.copy(isImporting = false) else state
                        }
                    }

                    else -> Unit
                }
            }
        }
    }
}
