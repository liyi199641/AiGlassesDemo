package com.lw.ai.glasses.utils

import androidx.annotation.StringRes
import com.fission.wear.glasses.sdk.constant.GlassesConstant
import com.fission.wear.glasses.sdk.constant.LyCmdConstant
import com.lw.ai.glasses.R

@StringRes
fun GlassesConstant.ServerEnvironment.titleRes(): Int = when (this) {
    GlassesConstant.ServerEnvironment.DEV -> R.string.env_dev
    GlassesConstant.ServerEnvironment.TEST -> R.string.env_test
    GlassesConstant.ServerEnvironment.CHINA -> R.string.env_china
    GlassesConstant.ServerEnvironment.EUROPE -> R.string.env_europe
    GlassesConstant.ServerEnvironment.SINGAPORE -> R.string.env_singapore
    GlassesConstant.ServerEnvironment.LOCAL -> R.string.env_local
    GlassesConstant.ServerEnvironment.XFUSION_DIRECT,
    GlassesConstant.ServerEnvironment.XFUSION_DIRECT_VOICE_TEST -> R.string.env_dev
}

private val hiddenServerEnvironments = setOf(
    GlassesConstant.ServerEnvironment.XFUSION_DIRECT,
    GlassesConstant.ServerEnvironment.XFUSION_DIRECT_VOICE_TEST,
)

fun GlassesConstant.ServerEnvironment.isSelectableInUi(): Boolean =
    this !in hiddenServerEnvironments

fun selectableServerEnvironments(): List<GlassesConstant.ServerEnvironment> =
    GlassesConstant.ServerEnvironment.entries.filter { it.isSelectableInUi() }

fun GlassesConstant.ServerEnvironment.toPersistedEnvironmentOrDefault(): GlassesConstant.ServerEnvironment =
    if (isSelectableInUi()) this else GlassesConstant.ServerEnvironment.DEV

@StringRes
fun GlassesConstant.OtaType.titleRes(): Int = when (this) {
    GlassesConstant.OtaType.FIRMWARE -> R.string.ota_type_firmware
    GlassesConstant.OtaType.WIFI_ISP -> R.string.ota_type_wifi_isp
}

@StringRes
fun LyCmdConstant.AudioVolumeType.titleRes(): Int = when (this) {
    LyCmdConstant.AudioVolumeType.SYSTEM -> R.string.system_volume
    LyCmdConstant.AudioVolumeType.MEDIA -> R.string.media_volume
    LyCmdConstant.AudioVolumeType.CALL -> R.string.call_volume
}
