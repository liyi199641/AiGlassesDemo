package com.lw.ai.glasses.ui.common

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fission.wear.glasses.sdk.constant.GlassesConstant
import com.lw.ai.glasses.R

private val WsConnectedBackground = Color(0x2617E559)
private val WsConnectedContent = Color(0xFF17E559)
private val WsConnectingBackground = Color(0x26FFAC29)
private val WsConnectingContent = Color(0xFFFFAC29)
private val WsDisconnectedBackground = Color(0x26FF007E)
private val WsDisconnectedContent = Color(0xFFFF494C)

@Composable
fun WsConnectionStatusBar(
    state: WsConnectionUiState,
    onReconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (backgroundColor, contentColor) = when {
        state.isConnected -> WsConnectedBackground to WsConnectedContent
        state.isConnecting -> WsConnectingBackground to WsConnectingContent
        else -> WsDisconnectedBackground to WsDisconnectedContent
    }

    val statusText = when {
        state.reconnectRequired -> stringResource(R.string.ws_status_reconnect_required)
        state.isConnecting -> stringResource(R.string.ws_status_connecting)
        state.isConnected -> stringResource(R.string.ws_status_connected)
        state.reconnectAttempts > 0 -> stringResource(
            R.string.ws_status_reconnecting,
            state.reconnectAttempts.coerceAtMost(WS_MAX_RECONNECT_ATTEMPTS),
            WS_MAX_RECONNECT_ATTEMPTS,
        )
        else -> stringResource(R.string.ws_status_disconnected)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(backgroundColor)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        WsStatusIndicator(
            connectionState = state.connectionState,
            contentColor = contentColor,
        )
        Text(
            text = statusText,
            fontWeight = if (state.reconnectRequired) FontWeight.SemiBold else FontWeight.Medium,
            color = contentColor,
            modifier = Modifier.weight(1f),
        )
        if (state.reconnectRequired || state.isDisconnected) {
            if (state.reconnectRequired) {
                TextButton(onClick = onReconnect) {
                    Text(
                        text = stringResource(R.string.ws_action_reconnect),
                        color = contentColor,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun WsStatusIndicator(
    connectionState: Int,
    contentColor: Color,
) {
    when {
        connectionState == GlassesConstant.WS_CONNECTION_STATE_CONNECTING -> {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = contentColor,
            )
        }
        connectionState == GlassesConstant.WS_CONNECTION_STATE_CONNECTED -> {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(contentColor),
            )
        }
        else -> {
            val transition = rememberInfiniteTransition(label = "ws_disconnected_pulse")
            val pulse by transition.animateFloat(
                initialValue = 0.45f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(800, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "ws_pulse_alpha",
            )
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .scale(0.85f + pulse * 0.15f)
                    .alpha(0.5f + pulse * 0.5f)
                    .clip(CircleShape)
                    .background(contentColor.copy(alpha = 0.85f)),
            )
        }
    }
}
