package com.lw.ai.glasses.config

import com.fission.wear.glasses.sdk.constant.GlassesConstant
import com.lw.top.lib_core.data.datastore.AppDataManager

object SdkChannelResolver {
    val selectableChannels: List<GlassesConstant.ChannelType> = listOf(
        GlassesConstant.ChannelType.TB,
        GlassesConstant.ChannelType.LY,
        GlassesConstant.ChannelType.RTK,
        GlassesConstant.ChannelType.QC,
    )

    val defaultChannel: GlassesConstant.ChannelType = GlassesConstant.ChannelType.LY

    fun fromName(name: String?): GlassesConstant.ChannelType {
        return name
            ?.let { saved -> runCatching { GlassesConstant.ChannelType.valueOf(saved) }.getOrNull() }
            ?: defaultChannel
    }

    suspend fun loadSaved(dataManager: AppDataManager): GlassesConstant.ChannelType {
        return fromName(dataManager.getSdkChannelName())
    }
}
