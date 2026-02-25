package com.lw.ai.glasses.ui.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.VideoCall
import androidx.compose.ui.graphics.vector.ImageVector
import com.polidea.rxandroidble3.scan.ScanResult

enum class ConnectionState(val value: Int) {
    IDLE(0),
    CONNECTING(1),
    CONNECTED(2),
    DISCONNECTED(3);

    companion object {
        fun fromValue(value: Int): ConnectionState {
            return entries.find { it.value == value } ?: IDLE
        }
    }
}

data class HomeUiState(
    val scannedDevices: List<ScanResult> = emptyList(),
    val isScanning: Boolean = false,
    val connectionState: ConnectionState = ConnectionState.IDLE,
    val batteryLevel: Int = -1,
    val isCharging: Boolean? = null,
    val connectedDeviceName: String? = null,
    val pendingSyncPhotosCount: Int = 0,
    val features: List<Feature> = emptyList()
) {
    companion object {
        fun initialFeatures(pendingSyncPhotosCount: Int): List<Feature> {
            return listOf(
                Feature(
                    id = "sync_photos", // 使用路由作为唯一ID
                    name = "同步图片",
                    icon = Icons.Default.Sync,
                    route = "sync_photos",
                    badgeCount = pendingSyncPhotosCount
                ),
                Feature(
                    id = "ai_chat",
                    name = "AI对话/识图",
                    icon = Icons.Default.QuestionAnswer,
                    route = "assistant"
                ),
                Feature(
                    id = "ai_translate",
                    name = "AI翻译",
                    icon = Icons.Default.Translate,
                    route = "ai_translate"
                ),
                Feature(
                    id = "ai_translate_image",
                    name = "AI图片翻译",
                    icon = Icons.Default.ImageSearch,
                    route = "ai_translate_image"
                ),
                Feature(
                    id = "live_streaming",
                    name = "直播",
                    icon = Icons.Default.LiveTv,
                    route = "live_streaming"
                ),
                Feature(
                    id = "av_call",
                    name = "音视频通话",
                    icon = Icons.Default.VideoCall, // 如果没有 VideoCall，也可以用 Icons.Default.Call
                    route = "av_call"
                ),
                Feature(
                    id = "glasses_settings",
                    name = "眼镜设置",
                    icon = Icons.Default.Settings,
                    route = "glasses_settings"
                ),
                Feature(
                    id = "ota_update",
                    name = "固件升级",
                    icon = Icons.Default.SystemUpdate,
                    route = "ota_update"
                )
            )
        }
    }
}

data class Feature(
    val id: String,
    val name: String,
    val icon: ImageVector,
    val route: String,
    val badgeCount: Int? = null
)