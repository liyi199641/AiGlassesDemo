package com.lw.ai.glasses.utils

import androidx.annotation.StringRes
import com.fission.wear.glasses.sdk.constant.GlassesConstant
import com.lw.ai.glasses.R

@StringRes
fun GlassesConstant.ServerEnvironment.titleRes(): Int = when (this) {
    GlassesConstant.ServerEnvironment.DEV -> R.string.env_dev
    GlassesConstant.ServerEnvironment.TEST -> R.string.env_test
    GlassesConstant.ServerEnvironment.CHINA -> R.string.env_china
    GlassesConstant.ServerEnvironment.EUROPE -> R.string.env_europe
    GlassesConstant.ServerEnvironment.SINGAPORE -> R.string.env_singapore
    GlassesConstant.ServerEnvironment.CUSTOM -> R.string.env_local
}

@StringRes
fun GlassesConstant.ChannelType.titleRes(): Int = when (this) {
    GlassesConstant.ChannelType.TB -> R.string.sdk_channel_tb
    GlassesConstant.ChannelType.LY -> R.string.sdk_channel_ly
    GlassesConstant.ChannelType.RTK -> R.string.sdk_channel_rtk
    GlassesConstant.ChannelType.QC -> R.string.sdk_channel_qc
}

@StringRes
fun GlassesConstant.OtaType.titleRes(): Int = when (this) {
    GlassesConstant.OtaType.FIRMWARE -> R.string.ota_type_firmware
    GlassesConstant.OtaType.WIFI_ISP -> R.string.ota_type_wifi_isp
}

@StringRes
fun GlassesConstant.AudioVolumeType.titleRes(): Int = when (this) {
    GlassesConstant.AudioVolumeType.SYSTEM -> R.string.system_volume
    GlassesConstant.AudioVolumeType.MEDIA -> R.string.media_volume
    GlassesConstant.AudioVolumeType.CALL -> R.string.call_volume
}
