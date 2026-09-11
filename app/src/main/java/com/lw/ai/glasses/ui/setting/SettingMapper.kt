package com.lw.ai.glasses.ui.setting

import android.content.Context
import com.fission.wear.glasses.sdk.constant.GlassesConstant
import com.lw.ai.glasses.R

object SettingMapper {

    fun toLedBrightnessOptions(context: Context): List<SelectOption<GlassesConstant.LedBrightnessLevel>> {
        return listOf(
            SelectOption(GlassesConstant.LedBrightnessLevel.LOW, context.getString(R.string.brightness_low)),
            SelectOption(GlassesConstant.LedBrightnessLevel.MEDIUM, context.getString(R.string.brightness_medium)),
            SelectOption(GlassesConstant.LedBrightnessLevel.HIGH, context.getString(R.string.brightness_high))
        )
    }

    fun toScreenOrientationOptions(context: Context): List<SelectOption<GlassesConstant.ScreenOrientation>> {
        return listOf(
            SelectOption(GlassesConstant.ScreenOrientation.PORTRAIT, context.getString(R.string.portrait)),
            SelectOption(GlassesConstant.ScreenOrientation.LANDSCAPE, context.getString(R.string.landscape))
        )
    }

    fun toGestureTypeTitle(context: Context, gestureType: GlassesConstant.GestureType): String {
        return when (gestureType) {
            GlassesConstant.GestureType.SLIDE_FORWARD -> context.getString(R.string.gesture_slide_forward)
            GlassesConstant.GestureType.SLIDE_BACKWARD -> context.getString(R.string.gesture_slide_backward)
            GlassesConstant.GestureType.SINGLE_TAP -> context.getString(R.string.gesture_single_tap)
            GlassesConstant.GestureType.DOUBLE_TAP -> context.getString(R.string.gesture_double_tap)
            GlassesConstant.GestureType.TRIPLE_TAP -> context.getString(R.string.gesture_triple_tap)
        }
    }

    fun toGestureActionOptions(context: Context): List<SelectOption<GlassesConstant.GestureAction>> {
        return listOf(
            SelectOption(GlassesConstant.GestureAction.VOLUME_DOWN, context.getString(R.string.gesture_volume_down)),
            SelectOption(GlassesConstant.GestureAction.VOLUME_UP, context.getString(R.string.gesture_volume_up)),
            SelectOption(GlassesConstant.GestureAction.PLAY_PAUSE, context.getString(R.string.gesture_play_pause)),
            SelectOption(GlassesConstant.GestureAction.PREVIOUS, context.getString(R.string.gesture_previous)),
            SelectOption(GlassesConstant.GestureAction.NEXT, context.getString(R.string.gesture_next)),
            SelectOption(GlassesConstant.GestureAction.HANG_UP, context.getString(R.string.gesture_hang_up))
        )
    }


}