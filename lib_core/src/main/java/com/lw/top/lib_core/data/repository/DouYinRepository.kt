package com.lw.top.lib_core.data.repository

import ai.instavision.ffmpegkit.FFmpegKit
import ai.instavision.ffmpegkit.FFmpegSession
import ai.instavision.ffmpegkit.ReturnCode
import ai.instavision.ffmpegkit.SessionState
import android.app.Activity
import android.app.Application
import com.blankj.utilcode.util.LogUtils
import com.bytedance.android.openlive.broadcast.DouyinBroadcastApi
import com.bytedance.android.openlive.broadcast.api.AccountAuthCallback
import com.bytedance.android.openlive.broadcast.api.BroadcastInitConfig
import com.bytedance.android.openlive.broadcast.api.IBroadcastAuth
import com.bytedance.android.openlive.broadcast.api.InitBroadcastListener
import com.bytedance.android.openlive.broadcast.api.model.BroadcastPrivacyConfig
import com.bytedance.android.openlive.broadcast.api.model.CamType
import com.bytedance.android.openlive.broadcast.api.model.LiveAngle
import com.bytedance.android.openlive.broadcast.api.model.StartLiveResp
import com.bytedance.sdk.open.aweme.authorize.model.Authorization
import com.bytedance.sdk.open.douyin.DouYinOpenApiFactory
import com.bytedance.sdk.open.douyin.api.DouYinOpenApi
import com.lw.top.lib_core.data.repository.base.BaseRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject


class DouYinRepository @Inject constructor(
    private val application: Application
) : BaseRepository() {
    private lateinit var douYinOpenApi: DouYinOpenApi
    private var currentSession: FFmpegSession? = null
    private val mScope = "user_info,trial.whitelist"

    fun initDouyinSdk(activity: Activity) {
        if (DouyinBroadcastApi.isBroadcastInited()) {
            LogUtils.d("ly", "直播已初始化")
            return
        }
        douYinOpenApi = DouYinOpenApiFactory.create(activity)
        val config =
            BroadcastInitConfig.Builder(application, "843349", "FunSeek", "1.0.0", 1)
                    .privacyConfig(
                        BroadcastPrivacyConfig.Builder()
                                .isCanUseMac(false)
                                .isCanUseImei(false)
                                .build()
                    )
                    .isDebug(com.bytedance.android.openlive.broadcast.BuildConfig.DEBUG)
                    .setInitBroadcastListener(object : InitBroadcastListener {
                        override fun onInitializeSuccess() {
                            if (DouyinBroadcastApi.isAuthorized()) {
                                LogUtils.d(
                                    "ly",
                                    "初始化成功 openid: ${DouyinBroadcastApi.getAccessToken()?.openId}"
                                )
                            } else {
                                LogUtils.d("ly", "初始化成功, 未授权")
                            }
                        }

                        override fun onInitializeFail(msg: String?) {
                            LogUtils.d("ly", "初始化失败：$msg")
                        }
                    })
        DouyinBroadcastApi.showBroadcastInitLoading(activity)
        DouyinBroadcastApi.init(config.build())
    }

    fun douyinAuth(): Boolean{
        val request: Authorization.Request = Authorization.Request()
        request.scope = mScope // 用户授权时必选权限
        //        request.optionalScope0 = mOptionalScope1;    // 用户授权时可选权限（默认不选）
        request.state = "ww" // 用于保持请求和回调的状态，授权请求后原样带回给第三方。
        request.callerLocalEntry = "com.lw.ai.glasses.douyinapi.DouYinEntryActivity"
        return douYinOpenApi.authorize(request) // 优先使用抖音app进行授权，如果抖音app因版本或者其他原因无法授权，则使用wap页授权
    }

    fun login(activity: Activity) {
        if (!DouyinBroadcastApi.isBroadcastInited()) {
            LogUtils.d("ly", "未初始化")
            return
        }
        DouyinBroadcastApi.login(activity, object : AccountAuthCallback {
            override fun onSuccess() {
                val token = DouyinBroadcastApi.getAccessToken()
                if (token == null) {
                    LogUtils.d("ly", "授权成功，但 token 为空")
                } else {
                    LogUtils.d("ly", "授权成功，openid: ${token.openId}，${token.accessToken}")
                }
            }

            override fun onFailed(errorCode: Int, errorMsg: String?) {
                LogUtils.d("ly", "授权失败：errorCode $errorCode, errorMsg $errorMsg")
            }
        })
    }

    suspend fun getUserInfo(): Result<Any> = withContext(Dispatchers.IO) {
        if (!DouyinBroadcastApi.isBroadcastInited()) {
            return@withContext Result.failure(IllegalStateException("SDK 未初始化"))
        }

        if (!DouyinBroadcastApi.isAuthorized()) {
            return@withContext Result.failure(SecurityException("账号未授权"))
        }

        try {
            val response = DouyinBroadcastApi.getAccountInfo()

            if (response == null) {
                Result.failure(Exception("获取用户信息失败，网络请求返回 null"))
            } else if (response.statusCode != 0) {
                Result.failure(Exception("获取失败 code:${response.statusCode}, msg:${response.prompts}"))
            } else {
                Result.success(response)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun startBroadcast(): Result<StartLiveResp> =
        withContext(Dispatchers.IO) {
            if (!DouyinBroadcastApi.isBroadcastInited()) {
                return@withContext Result.failure(IllegalStateException("SDK 未初始化"))
            }
            if (!DouyinBroadcastApi.isAuthorized()) {
                return@withContext Result.failure(SecurityException("未授权"))
            }
            try {
                val resp = DouyinBroadcastApi.startBroadcast(LiveAngle.STANDARD, CamType.APP)
                if (resp == null) {
                    Result.failure(Exception("网络请求失败，返回为空"))
                } else if (resp.statusCode != 0) {
                    if (resp.statusCode == IBroadcastAuth.USER_UNAUTHORIZED) {
                        Result.failure(SecurityException("Token失效或未授权"))
                    } else {
                        Result.failure(Exception("开启失败 code:${resp.statusCode}, msg:${resp.prompts}"))
                    }
                } else {
                    Result.success(resp)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun closeBroadcast(openRoomId: String?): Result<Unit> = withContext(Dispatchers.IO) {
        if (openRoomId.isNullOrEmpty()) return@withContext Result.success(Unit)

        try {
            val resp = DouyinBroadcastApi.turnOffBroadcast(openRoomId)
            if (resp != null && resp.statusCode == 0) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("关闭失败: ${resp?.prompts}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun startFFmpegPush(rtspUrl:String, rtmpPushUrl: String) {
        // 先停止之前的推流（如果有）
        stopFFmpegPush()

        LogUtils.d("ly", "准备推流: $rtspUrl -> $rtmpPushUrl")

        // 当前 ffmpeg-kit 未启用 libx264，RTSP 的 H264 视频直接封装到 FLV 推到 RTMP。
        val cmdBuilder = StringBuilder()
        cmdBuilder.append("-rtsp_transport tcp ")
        cmdBuilder.append("-i ").append(rtspUrl.quoteForFFmpeg()).append(" ")
        cmdBuilder.append("-c:v copy ")
        cmdBuilder.append("-an ")
        cmdBuilder.append("-f flv ")
        cmdBuilder.append(rtmpPushUrl.quoteForFFmpeg())

        val command = cmdBuilder.toString()

        LogUtils.d("ly", "FFmpeg执行命令: $command")

        // ✅ 变化3: 调用 FFmpegKit.executeAsync
        // 这里的 lambda 就是 FFmpegSessionCompleteCallback
        currentSession = FFmpegKit.executeAsync(command) { session ->
            val state = session.state
            val returnCode = session.returnCode

            if (ReturnCode.isSuccess(returnCode)) {
                LogUtils.d("ly", "FFmpeg 推流结束 (成功)")
            } else if (ReturnCode.isCancel(returnCode)) {
                LogUtils.d("ly", "FFmpeg 推流已手动取消")
            } else {
                // 失败时，可以通过 session.failStackTrace 获取错误日志
                LogUtils.e("ly", "FFmpeg 推流失败, Code: $returnCode")
                LogUtils.e("ly", "错误日志: ${session.failStackTrace}")
            }
        }
    }

    private fun String.quoteForFFmpeg(): String {
        return "'${replace("'", "'\\''")}'"
    }

    fun stopFFmpegPush() {
        currentSession?.let { session ->
            if (session.state == SessionState.RUNNING || session.state == SessionState.CREATED) {
                session.cancel()
            }
        }
        currentSession = null
    }

}