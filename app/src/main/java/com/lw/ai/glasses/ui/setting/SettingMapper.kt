package com.lw.ai.glasses.ui.setting

import android.content.Context
import com.fission.wear.glasses.sdk.constant.LyCmdConstant
import com.lw.ai.glasses.R

object SettingMapper {

    fun toLedBrightnessOptions(context: Context): List<SelectOption<LyCmdConstant.LedBrightnessLevel>> {
        return listOf(
            SelectOption(LyCmdConstant.LedBrightnessLevel.LOW, context.getString(R.string.brightness_low)),
            SelectOption(LyCmdConstant.LedBrightnessLevel.MEDIUM, context.getString(R.string.brightness_medium)),
            SelectOption(LyCmdConstant.LedBrightnessLevel.HIGH, context.getString(R.string.brightness_high))
        )
    }

    fun toScreenOrientationOptions(context: Context): List<SelectOption<LyCmdConstant.ScreenOrientation>> {
        return listOf(
            SelectOption(LyCmdConstant.ScreenOrientation.PORTRAIT, context.getString(R.string.portrait)),
            SelectOption(LyCmdConstant.ScreenOrientation.LANDSCAPE, context.getString(R.string.landscape))
        )
    }

    fun toGestureTypeTitle(context: Context, gestureType: LyCmdConstant.GestureType): String {
        return when (gestureType) {
            LyCmdConstant.GestureType.SLIDE_FORWARD -> context.getString(R.string.gesture_slide_forward)
            LyCmdConstant.GestureType.SLIDE_BACKWARD -> context.getString(R.string.gesture_slide_backward)
            LyCmdConstant.GestureType.SINGLE_TAP -> context.getString(R.string.gesture_single_tap)
            LyCmdConstant.GestureType.DOUBLE_TAP -> context.getString(R.string.gesture_double_tap)
            LyCmdConstant.GestureType.TRIPLE_TAP -> context.getString(R.string.gesture_triple_tap)
        }
    }

    fun toGestureActionOptions(context: Context): List<SelectOption<LyCmdConstant.GestureAction>> {
        return listOf(
            SelectOption(LyCmdConstant.GestureAction.VOLUME_DOWN, context.getString(R.string.gesture_volume_down)),
            SelectOption(LyCmdConstant.GestureAction.VOLUME_UP, context.getString(R.string.gesture_volume_up)),
            SelectOption(LyCmdConstant.GestureAction.PLAY_PAUSE, context.getString(R.string.gesture_play_pause)),
            SelectOption(LyCmdConstant.GestureAction.PREVIOUS, context.getString(R.string.gesture_previous)),
            SelectOption(LyCmdConstant.GestureAction.NEXT, context.getString(R.string.gesture_next)),
            SelectOption(LyCmdConstant.GestureAction.HANG_UP, context.getString(R.string.gesture_hang_up))
        )
    }


}