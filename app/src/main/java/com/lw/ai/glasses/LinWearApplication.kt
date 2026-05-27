package com.lw.ai.glasses

import android.app.Application
import com.blankj.utilcode.util.LogUtils
import com.blankj.utilcode.util.Utils
import com.bytedance.sdk.open.douyin.DouYinOpenApiFactory
import com.bytedance.sdk.open.douyin.DouYinOpenConfig
import com.lw.ai.glasses.startup.AppStartupReconnectManager
import dagger.hilt.android.HiltAndroidApp
import io.reactivex.rxjava3.exceptions.UndeliverableException
import io.reactivex.rxjava3.plugins.RxJavaPlugins
import java.io.IOException
import java.net.SocketException
import javax.inject.Inject

@HiltAndroidApp
class LinWearApplication : Application(){

    @Inject
    lateinit var startupReconnectManager: AppStartupReconnectManager

    override fun onCreate() {
        super.onCreate()

        Utils.init(this)

        val logDirPath = filesDir.absolutePath

        LogUtils.getConfig().apply {
            setConsoleSwitch(true)
            setLog2FileSwitch(true)
            setDir(logDirPath)
            setSaveDays(7)
            setFilePrefix("AiGlass")
        }

        val clientkey = "xxx" // 需要到开发者网站申请并替换
        DouYinOpenApiFactory.init(DouYinOpenConfig(clientkey))

        setupRxJavaErrorHandler()
        startupReconnectManager.start()
    }

    private fun setupRxJavaErrorHandler() {
        RxJavaPlugins.setErrorHandler { throwable ->
            if (throwable is UndeliverableException) {
                val e = throwable.cause
                if (e is IOException || e is SocketException) {
                    LogUtils.w("RxErrorHandler", "Ignoring benign network exception: $e")
                    return@setErrorHandler
                }
                if (e is InterruptedException) {
                    LogUtils.w("RxErrorHandler", "Ignoring InterruptedException: $e")
                    return@setErrorHandler
                }
            }
            LogUtils.e("RxErrorHandler", "Undeliverable exception received", throwable)
        }
    }

}
