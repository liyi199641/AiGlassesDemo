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
import androidx.annotation.StringRes
import androidx.compose.material.icons.filled.Functions
import androidx.compose.ui.graphics.vector.ImageVector
import com.fission.wear.glasses.sdk.constant.GlassesConstant
import com.lw.ai.glasses.R
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
    val isScanning: Boolean = false,
    val connectionState: ConnectionState = ConnectionState.IDLE,
    val batteryLevel: Int = -1,
    val isCharging: Boolean? = null,
    val connectedDeviceName: String? = null,
    val pendingSyncPhotosCount: Int = 0,
    val selectedEnvironment: GlassesConstant.ServerEnvironment = GlassesConstant.ServerEnvironment.CHINA,
    val localEnvironmentWsUrl: String = GlassesConstant.ServerEnvironment.LOCAL.wsUrl,
    val features: List<Feature> = emptyList()
) {
    companion object {
        fun initialFeatures(pendingSyncPhotosCount: Int): List<Feature> {
            return listOf(
                Feature(
                    id = "sync_photos", // 使用路由作为唯一ID
                    nameRes = R.string.feature_sync_photos,
                    icon = Icons.Default.Sync,
                    route = "sync_photos",
                    badgeCount = pendingSyncPhotosCount
                ),
                Feature(
                    id = "device_control",
                    nameRes = R.string.feature_device_control,
                    icon = Icons.Default.Functions,
                    route = "device_control"
                ),
                Feature(
                    id = "ai_chat",
                    nameRes = R.string.feature_ai_chat,
                    icon = Icons.Default.QuestionAnswer,
                    route = "assistant"
                ),
                Feature(
                    id = "ai_translate",
                    nameRes = R.string.feature_ai_translate,
                    icon = Icons.Default.Translate,
                    route = "ai_translate"
                ),
                Feature(
                    id = "ai_translate_image",
                    nameRes = R.string.feature_ai_image_translate,
                    icon = Icons.Default.ImageSearch,
                    route = "ai_translate_image"
                ),
                Feature(
                    id = "live_streaming",
                    nameRes = R.string.feature_live_streaming,
                    icon = Icons.Default.LiveTv,
                    route = "live_streaming"
                ),
                Feature(
                    id = "av_call",
                    nameRes = R.string.feature_av_call,
                    icon = Icons.Default.VideoCall, // 如果没有 VideoCall，也可以用 Icons.Default.Call
                    route = "av_call"
                ),
                Feature(
                    id = "glasses_settings",
                    nameRes = R.string.feature_glasses_settings,
                    icon = Icons.Default.Settings,
                    route = "glasses_settings"
                ),
                Feature(
                    id = "ota_update",
                    nameRes = R.string.feature_ota_update,
                    icon = Icons.Default.SystemUpdate,
                    route = "ota_update"
                )
            )
        }
    }
}

data class Feature(
    val id: String,
    @StringRes val nameRes: Int,
    val icon: ImageVector,
    val route: String,
    val badgeCount: Int? = null
)