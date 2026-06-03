package com.lw.ai.glasses.ui.common

import com.fission.wear.glasses.sdk.constant.GlassesConstant
import com.fission.wear.glasses.sdk.events.AgentEvent

/** 与 [com.fission.wear.glasses.sdk.manager.WebSocketClient] 默认重试上限一致。 */
const val WS_MAX_RECONNECT_ATTEMPTS = 10

data class WsConnectionUiState(
    val connectionState: Int = GlassesConstant.WS_CONNECTION_STATE_DISCONNECTED,
    val reconnectAttempts: Int = 0,
    val reconnectRequired: Boolean = false,
) {
    val isConnected: Boolean
        get() = connectionState == GlassesConstant.WS_CONNECTION_STATE_CONNECTED

    val isConnecting: Boolean
        get() = connectionState == GlassesConstant.WS_CONNECTION_STATE_CONNECTING

    val isDisconnected: Boolean
        get() = connectionState == GlassesConstant.WS_CONNECTION_STATE_DISCONNECTED
}

fun WsConnectionUiState.applyAgentEvent(event: AgentEvent): WsConnectionUiState = when (event) {
    is AgentEvent.AiAssistantConnectState -> copy(
        connectionState = event.state,
        reconnectAttempts = event.reconnectAttempts,
        reconnectRequired = false,
    )

    AgentEvent.ReconnectRequired -> copy(
        connectionState = GlassesConstant.WS_CONNECTION_STATE_DISCONNECTED,
        reconnectAttempts = WS_MAX_RECONNECT_ATTEMPTS,
        reconnectRequired = true,
    )

    else -> this
}

fun WsConnectionUiState.clearedForManualReconnect(): WsConnectionUiState = copy(
    connectionState = GlassesConstant.WS_CONNECTION_STATE_CONNECTING,
    reconnectRequired = false,
    reconnectAttempts = 0,
)
