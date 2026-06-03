package com.lw.ai.glasses.state

import android.content.Context
import com.blankj.utilcode.util.ToastUtils
import com.fission.wear.glasses.sdk.AiAssistantClient
import com.fission.wear.glasses.sdk.events.AgentEvent
import com.lw.ai.glasses.R
import com.lw.ai.glasses.ui.common.WsConnectionUiState
import com.lw.ai.glasses.ui.common.applyAgentEvent
import com.lw.ai.glasses.ui.common.clearedForManualReconnect
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 全局 WebSocket 连接状态：在应用启动时订阅 [AgentEvent]。
 */
@Singleton
class WsConnectionStateManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val started = AtomicBoolean(false)

    private val _state = MutableStateFlow(WsConnectionUiState())
    val state: StateFlow<WsConnectionUiState> = _state.asStateFlow()

    fun start() {
        if (!started.compareAndSet(false, true)) return
        appScope.launch {
            AiAssistantClient.getInstance().aiAgentEventFlow().collect { event ->
                val agentEvent = when {
                    event is AgentEvent.AiAssistantConnectState -> event
                    event == AgentEvent.ReconnectRequired -> AgentEvent.ReconnectRequired
                    else -> return@collect
                }
                val previous = _state.value
                val next = previous.applyAgentEvent(agentEvent)
                if (next != previous) {
                    _state.value = next
                }
                if (agentEvent == AgentEvent.ReconnectRequired) {
                    ToastUtils.showLong(context.getString(R.string.ws_status_reconnect_required))
                }
            }
        }
    }

    fun manualReconnect() {
        _state.update { it.clearedForManualReconnect() }
        AiAssistantClient.getInstance().manualReconnect()
    }
}
