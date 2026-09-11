

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun AppCircularProgressIndicator(
    modifier: Modifier = Modifier,
    initialCountdown: Int = 30,
    startNow: Boolean,
    onFinished: () -> Unit,
    autoRestart: Boolean = false,
    isClockwise: Boolean = true,
    strokeWidth: Dp = 1.dp,
    progressColor: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = Color.Transparent,
    baseTrackColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
) {
    var timeLeft by remember { mutableIntStateOf(initialCountdown) }
    var isInternallyRunning by remember { mutableStateOf(false) }
    var displayedProgress by remember { mutableFloatStateOf(1f) }

    val totalSecondsFloat = remember(initialCountdown) { initialCountdown.toFloat() }

    LaunchedEffect(startNow, initialCountdown, autoRestart) {
        if (startNow) {
            if (timeLeft == 0 || !isInternallyRunning) {
                timeLeft = initialCountdown
                displayedProgress = 1.0f
            }
            isInternallyRunning = true
        } else {
            if (!autoRestart) {
                isInternallyRunning = false
            } else if (isInternallyRunning && !startNow) {
                isInternallyRunning = false
            }
        }
    }

    LaunchedEffect(isInternallyRunning, timeLeft, autoRestart, initialCountdown) {
        if (isInternallyRunning && timeLeft > 0) {
            displayedProgress = if (totalSecondsFloat > 0) timeLeft.toFloat() / totalSecondsFloat else 0f
            delay(1000L)
            if (isInternallyRunning) {
                timeLeft--
            }
        } else if (timeLeft == 0 && isInternallyRunning) {
            displayedProgress = 0f
            onFinished()

            if (autoRestart) {
                timeLeft = initialCountdown
                displayedProgress = 1.0f
            } else {
                isInternallyRunning = false
            }
        } else if (!isInternallyRunning) {
            if (timeLeft == initialCountdown) {
                displayedProgress = 1f
            } else if (timeLeft > 0) {
                displayedProgress = if (totalSecondsFloat > 0) timeLeft.toFloat() / totalSecondsFloat else 0f
            } else {
                displayedProgress = 0f
            }
        }
    }

    val animatedDisplayedProgress by animateFloatAsState(
        targetValue = displayedProgress,
        animationSpec = tween(
            durationMillis = if (
                isInternallyRunning &&
                (timeLeft > 0 || (timeLeft == 0 && displayedProgress != 0f && autoRestart))
            ) 1000 else 0,
            easing = LinearEasing
        ),
        label = "CountdownVisualProgress"
    )

    val scaleModifier = if (isClockwise) Modifier else Modifier.scale(scaleX = -1f, scaleY = 1f)

    Box(modifier = modifier.then(scaleModifier)) {
        if (baseTrackColor != Color.Transparent) {
            CircularProgressIndicator(
                progress = { 1f },
                modifier = Modifier.fillMaxSize(),
                strokeWidth = strokeWidth,
                color = baseTrackColor,
                trackColor = Color.Transparent,
                strokeCap = StrokeCap.Round,
            )
        }
        CircularProgressIndicator(
            progress = {
                if (!isInternallyRunning || (isInternallyRunning && timeLeft == initialCountdown && displayedProgress == 1.0f)) {
                    displayedProgress
                } else {
                    animatedDisplayedProgress
                }
            },
            modifier = Modifier.fillMaxSize(),
            strokeWidth = strokeWidth,
            color = progressColor,
            trackColor = trackColor,
            strokeCap = StrokeCap.Round,
        )
    }
}