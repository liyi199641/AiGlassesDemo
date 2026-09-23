# LinWear Ai Glasses SDK — AI Feature Guide

> For Bluetooth, media sync, OTA, and live streaming, see the main doc: [README-en.md](README-en.md)  
> [中文](AI-README.md)

AI entry point: `AiAssistantClient`. Device-side capture / interrupt APIs remain on `GlassesManage`.

**AI business results (translation text, streaming dialogue replies, translated images, etc.) are delivered via `AiAssistantClient.aiAgentEventFlow()`.**

---

## Table of Contents

- [1. Overview and prerequisites](#1-overview-and-prerequisites)
- [2. Permissions](#2-permissions)
- [3. AI initialization and configuration](#3-ai-initialization-and-configuration)
  - [AiAgentConfig parameters](#aiagentconfig-parameters)
- [4. Lifecycle and connection](#4-lifecycle-and-connection)
  - [4.1 AI service during Wi-Fi usage](#41-ai-service-during-wi-fi-usage)
  - [4.2 Unified event subscription](#42-unified-event-subscription)
- [5. AI Dialogue](#5-ai-dialogue)
- [6. AI Translation](#6-ai-translation)
  - [6.1 Shared: events and AiTranslationDTO](#61-shared-events-and-aitranslationdto)
  - [6.2 Conversation translation](#62-conversation-translation)
  - [6.3 Real-time translation](#63-real-time-translation)
    - [6.3.1 Translated audio playback toggle](#631-translated-audio-playback-toggle)
    - [6.3.2 System acoustic echo cancellation (AEC)](#632-system-acoustic-echo-cancellation-aec)
- [7. A/V call translation](#7-av-call-translation)
  - [7.1 Usage flow](#71-usage-flow)
  - [7.1.1 Call control APIs](#711-call-control-apis)
  - [7.2 Call events](#72-call-events)
  - [7.3 Translation events and callback data](#73-translation-events-and-callback-data)
- [8. Image Translation](#8-image-translation)
- [9. Custom LLM](#9-custom-llm-implemented-by-the-host-app)
- [10. AI event summary](#10-ai-event-summary)
- [11. Error codes](#11-error-codes)

---

## 1. Overview and prerequisites

| Module | Description | Primary listen mode / API |
|--------|-------------|---------------------------|
| [AI Dialogue](#5-ai-dialogue) | Glasses-button voice assistant / vision Q&A | Device-triggered; result `AgentEvent.AiAssistantResult` |
| [AI Translation](#6-ai-translation) | **Two sub-features** (below) | App mic + `startAiTranslation` |
| └ [Conversation translation](#62-conversation-translation) | Face-to-face: result after each utterance | `AI_ASSISTANT_TYPE_LISTEN_MODE_TRANSLATION` |
| └ [Real-time translation](#63-real-time-translation) | Continuous listen-and-translate (lectures / movies) | `AI_ASSISTANT_TYPE_LISTEN_MODE_SIMULTANEOUS_INTERPRETATION` |
| [A/V call translation](#7-av-call-translation) | Bilateral translation inside a Zego call (**separate** from real-time translation) | `getVoiceRoomParams` / `startCall` + `VOICE_TRANSLATION` |
| [Image Translation](#8-image-translation) | Upload image → OCR + translated image | `getImageTransLangList` / `imageTrans` |

Before integrating:

1. Complete dependencies and `GlassesManage.initialize(SdkConfig)` (see [README-en §2–§3](README-en.md#2-dependencies-required))
2. Connect glasses BLE and obtain auth parameters (see [README-en §5](README-en.md#5-connect-device))
3. Call `initializeAiClient` → `connectAiAssistant` as described below

| Approach | Description |
|----------|-------------|
| SDK built-in LLM | Full `AiAssistantClient` APIs (recommended) |
| Custom LLM | Host app implements the model; device AI recording / AI vision via `GlassesManage` (see [§9](#9-custom-llm-implemented-by-the-host-app)) |

---

## 2. Permissions

```xml
<!-- AI translation, A/V call translation (phone-side capture) -->
<uses-permission android:name="android.permission.RECORD_AUDIO"/>
<!-- Video call -->
<uses-permission android:name="android.permission.CAMERA"/>
<!-- AI service -->
<uses-permission android:name="android.permission.INTERNET"/>
```

> Bluetooth / Wi-Fi / location: see main doc [§1 Permissions](README-en.md#1-permissions).

---

## 3. AI initialization and configuration

Initialize AI at Application startup or before glasses features (full startup order: main doc [§3](README-en.md#3-sdk-initialization)):

```kotlin
// Method A: preset environment (DEV / TEST / CHINA, etc.)
AiAssistantClient.getInstance().applyServerEnvironmentToGlobals(
    GlassesConstant.ServerEnvironment.DEV
)

// Method B: custom HTTP / AI service
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

// After BLE is connected and auth params are available:
AiAssistantClient.getInstance().connectAiAssistant(
    deviceId = deviceId,
    deviceName = deviceName,
    deviceModel = deviceModel,
    clientId = clientId,
    sk = sk
)
```

Notes:

- Each `initializeAiClient` call tears down the previous AI service connection and rebuilds dependencies, but does **not** reconnect automatically. After switching environments, call `connectAiAssistant(...)` or `manualReconnect()` again.
- Preset environment: Method A + `AiAgentConfig.serverEnvironment`.
- Custom addresses: Method B, or pass `customServerEnvironment` in `AiAgentConfig` (**overrides** `serverEnvironment`).

### AiAgentConfig parameters

| Parameter | Type | Required | Default | Description |
|-----------|------|:--------:|---------|-------------|
| `context` | `Context` | Yes | — | App context for Zego / image translation components. |
| `channel` | `GlassesConstant.ChannelType` | Yes | — | AI channel; **keep aligned with `SdkConfig.channel`**. |
| `aiModelType` | `GlassesConstant.AiModelVendor` | No | `DEFAULT` | LLM vendor id. |
| `serverEnvironment` | `GlassesConstant.ServerEnvironment` | No | `DEV` | Preset env; ignored when `customServerEnvironment` is set. |
| `customServerEnvironment` | `AiServerEnvironmentConfig?` | No | `null` | Custom addresses; **higher priority** than `serverEnvironment`. |
| `enableDefaultPlaySimultaneousAudio` | `Boolean` | No | `true` | Whether the SDK auto-plays **translated audio for real-time translation**. Runtime: `setTranslationAudioPlaybackEnabled` (see [§6.3.1](#631-translated-audio-playback-toggle)). |
| `enableDefaultPlayAgentAudio` | `Boolean` | No | `true` | Whether the SDK auto-plays **AI dialogue reply audio**. Runtime: `setAgentAudioPlaybackEnabled` (see [§5.4](#54-reply-audio-playback-toggle)). |
| `translationAudioStorageDirName` | `String` | No | `"transAudioFiles"` | Directory name for translation / dialogue audio files. |
| `aiDialogueLanguage` | `Int` | No | `140` | AI dialogue source `langType`. See [§5.5](#55-source-language). |

**`aiModelType`**: `DEFAULT` / `QWEN` / `GPT_5O_MINI` / `KIMI_V2`  
**`serverEnvironment`**: `DEV` / `TEST` / `CHINA` / `EUROPE` / `SINGAPORE` / `CUSTOM`  
**`AiServerEnvironmentConfig`**: `baseUrl` (HTTP), `wsUrl` (AI service WS)

---

## 4. Lifecycle and connection

- `getInstance()` / `applyServerEnvironmentToGlobals(...)` / `initializeAiClient(config)`
- `connectAiAssistant(...)` / `disconnect()` / `manualReconnect()` (after `AgentEvent.ReconnectRequired`)

### 4.1 AI service during Wi-Fi usage

Media sync, OTA, and live streaming use the glasses Wi-Fi. The SDK handles this uniformly — **no manual pause/resume by the App**:

| Phase | SDK behavior |
|-------|--------------|
| Session start | **Pause AI service** |
| Normal end / failure | **Auto-resume AI service** |
| Glasses BLE disconnect | Clear pause flag; do **not** resume proactively |

Related APIs: `syncAllMediaFile()`, `startOTA()` / `startRtkOta()`, `startLiveStreaming()` / `stopLiveStreaming()`.

### 4.2 Unified event subscription

```kotlin
viewModelScope.launch {
    AiAssistantClient.getInstance().aiAgentEventFlow().collect { event ->
        when (event) {
            is AgentEvent.AiAssistantConnectState -> { /* connection state */ }
            is AgentEvent.AiAssistantResult -> { /* AI dialogue — §5 */ }
            is AiTranslationEvent.AiTranslationResult -> {
                // Shared by AI translation (conversation / real-time) and A/V call translation — §6 / §7
                // Use isMe and business context to tell sources apart
            }
            is AiTranslationEvent.Failed -> { /* translation failure */ }
            is AgentEvent.VoiceRoomParamsEvent -> { /* call room params — §7 */ }
            is AgentEvent.ImageTransResult -> { /* image translation — §8 */ }
            else -> Unit
        }
    }
}
```

| Flow | Description |
|------|-------------|
| `aiAgentEventFlow(): Flow<AiAgentBase>` | Unified AI event stream |
| `aiDialogueInProgressFlow(): StateFlow<Boolean>` | AI dialogue in progress — see [§5](#5-ai-dialogue) |

**How to read callback `data` (general rules)**:

1. **`when` on the concrete event type**, then read payload fields.
2. **Streaming events fire multiple times**: merge by `id` / `messageId`; finalize with `isFinished`.
3. **HTTP-style requests must check `requestId`**: `getImageTransLangList` / `imageTrans` / `getVoiceRoomParams`.
4. **Null fields are normal**: update UI only when a field is present.

---

## 5. AI Dialogue

Glasses-side voice assistant: after the device starts/stops recording, the SDK sends speech to the AI service and auto-plays **AI reply audio**; the app subscribes for Q&A text and local audio paths.

Demo: `AiAssistantScreen` / `AiAssistantViewModel` / `AiAssistantConversationManager`.

### 5.1 Usage flow

```kotlin
val aiClient = AiAssistantClient.getInstance()

// 1. Subscribe to state and results first
viewModelScope.launch {
    aiClient.aiDialogueInProgressFlow().collect { inProgress ->
        // true: recording / waiting for reply / playing reply audio → show “in progress”, block re-entry
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

### 5.2 Related APIs

#### Device side (`GlassesManage`)

**API table**

| API | Description |
|-----|-------------|
| `GlassesManage.startAiAssistant()` | Tell the glasses to start AI dialogue recording; SDK begins capture and sends audio to the AI service |
| `GlassesManage.stopAiAssistant()` | Stop recording; the service starts generating reply text / reply audio |
| `GlassesManage.interruptAiAssistant()` | Interrupt the current round: stop recording and immediately stop **AI reply audio** playback |

#### AI client (`AiAssistantClient`)

**API table**

| API | Description |
|-----|-------------|
| `setAgentAudioPlaybackEnabled` / `isAgentAudioPlaybackEnabled` | Whether the SDK auto-plays **AI reply audio** |
| `setAiDialogueLanguage` / `getAiDialogueLanguage` | AI dialogue source language |
| `isListenBlockedByWsReconnect()` | Do not start listen while WS rebuilds after language change |
| `aiDialogueInProgressFlow()` | AI dialogue in-progress state |

### 5.3 Events and callback data

**Event table**

| Event | Payload | Description |
|-------|---------|-------------|
| `AgentEvent.AiAssistantResult` | `data: AiChatMessageDTO` | **Primary result** |
| `AgentEvent.AiAssistantConnectState` | `state`, `reconnectAttempts` | Connection state |
| ~~`AgentEvent.AiScheduleResult`~~ | ~~`data: McpScheduleData`~~ | ~~MCP schedule~~ |
| `AgentEvent.ReconnectRequired` | — | Call `manualReconnect()` |
| `AgentEvent.DeviceAiServiceError` | `data: ErrorData` | Device-side AI error |

#### `AiChatMessageDTO` (`event.data`)

Emitted **multiple times** per dialogue round.

**Field table**

| Field | Type | Description | Integrator tip |
|-------|------|-------------|----------------|
| `id` | `String?` | Server message id | Merge Q&A / audio for the same round by id |
| `question` | `Any?` | User question (streaming) or AI photo path | Convert to String; vision uses a local path |
| `answer` | `Any?` | AI reply (streaming) | **Append** to the current bubble |
| `questionType` / `answerType` | `AiContentType` | `NONE` / `TEXT` / `IMAGE_PATH` / `IMAGE_FILE` / `AUDIO_DATA` | |
| `questionAudioPath` | `String?` | Local path of the user question recording | Attach to the question bubble when present; can replay |
| `answerAudioPath` | `String?` | Local WAV path of **AI reply audio** | Emitted when reply playback finishes or is interrupted |
| `isFinished` | `Boolean` | Round finished | Finalize the bubble when `true` |

```kotlin
fun handleDialogueResult(msg: AiChatMessageDTO) {
    val q = msg.question?.toString().orEmpty()
    val a = msg.answer?.toString().orEmpty()
    when {
        q.isNotEmpty() -> { /* create/update question bubble */ }
        a.isNotEmpty() -> { /* append answer */ }
        !msg.questionAudioPath.isNullOrBlank() -> { /* attach question recording path only */ }
        !msg.answerAudioPath.isNullOrBlank() -> { /* attach reply audio path only */ }
    }
    if (msg.isFinished) { /* finalize round */ }
}
```

### 5.4 Reply audio playback toggle

Controls whether the SDK auto-plays **AI reply audio**. If `enableDefaultPlayAgentAudio = false` at init, the runtime toggle cannot enable playback. Disabling stops playback immediately without ending the dialogue session.

### 5.5 Source language

Applies only to glasses-side AI dialogue recording — **not** App-side AI translation / A/V call translation. Values are `langType` (e.g. `140`). After `setAiDialogueLanguage`, if already connected, WS is rebuilt; while `isListenBlockedByWsReconnect() == true`, do not start listen.

---

## 6. AI Translation

AI Translation has two capabilities that **share** `startAiTranslation` + `AiTranslationEvent`, with different listen modes:

| Capability | listen mode | Typical scenario | Demo |
|------------|-------------|------------------|------|
| [Conversation translation](#62-conversation-translation) | `AI_ASSISTANT_TYPE_LISTEN_MODE_TRANSLATION` | Face-to-face talk | `TranslatorScreen` → Conversation |
| [Real-time translation](#63-real-time-translation) | `AI_ASSISTANT_TYPE_LISTEN_MODE_SIMULTANEOUS_INTERPRETATION` | Lectures / movies | `TranslatorScreen` → Real-time |

> **Permission**: `RECORD_AUDIO` for both.  
> **Languages**: integer `langType`; the SDK does not expose an HTTP API for the voice-translation language table — maintain it in the host app (Demo: `assets/languages.json`).

### 6.1 Shared: events and AiTranslationDTO

**Event table**

| Event | Payload | Description |
|-------|---------|-------------|
| `AiTranslationEvent.AiTranslationResult` | `data: AiTranslationDTO` | **Primary result** (streaming, multiple times) |
| `AiTranslationEvent.Failed` | `reason`, `code` | Failure |
| `AgentEvent.AiAssistantConnectState` | `state`, `reconnectAttempts` | AI service connection (shared) |

#### `AiTranslationDTO` (`event.data`)

**Field table**

| Field | Type | Description | Integrator tip |
|-------|------|-------------|----------------|
| `id` | `String?` | Session id (matches `startAiTranslation` `reqId`) | Group by session |
| `messageId` | `String?` | Per-utterance segment id | Conversation: composite key `(id, messageId)`; real-time may ignore |
| `originalText` | `String?` | Source text | Update when non-null |
| `translatedText` | `String?` | Translated text | Update when non-null |
| `translatedFileUrl` | `String?` | Local WAV path of **translated audio** | May arrive alone with null text fields |
| `isFinished` | `Boolean` | Segment finished | Finalize when `true` |
| `isMe` | `Boolean` | Local speaker (default `true`) | Peer call text uses `false` (see §7) |

**API table**

| API | Description |
|-----|-------------|
| `startAiTranslation(from, toList, reqId, audioFormat)` | Create translation session; phone mic → `AI_TRANSLATION_AUDIO_FORMAT_RAW_PCM` |
| `startReceivingAudio(mode, language)` | Start capture; `language` = source `langType` |
| `sendReceivingAudioData(mode, byteArray)` | Stream mic PCM (16 kHz mono; SDK denoise/AGC) |
| `stopReceivingAudio(mode)` | End capture for the current mode |
| `cancelReceivingAudio()` | Hard abort capture |
| `pauseListening()` | **Real-time only**: pause capture without ending the session |
| `setTranslationAudioPlaybackEnabled` / `isTranslationAudioPlaybackEnabled` | **Real-time only**: auto-play translated audio (see [§6.3.1](#631-translated-audio-playback-toggle)) |

---

### 6.2 Conversation translation

Face-to-face: finish an utterance → release → get source / translated text.

```kotlin
val mode = GlassesConstant.AI_ASSISTANT_TYPE_LISTEN_MODE_TRANSLATION
val requestId = System.currentTimeMillis()

viewModelScope.launch {
    aiClient.aiAgentEventFlow().collect { event ->
        when (event) {
            is AiTranslationEvent.AiTranslationResult -> {
                val dto = event.data
                val reqId = dto.id ?: return@collect
                val msgId = dto.messageId ?: return@collect // ignore packets without messageId
                // upsert(reqId, msgId): merge originalText / translatedText / translatedFileUrl / isFinished
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

aiClient.stopReceivingAudio(mode) // release: end this utterance
```

**Merge tip**: use `(id, messageId)` as the composite key; the same utterance may first stream text, then a final packet that only carries `translatedFileUrl` (translated audio path).

---

### 6.3 Real-time translation

Continuous listen-and-translate (lectures / movies): hold to stream mic audio; release uses **`pauseListening` to pause the session** (does not end it); long-press / exit then `stopReceivingAudio`.  
You get scrolling **translated text**, and the SDK can auto-play **translated audio** (enable the playback toggle; on speaker, prefer [system AEC](#632-system-acoustic-echo-cancellation-aec)).

```kotlin
val mode = GlassesConstant.AI_ASSISTANT_TYPE_LISTEN_MODE_SIMULTANEOUS_INTERPRETATION
val requestId = System.currentTimeMillis()

aiClient.startAiTranslation(140, listOf(47), requestId,
    GlassesConstant.AI_TRANSLATION_AUDIO_FORMAT_RAW_PCM)
aiClient.setTranslationAudioPlaybackEnabled(true) // auto-play translated audio
aiClient.startReceivingAudio(mode, language = 140)

// Resolve policy before recording; bind session for speaker AEC (see §6.3.2)
val policy = aiClient.resolveSimultaneousInterpretationAudioPolicy()
recorder.start(simultaneousPolicy = policy) { pcm ->
    aiClient.sendReceivingAudioData(mode, pcm)
}
if (policy == SimultaneousInterpretationAudioPolicy.SPEAKER_WITH_AEC) {
    aiClient.bindSimultaneousInterpretationCaptureSession(recorder.audioSessionId)
}

aiClient.pauseListening() // release: pause
aiClient.clearSimultaneousInterpretationCaptureSession()
// Press again: only startReceivingAudio + send mic PCM (no new startAiTranslation)

aiClient.stopReceivingAudio(mode) // end whole session
```

| Item | Conversation translation | Real-time translation |
|------|--------------------------|------------------------|
| `messageId` | Required; composite-key upsert | Optional; merge scrolling text by `id` |
| On release | `stopReceivingAudio` | `pauseListening` |
| Translated audio | Local WAV via `translatedFileUrl` after the utterance; App plays it | SDK can auto-play translated audio while listening |

```kotlin
fun handleRealtimeTranslation(dto: AiTranslationDTO) {
    val requestId = dto.id ?: return
    // Merge originalText / translatedText by requestId
    // When translatedFileUrl is set: segment audio is on disk; show a replay control
}
```

#### 6.3.1 Translated audio playback toggle

Controls whether the SDK auto-plays **translated audio for real-time translation**. If `enableDefaultPlaySimultaneousAudio = false` at init, the runtime toggle cannot enable playback. Disabling stops playback immediately without ending the translation session.

```kotlin
aiClient.setTranslationAudioPlaybackEnabled(false)
val on = aiClient.isTranslationAudioPlaybackEnabled()
```

#### 6.3.2 System acoustic echo cancellation (AEC)

Real-time translation records and plays at the same time: the phone speaker plays translated audio while the mic keeps capturing. Without AEC, translated audio is re-captured and causes echo / feedback.

The SDK picks a policy from the current output device (`SimultaneousInterpretationAudioPolicy`):

| Policy | When | Behavior |
|--------|------|----------|
| `EXTERNAL_PLAYBACK` | Bluetooth A2DP / wired headset, etc. | Translated audio on headset; normal recognition capture — **usually no** system AEC |
| `SPEAKER_WITH_AEC` | Translated audio on the **phone speaker** | Communication audio path; App must bind mic `audioSessionId` and enable system `AcousticEchoCanceler` |

**Steps** (same as Demo `TranslatorViewModel` + `StreamAudioRecorder`):

```kotlin
val aiClient = AiAssistantClient.getInstance()

// 1. Before recording: resolve current policy
val policy = aiClient.resolveSimultaneousInterpretationAudioPolicy()

// 2. Create AudioRecord per policy (speaker: prefer VOICE_COMMUNICATION)
val recorder = StreamAudioRecorder(context)
recorder.start(fileName = "...", simultaneousPolicy = policy) { pcm ->
    aiClient.sendReceivingAudioData(
        GlassesConstant.AI_ASSISTANT_TYPE_LISTEN_MODE_SIMULTANEOUS_INTERPRETATION,
        pcm,
    )
}

// 3. Speaker policy only: bind mic session so translated playback and system AEC align
if (policy == SimultaneousInterpretationAudioPolicy.SPEAKER_WITH_AEC) {
    val sessionId = recorder.getAudioSessionId()
    if (sessionId != 0) {
        aiClient.bindSimultaneousInterpretationCaptureSession(sessionId)
    }
    // App should also:
    // AcousticEchoCanceler.create(sessionId)?.enabled = true
    // (Demo StreamAudioRecorder enables this automatically under SPEAKER_WITH_AEC)
}

// 4. Always unbind on pause / end
aiClient.clearSimultaneousInterpretationCaptureSession()
recorder.stop()
```

**API table**

| API | Description |
|-----|-------------|
| `resolveSimultaneousInterpretationAudioPolicy()` | Returns `EXTERNAL_PLAYBACK` or `SPEAKER_WITH_AEC` from the current audio route |
| `bindSimultaneousInterpretationCaptureSession(sessionId)` | Bind App `AudioRecord.audioSessionId` to the SDK translated-audio player; call **before** streaming mic data |
| `clearSimultaneousInterpretationCaptureSession()` | Clear binding when pausing / ending real-time recording |

Notes:

- **Real-time translation only**; conversation translation usually does not need this flow.
- After plugging/unplugging a headset, re-`resolve` and (if needed) `bind` when recording starts again.
- If `AcousticEchoCanceler.isAvailable()` is `false`, suggest using a headset.

---

## 7. A/V call translation

Bilateral real-time translation inside a Zego audio/video call.

| | Real-time translation (§6.3) | A/V call translation (this section) |
|--|------------------------------|-------------------------------------|
| Scenario | Single-sided lectures / movies | Two parties in a room |
| Join room | No | `getVoiceRoomParams` → `startCall` |
| listen mode | `SIMULTANEOUS_INTERPRETATION` | `VOICE_TRANSLATION` |
| Mic | App `StreamAudioRecorder` | Zego capture callback |
| Peer text | N/A | Custom signaling; `isMe = false` |

Demo: `CallScreen` / `CallViewModel`.

> **Permissions**: `RECORD_AUDIO`; video calls also need `CAMERA`.

### 7.1 Usage flow

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
                    isVideo = true, // false = voice call
                    local = localTextureView,
                    remote = remoteTextureView,
                )
            }
            is AgentEvent.VoiceRoomParamsFailEvent -> {
                if (event.requestId != pendingVoiceRoomRequestId) return@collect
                // event.code / event.msg
            }
            is AgentEvent.CallConnected -> { /* connected; start timer */ }
            is AgentEvent.CallDisconnected -> { /* hang up cleanup */ }
            is AgentEvent.RemoteVideoStateEvent -> { /* event.isMuted */ }
            is AgentEvent.RemoteLanguageEvent -> { /* peer language change event.language */ }
            is AiTranslationEvent.AiTranslationResult -> {
                handleCallTranslation(event.data) // see 7.3
            }
            else -> Unit
        }
    }
}

// type: 1 = video room, 2 = voice room
pendingVoiceRoomRequestId = aiClient.getVoiceRoomParams(
    lang = myLangType,
    target = peerLangType,
    type = 1,
    appId = zegoAppId,
    mac = deviceMac,
)
```

### 7.1.1 Call control APIs

**API table**

| API | Description |
|-----|-------------|
| `updateLocalView(view)` | Update local preview `TextureView` |
| `updateRemoteView(view)` | Update remote video `TextureView` |
| `endCall()` | Hang up |
| `muteMicrophone(mute)` | Mute / unmute microphone |
| `enableSpeaker(enable)` | Speaker on / off |
| `muteVideo(mute)` | Disable / enable local video capture |
| `switchCamera(useFront)` | Switch front / rear camera |
| `setPlayVolume(volume)` | Set call playback volume |

### 7.2 Call events

**Event table**

| Event | Fields | Description |
|-------|--------|-------------|
| `AgentEvent.VoiceRoomParamsEvent` | `requestId`, `params: VoiceRoomParamsDTO` | Room params success |
| `AgentEvent.VoiceRoomParamsFailEvent` | `requestId`, `code`, `msg` | Room params failure |
| `AgentEvent.CallConnected` / `CallDisconnected` | — | Connected / disconnected |
| `AgentEvent.RemoteVideoStateEvent` | `isMuted` | Remote video on / off |
| `AgentEvent.RemoteLanguageEvent` | `language: Int` | Remote language change |

#### `VoiceRoomParamsDTO` common fields

**Field table**

| Field | Description |
|-------|-------------|
| `appId` / `appToken` / `roomId` / `streamId` / `userId` | Pass to `startCall` |
| `language` / `targetLanguage` | Local / peer language |
| `type` | `1` video / `2` voice |
| `hostUrl` | Optional host / Web URL for UI |

### 7.3 Translation events and callback data

Shares `AiTranslationEvent.AiTranslationResult` → `AiTranslationDTO` with AI Translation (fields: [§6.1](#61-shared-events-and-aitranslationdto)).

**Call-specific usage** (Demo `CallViewModel`):

```kotlin
fun handleCallTranslation(dto: AiTranslationDTO) {
    val key = "${dto.id}-${dto.messageId}" // composite key to avoid overwrite
    val display = if (dto.isMe) {
        dto.originalText   // local: show what I said
    } else {
        dto.translatedText // peer: show text translated into my language
    } ?: return
    // upsert translation bubble by key; finalize when isFinished
}
```

| Field | Meaning in a call |
|-------|-------------------|
| `isMe = true` | Local speech (from AI service); UI usually shows `originalText` |
| `isMe = false` | Peer speech (Zego signaling); UI usually shows `translatedText` |
| `id` + `messageId` | Composite key for streaming updates |

---

## 8. Image Translation

Upload a local image for OCR + translated image; result is Base64 of the translated image.

Demo: `ImageTransScreen` / `ImageTranslateViewModel`.

### 8.1 Usage flow

> **requestId**: save the `Long` returned by the API and verify `event.requestId` in the callback. Starting a new request of the same type cancels the previous one.

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

| API | Returns | Result event |
|-----|---------|--------------|
| `getImageTransLangList(serviceType)` | `requestId` (`0` if not initialized) | `ImageTransLangListResult` |
| `imageTrans(...)` | `requestId` | `ImageTransResult` / `ImageTransFailEvent` |

### 8.2 Events and callback data

| Event | Fields | Description |
|-------|--------|-------------|
| `ImageTransLangListResult` | `requestId`, `languageList` | Language table |
| `ImageTransResult` | `requestId`, `imageBase64` | Translated image (fields on the event) |
| `ImageTransFailEvent` | `requestId`, `code`, `msg` | Failure |

**`LanguageResult`**: `langType` / `name` / `nameEn` / `code` / `supportSource` / `supportTarget`.  
**`imageBase64`**: `Base64.decode` → Bitmap; do not treat as a file path.

---

## 9. Custom LLM (implemented by the host app)

Contact the SDK team to enable. For glasses raw audio formats when integrating your own model, see **[Glasses Raw Audio Stream](Glasses-Raw-Audio-Stream.md)**.

- `GlassesManage.startAiAssistant` → `AudioStateEvent.ReceivingAudioData` (on **`GlassesManage.eventFlow()`**)
- `stopAiAssistant()` / `interruptAiAssistant()`
- `takePicture(true)` → `CmdResultEvent.ImageData` / `ImageFile`

---

## 10. AI event summary

> Subscribe via `AiAssistantClient.aiAgentEventFlow()` (**not** `GlassesManage.eventFlow()`).

### 10.1 By feature

| Feature | Primary events | Payload |
|---------|----------------|---------|
| AI Dialogue | `AgentEvent.AiAssistantResult` | `AiChatMessageDTO` — [§5.3](#53-events-and-callback-data) |
| AI Translation (conversation / real-time) | `AiTranslationEvent.AiTranslationResult` | `AiTranslationDTO` — [§6.1](#61-shared-events-and-aitranslationdto) |
| A/V call translation | Call events + `AiTranslationResult` (`isMe`) | [§7.2](#72-call-events) / [§7.3](#73-translation-events-and-callback-data) |
| Image Translation | `ImageTrans*` | [§8.2](#82-events-and-callback-data) |

### 10.2 AgentEvent

| Event | Description |
|-------|-------------|
| `AiAssistantConnectState` | `1` connecting / `2` connected / `3` disconnected |
| `AiAssistantResult` | AI dialogue result |
| `AiScheduleResult` | MCP schedule |
| `ImageTransLangListResult` / `ImageTransResult` / `ImageTransFailEvent` | Image translation |
| `VoiceRoomParamsEvent` / `VoiceRoomParamsFailEvent` | Call room params |
| `CallConnected` / `CallDisconnected` | Call connected / disconnected |
| `RemoteVideoStateEvent` / `RemoteLanguageEvent` | Remote video / language |
| `ReconnectRequired` | Call `manualReconnect()` |
| `DeviceAiServiceError` | Device-side AI error |

### 10.3 AiTranslationEvent

| Event | Description |
|-------|-------------|
| `AiTranslationResult(data)` | **Shared by conversation, real-time, and A/V call translation** |
| `Failed(reason, code)` | Failure |

### 10.4 LocalVadEvent (optional)

`SpeechStarted` / `SpeechEnded` — phone-side local VAD for UI indicators.

---

## 11. Error codes

| Code | Name | Description |
|:----:|:-----|:------------|
| 500001 | AIErrorCode.DUPLICATE_CONNECTION | Duplicate connection |
| 500002 | AIErrorCode.DEVICE_NOT_AUTHORIZED | Device not authorized |
| 500003 | AIErrorCode.SERVER_KEY_ERROR | Server key error |

> Other error codes: main doc [§13](README-en.md#13-error-codes).
