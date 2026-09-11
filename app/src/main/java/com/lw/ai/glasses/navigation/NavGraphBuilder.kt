
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDeepLink
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

// 定义你的动画常量
const val DEFAULT_ANIMATION_DURATION_MS = 300

// 默认的进入动画：从右侧滑入
val defaultEnterTransition: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition?) = {
    slideInHorizontally(
        initialOffsetX = { it },
        animationSpec = tween(DEFAULT_ANIMATION_DURATION_MS)
    )
}

// 默认的退出动画：向左侧滑出
val defaultExitTransition: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition?) = {
    slideOutHorizontally(
        targetOffsetX = { -it },
        animationSpec = tween(DEFAULT_ANIMATION_DURATION_MS)
    )
}

// 默认的 Pop 进入动画 (当上一个界面 Pop 后，此界面重新进入)：从左侧滑入
val defaultPopEnterTransition: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition?) = {
    slideInHorizontally(
        initialOffsetX = { -it },
        animationSpec = tween(DEFAULT_ANIMATION_DURATION_MS)
    )
}

// 默认的 Pop 退出动画 (当此界面被 Pop 时)：向右侧滑出
val defaultPopExitTransition: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition?) = {
    slideOutHorizontally(
        targetOffsetX = { it },
        animationSpec = tween(DEFAULT_ANIMATION_DURATION_MS)
    )
}

/**
 * 自定义的 composable 函数，应用默认的滑动动画。
 */
fun NavGraphBuilder.slidingComposable(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    deepLinks: List<NavDeepLink> = emptyList(),
    enterTransition: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition?)? = defaultEnterTransition,
    exitTransition: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition?)? = defaultExitTransition,
    popEnterTransition: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition?)? = defaultPopEnterTransition,
    popExitTransition: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition?)? = defaultPopExitTransition,
    content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit
) {
    composable(
        route = route,
        arguments = arguments,
        deepLinks = deepLinks,
        enterTransition = enterTransition,
        exitTransition = exitTransition,
        popEnterTransition = popEnterTransition,
        popExitTransition = popExitTransition,
        content = content
    )
}