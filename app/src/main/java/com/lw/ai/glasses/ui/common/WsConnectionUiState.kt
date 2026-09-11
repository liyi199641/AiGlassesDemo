package com.lw.ai.glasses.ui.common

import com.fission.wear.glasses.sdk.constant.GlassesConstant
import com.fission.wear.glasses.sdk.events.AgentEvent

/** 与 [com.fission.wear.glasses.sdk.manager.WebSocketClient] 默认重试上限一致。 */
const val WS_MAX_RECONNECT_ATTEMPTS = 10

data class WsConnectionUiState(
    val connectionState: Int = GlassesConstant.WS_CONNECTION_STATE_DISCONNECTED,
    val reconnectAttempts: Int = 0,
    val reconnectRequired: Boolean = false,
    val autoConnectAiEnabled: Boolean = true,
) {
    val isConnected: Boolean
        get() = connectionState == GlassesConstant.WS_CONNECTION_STATE_CONNECTED

    val isConnecting: Boolean
        get() = connectionState == GlassesConstant.WS_CONNECTION_STATE_CONNECTING

    val isDisconnected: Boolean
        get() = connectionState == GlassesConstant.WS_CONNECTION_STATE_DISCONNECTED

    /** 是否处于连接/重连过程（顶部通知显示「连接中」）。 */
    val isWsConnectingPhase: Boolean
        get() = autoConnectAiEnabled && (isConnecting || (reconnectAttempts > 0 && !reconnectRequired && !isConnected))

    /** 是否因未启用 AI 而无法连接。 */
    val isAutoConnectAiDisabled: Boolean
        get() = !autoConnectAiEnabled && !isConnected

    /** 是否处于连接失败态（顶部通知显示「连接失败」）。 */
    val isWsConnectionFailed: Boolean
        get() = !isConnected && !isWsConnectingPhase

    /** 仅在未连接时展示顶部通知。 */
    val shouldShowWsIssueNotification: Boolean
        get() = !isConnected
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
