package com.lw.ai.glasses.config

import com.fission.wear.glasses.sdk.constant.GlassesConstant

/**
 * Demo：根据蓝牙设备名称判断眼镜产品系列。
 * 当前约定：名称包含 AG19 为 T 系列，其余默认 S 系列。
 */
object ProductSeriesResolver {

    fun fromDeviceName(deviceName: String?): GlassesConstant.ProductSeries {
        val name = deviceName?.trim().orEmpty()
        if (name.contains("AG19", ignoreCase = true)) {
            return GlassesConstant.ProductSeries.T
        }
        return GlassesConstant.ProductSeries.S
    }
}
