package com.lw.ai.glasses.ui.live

import android.content.Context
import android.net.Network
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.PlayerView
import com.fission.wear.glasses.sdk.constant.GlassesConstant
import com.fission.wear.glasses.sdk.live.LivePreviewCallback
import com.fission.wear.glasses.sdk.util.FissionLogUtils

/**
 * Demo 侧 LY 直播预览：统一走 RTSP 中继 + ExoPlayer。
 *
 * 中继将眼镜 RTSP（S 系列 TCP / T 系列 UDP RTP）转为 localhost TCP interleaved，
 * ExoPlayer 连 localhost 即可，无需 bindProcessToNetwork，蜂窝推流不受影响。
 */
@UnstableApi
class LyLivePreviewController(
    appContext: Context,
) {

    private val applicationContext = appContext.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private var exoPlayer: ExoPlayer? = null
    private var playerView: PlayerView? = null
    private var previewCallback: LivePreviewCallback? = null
    private var previewStarted = false
    private var firstFrameRendered = false
    private var pendingPreview: PendingPreview? = null
    private var playTimeoutRunnable: Runnable? = null
    private var released = false
    private var relayServer: RtspRelayServer? = null

    // ── 卡顿自恢复：T 系列走 TCP 后，移动导致 WiFi 吞吐骤降 → media3 长时间 BUFFERING → 冻结。
    //    BUFFERING 持续超过阈值即重载 RTSP 源，重新对齐直播边缘并拿到新关键帧。
    private var lastLocalUrl: String? = null
    private var recoveryRunnable: Runnable? = null
    private var recoveryAttempts = 0
    private var bufferingSinceMs = 0L

    /** 当前预览是否启用音轨（由 startPreview 传入，运行时可切换）。 */
    private var audioEnabled = false

    private data class PendingPreview(val rtspUrl: String, val apNetwork: Network)

    fun attachPreviewView(view: PlayerView) {
        mainHandler.post {
            if (released) return@post
            if (playerView === view && pendingPreview == null) return@post
            detachPreviewViewInternal()
            playerView = view
            view.useController = false
            view.player = ensurePlayerOnMainThread()
            FissionLogUtils.d(TAG, "ExoPlayer preview view attached")
            pendingPreview?.let { pending ->
                pendingPreview = null
                startPreviewInternal(pending.rtspUrl, pending.apNetwork)
            }
        }
    }

    fun detachPreviewView() {
        mainHandler.post { detachPreviewViewInternal() }
    }

    private fun detachPreviewViewInternal() {
        playerView?.player = null
        playerView = null
    }

    fun setPreviewCallback(callback: LivePreviewCallback?) {
        previewCallback = callback
    }

    fun startPreview(rtspUrl: String, apNetwork: Network, enableAudio: Boolean = false) {
        audioEnabled = enableAudio
        startRelayAndPreview(rtspUrl, apNetwork)
    }

    /**
     * 启动 RTSP 中继（bind AP 拉流 → localhost TCP interleaved），
     * 然后 ExoPlayer 连 localhost，无需 bindProcessToNetwork。
     */
    private fun startRelayAndPreview(upstreamUrl: String, apNetwork: Network) {
        stopRelay()
        val relay = RtspRelayServer(applicationContext, upstreamUrl, apNetwork, audioEnabled)
        relayServer = relay
        FissionLogUtils.i(TAG, "starting RTSP relay for $upstreamUrl")
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val localUrl = relay.start()
                FissionLogUtils.i(TAG, "relay ready: $localUrl")
                mainHandler.post {
                    if (released) return@post
                    if (playerView == null) {
                        pendingPreview = PendingPreview(localUrl, apNetwork)
                        FissionLogUtils.d(TAG, "ExoPlayer startPreview deferred, waiting for view url=$localUrl")
                        return@post
                    }
                    startPreviewInternal(localUrl, apNetwork)
                }
            } catch (e: Exception) {
                FissionLogUtils.e(TAG, "relay start failed: ${e.message}")
                mainHandler.post {
                    previewCallback?.onPreviewFailed(
                        GlassesConstant.ERROR_CODE_LIVE_PREVIEW_START_FAILED,
                    )
                }
            }
        }
    }

    private fun stopRelay() {
        relayServer?.stop()
        relayServer = null
    }

    private fun startPreviewInternal(localUrl: String, apNetwork: Network) {
        playerView?.post {
            playViaRelay(localUrl)
        }
    }

    private fun ensurePlayerOnMainThread(): ExoPlayer {
        check(Looper.myLooper() == Looper.getMainLooper()) {
            "ExoPlayer must be used on main thread"
        }
        check(!released) { "LyLivePreviewController already released" }
        exoPlayer?.let { return it }
        if (RTSP_DEBUG) {
            Log.setLogLevel(Log.LOG_LEVEL_ALL)
            FissionLogUtils.i(TAG, "Media3 LogLevel=ALL；RTSP 信令见 logcat tag=RtspClient")
        }
        val loadControl = DefaultLoadControl.Builder()
            // 加大缓冲：良好 WiFi 期积累余量，抵消移动时的短时吞吐骤降（减少 2↔3 抖动）
            .setBufferDurationsMs(2_000, 8_000, 500, 1_500)
            .build()
        val trackSelector = DefaultTrackSelector(applicationContext).apply {
            parameters = buildUponParameters()
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, !audioEnabled)
                .build()
        }
        return ExoPlayer.Builder(applicationContext)
            .setLoadControl(loadControl)
            .setTrackSelector(trackSelector)
            .build()
            .also { player ->
                exoPlayer = player
                player.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        FissionLogUtils.d(
                            TAG,
                            "ExoPlayer state=$playbackState isPlaying=${player.isPlaying} " +
                                "playWhenReady=${player.playWhenReady}",
                        )
                        when (playbackState) {
                            Player.STATE_BUFFERING -> if (previewStarted) scheduleRebufferRecovery()
                            Player.STATE_READY -> cancelRebufferRecovery()
                            Player.STATE_ENDED -> {
                                cancelRebufferRecovery()
                                FissionLogUtils.w(TAG, "ExoPlayer ended unexpectedly")
                                previewCallback?.onPreviewFailed(
                                    GlassesConstant.ERROR_CODE_LIVE_PREVIEW_START_FAILED,
                                )
                            }
                        }
                        if (playbackState == Player.STATE_READY && !firstFrameRendered) {
                            FissionLogUtils.i(TAG, "ExoPlayer READY (awaiting first frame)")
                        }
                    }

                    override fun onRenderedFirstFrame() {
                        FissionLogUtils.i(TAG, "ExoPlayer first frame rendered")
                        firstFrameRendered = true
                        recoveryAttempts = 0 // 播放恢复成功，清零自恢复计数
                        onPreviewPlaying()
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        cancelPlayTimeout()
                        logPlaybackError(error)
                        val errorCode = if (isApNetworkLostError(error)) {
                            GlassesConstant.ERROR_CODE_LIVE_GLASSES_AP_LINK_LOST
                        } else {
                            GlassesConstant.ERROR_CODE_LIVE_PREVIEW_START_FAILED
                        }
                        previewCallback?.onPreviewFailed(errorCode)
                    }
                })
                player.addAnalyticsListener(object : AnalyticsListener {
                    override fun onDroppedVideoFrames(
                        eventTime: AnalyticsListener.EventTime,
                        droppedFrames: Int,
                        elapsedMs: Long,
                    ) {
                        if (droppedFrames > 0) {
                            FissionLogUtils.w(
                                TAG,
                                "ExoPlayer dropped $droppedFrames video frames in ${elapsedMs}ms",
                            )
                        }
                    }
                })
                FissionLogUtils.d(TAG, "ExoPlayer initialized on main thread")
            }
    }

    private fun onPreviewPlaying() {
        cancelPlayTimeout()
        if (previewStarted) return
        previewStarted = true
        FissionLogUtils.i(TAG, "LY local preview started (via relay)")
        previewCallback?.onPreviewStarted()
    }

    /**
     * ExoPlayer 连 relay 的 localhost URL。
     * relay 已处理所有协议改写（SDP/Content-Base/PLAY URL/Session timeout），
     * 且提供 TCP interleaved RTP，所以 forceRtpTcp=true，无需 socketFactory。
     */
    private fun playViaRelay(localUrl: String) {
        if (released) return
        val player = ensurePlayerOnMainThread()
        previewStarted = false
        firstFrameRendered = false
        lastLocalUrl = localUrl
        recoveryAttempts = 0
        cancelPlayTimeout()
        cancelRebufferRecovery()
        player.stop()
        player.clearMediaItems()
        player.trackSelectionParameters = TrackSelectionParameters.Builder(applicationContext)
            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, !audioEnabled)
            .build()
        FissionLogUtils.i(TAG, "ExoPlayer RTSP via relay url=$localUrl forceRtpTcp=true audio=$audioEnabled")
        player.setMediaSource(buildRtspMediaSource(localUrl))
        player.prepare()
        player.playWhenReady = true
        scheduleFirstFrameTimeout(localUrl)
    }

    private fun buildRtspMediaSource(localUrl: String): RtspMediaSource =
        RtspMediaSource.Factory()
            .setForceUseRtpTcp(true)
            .setTimeoutMs(RTSP_TIMEOUT_MS)
            .setDebugLoggingEnabled(RTSP_DEBUG)
            .createMediaSource(MediaItem.fromUri(localUrl))

    /** BUFFERING 持续超过 [REBUFFER_RECOVERY_MS] 仍未恢复 → 判定冻结，触发重载。 */
    private fun scheduleRebufferRecovery() {
        if (recoveryRunnable != null) return
        bufferingSinceMs = SystemClock.elapsedRealtime()
        val r = Runnable {
            recoveryRunnable = null
            attemptRebufferRecovery()
        }
        recoveryRunnable = r
        mainHandler.postDelayed(r, REBUFFER_RECOVERY_MS)
    }

    private fun cancelRebufferRecovery() {
        recoveryRunnable?.let { mainHandler.removeCallbacks(it) }
        recoveryRunnable = null
        bufferingSinceMs = 0L
    }

    private fun attemptRebufferRecovery() {
        if (released || !previewStarted) return
        val url = lastLocalUrl ?: return
        val player = exoPlayer ?: return
        // 若已自行恢复（非 BUFFERING）则跳过
        if (player.playbackState != Player.STATE_BUFFERING) return
        recoveryAttempts++
        if (recoveryAttempts > MAX_RECOVERY_ATTEMPTS) {
            FissionLogUtils.e(
                TAG,
                "[RECOVER] buffering persists after $MAX_RECOVERY_ATTEMPTS reloads; " +
                    "WiFi throughput too low → give up",
            )
            cancelRebufferRecovery()
            previewCallback?.onPreviewFailed(
                GlassesConstant.ERROR_CODE_LIVE_GLASSES_AP_LINK_LOST,
            )
            return
        }
        val stallMs = SystemClock.elapsedRealtime() - bufferingSinceMs
        FissionLogUtils.w(
            TAG,
            "[RECOVER] buffering ${stallMs}ms > ${REBUFFER_RECOVERY_MS}ms → reload RTSP " +
                "(attempt $recoveryAttempts/$MAX_RECOVERY_ATTEMPTS)",
        )
        firstFrameRendered = false
        cancelPlayTimeout()
        player.stop()
        player.clearMediaItems()
        player.setMediaSource(buildRtspMediaSource(url))
        player.prepare()
        player.playWhenReady = true
        scheduleFirstFrameTimeout(url)
    }

    private fun isApNetworkLostError(error: PlaybackException): Boolean {
        var cause: Throwable? = error.cause
        while (cause != null) {
            val message = cause.message.orEmpty()
            if (message.contains("ENONET", ignoreCase = true) ||
                message.contains("Machine is not on the network", ignoreCase = true)
            ) {
                return true
            }
            cause = cause.cause
        }
        return false
    }

    private fun scheduleFirstFrameTimeout(localUrl: String) {
        cancelPlayTimeout()
        playTimeoutRunnable = Runnable {
            if (!firstFrameRendered && !released) {
                FissionLogUtils.e(TAG, "ExoPlayer first-frame timeout (${FIRST_FRAME_TIMEOUT_MS}ms) url=$localUrl")
                previewCallback?.onPreviewFailed(
                    GlassesConstant.ERROR_CODE_LIVE_PREVIEW_START_FAILED,
                )
            }
        }
        mainHandler.postDelayed(playTimeoutRunnable!!, FIRST_FRAME_TIMEOUT_MS)
    }

    private fun cancelPlayTimeout() {
        playTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        playTimeoutRunnable = null
    }

    fun stop() {
        stopRelay()
        mainHandler.post {
            previewStarted = false
            firstFrameRendered = false
            pendingPreview = null
            cancelPlayTimeout()
            cancelRebufferRecovery()
            exoPlayer?.stop()
            exoPlayer?.clearMediaItems()
        }
    }

    fun release() {
        stopRelay()
        mainHandler.post {
            released = true
            previewStarted = false
            firstFrameRendered = false
            pendingPreview = null
            cancelPlayTimeout()
            cancelRebufferRecovery()
            detachPreviewViewInternal()
            exoPlayer?.release()
            exoPlayer = null
            previewCallback = null
            FissionLogUtils.d(TAG, "ExoPlayer preview released")
        }
    }

    private fun logPlaybackError(error: PlaybackException) {
        FissionLogUtils.e(
            TAG,
            "ExoPlayer error code=${error.errorCode} name=${error.errorCodeName} msg=${error.message}",
        )
        var cause: Throwable? = error.cause
        var depth = 0
        while (cause != null && depth < 8) {
            FissionLogUtils.e(
                TAG,
                "ExoPlayer cause[$depth] ${cause.javaClass.name}: ${cause.message}",
            )
            cause = cause.cause
            depth++
        }
    }

    companion object {
        private const val TAG = "LyLivePreviewController"
        private const val RTSP_DEBUG = false
        private const val RTSP_TIMEOUT_MS = 120_000L
        private const val FIRST_FRAME_TIMEOUT_MS = 25_000L

        /** BUFFERING 超过此时长判定为冻结，触发 RTSP 重载自恢复。 */
        private const val REBUFFER_RECOVERY_MS = 6_000L

        /** 连续重载上限；超过则判定链路不可用并上报失败（成功出帧后计数清零）。 */
        private const val MAX_RECOVERY_ATTEMPTS = 3
    }
}
