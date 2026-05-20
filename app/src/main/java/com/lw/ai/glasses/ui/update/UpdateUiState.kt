package com.lw.ai.glasses.ui.update

import com.fission.wear.glasses.sdk.constant.GlassesConstant

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
    val availableOtaTypes: List<GlassesConstant.OtaType> = listOf(GlassesConstant.OtaType.FIRMWARE, GlassesConstant.OtaType.WIFI_ISP),
    val selectedOtaType: GlassesConstant.OtaType = GlassesConstant.OtaType.FIRMWARE,
)

data class FirmwareFile(
    val id: String,
    val name: String,
    val path: String,
    val sizeInMb: Float,
    val addedTime: Long
) {
    fun toJson(): String {
        return "$id|SPL|$name|SPL|$path|SPL|$sizeInMb|SPL|$addedTime"
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
                    addedTime = parts[4].toLong()
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}
