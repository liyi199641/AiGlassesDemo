package com.lw.ai.glasses.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.NavHostController

/**
 * 安全返回：仅在非首页时出栈至 [Screen.Home]；已在首页时不再 pop，避免连续点击把栈弹空（绿屏）。
 * 多次调用也只会退一层子页面，不会重复出栈。
 */
fun NavHostController.popBackStackSafely(): Boolean {
    if (currentDestination?.route == Screen.Home.route) return false
    if (previousBackStackEntry == null) return false
    return popBackStack(Screen.Home.route, inclusive = false, saveState = false)
}

@Composable
fun rememberSafeNavigateBack(navController: NavHostController): () -> Unit {
    return remember(navController) {
        { navController.popBackStackSafely() }
    }
}
