package com.lw.ai.glasses.config

import com.fission.wear.glasses.sdk.constant.GlassesConstant

/**
 * Demo：解析 LY 眼镜产品系列（影响 AP 网关、媒体同步与直播预览地址）。
 *
 * T 系列（AP19 / 192.168.169.1）：名称含 AG17、AG19，或广播适配号 1700、1900。
 * 其余默认 S 系列（AG66 / 192.168.1.254）。
 */
object ProductSeriesResolver {

    private val T_SERIES_ADAPTER_NUMBERS = setOf("1700", "1900")

    fun resolve(
        deviceName: String?,
        adaptationNumber: String? = null,
    ): GlassesConstant.ProductSeries {
        if (isTSeriesAdaptationNumber(adaptationNumber)) {
            return GlassesConstant.ProductSeries.T
        }
        return fromDeviceName(deviceName)
    }

    fun fromDeviceName(deviceName: String?): GlassesConstant.ProductSeries {
        val name = deviceName?.trim().orEmpty()
        if (name.contains("AG19", ignoreCase = true) || name.contains("AG17", ignoreCase = true)) {
            return GlassesConstant.ProductSeries.T
        }
        return GlassesConstant.ProductSeries.S
    }

    private fun isTSeriesAdaptationNumber(adaptationNumber: String?): Boolean {
        val normalized = adaptationNumber?.trim()?.uppercase().orEmpty()
        return normalized in T_SERIES_ADAPTER_NUMBERS
    }
}
