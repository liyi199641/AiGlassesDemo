import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.blankj.utilcode.util.LogUtils
import com.lw.ai.glasses.navigation.rememberSafeNavigateBack
import com.lw.ai.glasses.ui.assistant.AiAssistantScreen
import com.lw.ai.glasses.ui.call.CallScreen
import com.lw.ai.glasses.ui.devicecontrol.DeviceControlScreen
import com.lw.ai.glasses.ui.home.HomeScreen
import com.lw.ai.glasses.ui.image.ImageScreen
import com.lw.ai.glasses.ui.imageocr.ImageTranslateScreen
import com.lw.ai.glasses.ui.live.LiveScreen
import com.lw.ai.glasses.ui.setting.SettingScreen
import com.lw.ai.glasses.ui.theme.PrimaryBackground
import com.lw.ai.glasses.ui.translate.TranslatorScreen
import com.lw.ai.glasses.ui.update.UpdateScreen

@SuppressLint("RestrictedApi", "UnrememberedGetBackStackEntry")
@ExperimentalMaterial3Api
@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    val navigateBack = rememberSafeNavigateBack(navController)

    LaunchedEffect(navController) {
        navController.currentBackStack.collect { backStackEntries ->
            val screenRoutes = backStackEntries.mapNotNull { it.destination.route }
            LogUtils.dTag(
                "AppNavHost",
                "Current Screen Back Stack: ${screenRoutes.joinToString(" -> ")}"
            )
        }
    }

    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = Modifier.background(PrimaryBackground)
    ) {

        composable(Screen.Home.route) {
            HomeScreen(
                onNavigate = {
                    navController.navigate(it)
                }
            )
        }

        composable(Screen.Image.route) {
            ImageScreen(onNavigateBack = navigateBack)
        }

        composable(Screen.DeviceControl.route) {
            DeviceControlScreen(onNavigateBack = navigateBack)
        }

        composable(Screen.Assistant.route) {
            AiAssistantScreen(onNavigateBack = navigateBack)
        }

        composable(Screen.Setting.route) {
            SettingScreen(onNavigateBack = navigateBack)
        }

        composable(Screen.Update.route) {
            UpdateScreen(onNavigateBack = navigateBack)
        }

        composable(Screen.Translate.route) {
            TranslatorScreen(onNavigateBack = navigateBack)
        }

        composable(Screen.Live.route) {
            LiveScreen(onNavigateBack = navigateBack)
        }
        composable(Screen.Call.route) {
            CallScreen(onNavigateBack = navigateBack)
        }

        composable(Screen.TranslateImage.route) {
            ImageTranslateScreen(onNavigateBack = navigateBack)
        }

    }
}