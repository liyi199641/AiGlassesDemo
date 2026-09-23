# LinWear Ai Glasses SDK — AI 功能使用文档

> 设备蓝牙、媒体同步、OTA、直播等请参阅主文档：[README.md](README.md)  
> [English](AI-README-en.md)

AI 入口为 `AiAssistantClient`；设备侧采集 / 打断等与眼镜联动的 API 仍通过 `GlassesManage` 调用。

**AI 业务结果（含翻译文本、对话流式回复、图片翻译图等）通过 `AiAssistantClient.aiAgentEventFlow()` 回调。**

---

## 📚 目录

- [1. 概述与前置条件](#1-概述与前置条件)
- [2. 权限](#2-权限)
- [3. AI 初始化与配置](#3-ai-初始化与配置)
  - [AiAgentConfig 参数说明](#aiagentconfig-参数说明)
- [4. 生命周期与连接](#4-生命周期与连接)
  - [4.1 占用 Wi-Fi 时的 AI 服务](#41-占用-wi-fi-时的-ai-服务)
  - [4.2 统一事件订阅入口](#42-统一事件订阅入口)
- [5. AI 对话](#5-ai-对话)
- [6. AI 翻译](#6-ai-翻译)
  - [6.1 共用：事件与 AiTranslationDTO](#61-共用事件与-aitranslationdto)
  - [6.2 对话翻译](#62-对话翻译)
  - [6.3 实时翻译](#63-实时翻译)
    - [6.3.1 译文音频播放开关](#631-译文音频播放开关)
    - [6.3.2 系统回声消除（AEC）](#632-系统回声消除aec)
- [7. 音视频通话翻译](#7-音视频通话翻译)
  - [7.1 使用流程](#71-使用流程)
  - [7.1.1 通话控制 API](#711-通话控制-api)
  - [7.2 通话相关事件](#72-通话相关事件)
  - [7.3 翻译事件与回调 data](#73-翻译事件与回调-data)
- [8. 图片翻译](#8-图片翻译)
- [9. 自定义大模型](#9-自定义大模型app-自己实现)
- [10. AI 事件订阅汇总](#10-ai-事件订阅汇总)
- [11. 错误码](#11-错误码)

---

## **1. 概述与前置条件**

| 功能模块 | 说明 | 主要 listen mode / API |
|----------|------|------------------------|
| [AI 对话](#5-ai-对话) | 眼镜按键语音助手 / 识图问答 | 眼镜侧触发；结果 `AgentEvent.AiAssistantResult` |
| [AI 翻译](#6-ai-翻译) | **含两种子能力**（见下） | App 侧采麦 + `startAiTranslation` |
| └ [对话翻译](#62-对话翻译) | 面对面：说完一句再出结果 | `AI_ASSISTANT_TYPE_LISTEN_MODE_TRANSLATION` |
| └ [实时翻译](#63-实时翻译) | 听讲座 / 看电影等连续听译（同传） | `AI_ASSISTANT_TYPE_LISTEN_MODE_SIMULTANEOUS_INTERPRETATION` |
| [音视频通话翻译](#7-音视频通话翻译) | 即构音/视频通话中的双边翻译（**独立功能**，≠ 实时翻译） | `getVoiceRoomParams` / `startCall` + `VOICE_TRANSLATION` |
| [图片翻译](#8-图片翻译) | 上传图片 OCR + 译图 | `getImageTransLangList` / `imageTrans` |

接入前请先完成：

1. 按主文档完成依赖与 `GlassesManage.initialize(SdkConfig)`（见 [README §2–§3](README.md#2-添加依赖必须)）
2. 眼镜 BLE 连接成功并取得鉴权参数（见 [README §5](README.md#5-连接设备)）
3. 调用本文的 `initializeAiClient` → `connectAiAssistant`

| 方式 | 说明                                                               |
|------|------------------------------------------------------------------|
| ✅ SDK 内部大模型 | 使用 `AiAssistantClient` 全套 API（推荐）                                |
| ✅ 自定义大模型 | App 自行实现；设备侧AI录音/AI识图走 `GlassesManage`（见 [§9](#9-自定义大模型app-自己实现)） |

---

## **2. 权限**

```xml
<!-- AI 翻译、音视频通话翻译（手机端采集） -->
<uses-permission android:name="android.permission.RECORD_AUDIO"/>
<!-- 视频通话 -->
<uses-permission android:name="android.permission.CAMERA"/>
<!-- 访问 AI 服务 -->
<uses-permission android:name="android.permission.INTERNET"/>
```

> 蓝牙、Wi-Fi、定位等设备权限见主文档 [§1 添加权限](README.md#1-添加权限)。

---

## **3. AI 初始化与配置**

推荐在 Application 启动或进入眼镜业务前完成 AI 初始化（完整启动顺序见主文档 [§3](README.md#3-sdk-初始化)）：

```kotlin
// 方式 A：预置环境（DEV / TEST / CHINA 等）
AiAssistantClient.getInstance().applyServerEnvironmentToGlobals(
    GlassesConstant.ServerEnvironment.DEV
)

// 方式 B：自定义 HTTP / AI 服务
AiAssistantClient.getInstance().applyServerEnvironmentToGlobals(
    AiServerEnvironmentConfig(
        baseUrl = "https://your-http-host/",
        wsUrl = "wss://your-ws-host"
    )
)

AiAssistantClient.getInstance().initializeAiClient(
    AiAgentConfig(
        context = applicationContext,
        channel = GlassesConstant.ChannelType.LY,
        serverEnvironment = GlassesConstant.ServerEnvironment.DEV,
    )
)

// 眼镜 BLE 连接成功并取得鉴权参数后：
AiAssistantClient.getInstance().connectAiAssistant(
    deviceId = deviceId,
    deviceName = deviceName,
    deviceModel = deviceModel,
    clientId = clientId,
    sk = sk
)
```

说明：

- `initializeAiClient` 每次调用会先清理旧 AI 服务连接并重建依赖，但**不会自动重连**；环境切换后需再次调用 `connectAiAssistant(...)` 或 `manualReconnect()`。
- 预置环境：方式 A + `AiAgentConfig.serverEnvironment` 即可。
- 自定义地址：使用方式 B，或在 `AiAgentConfig` 中传入 `customServerEnvironment`（**优先于** `serverEnvironment`）。

### **AiAgentConfig 参数说明**

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|:----:|--------|------|
| `context` | `Context` | 是 | — | 应用上下文，用于创建即构通话、图片翻译等组件。 |
| `channel` | `GlassesConstant.ChannelType` | 是 | — | AI 业务渠道，**建议与 `SdkConfig.channel` 保持一致**。 |
| `aiModelType` | `GlassesConstant.AiModelVendor` | 否 | `DEFAULT` | 大模型供应商标识。 |
| `serverEnvironment` | `GlassesConstant.ServerEnvironment` | 否 | `DEV` | 预置环境；`customServerEnvironment` 非空时忽略。 |
| `customServerEnvironment` | `AiServerEnvironmentConfig?` | 否 | `null` | 自定义地址；**优先于** `serverEnvironment`。 |
| `enableDefaultPlaySimultaneousAudio` | `Boolean` | 否 | `true` | 是否由 SDK 自动播放**实时翻译的译文音频**。运行时可用 `setTranslationAudioPlaybackEnabled`（见 [§6.3.1](#631-译文音频播放开关)）。 |
| `enableDefaultPlayAgentAudio` | `Boolean` | 否 | `true` | 是否由 SDK 自动播放 **AI 对话的回复音频**。运行时可用 `setAgentAudioPlaybackEnabled`（见 [§5.4](#54-回复音频播放开关)）。 |
| `translationAudioStorageDirName` | `String` | 否 | `"transAudioFiles"` | 翻译/对话录音落盘目录名。 |
| `aiDialogueLanguage` | `Int` | 否 | `140` | AI 对话源语种 `langType`。见 [§5.5](#55-源语种)。 |

**`aiModelType`**：`DEFAULT` / `QWEN` / `GPT_5O_MINI` / `KIMI_V2`  
**`serverEnvironment`**：`DEV` / `TEST` / `CHINA` / `EUROPE` / `SINGAPORE` / `CUSTOM`  
**`AiServerEnvironmentConfig`**：`baseUrl`（HTTP）、`wsUrl`（AI 服务 WS）

---

## **4. 生命周期与连接**

- `getInstance()` / `applyServerEnvironmentToGlobals(...)` / `initializeAiClient(config)`
- `connectAiAssistant(...)` / `disconnect()` / `manualReconnect()`（收到 `AgentEvent.ReconnectRequired` 后）

### **4.1 占用 Wi-Fi 时的 AI 服务**

媒体同步、OTA、直播会占用眼镜 Wi-Fi。SDK 统一处理，**无需 App 手动暂停或恢复**：

| 阶段 | SDK 行为 |
|------|----------|
| 流程开始 | **暂停 AI 服务** |
| 正常结束 / 失败 | **自动恢复 AI 服务** |
| 眼镜 BLE 断开 | 取消暂停标记，**不**主动恢复 |

相关 API：`syncAllMediaFile()`、`startOTA()` / `startRtkOta()`、`startLiveStreaming()` / `stopLiveStreaming()`。

### **4.2 统一事件订阅入口**

```kotlin
viewModelScope.launch {
    AiAssistantClient.getInstance().aiAgentEventFlow().collect { event ->
        when (event) {
            is AgentEvent.AiAssistantConnectState -> { /* 连接状态 */ }
            is AgentEvent.AiAssistantResult -> { /* AI 对话，见 §5 */ }
            is AiTranslationEvent.AiTranslationResult -> {
                // AI 翻译（对话/实时）与音视频通话翻译共用此事件，见 §6 / §7
                // 可用 isMe、业务上下文区分来源
            }
            is AiTranslationEvent.Failed -> { /* 翻译失败 */ }
            is AgentEvent.VoiceRoomParamsEvent -> { /* 通话房间参数，见 §7 */ }
            is AgentEvent.ImageTransResult -> { /* 图片翻译，见 §8 */ }
            else -> Unit
        }
    }
}
```

| Flow | 说明 |
|------|------|
| `aiAgentEventFlow(): Flow<AiAgentBase>` | AI 统一事件流 |
| `aiDialogueInProgressFlow(): StateFlow<Boolean>` | AI 对话进行中，见 [§5](#5-ai-对话) |

**如何读回调 `data`（通用原则）**：

1. **先 `when` 到具体事件类型**，再取载荷字段。
2. **流式事件会多次下发**：用 `id` / `messageId` 合并，用 `isFinished` 收尾。
3. **HTTP 类请求必须校验 `requestId`**：`getImageTransLangList` / `imageTrans` / `getVoiceRoomParams`。
4. **空字段是正常现象**：有值才更新 UI。

---

## **5. AI 对话**

眼镜侧语音助手：设备开始/结束录音后，SDK 将语音送往 AI 服务，并自动播放 **AI 回复音频**；App 订阅事件展示问答文本与本地音频路径。

Demo：`AiAssistantScreen` / `AiAssistantViewModel` / `AiAssistantConversationManager`。

### **5.1 使用流程**

```kotlin
val aiClient = AiAssistantClient.getInstance()

// 1. 先订阅状态与结果
viewModelScope.launch {
    aiClient.aiDialogueInProgressFlow().collect { inProgress ->
        // true：录音中 / 等待回复 / 播放回复音频 → 展示「对话中」、禁用重复触发
    }
}
viewModelScope.launch {
    aiClient.aiAgentEventFlow().collect { event ->
        when (event) {
            is AgentEvent.AiAssistantConnectState -> {
                // state: CONNECTING(1) / CONNECTED(2) / DISCONNECTED(3)
            }
            is AgentEvent.AiAssistantResult -> handleDialogueResult(event.data)
            is AgentEvent.ReconnectRequired -> aiClient.manualReconnect()
            is AgentEvent.DeviceAiServiceError -> { /* event.data */ }
            else -> Unit
        }
    }
}
```

### **5.2 相关 API**

#### 设备侧（`GlassesManage`）

**API 表**

| API | 说明 |
|-----|------|
| `GlassesManage.startAiAssistant()` | 通知眼镜开始 AI 对话录音；SDK 进入收音并送往 AI 服务 |
| `GlassesManage.stopAiAssistant()` | 停止录音；服务端开始生成回复文本 / 回复音频 |
| `GlassesManage.interruptAiAssistant()` | 打断当前轮次：停止录音，并立即停止正在播放的 **AI 回复音频** |

#### AI 客户端（`AiAssistantClient`）

**API 表**

| API | 说明 |
|-----|------|
| `setAgentAudioPlaybackEnabled` / `isAgentAudioPlaybackEnabled` | 是否由 SDK 自动播放 **AI 回复音频** |
| `setAiDialogueLanguage` / `getAiDialogueLanguage` | AI 对话源语种 |
| `isListenBlockedByWsReconnect()` | 切语种后 WS 重建中禁止开听 |
| `aiDialogueInProgressFlow()` | AI 对话进行中状态 |

### **5.3 事件与回调 data**

**Event 表**

| Event | 载荷 | 说明 |
|-------|------|------|
| `AgentEvent.AiAssistantResult` | `data: AiChatMessageDTO` | **主结果** |
| `AgentEvent.AiAssistantConnectState` | `state`, `reconnectAttempts` | 连接状态 |
| ~~`AgentEvent.AiScheduleResult`~~ | ~~`data: McpScheduleData`~~ | ~~MCP 日程~~ |
| `AgentEvent.ReconnectRequired` | — | 调用 `manualReconnect()` |
| `AgentEvent.DeviceAiServiceError` | `data: ErrorData` | 设备侧 AI 错误 |

#### `AiChatMessageDTO`（`event.data`）

一次对话轮次可能**多次**下发。

**字段表**

| 字段 | 类型 | 说明                                                           | 调用方建议 |
|------|------|--------------------------------------------------------------|------------|
| `id` | `String?` | 服务端消息 ID                                                     | 按 id 合并同轮问答 / 音频 |
| `question` | `Any?` | 用户提问内容（流式） 或 AI拍照图片路径                                            | 转 String；识图为本地路径 |
| `answer` | `Any?` | AI 回复（流式）                                                    | **追加**到当前气泡 |
| `questionType` / `answerType` | `AiContentType` | `NONE` / `TEXT` / `IMAGE_PATH` / `IMAGE_FILE` / `AUDIO_DATA` | |
| `questionAudioPath` | `String?` | 用户提问录音的本地路径 | 有值则挂到问题气泡，可回放 |
| `answerAudioPath` | `String?` | **AI 回复音频**的本地 WAV 路径 | 回复播完或打断时下发 |
| `isFinished` | `Boolean` | 本轮结束 | `true` 时固化气泡 |

```kotlin
fun handleDialogueResult(msg: AiChatMessageDTO) {
    val q = msg.question?.toString().orEmpty()
    val a = msg.answer?.toString().orEmpty()
    when {
        q.isNotEmpty() -> { /* 新建/更新问题气泡 */ }
        a.isNotEmpty() -> { /* 追加 answer */ }
        !msg.questionAudioPath.isNullOrBlank() -> { /* 仅补提问录音路径 */ }
        !msg.answerAudioPath.isNullOrBlank() -> { /* 仅补回复音频路径 */ }
    }
    if (msg.isFinished) { /* 本轮收尾 */ }
}
```

### **5.4 回复音频播放开关**

控制 SDK 是否自动播放 **AI 回复音频**。若初始化 `enableDefaultPlayAgentAudio = false`，运行时无法开启。禁用会立即停播，**不**中断对话会话。

### **5.5 源语种**

仅作用于眼镜侧 AI 对话收音，**不含** App 侧 AI 翻译 / 音视频通话翻译。取值 `langType`（如 `140`）。`setAiDialogueLanguage` 后若已连接会重建 WS；`isListenBlockedByWsReconnect() == true` 时禁止开听。

---

## **6. AI 翻译**

AI 翻译包含两种能力，**共用** `startAiTranslation` + `AiTranslationEvent`，但 listen mode 不同：

| 能力 | listen mode | 典型场景 | Demo |
|-------|-------------|----------|------|
| [对话翻译](#62-对话翻译) | `AI_ASSISTANT_TYPE_LISTEN_MODE_TRANSLATION` | 面对面交谈 | `TranslatorScreen` → 对话翻译 |
| [实时翻译](#63-实时翻译) | `AI_ASSISTANT_TYPE_LISTEN_MODE_SIMULTANEOUS_INTERPRETATION` | 听讲座 / 看电影 | `TranslatorScreen` → 实时翻译 |

> **权限**：均需 `RECORD_AUDIO`。  
> **语种**：整数 `langType`；SDK 不提供语音翻译语种 HTTP 接口，请宿主维护（Demo：`assets/languages.json`）。

### **6.1 共用：事件与 AiTranslationDTO**

**Event 表**

| Event | 载荷 | 说明 |
|-------|------|------|
| `AiTranslationEvent.AiTranslationResult` | `data: AiTranslationDTO` | **主结果**（流式多次） |
| `AiTranslationEvent.Failed` | `reason`, `code` | 失败 |
| `AgentEvent.AiAssistantConnectState` | `state`, `reconnectAttempts` | AI 服务连接（共用） |

#### `AiTranslationDTO`（`event.data`）

**字段表**

| 字段 | 类型 | 说明 | 调用方建议 |
|------|------|------|------------|
| `id` | `String?` | 会话 ID（对应 `startAiTranslation` 的 `reqId`） | 归属某次会话 |
| `messageId` | `String?` | 一句话分片 ID | 对话翻译：复合键 `(id, messageId)`；实时翻译可忽略 |
| `originalText` | `String?` | 原文 | 有值才更新 |
| `translatedText` | `String?` | 译文 | 有值才更新 |
| `translatedFileUrl` | `String?` | **译文音频**本地 WAV 路径 | 可仅有此字段、原文/译文文本为 `null` |
| `isFinished` | `Boolean` | 该分片是否结束 | `true` 后固化 |
| `isMe` | `Boolean` | 是否本端（默认 `true`） | 通话翻译对端为 `false`（见 §7） |

**API 表**

| API | 说明 |
|-----|------|
| `startAiTranslation(from, toList, reqId, audioFormat)` | 创建翻译会话；手机麦克风采集时用 `AI_TRANSLATION_AUDIO_FORMAT_RAW_PCM` |
| `startReceivingAudio(mode, language)` | 开始收音；`language` = 源语 `langType` |
| `sendReceivingAudioData(mode, byteArray)` | 持续发送麦克风 PCM（16k / 单声道；SDK 内降噪/AGC） |
| `stopReceivingAudio(mode)` | 结束当前模式的收音 |
| `cancelReceivingAudio()` | 强制中断收音 |
| `pauseListening()` | **仅实时翻译**：暂停收音，但不结束整场会话 |
| `setTranslationAudioPlaybackEnabled` / `isTranslationAudioPlaybackEnabled` | **仅实时翻译**：是否自动播放译文音频（见 [§6.3.1](#631-译文音频播放开关)） |

---

### **6.2 对话翻译**

面对面：说完一句 → 松手结束本句 → 出原文/译文。

```kotlin
val mode = GlassesConstant.AI_ASSISTANT_TYPE_LISTEN_MODE_TRANSLATION
val requestId = System.currentTimeMillis()

viewModelScope.launch {
    aiClient.aiAgentEventFlow().collect { event ->
        when (event) {
            is AiTranslationEvent.AiTranslationResult -> {
                val dto = event.data
                val reqId = dto.id ?: return@collect
                val msgId = dto.messageId ?: return@collect // 无 messageId 可忽略
                // upsert(reqId, msgId)：合并 originalText / translatedText / translatedFileUrl / isFinished
            }
            is AiTranslationEvent.Failed -> { /* ... */ }
            else -> Unit
        }
    }
}

aiClient.startAiTranslation(140, listOf(47), requestId,
    GlassesConstant.AI_TRANSLATION_AUDIO_FORMAT_RAW_PCM)
aiClient.startReceivingAudio(mode, language = 140)
audioRecorderPcmFlow.collect { aiClient.sendReceivingAudioData(mode, it) }

aiClient.stopReceivingAudio(mode) // 松手：结束本句
```

**合并要点**：以 `(id, messageId)` 为复合键；同一句可能先收到文本流，再收到仅含 `translatedFileUrl`（译文音频路径）的结束包。

---

### **6.3 实时翻译**

连续听译（讲座 / 影片）：按住送麦克风音频，松手 **`pauseListening` 暂停会话**（不结束），长按/退出再 `stopReceivingAudio`。  
边听边出 **译文文本**，并可自动播放 **译文音频**（需开启播放开关；扬声器场景建议配合 [系统回声消除](#632-系统回声消除aec)）。

```kotlin
val mode = GlassesConstant.AI_ASSISTANT_TYPE_LISTEN_MODE_SIMULTANEOUS_INTERPRETATION
val requestId = System.currentTimeMillis()

aiClient.startAiTranslation(140, listOf(47), requestId,
    GlassesConstant.AI_TRANSLATION_AUDIO_FORMAT_RAW_PCM)
aiClient.setTranslationAudioPlaybackEnabled(true) // 自动播放译文音频
aiClient.startReceivingAudio(mode, language = 140)

// 开始录音前解析策略；扬声器场景绑定 session 做回声消除（见 §6.3.2）
val policy = aiClient.resolveSimultaneousInterpretationAudioPolicy()
recorder.start(simultaneousPolicy = policy) { pcm ->
    aiClient.sendReceivingAudioData(mode, pcm)
}
if (policy == SimultaneousInterpretationAudioPolicy.SPEAKER_WITH_AEC) {
    aiClient.bindSimultaneousInterpretationCaptureSession(recorder.audioSessionId)
}

aiClient.pauseListening() // 松手暂停
aiClient.clearSimultaneousInterpretationCaptureSession()
// 再次按住：只需 startReceivingAudio + 送麦克风 PCM（不必再 startAiTranslation）

aiClient.stopReceivingAudio(mode) // 结束整场
```

| 对比项 | 对话翻译 | 实时翻译 |
|--------|----------|----------|
| `messageId` | 必须；复合键 upsert | 可忽略；按 `id` 合并滚动文本 |
| 松手 | `stopReceivingAudio` | `pauseListening` |
| 译文音频 | 结束后通过 `translatedFileUrl` 给本地 WAV，由 App 自行播放 | SDK 可边听边自动播放译文音频 |

```kotlin
fun handleRealtimeTranslation(dto: AiTranslationDTO) {
    val requestId = dto.id ?: return
    // 按 requestId 合并 originalText / translatedText
    // translatedFileUrl 有值时：本段译文音频已落盘，可挂回放按钮
}
```

#### **6.3.1 译文音频播放开关**

控制 SDK 是否自动播放 **实时翻译的译文音频**。若初始化 `enableDefaultPlaySimultaneousAudio = false`，运行时无法开启。禁用立即停播，**不**中断翻译会话。

```kotlin
aiClient.setTranslationAudioPlaybackEnabled(false)
val on = aiClient.isTranslationAudioPlaybackEnabled()
```

#### **6.3.2 系统回声消除（AEC）**

实时翻译会「边录边播」：手机扬声器播放译文音频的同时，麦克风仍在采集。若不做回声消除，译文会被麦克风再次录进，造成回声 / 反馈啸叫。

SDK 按当前输出设备给出策略（`SimultaneousInterpretationAudioPolicy`）：

| 策略 | 何时 | 行为 |
|------|------|------|
| `EXTERNAL_PLAYBACK` | 已连接蓝牙 A2DP / 有线耳机等外放 | 译文走耳机；录音用普通识别通路，**一般无需**系统 AEC |
| `SPEAKER_WITH_AEC` | 译文走**手机扬声器** | 使用通话音频通路，并要求 App 绑定麦克风 `audioSessionId`，启用系统 `AcousticEchoCanceler` |

**调用步骤**（与 Demo `TranslatorViewModel` + `StreamAudioRecorder` 一致）：

```kotlin
val aiClient = AiAssistantClient.getInstance()

// 1. 开始录音前：解析当前策略
val policy = aiClient.resolveSimultaneousInterpretationAudioPolicy()

// 2. 按策略创建 AudioRecord（扬声器场景建议 VOICE_COMMUNICATION）
val recorder = StreamAudioRecorder(context)
recorder.start(fileName = "...", simultaneousPolicy = policy) { pcm ->
    aiClient.sendReceivingAudioData(
        GlassesConstant.AI_ASSISTANT_TYPE_LISTEN_MODE_SIMULTANEOUS_INTERPRETATION,
        pcm,
    )
}

// 3. 仅扬声器策略：把麦克风 session 交给 SDK，供译文播放与系统 AEC 对齐
if (policy == SimultaneousInterpretationAudioPolicy.SPEAKER_WITH_AEC) {
    val sessionId = recorder.getAudioSessionId()
    if (sessionId != 0) {
        aiClient.bindSimultaneousInterpretationCaptureSession(sessionId)
    }
    // App 侧建议同时：
    // AcousticEchoCanceler.create(sessionId)?.enabled = true
    // （Demo StreamAudioRecorder 已在 SPEAKER_WITH_AEC 下自动开启）
}

// 4. 暂停 / 结束录音时务必解绑
aiClient.clearSimultaneousInterpretationCaptureSession()
recorder.stop()
```

**API 表**

| API | 说明 |
|-----|------|
| `resolveSimultaneousInterpretationAudioPolicy()` | 根据当前音频路由返回 `EXTERNAL_PLAYBACK` 或 `SPEAKER_WITH_AEC` |
| `bindSimultaneousInterpretationCaptureSession(sessionId)` | 将 App `AudioRecord.audioSessionId` 绑定给 SDK 译文播放器；**开始送麦克风数据前**调用 |
| `clearSimultaneousInterpretationCaptureSession()` | 暂停/结束实时翻译录音时清除绑定 |

注意：

- **仅实时翻译**需要；对话翻译一般一句说完再出结果，不强制走此流程。
- 插拔耳机后策略可能变化，重新开始录音时应再次 `resolve` +（按需）`bind`。
- `AcousticEchoCanceler.isAvailable()` 为 `false` 的机型无法启用硬件 AEC，可提示用户改用耳机。

---

## **7. 音视频通话翻译**

即构（Zego）音/视频通话中的**双边实时翻译**

| | 实时翻译（§6.3） | 音视频通话翻译（本节） |
|--|------------------|------------------------|
| 场景 | 单端听讲座 / 看电影 | 双方进房通话 |
| 进房 | 无 | `getVoiceRoomParams` → `startCall` |
| listen mode | `SIMULTANEOUS_INTERPRETATION` | `VOICE_TRANSLATION`|
| 采麦 | App `StreamAudioRecorder` | 即构采集回调 |
| 对端译文 | 无 | 自定义信令转发；`isMe = false` |

Demo：`CallScreen` / `CallViewModel`。

> **权限**：`RECORD_AUDIO`；视频通话另需 `CAMERA`。

### **7.1 使用流程**

```kotlin
var pendingVoiceRoomRequestId: Long? = null

viewModelScope.launch {
    aiClient.aiAgentEventFlow().collect { event ->
        when (event) {
            is AgentEvent.VoiceRoomParamsEvent -> {
                if (event.requestId != pendingVoiceRoomRequestId) return@collect
                val p = event.params // VoiceRoomParamsDTO
                aiClient.startCall(
                    appID = p.appId.toLong(),
                    token = p.appToken,
                    roomID = p.roomId,
                    streamId = p.streamId,
                    userID = p.userId,
                    isVideo = true, // false = 语音通话
                    local = localTextureView,
                    remote = remoteTextureView,
                )
            }
            is AgentEvent.VoiceRoomParamsFailEvent -> {
                if (event.requestId != pendingVoiceRoomRequestId) return@collect
                // event.code / event.msg
            }
            is AgentEvent.CallConnected -> { /* 接通，可开计时 */ }
            is AgentEvent.CallDisconnected -> { /* 挂断清理 */ }
            is AgentEvent.RemoteVideoStateEvent -> { /* event.isMuted */ }
            is AgentEvent.RemoteLanguageEvent -> { /* 对端切换语种 event.language */ }
            is AiTranslationEvent.AiTranslationResult -> {
                handleCallTranslation(event.data) // 见 7.3
            }
            else -> Unit
        }
    }
}

// type: 1 = 视频房间，2 = 语音房间
pendingVoiceRoomRequestId = aiClient.getVoiceRoomParams(
    lang = myLangType,
    target = peerLangType,
    type = 1,
    appId = zegoAppId,
    mac = deviceMac,
)
```

### **7.1.1 通话控制 API**

**API 表**

| API | 说明 |
|-----|------|
| `updateLocalView(view)` | 更新本地预览 `TextureView` |
| `updateRemoteView(view)` | 更新远端画面 `TextureView` |
| `endCall()` | 挂断通话 |
| `muteMicrophone(mute)` | 静音 / 取消静音麦克风 |
| `enableSpeaker(enable)` | 切换扬声器播放 |
| `muteVideo(mute)` | 关闭 / 打开本地视频采集 |
| `switchCamera(useFront)` | 切换前后摄像头 |
| `setPlayVolume(volume)` | 设置通话播放音量 |

### **7.2 通话相关事件**

**Event 表**

| Event | 字段 | 说明 |
|-------|------|------|
| `AgentEvent.VoiceRoomParamsEvent` | `requestId`, `params: VoiceRoomParamsDTO` | 房间参数成功 |
| `AgentEvent.VoiceRoomParamsFailEvent` | `requestId`, `code`, `msg` | 取参失败 |
| `AgentEvent.CallConnected` / `CallDisconnected` | — | 接通 / 断开 |
| `AgentEvent.RemoteVideoStateEvent` | `isMuted` | 远端视频开关 |
| `AgentEvent.RemoteLanguageEvent` | `language: Int` | 远端语种变化 |

#### `VoiceRoomParamsDTO` 常用字段

**字段表**

| 字段 | 说明 |
|------|------|
| `appId` / `appToken` / `roomId` / `streamId` / `userId` | 传给 `startCall` |
| `language` / `targetLanguage` | 本端 / 对端语种 |
| `type` | `1` 视频 / `2` 语音 |
| `hostUrl` | 可展示的会控 / Web 地址 |

### **7.3 翻译事件与回调 data**

与 AI 翻译共用 `AiTranslationEvent.AiTranslationResult` → `AiTranslationDTO`（字段见 [§6.1](#61-共用事件与-aitranslationdto)）。

**通话场景特有用法**（Demo `CallViewModel`）：

```kotlin
fun handleCallTranslation(dto: AiTranslationDTO) {
    val key = "${dto.id}-${dto.messageId}" // 复合键防覆盖
    val display = if (dto.isMe) {
        dto.originalText   // 本端：展示我说的原文
    } else {
        dto.translatedText // 对端：展示译成我方语种的译文
    } ?: return
    // upsert translation bubble by key；isFinished 时固化
}
```

| 字段 | 通话中含义 |
|------|------------|
| `isMe = true` | 本端发言（AI 服务返回）；UI 通常显示 `originalText` |
| `isMe = false` | 对端发言（即构信令转发）；UI 通常显示 `translatedText` |
| `id` + `messageId` | 复合键做流式更新 |

---

## **8. 图片翻译**

上传本地图片，OCR + 译图，返回译后图 Base64。

Demo：`ImageTransScreen` / `ImageTranslateViewModel`。

### **8.1 使用流程**

> **requestId**：保存 API 返回的 `Long`，回调中校验 `event.requestId`。同类型新请求会取消上一笔。

```kotlin
var pendingLangListRequestId: Long? = null
var pendingImageTransRequestId: Long? = null

viewModelScope.launch {
    aiClient.aiAgentEventFlow().collect { event ->
        when (event) {
            is AgentEvent.ImageTransLangListResult -> {
                if (event.requestId != pendingLangListRequestId) return@collect
                // event.languageList: LanguageResult
            }
            is AgentEvent.ImageTransResult -> {
                if (event.requestId != pendingImageTransRequestId) return@collect
                val bytes = Base64.decode(event.imageBase64, Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
            is AgentEvent.ImageTransFailEvent -> {
                if (event.requestId != pendingImageTransRequestId) return@collect
                // event.code / event.msg
            }
            else -> Unit
        }
    }
}

pendingLangListRequestId =
    aiClient.getImageTransLangList(GlassesConstant.ImageTranslateServerType.VOLC_ENGINE)

pendingImageTransRequestId = aiClient.imageTrans(
    targetImage = imageFile,
    sourceLanguage = srcLangType,
    targetLanguage = targetLangType,
)
```

| API | 返回 | 结果事件 |
|-----|------|----------|
| `getImageTransLangList(serviceType)` | `requestId`（未初始化为 `0`） | `ImageTransLangListResult` |
| `imageTrans(...)` | `requestId` | `ImageTransResult` / `ImageTransFailEvent` |

### **8.2 事件与回调 data**

| 事件 | 字段 | 说明 |
|------|------|------|
| `ImageTransLangListResult` | `requestId`, `languageList` | 语种表 |
| `ImageTransResult` | `requestId`, `imageBase64` | 译后图（字段直接在事件上） |
| `ImageTransFailEvent` | `requestId`, `code`, `msg` | 失败 |

**`LanguageResult`**：`langType` / `name` / `nameEn` / `code` / `supportSource` / `supportTarget`。  
**`imageBase64`**：`Base64.decode` → Bitmap，勿当文件路径。

---

## **9. 自定义大模型（App 自己实现）**

如需开启请联系开发人员。自定义接入时的眼镜原始音频格式见 **[眼镜原始音频流说明](Glasses-Raw-Audio-Stream.md)**。

- `GlassesManage.startAiAssistant` → `AudioStateEvent.ReceivingAudioData`（**`GlassesManage.eventFlow()`**）
- `stopAiAssistant()` / `interruptAiAssistant()`
- `takePicture(true)` → `CmdResultEvent.ImageData` / `ImageFile`

---

## **10. AI 事件订阅汇总**

> 订阅：`AiAssistantClient.aiAgentEventFlow()`（**不是** `GlassesManage.eventFlow()`）。

### **10.1 按功能**

| 功能 | 主要事件 | 载荷要点 |
|------|----------|----------|
| AI 对话 | `AgentEvent.AiAssistantResult` | `AiChatMessageDTO`，[§5.3](#53-事件与回调-data) |
| AI 翻译（对话 / 实时） | `AiTranslationEvent.AiTranslationResult` | `AiTranslationDTO`，[§6.1](#61-共用事件与-aitranslationdto) |
| 音视频通话翻译 | 通话状态事件 + `AiTranslationResult`（含 `isMe`） | [§7.2](#72-通话相关事件) / [§7.3](#73-翻译事件与回调-data) |
| 图片翻译 | `ImageTrans*` | [§8.2](#82-事件与回调-data) |

### **10.2 AgentEvent**

| 事件 | 说明 |
|------|------|
| `AiAssistantConnectState` | `1` 连接中 / `2` 已连接 / `3` 断开 |
| `AiAssistantResult` | AI 对话结果 |
| `AiScheduleResult` | MCP 日程 |
| `ImageTransLangListResult` / `ImageTransResult` / `ImageTransFailEvent` | 图片翻译 |
| `VoiceRoomParamsEvent` / `VoiceRoomParamsFailEvent` | 通话房间参数 |
| `CallConnected` / `CallDisconnected` | 通话接通 / 断开 |
| `RemoteVideoStateEvent` / `RemoteLanguageEvent` | 远端视频 / 语种 |
| `ReconnectRequired` | 需 `manualReconnect()` |
| `DeviceAiServiceError` | 设备侧 AI 错误 |

### **10.3 AiTranslationEvent**

| 事件 | 说明 |
|------|------|
| `AiTranslationResult(data)` | **对话翻译、实时翻译、音视频通话翻译共用** |
| `Failed(reason, code)` | 失败 |

### **10.4 LocalVadEvent（可选）**

`SpeechStarted` / `SpeechEnded` — 手机端本地 VAD，可用于 UI 指示。

---

## **11. 错误码**

| 错误码 | 名称 | 描述 |
|:-------:|:------|:------|
| 500001 | AIErrorCode.DUPLICATE_CONNECTION | 重复连接 |
| 500002 | AIErrorCode.DEVICE_NOT_AUTHORIZED | 设备未授权 |
| 500003 | AIErrorCode.SERVER_KEY_ERROR | 服务器密钥错误 |

> 其它错误码见主文档 [§13](README.md#13-错误码说明)。
