
sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Image : Screen("sync_photos")
    object Assistant : Screen("assistant")
    object Setting : Screen("glasses_settings")
    object Update : Screen("ota_update")
    object Translate : Screen("ai_translate")
    object TranslateImage : Screen("ai_translate_image")
    object Live : Screen("live_streaming")
    object Call : Screen("av_call")

}