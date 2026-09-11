

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape

object ShimmerAnimation {
    @Composable
    fun getShimmerBrush(
        colors: List<Color> = listOf(
            CardBackground,
            CardBackground.copy(alpha = 0.1f) ,
            CardBackground
        ),
        targetValue: Float = 1000f,
        animationDuration: Int = 1000
    ): Brush {
        val transition = rememberInfiniteTransition(label = "shimmer_transition")
        val translateAnimation by transition.animateFloat(
            initialValue = 0f,
            targetValue = targetValue,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = animationDuration, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "shimmer_translate_animation"
        )

        return Brush.linearGradient(
            colors = colors,
            start = Offset(x = translateAnimation - (targetValue / 2), y = 0f),
            end = Offset(x = translateAnimation + (targetValue / 2), y = 0f)
        )
    }
}

@Composable
fun ShimmerPlaceholder(
    modifier: Modifier = Modifier,
    shape: Shape? = null,
    brush: Brush = ShimmerAnimation.getShimmerBrush()
) {
    if (shape != null) {
        Box(modifier = modifier.background(brush = brush, shape = shape))
    } else {
        Box(modifier = modifier.background(brush = brush))
    }
}

