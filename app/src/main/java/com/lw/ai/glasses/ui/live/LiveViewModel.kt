package com.lw.ai.glasses.ui.live

import BaseViewModel
import android.app.Activity
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.blankj.utilcode.util.LogUtils
import com.blankj.utilcode.util.ToastUtils
import com.bytedance.android.openlive.broadcast.DouyinBroadcastApi
import com.fission.wear.glasses.sdk.GlassesManage
import com.fission.wear.glasses.sdk.constant.GlassesConstant
import com.fission.wear.glasses.sdk.data.model.LiveStreamingConfig
import com.fission.wear.glasses.sdk.events.LiveEvent
import com.lw.ai.glasses.R
import com.lw.top.lib_core.data.repository.DouYinRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import java.lang.ref.WeakReference
import javax.inject.Inject

@HiltViewModel
class LiveViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val repository: DouYinRepository
) : BaseViewModel() {

    sealed interface LiveStateEvent {
        data class IDLE(val result: String) : LiveStateEvent
        data class Loading(val result: String) : LiveStateEvent
        data class RtspSuccess(val rtspUrl: String) : LiveStateEvent
        data class RtspFail(val result: String) : LiveStateEvent
        data class RtspStop(val result: String) : LiveStateEvent
    }

    private val _uiState = MutableStateFlow(LiveUiState())
    val uiState: StateFlow<LiveUiState> = _uiState.asStateFlow()
    var vlcPlayer: MediaPlayer? = null
    private var libVlc: LibVLC? = null

    private var openRoomId: String? = null
    private var pendingDouyinActivityRef: WeakReference<Activity>? = null
    //直播状态
    private val _douyinLive: MutableStateFlow<LiveStateEvent> = MutableStateFlow(LiveStateEvent.IDLE(""))
    val douyinLive: StateFlow<LiveStateEvent> = _douyinLive

    init {
        val options = ArrayList<String>().apply {
            add("--rtsp-tcp") // 强制 TCP (防花屏)
            add("--network-caching=300") // 降低网络缓存 (低延迟)
            add("-vvv") // 详细日志
        }
        libVlc = LibVLC(context, options)
        vlcPlayer = MediaPlayer(libVlc)

        viewModelScope.launch {
            GlassesManage.eventFlow().collect { events ->
                when (events) {
                    is LiveEvent.LiveSuccess -> {
                        LogUtils.d("推流成功：${events.rtsp}")
                        val pendingDouyinActivity = pendingDouyinActivityRef?.get()
                        _uiState.update {
                            it.copy(
                                rtspUrl = events.rtsp,
                                isDeviceStreaming = true,
                                isConnecting = false,
                                errorMessage = null
                            )
                        }
                        if (pendingDouyinActivity != null) {
                            pendingDouyinActivityRef = null
                            startDouyinLive(pendingDouyinActivity)
                        } else {
                            startLocalPlay()
                        }
                    }

                    is LiveEvent.Failed -> {
                        vlcPlayer?.stop()
                        clearProcessNetworkBinding()
                        pendingDouyinActivityRef = null
                        _uiState.update {
                            it.copy(
                                isConnecting = false,
                                isPlayingLocal = false,
                                isDeviceStreaming = false,
                                errorMessage = events.reason
                            )
                        }
                        ToastUtils.showLong(events.reason)
                    }

                    LiveEvent.RespStop -> {
                        pendingDouyinActivityRef = null
                        restoreStoppedUiState()
                    }

                    else -> {

                    }
                }

            }
        }
    }

    fun updateFps(fps: Int) {
        _uiState.update { it.copy(targetFps = fps) }
    }

    fun updateResolution(res: String) {
        _uiState.update { it.copy(targetResolution = res) }
    }

    fun updateBitrateChange(bitrate: Int) {
        _uiState.update {
            it.copy(bitrate = bitrate)
        }
    }

    fun updateLiveChannel(liveChannel: GlassesConstant.LiveChannel) {
        _uiState.update {
            it.copy(liveChannel = liveChannel)
        }
    }

    fun startStreaming() {
        val (width, height) = parseResolution(_uiState.value.targetResolution)
        _uiState.update {
            it.copy(
                isConnecting = true,
                isPlayingLocal = false,
                isDeviceStreaming = false,
                errorMessage = null,
                rtspUrl = ""
            )
        }

        GlassesManage.startLiveStreaming(
            LiveStreamingConfig(
                width, height,
                _uiState.value.targetFps, _uiState.value.bitrate, _uiState.value.liveChannel
            )
        )
    }

    fun stopStreaming() {
        GlassesManage.stopLiveStreaming()
    }

    fun switchFunction(mode: AppFunctionMode) {
        _uiState.update { it.copy(currentFunction = mode) }
        stopLocalPlay()
    }

    fun toggleLocalPlay() {
        if (_uiState.value.isPlayingLocal) {
            stopLocalPlay()
        } else {
            startLocalPlay()
        }
    }

    private fun startLocalPlay() {
        val url = _uiState.value.rtspUrl
        try {
            LogUtils.d("ly", "开始本地预览: channel=${_uiState.value.liveChannel}, rtsp=$url")
            prepareNetworkForLocalPlay()
            val media = Media(libVlc, Uri.parse(url))
            // 针对低延迟的关键配置
            media.addOption(":network-caching=300")
            media.addOption(":clock-jitter=0")
            media.addOption(":clock-synchro=0")

            vlcPlayer?.media = media
            val playResult = vlcPlayer?.play()
            LogUtils.d("ly", "VLC play result: $playResult")
            media.release()

            _uiState.update { it.copy(isPlayingLocal = true) }
        } catch (e: Exception) {
            LogUtils.e("ly", "本地预览启动失败: ${e.message}")
            _uiState.update {
                it.copy(
                    isPlayingLocal = false,
                    errorMessage = e.message
                )
            }
        }

    }

    private fun stopLocalPlay() {
        vlcPlayer?.stop()
        _uiState.update { it.copy(isPlayingLocal = false) }
    }

    private fun restoreStoppedUiState() {
        vlcPlayer?.stop()
        clearProcessNetworkBinding()
        _uiState.update {
            it.copy(
                isPlayingLocal = false,
                isPushingToDouyin = false,
                isDeviceStreaming = false,
                isConnecting = false,
                rtspUrl = ""
            )
        }
    }

    private fun parseResolution(resolution: String): Pair<Int, Int> {
        val parts = resolution.split("x", "X", "*")
        val width = parts.getOrNull(0)?.toIntOrNull() ?: 720
        val height = parts.getOrNull(1)?.toIntOrNull() ?: 960
        return width to height
    }

    private fun prepareNetworkForLocalPlay() {
        if (_uiState.value.liveChannel == GlassesConstant.LiveChannel.WIFI_STATION) {
            clearProcessNetworkBinding()
            return
        }

        if (!bindProcessToWifiNetwork()) {
            ToastUtils.showLong(context.getString(R.string.wifi_network_not_found))
        }
    }

    private fun bindProcessToWifiNetwork(): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val wifiNetwork = connectivityManager.allNetworks.firstOrNull { network ->
            connectivityManager.getNetworkCapabilities(network)
                ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        } ?: return false

        val result = connectivityManager.bindProcessToNetwork(wifiNetwork)
        LogUtils.d("ly", "bindProcessToWifiNetwork($wifiNetwork) -> $result")
        return result
    }

    private fun clearProcessNetworkBinding() {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        connectivityManager.bindProcessToNetwork(null)
    }


    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun douyinAuth() {
        repository.douyinAuth()
    }

    fun startLiveStreaming(activity: Activity) {
        if (!DouyinBroadcastApi.isBroadcastInited()) {
            ToastUtils.showShort(context.getString(R.string.douyin_live_initializing))
            return
        }
        pendingDouyinActivityRef = WeakReference(activity)
        if (_uiState.value.isDeviceStreaming && _uiState.value.rtspUrl.isNotBlank()) {
            pendingDouyinActivityRef = null
            startDouyinLive(activity)
        } else {
            startStreaming()
        }
    }

    private fun startDouyinLive(activity: Activity) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isConnecting = true,
                    errorMessage = null
                )
            }
            if (!DouyinBroadcastApi.isBroadcastInited()) {
                ToastUtils.showShort(context.getString(R.string.douyin_live_initializing))
                _uiState.update { it.copy(isConnecting = false) }
            } else if (!DouyinBroadcastApi.isAuthorized()) {
                _uiState.update { it.copy(isConnecting = false) }
                withContext(Dispatchers.IO){
                    repository.login(activity)
                    if (repository.getUserInfo().isSuccess){
                        startBroadcast(activity)
                    }
                }
            } else {
                if (repository.getUserInfo().isSuccess) {
                    startBroadcast(activity)
                } else {
                    _uiState.update { it.copy(isConnecting = false) }
                }
            }
        }
    }

    private fun startBroadcast(activity: Activity) {
        if (_douyinLive.value is LiveStateEvent.RtspSuccess) {
            _uiState.update { it.copy(isConnecting = false) }
            return
        }
        if (!DouyinBroadcastApi.isBroadcastInited()) {
            LogUtils.d("ly", "未初始化")
            _uiState.update { it.copy(isConnecting = false) }
            return
        }
        if (!DouyinBroadcastApi.isAuthorized()) {
            LogUtils.d("ly", "未授权")
            _uiState.update { it.copy(isConnecting = false) }
            return
        }
        _douyinLive.value = LiveStateEvent.Loading("")
        _uiState.update {
            it.copy(
                isConnecting = true,
                errorMessage = null
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            val result = repository.startBroadcast()
            viewModelScope.launch(Dispatchers.Main) {
                if (result.isSuccess) {
                    openRoomId = result.getOrNull()?.openRoomId
                    _douyinLive.value = LiveStateEvent.RtspSuccess("")
                    _uiState.update {
                        it.copy(
                            isConnecting = false,
                            isPushingToDouyin = true,
                            errorMessage = null
                        )
                    }
                    LogUtils.d("ly","开始直播成功: openRoomId:${result.getOrNull()?.openRoomId}, rtmpPushUrl:${ result.getOrNull()?.rtmpPushUrl}")
                    if (uiState.value.liveChannel != GlassesConstant.LiveChannel.WIFI_STATION) {
                        GlassesManage.startPushLiveStreaming( result.getOrNull()?.rtmpPushUrl!!)
                    } else {
                        clearProcessNetworkBinding()
                        LogUtils.d(
                            "ly",
                            "使用 FFmpeg 推流: channel=${uiState.value.liveChannel}, rtsp=${uiState.value.rtspUrl}"
                        )
                        repository.startFFmpegPush(
                            uiState.value.rtspUrl,
                            result.getOrNull()?.rtmpPushUrl ?: ""
                        )
                    }
                } else {
                    _douyinLive.value = LiveStateEvent.RtspFail("")
                    _uiState.update {
                        it.copy(
                            isConnecting = false,
                            isPushingToDouyin = false,
                            errorMessage = result.exceptionOrNull()?.message
                        )
                    }
                    LogUtils.d("ly","开始直播失败：${result.exceptionOrNull()?.message}")
                    if (result.exceptionOrNull() is SecurityException) {
                        LogUtils.d("ly", "开始直播失败，未授权, 跳转授权")
                        repository.login(activity)
                    }
                }
            }
        }
    }

    fun closeBroadcast() {
        viewModelScope.launch(Dispatchers.Main) {
            pendingDouyinActivityRef = null
            _uiState.update { it.copy(isConnecting = true) }
            val result = repository.closeBroadcast(openRoomId)
            if (result.isSuccess) {
                openRoomId = null
                _douyinLive.value = LiveStateEvent.RtspStop("")
                repository.stopFFmpegPush()
                stopStreaming()
                _uiState.update {
                    it.copy(
                        isConnecting = false,
                        isDeviceStreaming = false,
                        isPushingToDouyin = false,
                        errorMessage = null
                    )
                }
                LogUtils.d("ly","关闭直播成功")
            } else {
                _uiState.update {
                    it.copy(
                        isConnecting = false,
                        errorMessage = result.exceptionOrNull()?.message
                    )
                }
                LogUtils.d("ly","关闭直播失败")
            }
        }
    }

    fun initDouyinSdk(activity: Activity) {
        repository.initDouyinSdk(activity)
    }

    override fun onCleared() {
        super.onCleared()
        clearProcessNetworkBinding()
        stopStreaming()
        closeBroadcast()
    }

}