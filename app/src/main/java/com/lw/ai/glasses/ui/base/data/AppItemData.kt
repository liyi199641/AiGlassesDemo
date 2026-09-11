
import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color

enum class AppItemType {
    SECURITY_CENTER,
    LANGUAGE_SWITCHING,
    CONTACT_SERVICE,
    USER_AVATAR,
    USER_NAME,
    USER_EMAIL,
    SETTINGS_ABOUT,
    SETTINGS_CACHE,
    SETTINGS_NETWORK,
    SETTINGS_DELETE_ACCOUNT,
    SETTINGS_SWITCH_ACCOUNT,
    SETTINGS_LOGOUT,
    ABOUT_UPDATES,
    ABOUT_PRIVACY_AGREEMENT,
    ABOUT_TERMS_SERVICE,
    ABOUT_RESOURCE_VERSION,
    LOGIN_PASSWORD,
    TRANSACTION_PASSWORD
}


data class AppItemData(
    val type: AppItemType,
    val bgColor: Color = CardBackground,
    @DrawableRes val iconRes: Int? = null,
    @DrawableRes val titleRes: Int,
    var badgeText: String? = null,
    val tagText: String? = null,
    var trailingPath: String? = null,
)
