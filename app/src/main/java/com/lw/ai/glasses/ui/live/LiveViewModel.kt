package com.lw.ai.glasses.ui.live

import BaseViewModel
import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.net.Network
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.view.View
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.ui.PlayerView
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.blankj.utilcode.util.LogUtils
import com.blankj.utilcode.util.ToastUtils
import com.bytedance.android.openlive.broadcast.DouyinBroadcastApi
import com.fission.wear.glasses.sdk.GlassesManage
import com.fission.wear.glasses.sdk.constant.GlassesConstant
import com.fission.wear.glasses.sdk.constant.GlassesConstant.LiveStreamingMode
import com.fission.wear.glasses.sdk.data.model.LiveStreamingConfig
import com.fission.wear.glasses.sdk.events.LiveEvent
import com.fission.wear.glasses.sdk.live.LiveBatteryGuard
import com.fission.wear.glasses.sdk.live.LiveDisconnectMonitor
import com.fission.wear.glasses.sdk.live.LivePreviewCallback
import com.fission.wear.glasses.sdk.live.LiveStreamErrors
import com.lw.ai.glasses.R
import com.lw.ai.glasses.config.ProductSeriesResolver
import com.lw.ai.glasses.config.SdkChannelResolver
import com.lw.ai.glasses.utils.SdkErrorMessages
import com.lw.top.lib_core.data.datastore.BluetoothDataManager
import com.lw.top.lib_core.data.repository.DouYinRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject

@HiltViewModel
class LiveViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val repository: DouYinRepository,
    private val bluetoothDataManager: BluetoothDataManager,
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

    private var openRoomId: String? = null
    private var pendingRtmpPushUrl: String? = null
    private var previewViewAttached = false
    private var previewView: View? = null
    @SuppressLint("UnsafeOptInUsageError")
    private var lyLivePreviewController: LyLivePreviewController? = null
    private var lyLiveDisconnectMonitor: LiveDisconnectMonitor? = null
    private var pendingLyRtspUrl: String? = null
    private var pendingLyApNetwork: Network? = null
    private var pendingDouyinActivity: WeakReference<Activity>? = null
    /** 上次发起直播（开启眼镜 AP 热点）的时间，用于 Demo 10 秒启用间隔限制 */
    private var lastLiveApEnableAtMs: Long = 0L
    private val _douyinLive: MutableStateFlow<LiveStateEvent> = MutableStateFlow(LiveStateEvent.IDLE(""))
    val douyinLive: StateFlow<LiveStateEvent> = _douyinLive

    private val _requestLocationPermissionEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val requestLocationPermissionEvent: SharedFlow<Unit> = _requestLocationPermissionEvent.asSharedFlow()

    init {
        viewModelScope.launch {
            val channel = SdkChannelResolver.loadBoundDeviceChannel(bluetoothDataManager)
                ?: SdkChannelResolver.defaultChannel
            val isLyScheme = channel == GlassesConstant.ChannelType.LY
            _uiState.update {
                it.copy(
                    isLyScheme = isLyScheme,
                    streamingMode = if (isLyScheme) {
                        LiveStreamingMode.PREVIEW
                    } else {
                        it.streamingMode
                    },
                )
            }
        }
        viewModelScope.launch {
            GlassesManage.eventFlow().collect { events ->
                when (events) {
                    is LiveEvent.RespSuccess -> {
                        LogUtils.d("设备直播就绪：${events.rtsp}")
                        if (_uiState.value.isLyScheme) {
                            onLyLiveSessionReady(events.rtsp, events.apNetwork)
                        } else {
                            _uiState.update {
                                it.copy(
                                    rtspUrl = events.rtsp,
                                    isDeviceStreaming = true,
                                    isConnecting = if (it.connectPhase == LiveConnectPhase.DOUYIN) {
                                        it.isConnecting
                                    } else {
                                        false
                                    },
                                    isPlayingLocal = it.showsLocalPreview &&
                                        it.screenPhase == LiveScreenPhase.LIVE,
                                    errorMessage = null,
                                )
                            }
                        }
                    }

                    LiveEvent.PreviewStarted -> {
                        if (_uiState.value.isLyScheme) return@collect
                        when (_uiState.value.streamingMode) {
                            LiveStreamingMode.PREVIEW -> onPreviewOnlyStarted()
                            LiveStreamingMode.PUSH -> onPushOnlyStarted()
                            LiveStreamingMode.PREVIEW_PUSH,
                            LiveStreamingMode.PREVIEW_PUSH_MANUAL -> onPreviewPushStarted()
                        }
                    }

                    is LiveEvent.PreviewFailed -> {
                        if (_uiState.value.isLyScheme) return@collect
                        handleLivePreviewFailed(events.code, events.reason)
                    }

                    is LiveEvent.Failed -> {
                        pendingDouyinActivity = null
                        pendingRtmpPushUrl = null
                        if (openRoomId != null) {
                            viewModelScope.launch(Dispatchers.IO) {
                                runCatching { repository.closeBroadcast(openRoomId) }
                            }
                            openRoomId = null
                        }
                        val message = SdkErrorMessages.forLive(context, events.code, events.reason)
                        _uiState.update {
                            it.copy(
                                isConnecting = false,
                                isPlayingLocal = false,
                                isDeviceStreaming = false,
                                isPushingToDouyin = false,
                                connectPhase = LiveConnectPhase.NONE,
                                screenPhase = LiveScreenPhase.CONFIG,
                                errorMessage = message,
                            )
                        }
                        ToastUtils.showLong(message)
                    }

                    LiveEvent.RespStop -> {
                        pendingDouyinActivity = null
                        pendingRtmpPushUrl = null
                        restoreStoppedUiState(resetPhase = true)
                        closeDouyinBroadcastInBackground()
                    }

                    LiveEvent.StoppedByNotification -> {
                        pendingDouyinActivity = null
                        pendingRtmpPushUrl = null
                        restoreStoppedUiState(resetPhase = true)
                        _uiState.update { it.copy(errorMessage = null) }
                        closeDouyinBroadcastInBackground()
                    }

                    is LiveEvent.Disconnected -> {
                        onLiveDisconnected(events.code, events.reason)
                    }

                    else -> Unit
                }
            }
        }
    }

    @OptIn(UnstableApi::class)
    fun onPreviewViewReady(view: View) {
        if (previewView === view && previewViewAttached) return
        previewView = view
        previewViewAttached = true
        // 预览视图统一通过 LiveStreamingConfig.previewView 传入 startLiveStreaming（RTK 方案）；
        // LY 方案在 App 侧用 ExoPlayer 拉流，视图交给本地 LyLivePreviewController。
        if (_uiState.value.isLyScheme) {
            val controller = ensureLyLivePreviewController()
            val playerView = view as? PlayerView ?: return
            controller.attachPreviewView(playerView)
            tryStartLyLocalPreview()
        }
    }

    @OptIn(UnstableApi::class)
    fun onPreviewLifecycleStop() {
        if (_uiState.value.isLyScheme) {
            lyLivePreviewController?.stop()
        }
    }

    @OptIn(UnstableApi::class)
    fun detachPreviewView() {
        previewViewAttached = false
        previewView = null
        if (_uiState.value.isLyScheme) {
            lyLivePreviewController?.detachPreviewView()
        }
    }

    fun updateStreamingMode(mode: LiveStreamingMode, activity: Activity? = null) {
        if (_uiState.value.isLyScheme) return
        _uiState.update { it.copy(streamingMode = mode) }
        if (mode.requiresDouyinSdkInit()) {
            activity?.let(::initDouyinSdk)
        }
    }

    fun updateManualPushUrl(url: String) {
        _uiState.update { it.copy(manualPushUrl = url) }
    }

    fun togglePreviewMic() {
        val enabled = !_uiState.value.isMicOn
        _uiState.update { it.copy(isMicOn = enabled) }
        GlassesManage.setLivePreviewMicState(
            if (enabled) GlassesConstant.EnableState.ON else GlassesConstant.EnableState.OFF,
        )
    }

    fun togglePreviewRotation() {
        val nextRotation = if (_uiState.value.previewRotation == 0) 90 else 0
        _uiState.update { it.copy(previewRotation = nextRotation) }
        GlassesManage.setLivePreviewRotation(nextRotation)
    }

    /**
     * 验证“预览方案下进程默认网络在蜂窝且可访问公网”。
     *
     * relay 方案不会 `bindProcessToNetwork`，眼镜拉流通过 apNetwork 显式绑定，
     * 因此进程默认路由应仍为蜂窝（眼镜 AP 热点无公网，Android 不会选为默认网络）。
     * 此处用未绑定的 HttpURLConnection 访问公网，走的就是进程默认网络。
     */
    fun verifyPublicNetwork() {
        _uiState.update { it.copy(networkCheckResult = "验证中…") }
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching {
                val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                val caps = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) }
                val transport = when {
                    caps == null -> "无活动网络"
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "蜂窝(CELLULAR)"
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi(WIFI)"
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "以太网(ETHERNET)"
                    else -> "其他"
                }
                val conn = URL(PUBLIC_PROBE_URL).openConnection() as HttpURLConnection
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                conn.requestMethod = "GET"
                conn.instanceFollowRedirects = false
                val startMs = System.currentTimeMillis()
                val code = conn.responseCode
                val cost = System.currentTimeMillis() - startMs
                conn.disconnect()
                val reachable = code in 200..399
                "进程默认网络=$transport | 公网 HTTP=$code ${if (reachable) "✓可达" else "✗不可达"} | 耗时=${cost}ms"
            }.getOrElse { e ->
                "公网验证失败：${e.javaClass.simpleName}: ${e.message}"
            }
            LogUtils.d("LiveViewModel", "verifyPublicNetwork: $result")
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(networkCheckResult = result) }
                ToastUtils.showShort(result)
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
        _uiState.update { it.copy(bitrate = bitrate) }
    }

    fun confirmAndStartLive(activity: Activity) {
        if (_uiState.value.screenPhase == LiveScreenPhase.LIVE) return
        if (_uiState.value.isLyScheme && _uiState.value.streamingMode != LiveStreamingMode.PREVIEW) {
            _uiState.update { it.copy(streamingMode = LiveStreamingMode.PREVIEW) }
        }
        if (!checkLiveApEnableCooldown()) return
        if (!hasLocationPermission()) {
            requestLocationPermissionIfNeeded()
            ToastUtils.showLong(context.getString(R.string.permission_live_required))
            return
        }
        viewModelScope.launch {
            if (!ensureBatteryForPreviewIfNeeded()) return@launch
            when (_uiState.value.streamingMode) {
                LiveStreamingMode.PREVIEW -> startPreviewOnlyFlow()
                LiveStreamingMode.PUSH -> startPushOnlyFlow(activity)
                LiveStreamingMode.PREVIEW_PUSH -> startPreviewPushFlow(activity)
                LiveStreamingMode.PREVIEW_PUSH_MANUAL -> startManualPreviewPushFlow()
            }
        }
    }

    private suspend fun ensureBatteryForPreviewIfNeeded(): Boolean {
        if (_uiState.value.streamingMode == LiveStreamingMode.PUSH) return true
        val failure = LiveBatteryGuard.ensureMinimumForPreview()
        if (failure == null) return true
        val message = SdkErrorMessages.forLive(context, failure.code, failure.reason)
        _uiState.update { it.copy(errorMessage = message) }
        ToastUtils.showLong(message)
        return false
    }

    fun cancelConnecting() {
        finishLiveSession(resetPhase = true)
    }

    fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun liveLocationPermissions(): Array<String> = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    )

    fun requestLocationPermissionIfNeeded() {
        if (!hasLocationPermission()) {
            _requestLocationPermissionEvent.tryEmit(Unit)
        }
    }

    fun onLocationPermissionResult(allGranted: Boolean) {
        if (!allGranted || !hasLocationPermission()) {
            ToastUtils.showLong(context.getString(R.string.permission_live_required))
        }
    }

    private suspend fun startPreviewOnlyFlow() {
        pendingDouyinActivity = null
        pendingRtmpPushUrl = null
        _uiState.update {
            it.copy(
                screenPhase = LiveScreenPhase.LIVE,
                connectPhase = LiveConnectPhase.PREVIEW,
                isConnecting = true,
                isPlayingLocal = false,
                isDeviceStreaming = false,
                isPushingToDouyin = false,
                errorMessage = null,
                rtspUrl = "",
            )
        }
        startLiveStreaming(mode = LiveStreamingMode.PREVIEW)
    }

    private suspend fun startPreviewPushFlow(activity: Activity) {
        initDouyinSdk(activity)
        if (!ensureDouyinAuthorized(activity)) return
        pendingDouyinActivity = WeakReference(activity)
        pendingRtmpPushUrl = null
        _uiState.update {
            it.copy(
                screenPhase = LiveScreenPhase.LIVE,
                connectPhase = LiveConnectPhase.DOUYIN,
                isConnecting = true,
                isPlayingLocal = false,
                isDeviceStreaming = false,
                isPushingToDouyin = false,
                errorMessage = null,
                rtspUrl = "",
            )
        }
        val rtmpPushUrl = fetchDouyinRtmpPushUrl(activity) ?: run {
            pendingDouyinActivity = null
            _uiState.update {
                it.copy(
                    screenPhase = LiveScreenPhase.CONFIG,
                    connectPhase = LiveConnectPhase.NONE,
                    isConnecting = false,
                )
            }
            return
        }
        pendingRtmpPushUrl = rtmpPushUrl
        _uiState.update {
            it.copy(connectPhase = LiveConnectPhase.PREVIEW)
        }
        startLiveStreaming(
            mode = LiveStreamingMode.PREVIEW_PUSH,
            pushUrl = rtmpPushUrl,
        )
    }

    private suspend fun startManualPreviewPushFlow() {
        val pushUrl = _uiState.value.manualPushUrl.trim()
        if (pushUrl.isBlank()) {
            ToastUtils.showShort(context.getString(R.string.manual_push_url_required))
            return
        }
        pendingDouyinActivity = null
        pendingRtmpPushUrl = pushUrl
        _uiState.update {
            it.copy(
                screenPhase = LiveScreenPhase.LIVE,
                connectPhase = LiveConnectPhase.PREVIEW,
                isConnecting = true,
                isPlayingLocal = false,
                isDeviceStreaming = false,
                isPushingToDouyin = false,
                errorMessage = null,
                rtspUrl = "",
            )
        }
        startLiveStreaming(
            mode = LiveStreamingMode.PREVIEW_PUSH_MANUAL,
            pushUrl = pushUrl,
        )
    }

    private suspend fun startPushOnlyFlow(activity: Activity) {
        initDouyinSdk(activity)
        if (!ensureDouyinAuthorized(activity)) return
        pendingDouyinActivity = WeakReference(activity)
        _uiState.update {
            it.copy(
                screenPhase = LiveScreenPhase.LIVE,
                connectPhase = LiveConnectPhase.DOUYIN,
                isConnecting = true,
                isPlayingLocal = false,
                isDeviceStreaming = false,
                isPushingToDouyin = false,
                errorMessage = null,
                rtspUrl = "",
            )
        }
        val rtmpPushUrl = fetchDouyinRtmpPushUrl(activity) ?: run {
            pendingDouyinActivity = null
            _uiState.update {
                it.copy(
                    screenPhase = LiveScreenPhase.CONFIG,
                    connectPhase = LiveConnectPhase.NONE,
                    isConnecting = false,
                )
            }
            return
        }
        pendingRtmpPushUrl = rtmpPushUrl
        _uiState.update {
            it.copy(
                connectPhase = LiveConnectPhase.PREVIEW,
            )
        }
        startLiveStreaming(
            mode = LiveStreamingMode.PUSH,
            pushUrl = rtmpPushUrl,
        )
    }

    private fun onPreviewOnlyStarted() {
        applyPreviewMediaControls()
        _uiState.update {
            it.copy(
                isConnecting = false,
                isPlayingLocal = true,
                connectPhase = LiveConnectPhase.NONE,
                errorMessage = null,
            )
        }
    }

    private fun onPushOnlyStarted() {
        _uiState.update {
            it.copy(
                isConnecting = false,
                isPlayingLocal = false,
                isPushingToDouyin = true,
                connectPhase = LiveConnectPhase.NONE,
                errorMessage = null,
            )
        }
    }

    private fun onPreviewPushStarted() {
        applyPreviewMediaControls()
        _uiState.update {
            it.copy(
                isConnecting = false,
                isPlayingLocal = true,
                isPushingToDouyin = pendingRtmpPushUrl.isNullOrBlank().not(),
                connectPhase = LiveConnectPhase.NONE,
                errorMessage = null,
            )
        }
    }

    private fun applyPreviewMediaControls() {
        val state = _uiState.value
        if (state.isLyScheme) return
        GlassesManage.setLivePreviewMicState(
            if (state.isMicOn) GlassesConstant.EnableState.ON else GlassesConstant.EnableState.OFF,
        )
        GlassesManage.setLivePreviewRotation(state.previewRotation)
    }

    private suspend fun ensureDouyinAuthorized(activity: Activity): Boolean {
        if (!DouyinBroadcastApi.isBroadcastInited()) {
            ToastUtils.showShort(context.getString(R.string.douyin_live_initializing))
            return false
        }
        if (!DouyinBroadcastApi.isAuthorized()) {
            withContext(Dispatchers.IO) { repository.login(activity) }
        }
        if (!DouyinBroadcastApi.isAuthorized()) {
            ToastUtils.showShort(context.getString(R.string.douyin_auth_required))
            return false
        }
        val userInfo = withContext(Dispatchers.IO) { repository.getUserInfo() }
        if (!userInfo.isSuccess) {
            ToastUtils.showShort(context.getString(R.string.douyin_auth_required))
            return false
        }
        return true
    }

    private suspend fun fetchDouyinRtmpPushUrl(activity: Activity): String? {
        _douyinLive.value = LiveStateEvent.Loading("")
        val result = withContext(Dispatchers.IO) { repository.startBroadcast() }
        if (!result.isSuccess) {
            _douyinLive.value = LiveStateEvent.RtspFail("")
            val message = result.exceptionOrNull()?.message
                ?: context.getString(R.string.douyin_auth_required)
            _uiState.update { it.copy(errorMessage = message) }
            ToastUtils.showLong(message)
            if (result.exceptionOrNull() is SecurityException) {
                withContext(Dispatchers.IO) { repository.login(activity) }
            }
            return null
        }
        openRoomId = result.getOrNull()?.openRoomId
        val rtmpPushUrl = result.getOrNull()?.rtmpPushUrl.orEmpty()
        if (rtmpPushUrl.isBlank()) {
            _douyinLive.value = LiveStateEvent.RtspFail("")
            _uiState.update { it.copy(errorMessage = "RTMP 地址为空") }
            ToastUtils.showLong("RTMP 地址为空")
            return null
        }
        _douyinLive.value = LiveStateEvent.RtspSuccess("")
        LogUtils.d("LiveViewModel", "获取 RTMP: openRoomId=$openRoomId, rtmp=$rtmpPushUrl")
        return rtmpPushUrl
    }

    private suspend fun syncLyProductSeriesForLive() {
        if (!_uiState.value.isLyScheme) return
        val series = ProductSeriesResolver.resolve(
            bluetoothDataManager.getBluetoothName(),
            bluetoothDataManager.getBluetoothAdapter(),
        )
        _uiState.update {
            it.copy(
                isLyTSeries = series == GlassesConstant.ProductSeries.T,
                // S 系列源画面需旋转 270°；T 系列无需旋转。
                lyRotationDegrees = if (series == GlassesConstant.ProductSeries.T) 0 else 270,
            )
        }
        GlassesManage.setProductSeries(series)
    }

    private suspend fun startLiveStreaming(
        mode: LiveStreamingMode,
        pushUrl: String? = null,
    ) {
        syncLyProductSeriesForLive()
        markLiveApEnableStarted()
        val (width, height) = parseResolution(_uiState.value.targetResolution)
        val isLy = _uiState.value.isLyScheme
        GlassesManage.startLiveStreaming(
            LiveStreamingConfig(
                videoPictureWidth = width,
                videoPictureHeight = height,
                fps = _uiState.value.targetFps,
                bps = _uiState.value.bitrate,
                pushUrl = pushUrl,
                previewView = if (!isLy && mode != LiveStreamingMode.PUSH) previewView else null,
                mode = mode,
            ),
        )
    }

    @OptIn(UnstableApi::class)
    private fun ensureLyLivePreviewController(): LyLivePreviewController {
        return lyLivePreviewController ?: LyLivePreviewController(context.applicationContext).also { controller ->
            lyLivePreviewController = controller
            controller.setPreviewCallback(object : LivePreviewCallback {
                override fun onPreviewStarted() {
                    onPreviewOnlyStarted()
                }

                override fun onPreviewFailed(errorCode: Int) {
                    handleLyLocalPreviewFailed(errorCode)
                }

                override fun onLiveStoppedByNotification() = Unit
            })
        }
    }

    private fun onLyLiveSessionReady(rtspUrl: String, apNetwork: Network?) {
        // apNetwork 由 LiveEvent.RespSuccess 事件下发（LY 方案仅在 AP Network 就绪时才发 RespSuccess）。
        val network = apNetwork
        if (network == null) {
            handleLivePreviewFailed(
                GlassesConstant.ERROR_CODE_LIVE_PREVIEW_START_FAILED,
                "Glasses AP Network unavailable",
            )
            return
        }
        pendingLyRtspUrl = rtspUrl
        pendingLyApNetwork = network
        _uiState.update {
            it.copy(
                rtspUrl = rtspUrl,
                isDeviceStreaming = true,
                isConnecting = false,
                errorMessage = null,
            )
        }
        startLyLiveDisconnectMonitor()
        tryStartLyLocalPreview()
    }

    private fun startLyLiveDisconnectMonitor() {
        if (lyLiveDisconnectMonitor != null) return
        lyLiveDisconnectMonitor = LiveDisconnectMonitor(
            appContext = context.applicationContext,
            shouldMonitorApLink = { _uiState.value.isDeviceStreaming && _uiState.value.isLyScheme },
            onPhoneWifiDisabled = {
                onLiveDisconnected(
                    LiveStreamErrors.CODE_PHONE_WIFI_OFF,
                    LiveDisconnectMonitor.REASON_PHONE_WIFI_OFF,
                )
            },
            onGlassesApLinkLost = {
                onLiveDisconnected(
                    LiveStreamErrors.CODE_GLASSES_AP_LINK_LOST,
                    LiveDisconnectMonitor.REASON_GLASSES_AP_LINK_LOST,
                )
            },
        ).also { it.start() }
    }

    private fun stopLyLiveDisconnectMonitor() {
        lyLiveDisconnectMonitor?.stop()
        lyLiveDisconnectMonitor = null
    }

    @OptIn(UnstableApi::class)
    private fun tryStartLyLocalPreview() {
        val rtspUrl = pendingLyRtspUrl ?: return
        val network = pendingLyApNetwork ?: return
        if (!previewViewAttached) return
        if (previewView !is PlayerView) return
        ensureLyLivePreviewController().startPreview(
            rtspUrl, network, _uiState.value.previewAudioEnabled,
        )
    }

    /** 循环切换 LY 预览画面旋转角度（0→90→180→270），仅客户端 graphicsLayer 旋转。 */
    fun toggleLyPreviewRotation() {
        if (!_uiState.value.isLyScheme) return
        val next = (_uiState.value.lyRotationDegrees + 90) % 360
        _uiState.update { it.copy(lyRotationDegrees = next) }
    }

    private fun handleLyLocalPreviewFailed(errorCode: Int) {
        releaseLyLivePreviewController()
        pendingLyRtspUrl = null
        pendingLyApNetwork = null
        when (errorCode) {
            LiveStreamErrors.CODE_GLASSES_AP_LINK_LOST,
            LiveStreamErrors.CODE_PHONE_WIFI_OFF,
            LiveStreamErrors.CODE_GLASSES_AP_CLOSED,
            -> onLiveDisconnected(errorCode, LiveStreamErrors.defaultReason(errorCode))

            else -> {
                GlassesManage.stopLiveStreaming()
                handleLivePreviewFailed(
                    errorCode,
                    LiveStreamErrors.previewStartFailedReason(errorCode),
                )
            }
        }
    }

    private fun handleLivePreviewFailed(code: Int, reason: String) {
        pendingDouyinActivity = null
        pendingRtmpPushUrl = null
        if (openRoomId != null) {
            viewModelScope.launch(Dispatchers.IO) {
                runCatching { repository.closeBroadcast(openRoomId) }
            }
            openRoomId = null
        }
        val message = SdkErrorMessages.forLive(context, code, reason)
        _uiState.update {
            it.copy(
                isConnecting = false,
                isPlayingLocal = false,
                isPushingToDouyin = false,
                connectPhase = LiveConnectPhase.NONE,
                screenPhase = LiveScreenPhase.CONFIG,
                errorMessage = message,
            )
        }
        ToastUtils.showLong(message)
    }

    @OptIn(UnstableApi::class)
    private fun releaseLyLivePreviewController() {
        stopLyLiveDisconnectMonitor()
        lyLivePreviewController?.release()
        lyLivePreviewController = null
    }

    private fun parseResolution(resolution: String): Pair<Int, Int> {
        val normalized = resolution.replace('*', 'x')
        val parts = normalized.split('x', 'X').mapNotNull { it.trim().toIntOrNull() }
        if (parts.size >= 2) return parts[0] to parts[1]
        return 1280 to 720
    }

    private fun checkLiveApEnableCooldown(): Boolean {
        if (lastLiveApEnableAtMs == 0L) return true
        val elapsed = System.currentTimeMillis() - lastLiveApEnableAtMs
        if (elapsed >= LIVE_AP_ENABLE_MIN_INTERVAL_MS) return true
        val remainSec = ((LIVE_AP_ENABLE_MIN_INTERVAL_MS - elapsed) + 999) / 1000
        val message = context.getString(R.string.live_ap_enable_cooldown, remainSec)
        _uiState.update { it.copy(errorMessage = message) }
        ToastUtils.showLong(message)
        return false
    }

    private fun markLiveApEnableStarted() {
        lastLiveApEnableAtMs = System.currentTimeMillis()
    }

    fun stopStreaming() {
        GlassesManage.stopLiveStreaming()
    }

    fun exitLiveScreen() {
        finishLiveSession(resetPhase = true)
    }

    private fun finishLiveSession(resetPhase: Boolean = true) {
        pendingDouyinActivity = null
        pendingRtmpPushUrl = null
        pendingLyRtspUrl = null
        pendingLyApNetwork = null
        releaseLyLivePreviewController()
        stopStreaming()
        restoreStoppedUiState(resetPhase = resetPhase)
        closeDouyinBroadcastInBackground()
    }

    /** 抖音关播在后台执行，不阻塞退出直播界面。 */
    private fun closeDouyinBroadcastInBackground() {
        val roomId = openRoomId ?: return
        openRoomId = null
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { repository.closeBroadcast(roomId) }
                .onSuccess {
                    _douyinLive.value = LiveStateEvent.RtspStop("")
                }
                .onFailure { e ->
                    LogUtils.w("LiveViewModel", "closeDouyinBroadcast failed: ${e.message}")
                }
        }
    }

    private fun restoreStoppedUiState(resetPhase: Boolean = false) {
        _uiState.update {
            it.copy(
                screenPhase = if (resetPhase) LiveScreenPhase.CONFIG else it.screenPhase,
                connectPhase = LiveConnectPhase.NONE,
                isPlayingLocal = false,
                isPushingToDouyin = false,
                isDeviceStreaming = false,
                isConnecting = false,
                rtspUrl = "",
            )
        }
    }

    /** 直播中异常断连：关播、停推流并回到配置页 */
    private fun onLiveDisconnected(code: Int, reason: String) {
        viewModelScope.launch {
            pendingDouyinActivity = null
            pendingRtmpPushUrl = null
            pendingLyRtspUrl = null
            pendingLyApNetwork = null
            releaseLyLivePreviewController()
            val wasPushing = openRoomId != null || _uiState.value.isPushingToDouyin
            closeDouyinBroadcastInBackground()
            stopStreaming()
            val message = SdkErrorMessages.forLive(context, code, reason)
            _uiState.update {
                it.copy(
                    screenPhase = LiveScreenPhase.CONFIG,
                    connectPhase = LiveConnectPhase.NONE,
                    isConnecting = false,
                    isPlayingLocal = false,
                    isDeviceStreaming = false,
                    isPushingToDouyin = false,
                    rtspUrl = "",
                    errorMessage = message,
                )
            }
            if (wasPushing) {
                ToastUtils.showLong(message)
            }
            LogUtils.w("LiveViewModel", "直播异常断连: code=$code msg=$message")
        }
    }

    fun retryDouyinBroadcast(activity: Activity) {
        if (!_uiState.value.canRetryDouyinBroadcast) return
        pendingDouyinActivity = WeakReference(activity)
        _uiState.update {
            it.copy(
                isConnecting = true,
                connectPhase = LiveConnectPhase.DOUYIN,
                errorMessage = null,
            )
        }
        viewModelScope.launch {
            val rtmpPushUrl = fetchDouyinRtmpPushUrl(activity) ?: run {
                _uiState.update {
                    it.copy(
                        isConnecting = false,
                        connectPhase = LiveConnectPhase.NONE,
                    )
                }
                return@launch
            }
            pendingRtmpPushUrl = rtmpPushUrl
            GlassesManage.startPushLiveStreaming(rtmpPushUrl)
            _uiState.update {
                it.copy(
                    isPushingToDouyin = true,
                    isConnecting = false,
                    connectPhase = LiveConnectPhase.NONE,
                    errorMessage = null,
                )
            }
        }
    }

    fun closeBroadcast(resetPhase: Boolean = false) {
        finishLiveSession(resetPhase = resetPhase)
    }

    fun initDouyinSdk(activity: Activity) {
        repository.initDouyinSdk(activity)
    }

    override fun onCleared() {
        super.onCleared()
        previewViewAttached = false
        pendingDouyinActivity = null
        pendingRtmpPushUrl = null
        detachPreviewView()
        stopStreaming()
        closeDouyinBroadcastInBackground()
    }

    private companion object {
        private const val LIVE_AP_ENABLE_MIN_INTERVAL_MS = 10_000L

        /** 公网连通性探测地址（国内可达，不绑定任何特定网络）。 */
        private const val PUBLIC_PROBE_URL = "https://www.baidu.com"

        private fun LiveStreamingMode.requiresDouyinSdkInit(): Boolean =
            this == LiveStreamingMode.PREVIEW_PUSH || this == LiveStreamingMode.PUSH
    }
}
