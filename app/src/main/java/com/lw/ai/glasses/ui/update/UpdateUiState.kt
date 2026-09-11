package com.lw.ai.glasses.ui.update

import com.fission.wear.glasses.sdk.constant.GlassesConstant
import com.fission.wear.glasses.sdk.data.dto.DeviceVersionInfoDTO

enum class OtaStatus {
    IDLE,           // 空闲状态
    CONNECTING,     // 正在连接
    CONNECTED,      // 已连接
    FILE_SELECTING, // 正在选择文件
    READY_TO_UPGRADE, // 文件已选，准备升级
    UPGRADING,      // 正在升级
    SUCCESS,        // 升级成功
    FAILED          // 升级失败
}

data class UpdateUiState(
    val otaStatus: OtaStatus = OtaStatus.IDLE,
    val progress: Int = 0,
    val statusText: String = "",
    val recentFiles: List<FirmwareFile> = emptyList(),
    val selectedFileId: String? = null,
    /** RTK 方案选中的 WiFi 固件压缩包 id（其他方案不使用）。 */
    val selectedWifiZipId: String? = null,
    /** 当前 SDK 渠道，RTK 方案需要 BT 文件 + WiFi 压缩包组合升级。 */
    val currentChannel: GlassesConstant.ChannelType = GlassesConstant.ChannelType.LY,
    /** 设备当前版本信息（固件 / WiFi / 硬件）。 */
    val deviceVersionInfo: DeviceVersionInfoDTO? = null,
    /** 从文件名解析出的版本号，可手动修改后用于 OTA。 */
    val firmwareVersion: String = "",
    val availableOtaTypes: List<GlassesConstant.OtaType> = listOf(GlassesConstant.OtaType.FIRMWARE, GlassesConstant.OtaType.WIFI_ISP),
    val selectedOtaType: GlassesConstant.OtaType = GlassesConstant.OtaType.FIRMWARE,
) {
    val isRtk: Boolean get() = currentChannel == GlassesConstant.ChannelType.RTK
}

data class FirmwareFile(
    val id: String,
    val name: String,
    val path: String,
    val sizeInMb: Float,
    val addedTime: Long,
    /** true 表示这是一个 WiFi 固件压缩包（RTK WiFi 升级包，内含 ota.json，由 SDK 解压）。 */
    val isWifiZip: Boolean = false
) {
    fun toJson(): String {
        return "$id|SPL|$name|SPL|$path|SPL|$sizeInMb|SPL|$addedTime|SPL|$isWifiZip"
    }

    companion object {
        fun fromJson(json: String): FirmwareFile? {
            return try {
                val parts = json.split("|SPL|")
                FirmwareFile(
                    id = parts[0],
                    name = parts[1],
                    path = parts[2],
                    sizeInMb = parts[3].toFloat(),
                    addedTime = parts[4].toLong(),
                    isWifiZip = parts.getOrNull(5)?.toBooleanStrictOrNull() ?: false
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}
