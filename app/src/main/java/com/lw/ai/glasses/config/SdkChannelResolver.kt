package com.lw.ai.glasses.config

import com.fission.wear.glasses.sdk.constant.GlassesConstant
import com.lw.top.lib_core.data.datastore.AppDataManager
import com.lw.top.lib_core.data.datastore.BluetoothDataManager

object SdkChannelResolver {

    val defaultChannel: GlassesConstant.ChannelType = GlassesConstant.ChannelType.LY

    fun fromName(name: String?): GlassesConstant.ChannelType? {
        return name
            ?.let { saved -> runCatching { GlassesConstant.ChannelType.valueOf(saved) }.getOrNull() }
    }

    /** 已绑定设备保存的 SDK 渠道；未绑定时返回 null。 */
    suspend fun loadBoundDeviceChannel(
        bluetoothDataManager: BluetoothDataManager,
    ): GlassesConstant.ChannelType? {
        if (bluetoothDataManager.getBluetoothAddress().isNullOrBlank()) return null
        return fromName(bluetoothDataManager.getSdkChannelName())
    }

    /**
     * SDK / AI 初始化用渠道：优先已绑定设备广播解析结果，兼容旧版手动配置，最后默认 LY。
     */
    suspend fun loadForSdkInit(
        bluetoothDataManager: BluetoothDataManager,
        appDataManager: AppDataManager? = null,
    ): GlassesConstant.ChannelType {
        loadBoundDeviceChannel(bluetoothDataManager)?.let { return it }
        appDataManager?.let { fromName(it.getSdkChannelName()) }?.let { return it }
        return defaultChannel
    }
}
