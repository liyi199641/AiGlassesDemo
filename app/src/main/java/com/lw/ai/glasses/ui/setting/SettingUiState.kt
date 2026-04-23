package com.lw.ai.glasses.ui.setting

import androidx.compose.runtime.Stable

data class SettingUiState(
    val settingItems: List<SettingItem> = emptyList(),
    val disconnectAction: DisconnectActionState = DisconnectActionState(),
    val isUnbinding: Boolean = false,
    val isSupportLiveSteaming: Boolean = false,
    val isSupportsQuickVolumeAdjust: Boolean = false
)

data class SelectOption<T>(
    val value: T,
    val title: String
)

@Stable
data class DisconnectActionState(
    val title: String = "断开连接",
    val isEnabled: Boolean = true
)

sealed interface SettingItem {
    data class ActionItem(
        val id: String,
        val title: String,
        val summary: String? = null,
        val isEnabled: Boolean = true
    ) : SettingItem

    data class SwitchItem(
        val id: String,
        val title: String,
        val summary: String? = null,
        val isChecked: Boolean,
        val isEnabled: Boolean = true
    ) : SettingItem

    data class InfoItem(
        val title: String,
        val value: String
    ) : SettingItem

    data class DropdownItem<T>(
        val id: String,
        val title: String,
        val selectedOption: SelectOption<T>,
        val options: List<SelectOption<T>>,
        val isEnabled: Boolean = true
    ) : SettingItem


    data object Divider : SettingItem
}