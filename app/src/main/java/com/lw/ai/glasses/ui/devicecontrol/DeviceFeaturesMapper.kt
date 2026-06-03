package com.lw.ai.glasses.ui.devicecontrol

import com.fission.wear.glasses.sdk.data.model.GlassesFeaturesConfigInfo
import com.lw.ai.glasses.R

object DeviceFeaturesMapper {
    fun toFeatureSupportRows(config: GlassesFeaturesConfigInfo): List<FeatureSupportRow> {
        return listOf(
            FeatureSupportRow(R.string.feature_support_live_streaming, config.supportLiveStreaming),
            FeatureSupportRow(R.string.feature_support_quickly_adjust_volume, config.supportQuicklyAdjustVolume),
            FeatureSupportRow(R.string.feature_support_photo_watermark, config.supportPhotoWatermark),
            FeatureSupportRow(R.string.feature_support_wear_detection_dynamic, config.supportWearDetectionDynamic),
            FeatureSupportRow(R.string.feature_support_wear_detection, config.supportWearDetection),
            FeatureSupportRow(R.string.feature_support_anti_shake_dynamic, config.supportAntiShakeDynamic),
            FeatureSupportRow(R.string.feature_support_anti_shake, config.supportAntiShake),
            FeatureSupportRow(R.string.feature_support_ai_wakeup_switch, config.supportAiWakeupSwitch),
            FeatureSupportRow(R.string.feature_support_local_offline_voice, config.supportLocalOfflineVoice),
            FeatureSupportRow(R.string.feature_support_dynamic_language_switch, config.supportDynamicLanguageSwitch),
            FeatureSupportRow(R.string.feature_support_orientation_dynamic, config.supportOrientationDynamic),
            FeatureSupportRow(R.string.feature_support_orientation, config.supportOrientation),
            FeatureSupportRow(R.string.feature_support_voice_command_status, config.supportVoiceCommandStatus),
        )
    }
}
