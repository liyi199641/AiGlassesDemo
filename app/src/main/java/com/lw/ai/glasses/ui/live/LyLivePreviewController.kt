package com.lw.ai.glasses.ui.live

import android.content.Context
import android.net.Network
import android.os.Handler
import android.os.Looper
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
 * Demo 侧 LY 直播预览：ExoPlayer RTSP + SDK 返回的 AP [Network.socketFactory] 分流。
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

    fun startPreview(rtspUrl: String, apNetwork: Network) {
        mainHandler.post {
            if (released) return@post
            if (playerView == null) {
                pendingPreview = PendingPreview(rtspUrl, apNetwork)
                FissionLogUtils.d(TAG, "ExoPlayer startPreview deferred, waiting for view url=$rtspUrl")
                return@post
            }
            startPreviewInternal(rtspUrl, apNetwork)
        }
    }

    private fun startPreviewInternal(rtspUrl: String, apNetwork: Network) {
        playerView?.post {
            playOnApNetwork(rtspUrl, apNetwork)
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
            .setBufferDurationsMs(1_500, 5_000, 500, 1_000)
            .build()
        val trackSelector = DefaultTrackSelector(applicationContext).apply {
            parameters = buildUponParameters()
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
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
                            "ExoPlayer state=$playbackState isPlaying=${player.isPlaying}",
                        )
                        if (playbackState == Player.STATE_ENDED) {
                            FissionLogUtils.w(TAG, "ExoPlayer ended unexpectedly")
                            previewCallback?.onPreviewFailed(
                                GlassesConstant.ERROR_CODE_LIVE_PREVIEW_START_FAILED,
                            )
                        }
                    }

                    override fun onRenderedFirstFrame() {
                        FissionLogUtils.i(TAG, "ExoPlayer first frame rendered")
                        firstFrameRendered = true
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
        FissionLogUtils.i(TAG, "ExoPlayer preview started")
        previewCallback?.onPreviewStarted()
    }

    private fun playOnApNetwork(rtspUrl: String, apNetwork: Network) {
        if (released) return
        val player = ensurePlayerOnMainThread()
        previewStarted = false
        firstFrameRendered = false
        cancelPlayTimeout()
        player.stop()
        player.clearMediaItems()
        player.trackSelectionParameters = TrackSelectionParameters.Builder(applicationContext)
            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
            .build()
        val mediaSource = RtspMediaSource.Factory()
            .setForceUseRtpTcp(true)
            .setTimeoutMs(RTSP_TIMEOUT_MS)
            // 眼镜 RTSP(LIVE555) 只允许 PLAY 真实流(Content-Base=00000001)，PLAY xxx.mov 恒 404；
            // 这里保留 DESCRIBE/连接在 xxx.mov(触发推流)，把出向 PLAY/keep-alive 改写为 Content-Base，
            // 使 SETUP 与 PLAY 同流→200，且 keep-alive 打在会话真实流上避免超时断流；底层仍走 AP 分流。
            .setSocketFactory(RtspPlayRewriteSocketFactory(apNetwork.socketFactory))
            .setDebugLoggingEnabled(RTSP_DEBUG)
            .createMediaSource(MediaItem.fromUri(rtspUrl))
        FissionLogUtils.i(
            TAG,
            "ExoPlayer RTSP via AP socketFactory network=$apNetwork url=$rtspUrl (video-only)",
        )
        player.setMediaSource(mediaSource)
        player.prepare()
        player.playWhenReady = true
        scheduleFirstFrameTimeout(rtspUrl)
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

    private fun scheduleFirstFrameTimeout(rtspUrl: String) {
        cancelPlayTimeout()
        playTimeoutRunnable = Runnable {
            if (!firstFrameRendered && !released) {
                FissionLogUtils.e(TAG, "ExoPlayer first-frame timeout (15s) url=$rtspUrl")
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
        mainHandler.post {
            previewStarted = false
            firstFrameRendered = false
            pendingPreview = null
            cancelPlayTimeout()
            exoPlayer?.stop()
            exoPlayer?.clearMediaItems()
        }
    }

    fun release() {
        mainHandler.post {
            released = true
            previewStarted = false
            firstFrameRendered = false
            pendingPreview = null
            cancelPlayTimeout()
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

    private companion object {
        private const val TAG = "LyLivePreviewController"
        // RTSP 信令调试开关：需要抓 RtspClient 的 OPTIONS/DESCRIBE/SETUP/PLAY 信令时临时置 true。
        private const val RTSP_DEBUG = false
        private const val RTSP_TIMEOUT_MS = 120_000L
        private const val FIRST_FRAME_TIMEOUT_MS = 15_000L
    }
}
