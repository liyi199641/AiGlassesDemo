import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color.Companion.White
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.blankj.utilcode.util.LogUtils
import com.lw.ai.glasses.ui.assistant.AiAssistantScreen
import com.lw.ai.glasses.ui.call.CallScreen
import com.lw.ai.glasses.ui.home.HomeScreen
import com.lw.ai.glasses.ui.image.ImageScreen
import com.lw.ai.glasses.ui.imageocr.ImageTranslateScreen
import com.lw.ai.glasses.ui.live.LiveScreen
import com.lw.ai.glasses.ui.setting.SettingScreen
import com.lw.ai.glasses.ui.translate.TranslatorScreen
import com.lw.ai.glasses.ui.update.UpdateScreen

@SuppressLint("RestrictedApi", "UnrememberedGetBackStackEntry")
@ExperimentalMaterial3Api
@Composable
fun AppNavHost() {
    val navController = rememberNavController()

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
        modifier = Modifier.background(White)
    ) {

        composable(Screen.Home.route) {
            HomeScreen(
                onNavigate = {
                    navController.navigate(it)
                }
            )
        }

        composable(Screen.Image.route) {
            ImageScreen(onNavigateBack = {
                navController.popBackStack()
            })
        }

        composable(Screen.Assistant.route) {
            AiAssistantScreen(onNavigateBack = {
                navController.popBackStack()
            })
        }

        composable(Screen.Setting.route) {
            SettingScreen(onNavigateBack = {
                navController.popBackStack()
            })
        }

        composable(Screen.Update.route) {
            UpdateScreen(onNavigateBack = {
                navController.popBackStack()
            })
        }

        composable(Screen.Translate.route) {
            TranslatorScreen(onNavigateBack = {
                navController.popBackStack()
            })
        }

        composable(Screen.Live.route) {
            LiveScreen(onNavigateBack = {
                navController.popBackStack()
            })
        }

        composable(Screen.Call.route) {
            CallScreen (onNavigateBack = {
                navController.popBackStack()
            })
        }

        composable(Screen.TranslateImage.route) {
            ImageTranslateScreen(onNavigateBack = {
                navController.popBackStack()
            })
        }

    }
}