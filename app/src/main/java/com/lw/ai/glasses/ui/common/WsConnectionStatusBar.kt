package com.lw.ai.glasses.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fission.wear.glasses.sdk.constant.GlassesConstant
import com.lw.ai.glasses.R

private val WsConnectingBackground = Color(0xFFFFAC29)
private val WsConnectingOnBackground = Color(0xFF3D2E00)
private val WsFailedBackground = Color(0xFFFF494C)
private val WsFailedOnBackground = Color.White
private val WsDisabledBackground = Color(0xFF616161)
private val WsDisabledOnBackground = Color.White

private val NotificationMaxWidth = 320.dp
private val TopAppBarClearance = 56.dp

/**
 * 顶部居中浮层通知：不遮挡返回按钮；仅在连接中/失败时展示。
 */
@Composable
fun WsConnectionTopNotification(
    state: WsConnectionUiState,
    onReconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val visible = state.shouldShowWsIssueNotification
    val isConnecting = state.isWsConnectingPhase
    val isAutoConnectDisabled = state.isAutoConnectAiDisabled

    Box(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = TopAppBarClearance, start = 16.dp, end = 16.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(initialOffsetY = { -it / 2 }) + fadeIn(tween(220)),
            exit = slideOutVertically(targetOffsetY = { -it / 2 }) + fadeOut(tween(180)),
        ) {
            val backgroundColor = when {
                isConnecting -> WsConnectingBackground
                isAutoConnectDisabled -> WsDisabledBackground
                else -> WsFailedBackground
            }
            val contentColor = when {
                isConnecting -> WsConnectingOnBackground
                isAutoConnectDisabled -> WsDisabledOnBackground
                else -> WsFailedOnBackground
            }

            val statusText = when {
                isAutoConnectDisabled -> stringResource(R.string.ws_auto_connect_ai_disabled_hint)
                isConnecting && state.reconnectAttempts > 0 -> stringResource(
                    R.string.ws_status_reconnecting,
                    state.reconnectAttempts.coerceAtMost(WS_MAX_RECONNECT_ATTEMPTS),
                    WS_MAX_RECONNECT_ATTEMPTS,
                )
                isConnecting -> stringResource(R.string.ws_status_connecting)
                state.reconnectRequired -> stringResource(R.string.ws_status_reconnect_required)
                else -> stringResource(R.string.ws_status_connection_failed)
            }

            Surface(
                modifier = Modifier.widthIn(max = NotificationMaxWidth),
                shape = RoundedCornerShape(12.dp),
                color = backgroundColor,
                shadowElevation = 6.dp,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    WsStatusIndicator(
                        connectionState = when {
                            isConnecting -> GlassesConstant.WS_CONNECTION_STATE_CONNECTING
                            else -> GlassesConstant.WS_CONNECTION_STATE_DISCONNECTED
                        },
                        contentColor = contentColor,
                        showPulse = !isConnecting && !isAutoConnectDisabled,
                    )
                    Text(
                        text = statusText,
                        fontWeight = FontWeight.Medium,
                        color = contentColor,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (
                        state.autoConnectAiEnabled &&
                        !isConnecting &&
                        (state.reconnectRequired || state.isWsConnectionFailed)
                    ) {
                        TextButton(
                            onClick = onReconnect,
                            modifier = Modifier.padding(start = 0.dp),
                        ) {
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
    }
}

@Composable
private fun WsStatusIndicator(
    connectionState: Int,
    contentColor: Color,
    showPulse: Boolean = true,
) {
    when {
        connectionState == GlassesConstant.WS_CONNECTION_STATE_CONNECTING -> {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = contentColor,
            )
        }
        showPulse -> {
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
        else -> {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(contentColor.copy(alpha = 0.85f)),
            )
        }
    }
}
