package com.lw.ai.glasses.utils

import android.content.Context
import com.fission.wear.glasses.sdk.constant.GlassesConstant
import com.fission.wear.glasses.sdk.live.LiveStreamErrors
import com.fission.wear.glasses.sdk.rtk.RtkOtaErrors
import com.fission.wear.glasses.sdk.rtk.RtkSoftApErrors
import com.lw.ai.glasses.R

/**
 * Demo 侧按 SDK 错误码解析展示文案。
 * 优先 App 定制；否则回落 SDK [LiveStreamErrors] / [RtkSoftApErrors] / [RtkOtaErrors] 默认说明，再回落 [fallbackReason]。
 */
object SdkErrorMessages {

    fun forLive(context: Context, code: Int, fallbackReason: String = ""): String {
        val customized = when (code) {
            LiveStreamErrors.CODE_WIFI_JOIN_REJECTED,
            RtkSoftApErrors.CODE_WIFI_JOIN_REJECTED,
            -> context.getString(R.string.error_rtk_wifi_join_rejected)
            LiveStreamErrors.CODE_PHONE_WIFI_OFF ->
                context.getString(R.string.error_live_phone_wifi_off)
            LiveStreamErrors.CODE_CELLULAR_UNAVAILABLE ->
                context.getString(R.string.error_live_cellular_unavailable)
            LiveStreamErrors.CODE_BATTERY_QUERY_FAILED ->
                context.getString(R.string.error_live_battery_query_failed)
            LiveStreamErrors.CODE_HOTSPOT_START_FAILED,
            RtkSoftApErrors.CODE_AP_ENABLE_FAILED,
            -> context.getString(R.string.error_rtk_ap_enable_failed)
            LiveStreamErrors.CODE_WIFI_JOIN_FAILED,
            RtkSoftApErrors.CODE_WIFI_JOIN_FAILED,
            -> context.getString(R.string.error_rtk_wifi_join_failed)
            LiveStreamErrors.CODE_WIFI_CONNECT_TIMEOUT,
            RtkSoftApErrors.CODE_WIFI_JOIN_TIMEOUT,
            -> context.getString(R.string.error_rtk_wifi_join_timeout)
            else -> null
        }
        if (!customized.isNullOrBlank()) return customized
        return LiveStreamErrors.defaultReason(code)
            .ifBlank { RtkSoftApErrors.defaultReason(code) }
            .ifBlank { fallbackReason }
            .ifBlank { context.getString(R.string.error_unknown_with_code, code) }
    }

    fun forFileSync(context: Context, code: Int, fallbackReason: String = ""): String {
        val customized = when (code) {
            in GlassesConstant.ERROR_CODE_WIFI_CONNECT_TIMEOUT..
                GlassesConstant.ERROR_CODE_WIFI_CLOSED ->
                lyWifiMessage(context, code)
            in GlassesConstant.ERROR_CODE_RTK_AP_ENABLE_FAILED..
                GlassesConstant.ERROR_CODE_RTK_WIFI_JOIN_TIMEOUT ->
                rtkSoftApMessage(context, code)
            in GlassesConstant.ERROR_CODE_DOWNLOAD_GET_FILE_LIST_FAILED..
                GlassesConstant.ERROR_CODE_DOWNLOAD_DELETE ->
                downloadMessage(context, code)
            GlassesConstant.ERROR_CODE_BLE_NOT_CONNECTED ->
                context.getString(R.string.error_ble_not_connected)
            else -> null
        }
        if (!customized.isNullOrBlank()) return customized
        return RtkSoftApErrors.defaultReason(code)
            .ifBlank { fallbackReason }
            .ifBlank { context.getString(R.string.file_sync_failed_unknown) }
    }

    fun forOta(context: Context, code: Int, fallbackReason: String = ""): String {
        val customized = when (code) {
            in GlassesConstant.ERROR_CODE_RTK_AP_ENABLE_FAILED..
                GlassesConstant.ERROR_CODE_RTK_WIFI_JOIN_TIMEOUT ->
                rtkSoftApMessage(context, code)
            in GlassesConstant.ERROR_CODE_RTK_OTA_PACKAGE_INVALID..
                GlassesConstant.ERROR_CODE_RTK_OTA_DFU_PROCEDURE_FAILED ->
                rtkOtaMessage(context, code)
            GlassesConstant.ERROR_CODE_OTA_FILE_NOT_FOUND ->
                context.getString(R.string.error_ota_file_not_found)
            GlassesConstant.ERROR_CODE_OTA_HANDSHAKE_FAILED ->
                context.getString(R.string.error_ota_handshake_failed)
            GlassesConstant.ERROR_CODE_OTA_TRANSFER_FAILED ->
                context.getString(R.string.error_ota_transfer_failed)
            GlassesConstant.ERROR_CODE_OTA_UPGRADE_FAILED ->
                context.getString(R.string.error_ota_upgrade_failed)
            GlassesConstant.ERROR_CODE_OTA_FILE_OR_VERSION_INVALID ->
                context.getString(R.string.error_ota_package_invalid)
            GlassesConstant.ERROR_CODE_BLE_NOT_CONNECTED ->
                context.getString(R.string.error_ble_not_connected)
            else -> null
        }
        if (!customized.isNullOrBlank()) return customized
        return RtkOtaErrors.defaultReason(code)
            .ifBlank { RtkSoftApErrors.defaultReason(code) }
            .ifBlank { fallbackReason }
            .ifBlank { context.getString(R.string.error_unknown_with_code, code) }
    }

    /** LY Wi‑Fi 连接失败（媒体同步） */
    fun isLyWifiConnectFail(code: Int): Boolean =
        code in GlassesConstant.ERROR_CODE_WIFI_CONNECT_TIMEOUT..
            GlassesConstant.ERROR_CODE_WIFI_CLOSED

    /** RTK SoftAP 开 AP / 连热点失败（媒体同步 / 直播 / OTA 共用） */
    fun isRtkSoftApFail(code: Int): Boolean =
        code in GlassesConstant.ERROR_CODE_RTK_AP_ENABLE_FAILED..
            GlassesConstant.ERROR_CODE_RTK_WIFI_JOIN_TIMEOUT

    fun isDownloadFail(code: Int): Boolean =
        code in GlassesConstant.ERROR_CODE_DOWNLOAD_GET_FILE_LIST_FAILED..
            GlassesConstant.ERROR_CODE_DOWNLOAD_DELETE

    private fun lyWifiMessage(context: Context, code: Int): String = when (code) {
        GlassesConstant.ERROR_CODE_WIFI_CONNECT_TIMEOUT ->
            context.getString(R.string.error_ly_wifi_connect_timeout)
        GlassesConstant.ERROR_CODE_WIFI_DEVICE_DISCOVERY_TIMEOUT ->
            context.getString(R.string.error_ly_wifi_device_discovery_timeout)
        GlassesConstant.ERROR_CODE_WIFI_NEGOTIATION_TIMEOUT ->
            context.getString(R.string.error_ly_wifi_negotiation_timeout)
        GlassesConstant.ERROR_CODE_WIFI_OPEN_ERROR ->
            context.getString(R.string.error_ly_wifi_open_error)
        GlassesConstant.ERROR_CODE_WIFI_NO_PERMISSION ->
            context.getString(R.string.error_ly_wifi_no_permission)
        GlassesConstant.ERROR_CODE_WIFI_NO_OPEN_LOCATION ->
            context.getString(R.string.error_ly_wifi_no_location)
        GlassesConstant.ERROR_CODE_WIFI_CLOSED ->
            context.getString(R.string.error_ly_wifi_closed)
        else -> context.getString(R.string.error_ly_wifi_unknown)
    }

    private fun rtkSoftApMessage(context: Context, code: Int): String = when (code) {
        RtkSoftApErrors.CODE_AP_ENABLE_FAILED ->
            context.getString(R.string.error_rtk_ap_enable_failed)
        RtkSoftApErrors.CODE_AP_INFO_UNAVAILABLE ->
            context.getString(R.string.error_rtk_ap_info_unavailable)
        RtkSoftApErrors.CODE_WIFI_JOIN_REJECTED ->
            context.getString(R.string.error_rtk_wifi_join_rejected)
        RtkSoftApErrors.CODE_WIFI_JOIN_FAILED ->
            context.getString(R.string.error_rtk_wifi_join_failed)
        RtkSoftApErrors.CODE_WIFI_JOIN_TIMEOUT ->
            context.getString(R.string.error_rtk_wifi_join_timeout)
        else -> RtkSoftApErrors.defaultReason(code)
    }

    private fun rtkOtaMessage(context: Context, code: Int): String = when (code) {
        RtkOtaErrors.CODE_PACKAGE_INVALID ->
            context.getString(R.string.error_ota_package_invalid)
        RtkOtaErrors.CODE_BT_FILE_NOT_FOUND ->
            context.getString(R.string.error_ota_file_not_found)
        RtkOtaErrors.CODE_DEVICE_ADDRESS_EMPTY ->
            context.getString(R.string.error_rtk_ota_device_address_empty)
        RtkOtaErrors.CODE_DFU_CONNECT_FAILED ->
            context.getString(R.string.error_rtk_ota_dfu_connect_failed)
        RtkOtaErrors.CODE_DFU_PREPARE_FAILED ->
            context.getString(R.string.error_rtk_ota_dfu_prepare_failed)
        RtkOtaErrors.CODE_PUSH_FAILED ->
            context.getString(R.string.error_rtk_ota_push_failed)
        RtkOtaErrors.CODE_ACTIVATE_FAILED ->
            context.getString(R.string.error_rtk_ota_activate_failed)
        RtkOtaErrors.CODE_DFU_PROCEDURE_FAILED ->
            context.getString(R.string.error_rtk_ota_dfu_procedure_failed)
        else -> RtkOtaErrors.defaultReason(code)
    }

    private fun downloadMessage(context: Context, code: Int): String = when (code) {
        GlassesConstant.ERROR_CODE_DOWNLOAD_GET_FILE_LIST_FAILED ->
            context.getString(R.string.error_download_get_file_list_failed)
        GlassesConstant.ERROR_CODE_DOWNLOAD_FILE_NOT_FOUND ->
            context.getString(R.string.error_download_file_not_found)
        GlassesConstant.ERROR_CODE_DOWNLOAD_NETWORK_ERROR ->
            context.getString(R.string.error_download_network_error)
        GlassesConstant.ERROR_CODE_DOWNLOAD_DELETE ->
            context.getString(R.string.error_download_delete)
        else -> context.getString(R.string.error_download_failed)
    }
}
