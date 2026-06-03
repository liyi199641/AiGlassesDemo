package com.lw.ai.glasses.ui.devicecontrol

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material.icons.filled.WatchLater
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

enum class ActionStatusVisual(
    val activeIcon: ImageVector,
    val idleIcon: ImageVector,
) {
    MUSIC(Icons.Filled.MusicNote, Icons.Filled.MusicOff),
    RECORD_AUDIO(Icons.Filled.Mic, Icons.Filled.MicOff),
    RECORD_VIDEO(Icons.Filled.Videocam, Icons.Filled.VideocamOff),
    PHOTO(Icons.Filled.CameraAlt, Icons.Filled.CameraAlt),
    IMPORT(Icons.Filled.CloudDownload, Icons.Filled.CloudQueue),
    WEAR(Icons.Filled.Watch, Icons.Filled.WatchLater),
}

@Composable
fun ActionStatusIndicator(
    text: String,
    active: Boolean,
    visual: ActionStatusVisual,
    modifier: Modifier = Modifier,
) {
    val containerAlpha by animateFloatAsState(
        targetValue = if (active) 1f else 0.72f,
        animationSpec = tween(350),
        label = "status_container_alpha",
    )
    val contentColor = if (active) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val containerColor = if (active) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .alpha(containerAlpha)
            .clip(RoundedCornerShape(12.dp))
            .background(containerColor)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StatusIconCluster(
            active = active,
            visual = visual,
            tint = contentColor,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
            color = contentColor,
            modifier = Modifier.weight(1f),
        )
        if (active) {
            ActiveSideEffect(visual = visual, tint = contentColor)
        } else {
            IdleStatusDot(color = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun StatusIconCluster(
    active: Boolean,
    visual: ActionStatusVisual,
    tint: Color,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(40.dp),
    ) {
        if (active) {
            PulsingRings(tint = tint.copy(alpha = 0.35f))
        }
        val iconScale by animateFloatAsState(
            targetValue = if (active) 1.08f else 1f,
            animationSpec = tween(300),
            label = "status_icon_scale",
        )
        val pulseTransition = rememberInfiniteTransition(label = "status_icon_pulse")
        val pulseScale by pulseTransition.animateFloat(
            initialValue = 1f,
            targetValue = if (active) 1.12f else 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(900, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "status_icon_pulse_scale",
        )
        Icon(
            imageVector = if (active) visual.activeIcon else visual.idleIcon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier
                .size(24.dp)
                .scale(iconScale * if (active) pulseScale else 1f),
        )
    }
}

@Composable
private fun PulsingRings(tint: Color) {
    val transition = rememberInfiniteTransition(label = "status_pulse_rings")
    val outer by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "status_outer_ring",
    )
    val inner by transition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "status_inner_ring",
    )
    Canvas(modifier = Modifier.size(40.dp)) {
        val center = size.minDimension / 2f
        drawCircle(
            color = tint,
            radius = center * outer,
            alpha = (1f - outer) * 0.9f,
        )
        drawCircle(
            color = tint,
            radius = center * inner * 0.82f,
            alpha = (1f - inner) * 0.6f,
        )
    }
}

@Composable
private fun ActiveSideEffect(
    visual: ActionStatusVisual,
    tint: Color,
) {
    when (visual) {
        ActionStatusVisual.RECORD_AUDIO,
        ActionStatusVisual.RECORD_VIDEO,
        ActionStatusVisual.MUSIC -> AudioWaveBars(tint = tint)
        ActionStatusVisual.IMPORT -> RotatingSyncIcon(tint = tint)
        ActionStatusVisual.PHOTO -> CameraFlashDot(tint = tint)
        ActionStatusVisual.WEAR -> WearPulseDot(tint = tint)
    }
}

@Composable
private fun AudioWaveBars(tint: Color) {
    val transition = rememberInfiniteTransition(label = "audio_wave")
    val bar1 by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(420), RepeatMode.Reverse),
        label = "bar1",
    )
    val bar2 by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(520), RepeatMode.Reverse),
        label = "bar2",
    )
    val bar3 by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(380), RepeatMode.Reverse),
        label = "bar3",
    )
    Row(
        modifier = Modifier
            .height(20.dp)
            .width(22.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.Bottom,
    ) {
        WaveBar(fraction = bar1, tint = tint)
        WaveBar(fraction = bar2, tint = tint)
        WaveBar(fraction = bar3, tint = tint)
    }
}

@Composable
private fun WaveBar(fraction: Float, tint: Color) {
    Box(
        modifier = Modifier
            .width(4.dp)
            .fillMaxHeight(fraction.coerceIn(0.25f, 1f))
            .clip(RoundedCornerShape(2.dp))
            .background(tint),
    )
}

@Composable
private fun RotatingSyncIcon(tint: Color) {
    val transition = rememberInfiniteTransition(label = "import_spin")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "import_rotation",
    )
    Icon(
        imageVector = Icons.Filled.CloudDownload,
        contentDescription = null,
        tint = tint,
        modifier = Modifier
            .size(20.dp)
            .rotate(rotation),
    )
}

@Composable
private fun CameraFlashDot(tint: Color) {
    val transition = rememberInfiniteTransition(label = "camera_flash")
    val alpha by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse),
        label = "camera_flash_alpha",
    )
    Box(
        modifier = Modifier
            .size(12.dp)
            .alpha(alpha)
            .clip(CircleShape)
            .background(tint),
    )
}

@Composable
private fun WearPulseDot(tint: Color) {
    val transition = rememberInfiniteTransition(label = "wear_pulse")
    val scale by transition.animateFloat(
        initialValue = 0.75f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "wear_pulse_scale",
    )
    Box(
        modifier = Modifier
            .size(12.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(tint),
    )
}

@Composable
private fun IdleStatusDot(color: Color) {
    Box(
        modifier = Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.55f)),
    )
}
