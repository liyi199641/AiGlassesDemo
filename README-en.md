# LinWear Ai Glasses SDK Documentation

---

## Table of Contents
- [1. Permissions](#1-permissions)
- [2. Dependencies (Required)](#2-dependencies-required)
- [3. SDK Initialization](#3-sdk-initialization)
- [4. Scan Devices](#4-scan-devices)
- [5. Connect Device](#5-connect-device)
  - [5.1 Connect / Disconnect BLE](#51-connect--disconnect-ble)
  - [5.2 Subscribe BLE + BT State (Recommended)](#52-subscribe-ble--bt-state-recommended)
  - [5.3 Manual BT Reconnect](#53-manual-bt-reconnect)
- [6. File Sync](#6-file-sync)
- [7. AI Assistant](#7-ai-assistant)
- [8. AI Translation](#8-ai-translation)
- [9. Live Streaming](#9-live-streaming)
- [10. SDK Flow Events](#10-sdk-flow-events)
- [11. Device Settings](#11-device-settings)
- [12. OTA Upgrade](#12-ota-upgrade)
- [13. Error Codes](#13-error-codes)

---

## 1. Permissions
```xml
<uses-permission android:name="android.permission.INTERNET"/>
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"/>
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"/>
<!-- Bluetooth connection -->
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION"/>
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<!-- Media file sync -->
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE"/>
<uses-permission android:name="android.permission.CHANGE_WIFI_STATE"/>
<uses-permission
android:name="android.permission.NEARBY_WIFI_DEVICES"
android:usesPermissionFlags="neverForLocation"
tools:targetApi="33" />
<!-- AI translation / AV call (phone-side capture) -->
<uses-permission android:name="android.permission.RECORD_AUDIO"/>
<!-- Video call -->
<uses-permission android:name="android.permission.CAMERA"/>
```

---

## 2. Dependencies (Required)
```gradle
implementation("com.fission.wear.glasses:sdk:lastVersion")
implementation("io.reactivex.rxjava3:rxjava:3.1.6")
```

Required dependencies:
- Add to `settings.gradle`: `maven { url = uri("https://repo.repsy.io/mvn/linwear/android") }`
- Add to `settings.gradle`: `maven { url = uri("https://maven.zego.im") }`
- Import `aar/jar` files under `app/libs`
- RxJava3
- RxAndroid
- RxAndroidBle
- OkHttp
- Retrofit
- UtilCodex
- See `settings.gradle` for details

### Native SO conflict handling in host apps
The SDK already includes a library-side fallback for `libc++_shared.so`, but Android native library conflicts still ultimately happen when the host `app` packages the APK/AAB.

If your host project also depends on other libraries that bundle `libc++_shared.so`, add this to `app/build.gradle(.kts)`:

```kotlin
android {
    packaging {
        jniLibs {
            pickFirsts += setOf(
                "lib/armeabi-v7a/libc++_shared.so",
                "lib/arm64-v8a/libc++_shared.so"
            )
        }
    }
}
```

---

## 3. SDK Initialization

| Module | Entry | Responsibility |
|--------|-------|----------------|
| Glasses SDK | `GlassesManage.initialize(SdkConfig)` | BLE scan/connect, device commands, OTA, media sync, etc. |
| AI client | `AiAssistantClient.getInstance().initializeAiClient(AiAgentConfig)` | AI service, voice assistant, translation, image translation, Zego calls, etc. |

Recommended order (see Demo: `LinWearApplication` + `AppStartupReconnectManager`):

1. `Utils.init(application)` — UtilCodex (required)
2. (Optional) `RxJavaPlugins.setErrorHandler { ... }` — Rx global error handler in Demo
3. (Optional) `AiAssistantClient.getInstance().applyServerEnvironmentToGlobals(...)` — set AI service environment (callable before or after `initializeAiClient`)
4. `GlassesManage.initialize(SdkConfig(...))` — only the **first** call takes effect
5. `AiAssistantClient.getInstance().initializeAiClient(AiAgentConfig(...))` — call again when switching environments or reconfiguring AI; does **not** auto-reconnect the AI service
6. After BLE connect and auth params are available, call `connectAiAssistant(...)` (see section 7)

```kotlin
// Application.onCreate or before entering glasses features
Utils.init(this)

// Method A: preset environment (DEV / TEST / CHINA, etc.)
AiAssistantClient.getInstance().applyServerEnvironmentToGlobals(
    GlassesConstant.ServerEnvironment.DEV
)

// Method B: custom HTTP / AI service (custom environment)
AiAssistantClient.getInstance().applyServerEnvironmentToGlobals(
    AiServerEnvironmentConfig(
        baseUrl = "https://your-http-host/",
        wsUrl = "wss://your-ws-host"
    )
)

// Glasses SDK (channel must match hardware: TB / LY / RTK)
GlassesManage.initialize(
    SdkConfig(
        isDebug = BuildConfig.DEBUG,
        context = applicationContext,
        channel = GlassesConstant.ChannelType.LY,
        logLevel = LogUtils.V,
    )
)

// AI runtime (channel should match SdkConfig)
AiAssistantClient.getInstance().initializeAiClient(
    AiAgentConfig(
        context = applicationContext,
        channel = GlassesConstant.ChannelType.LY,
        serverEnvironment = GlassesConstant.ServerEnvironment.DEV,
    )
)
```

### SdkConfig parameters

Used by `GlassesManage.initialize(SdkConfig(...))`:

| Parameter | Type | Required | Default | Description |
|-----------|------|:--------:|---------|-------------|
| `isDebug` | `Boolean` | Yes | — | Debug mode flag. Pass `BuildConfig.DEBUG` to match the host app build type. |
| `context` | `Context` | Yes | — | Application context. The SDK uses `applicationContext` internally. |
| `channel` | `GlassesConstant.ChannelType` | Yes | — | Glasses hardware/protocol channel. Selects the BLE command strategy and capability set. **Must match the connected glasses platform.** |
| `logLevel` | `Int` | No | `LogUtils.V` | SDK log verbosity, using UtilCodex `LogUtils` constants: `V` (most verbose) → `D` → `I` → `W` → `E` (least verbose). |
| `mediaFilesStorageDirName` | `String` | No | `"mediaFiles"` | Subdirectory name under `context.filesDir` for media files synced from glasses. |
| `aiImageRecognitionStorageDirName` | `String` | No | `"tempImages"` | Subdirectory name under `context.filesDir` for temporary AI image-recognition files. |

**`channel` values**:

| Enum | Description |
|------|-------------|
| `ChannelType.TB` | TB platform |
| `ChannelType.LY` | LY platform (Demo default) |
| `ChannelType.RTK` | RTK platform |
| `ChannelType.QC` | QC platform |

> `GlassesManage.initialize` only takes effect on the **first** call. Repeated calls are ignored; you cannot change `SdkConfig` by calling `initialize` again.

---

### AiAgentConfig parameters

Used by `AiAssistantClient.getInstance().initializeAiClient(AiAgentConfig(...))`:

| Parameter | Type | Required | Default | Description |
|-----------|------|:--------:|---------|-------------|
| `context` | `Context` | Yes | — | Application context, used to create Zego call, image translation, and other AI components. |
| `channel` | `GlassesConstant.ChannelType` | Yes | — | AI business channel. **Should match `SdkConfig.channel`.** |
| `aiModelType` | `GlassesConstant.AiModelVendor` | No | `DEFAULT` | LLM vendor identifier; affects routing for AI chat, translation, and related requests. |
| `serverEnvironment` | `GlassesConstant.ServerEnvironment` | No | `DEV` | Preset AI service environment (HTTP `baseUrl` + AI service `wsUrl`). Ignored when `customServerEnvironment` is set. |
| `customServerEnvironment` | `AiServerEnvironmentConfig?` | No | `null` | Custom AI service URLs; **takes precedence over** `serverEnvironment`. |
| `enableDefaultPlaySimultaneousAudio` | `Boolean` | No | `true` | Whether the SDK auto-plays real-time simultaneous-interpretation downlink PCM (`simultaneous_audio`). When `false`, subscribe to `AgentAudioEvent.TranslationAudioSend` and handle playback yourself. The runtime toggle `setTranslationAudioPlaybackEnabled` still applies when init allows it (see [section 8](#real-time-translation-audio-playback-toggle)). |
| `enableDefaultPlayAgentAudio` | `Boolean` | No | `true` | Whether the SDK auto-plays AI assistant (Agent) downlink PCM. When `false`, subscribe to `AgentAudioEvent.AgentAudioSend` and handle playback yourself. The runtime toggle `setAgentAudioPlaybackEnabled` still applies when init allows it (see [section 7.6](#76-ai-chat-reply-audio-playback-toggle)). |
| `translationAudioStorageDirName` | `String` | No | `"transAudioFiles"` | Subdirectory name under `context.filesDir` for translation/dialog recording files. |

**`aiModelType` values**:

| Enum | Description |
|------|-------------|
| `AiModelVendor.DEFAULT` | Use backend default |
| `AiModelVendor.QWEN` | Qwen |
| `AiModelVendor.GPT_5O_MINI` | GPT-5o mini |
| `AiModelVendor.KIMI_V2` | Kimi v2 |

**`serverEnvironment` presets**:

| Enum | Description |
|------|-------------|
| `DEV` | Development |
| `TEST` | Test / pre-release |
| `CHINA` | Production (China) |
| `EUROPE` | Production (Europe) |
| `SINGAPORE` | Production (Singapore) |

**`customServerEnvironment` (`AiServerEnvironmentConfig`) fields**:

| Field | Type | Description |
|-------|------|-------------|
| `baseUrl` | `String` | AI HTTP service root URL, e.g. `https://your-http-host/` |
| `wsUrl` | `String` | AI service URL, e.g. `wss://your-ws-host` |

> Each `initializeAiClient` call tears down the previous AI service connection and rebuilds dependencies, but does **not** reconnect automatically. After switching environments, call `connectAiAssistant(...)` or `manualReconnect()` again.

### GlassesManage core APIs

| API | Description |
|-----|-------------|
| `eventFlow(): Flow<GlassesEvent>` | Unified glasses SDK event stream. Scan, file sync, OTA, live streaming, and device command results are delivered here. Subscribe in Application or page lifecycle scope. |
| `connectionStateFlow(): StateFlow<GlassesConnectionState>` | **Aggregated BLE + BT connection state** (recommended). Maintained by the SDK; subscribe for connect/pair/audio UI. BT state is auto-synced after BLE connect, A2DP profile ready, and app restart reconnect. |
| `currentConnectionState(): GlassesConnectionState` | Snapshot of current BLE/BT connection state. |
| `reconnectBluetooth()` | Manually reconnect BT (BREDR pairing / A2DP·HFP). Requires BLE connected; **skipped automatically in OTA mode**. |

Notes:

- `Utils.init()` comes from UtilCodex, not the SDK. File logging is optional (enabled in Demo).
- Preset env: use method A plus `AiAgentConfig.serverEnvironment`.
- Custom URLs: use method B, or pass `customServerEnvironment` in `AiAgentConfig` (it **overrides** `serverEnvironment`); do not configure both redundantly.
- If you switch environments after the AI service is connected, call `connectAiAssistant(...)` or `manualReconnect()` again.
- Demo “Custom environment” maps to `GlassesConstant.ServerEnvironment.LOCAL` with separate `baseUrl` and `wsUrl`.

---

## 4. Scan Devices

You can implement your own scan logic, or use the SDK helper. Results are delivered via `GlassesManage.eventFlow()` as `ScanStateEvent`.

```kotlin
// Subscribe once in Application or page lifecycle
viewModelScope.launch {
    GlassesManage.eventFlow().collect { event ->
        when (event) {
            is ScanStateEvent.DeviceFound -> {
                val scanResult = event.data
                val mac = scanResult.bleDevice.macAddress
                val name = scanResult.bleDevice.name
                val rssi = scanResult.rssi
            }
            is ScanStateEvent.ScanFinished -> {
                // Scan round finished (timeout or manual stop)
            }
            is ScanStateEvent.Error -> {
                // event.throwable
            }
            else -> Unit
        }
    }
}

GlassesManage.startScanBleDevices(
    bleScanConfig = BleScanConfig(
        isContinuousScan = false,
        scanDuration = 120_000,
    ),
    scanSettings = ScanSettings.Builder()
        .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
        .build(),
    scanFilters = arrayOf(ScanFilter.Builder().build()),
)

// Stop scan and unbind scan Service
GlassesManage.stopScanBleDevices(context)
```

**Callback events (`ScanStateEvent`)**

| Event | Description |
|-------|-------------|
| `DeviceFound` | Device found (`ScanResult`: MAC, name, RSSI) |
| `ScanFinished` | Scan round finished |
| `Error` | Scan error |

> Android 12+ requires `BLUETOOTH_SCAN`. See [section 1](#1-permissions).

---

## 5. Connect Device

Connect glasses over BLE. **For BLE/BT connection UI, subscribe to `connectionStateFlow()`** (below). `ConnectionStateEvent` / `BtConnectEvent` on `eventFlow()` remain available for advanced use, but connection display and reconnect logic should use `GlassesConnectionState`.

### 5.1 Connect / Disconnect BLE

```kotlin
GlassesManage.connect(
    BleComConfig(
        context = context,
        mac = macAddress,
        isOtaMode = false, // true: OTA mode — SDK skips BT pairing/reconnect
    )
)

// Disconnect and release SDK resources
GlassesManage.disConnect(unpair = true)
```

### 5.2 Subscribe BLE + BT State (Recommended)

The SDK aggregates BLE and classic Bluetooth (BREDR / A2DP·HFP) state via `GlassesConnectionStateManager`. BT state is **auto-synced** after BLE connect, A2DP profile ready, and app restart reconnect — no public sync API is required.

```kotlin
viewModelScope.launch {
    GlassesManage.connectionStateFlow().collect { state ->
        when (state.bleState) {
            SdkBleConnectionState.IDLE -> { /* idle */ }
            SdkBleConnectionState.CONNECTING -> { /* BLE connecting */ }
            SdkBleConnectionState.CONNECTED -> {
                // BLE ready for commands and AI session
                if (state.isBtAudioConnected) { /* A2DP connected */ }
            }
            SdkBleConnectionState.DISCONNECTED -> { /* BLE disconnected */ }
            SdkBleConnectionState.FAILED -> { /* BLE failed: state.bleErrorMessage */ }
        }

        when (state.btState) {
            SdkBtConnectionState.IDLE -> { /* BT not started (e.g. OTA mode) */ }
            SdkBtConnectionState.BONDING -> { /* pairing */ }
            SdkBtConnectionState.CONNECTING -> { /* connecting A2DP/HFP */ }
            SdkBtConnectionState.CONNECTED -> { /* audio connected */ }
            SdkBtConnectionState.FAILED -> { /* failed: state.btFailureReason */ }
            SdkBtConnectionState.DISCONNECTED -> { /* audio disconnected; manual reconnect */ }
        }

        state.deviceName
        state.deviceAddress
    }
}

val snapshot = GlassesManage.currentConnectionState()
```

**`GlassesConnectionState` fields**

| Field | Type | Description |
|-------|------|-------------|
| `bleState` | `SdkBleConnectionState` | BLE connection state |
| `btState` | `SdkBtConnectionState` | Classic BT / audio profile state (LY, etc.) |
| `deviceName` / `deviceAddress` | `String?` | Current device info |
| `btFailureReason` | `String?` | BT pairing or connect failure reason |
| `bleErrorMessage` | `String?` | BLE connect failure reason |
| `isBleConnected` | `Boolean` | Whether BLE is connected |
| `isBtAudioConnected` | `Boolean` | Whether A2DP is connected |

**State enums**

| BLE (`SdkBleConnectionState`) | Description |
|------|------|
| `IDLE` | Idle |
| `CONNECTING` | Connecting |
| `CONNECTED` | Connected |
| `DISCONNECTED` | Disconnected |
| `FAILED` | Connect failed |

| BT (`SdkBtConnectionState`) | Description |
|------|------|
| `IDLE` | Not started (incl. OTA mode) |
| `BONDING` | Pairing |
| `CONNECTING` | Connecting audio profiles |
| `CONNECTED` | Audio connected |
| `FAILED` | Pair/connect failed |
| `DISCONNECTED` | Audio disconnected |

### 5.3 Manual BT Reconnect

When `btState` is `FAILED` or `DISCONNECTED` while BLE remains `CONNECTED`:

```kotlin
GlassesManage.reconnectBluetooth()
```

Notes:

- Checks current system BT state first; skips if already connected or connecting
- **No-op in OTA mode (`isOtaMode = true`)**
- Requires `BLUETOOTH_CONNECT` on Android 12+

---

## 6. File Sync

Sync media files from glasses to the phone (BLE must be connected first). Progress is delivered via `FileSyncEvent` on `GlassesManage.eventFlow()`.

```kotlin
viewModelScope.launch {
    GlassesManage.eventFlow().collect { event ->
        when (event) {
            is FileSyncEvent.ConnectSuccess -> { /* Wi-Fi linked, download starting */ }
            is FileSyncEvent.DownloadProgress -> {
                val progress = event.progress
                val index = event.curFileIndex
                val total = event.totalFileCount
                val speed = event.speed
            }
            is FileSyncEvent.DownloadSuccess -> {
                val localPath = event.filePath
            }
            is FileSyncEvent.Failed -> {
                // event.reason / event.code
            }
            else -> Unit
        }
    }
}

// wifiMode: AP_MODE (hotspot) or P2P_MODE (Wi-Fi Direct), per firmware capability
GlassesManage.syncAllMediaFile(GlassesConstant.WifiMode.P2P_MODE)
```

**Callback events (`FileSyncEvent`)**

| Event | Description |
|-------|-------------|
| `ConnectSuccess` | Wi-Fi link to glasses established |
| `DownloadProgress` | Per-file download progress |
| `DownloadSuccess` | File saved locally |
| `Failed` | Sync failed |

> Location and Wi-Fi permissions are required (see [section 1](#1-permissions) and Demo media sync page).

---

## 7. AI Assistant
AI capabilities include **voice chat, image recognition, and translation**. Two integration approaches are available:

### SDK built-in LLM
```kotlin
val aiClient = AiAssistantClient.getInstance()

// Optional: set custom environment URLs (callable before or after initializeAiClient)
aiClient.applyServerEnvironmentToGlobals(
    AiServerEnvironmentConfig(
        baseUrl = "https://your-http-host/",
        wsUrl = "wss://your-ws-host"
    )
)

// Initialize AI runtime
aiClient.initializeAiClient(
    AiAgentConfig(
        context = context,
        channel = channel,
        aiModelType = GlassesConstant.AiModelVendor.DEFAULT,
        serverEnvironment = GlassesConstant.ServerEnvironment.DEV,
        // When provided, this custom environment takes precedence over serverEnvironment
        customServerEnvironment = AiServerEnvironmentConfig(
            baseUrl = "https://your-http-host/",
            wsUrl = "wss://your-ws-host"
        ),
        enableDefaultPlaySimultaneousAudio = true, // true: SDK auto-plays simultaneous interpretation audio
        enableDefaultPlayAgentAudio = true,        // true: SDK auto-plays Agent audio
        translationAudioStorageDirName = GlassesConstant.DEFAULT_TRANS_AUDIO_FILES_STORAGE_DIR
    )
)

// Connect AI service (must be called after initializeAiClient)
aiClient.connectAiAssistant(
    deviceId = deviceId,
    deviceName = deviceName,
    deviceModel = deviceModel,
    clientId = clientId,
    sk = sk
)

// Subscribe to AI events
aiClient.aiAgentEventFlow().collect { event ->
    when (event) {
        is AgentEvent.AiAssistantConnectState -> Unit
        is AgentEvent.AiAssistantResult -> Unit
        is AiTranslationEvent.AiTranslationResult -> Unit
        is AgentAudioEvent.AgentAudioSend -> Unit
        else -> Unit
    }
}
```

The following public methods are exposed by `AiAssistantClient` for host apps.

### 7.1 Lifecycle and connection
- `AiAssistantClient.getInstance()`: returns the singleton instance.
- `applyServerEnvironmentToGlobals(env, localWsUrl)`: syncs a preset AI service environment. In `LOCAL` mode, `localWsUrl` can override the default AI service address. Callable before or after `initializeAiClient`; if the AI service is already connected, call `connectAiAssistant(...)` or `manualReconnect()` again after switching environments.
- `applyServerEnvironmentToGlobals(serverConfig)`: syncs a custom AI service environment, allowing the host app to pass `baseUrl` and `wsUrl` directly.
- `initializeAiClient(config: AiAgentConfig)`: initializes the AI client runtime and creates the dependencies required by the AI service, image translation, and calling. Repeated calls clear the previous connection first, but **do not reconnect automatically**. Call `connectAiAssistant(...)` again if needed. If `customServerEnvironment` is provided, it takes precedence.
- `connectAiAssistant(deviceId, deviceName, deviceModel, clientId, sk)`: establishes the AI assistant connection. Usually called after the device is connected and auth parameters are available.
- `disconnect()`: disconnects the AI service, ends the call, clears image translation state, and cancels internal coroutines. Recommended when leaving the page or disconnecting the device.
- `manualReconnect()`: manually triggers AI service reconnect. Call it after receiving `AgentEvent.ReconnectRequired`.

### 7.2 Event subscription
- `aiAgentEventFlow(): Flow<AiAgentBase>`: unified AI event stream.
- Possible event types:
  `AgentEvent` (connection state, chat result, image translation result, call state, etc.),
  `AiTranslationEvent` (translation text result / failure),
  `AgentAudioEvent` (chat audio stream / translation audio stream),
  `LocalVadEvent` (local VAD state).

If SDK default audio playback is disabled (`enableDefaultPlaySimultaneousAudio = false` or `enableDefaultPlayAgentAudio = false`), subscribe to `AgentAudioEvent` and handle PCM audio yourself. The current audio callback format is **PCM / 16000Hz / mono**.

If SDK simultaneous-interpretation playback is enabled at init time but you need an in-session toggle for translated audio during a single real-time translation session, use `setTranslationAudioPlaybackEnabled` / `isTranslationAudioPlaybackEnabled` (see [Real-time translation audio playback toggle](#real-time-translation-audio-playback-toggle) in section 8).

If SDK agent audio playback is enabled at init time but you need an in-session toggle for AI reply audio during a chat, use `setAgentAudioPlaybackEnabled` / `isAgentAudioPlaybackEnabled` (see [section 7.6](#76-ai-chat-reply-audio-playback-toggle)).

### 7.3 AI translation APIs

> **Permission**: When the app captures phone microphone audio for `startReceivingAudio` / `sendReceivingAudioData`, request and hold `android.permission.RECORD_AUDIO`.

#### Language list

| Scenario | How to get languages | Notes |
|----------|----------------------|-------|
| **Voice / dialog / simultaneous translation** | Integer `langType` | No dedicated SDK HTTP API for a voice-translation language list. Pass `from`, `language`, and `toList` as backend language IDs (Demo defaults: source `140`, target `47`). Maintain display names locally; see Demo `assets/languages.json` (`name`, `nameEn`, `langType`, `code`). |
| **Image translation** | `getImageTransLangList(serviceType)` | Fetches supported languages per provider via `AgentEvent.ImageTransLangListResult`. |

**Image translation — fetch language list**

```kotlin
val aiClient = AiAssistantClient.getInstance()

viewModelScope.launch {
    aiClient.aiAgentEventFlow().collect { event ->
        when (event) {
            is AgentEvent.ImageTransLangListResult -> {
                event.languageList.forEach { lang ->
                    // lang.langType        — language ID (for imageTrans)
                    // lang.name            — display name (Chinese)
                    // lang.nameEn          — English name
                    // lang.code            — e.g. zh-CN
                    // lang.supportSource / lang.supportTarget — valid as source/target
                }
            }
            else -> Unit
        }
    }
}

// serviceType: VOLC_ENGINE(1) / ALIYUN(2) / MICROSOFT(3) / OPEN_AI(4)
aiClient.getImageTransLangList(GlassesConstant.ImageTranslateServerType.VOLC_ENGINE)
```

`LanguageResult` fields: `name`, `nameEn`, `langType`, `code`, `supportSource`, `supportTarget`.

- `startAiTranslation(from, toList, reqId, audioFormat)`: creates a translation session. When the app captures microphone audio on the phone side, use `GlassesConstant.AI_TRANSLATION_AUDIO_FORMAT_RAW_PCM` for `audioFormat`.
- `startReceivingAudio(mode, language)`: starts sending recorded audio to the AI service. Common `mode` values:
  `GlassesConstant.AI_ASSISTANT_TYPE_LISTEN_MODE_TRANSLATION` (dialog translation),
  `GlassesConstant.AI_ASSISTANT_TYPE_LISTEN_MODE_SIMULTANEOUS_INTERPRETATION` (real-time simultaneous interpretation).
- `sendReceivingAudioData(mode, byteArray)`: continuously sends 16 kHz mono PCM. The SDK applies uplink preprocessing (denoise, AGC, etc.) internally; the host app should send raw PCM only.
- `pauseListening()`: pauses the current listening session. Useful for simultaneous interpretation when you want to pause without ending the whole session.
- `stopReceivingAudio(mode)`: sends `stop` and ends the current recording flow for the given mode.
- `cancelReceivingAudio()`: aborts the current recording / AI listening flow immediately.
- `setTranslationAudioPlaybackEnabled(enabled)`: enables or disables SDK auto-playback of **real-time translation** downlink audio (mainly for simultaneous interpretation `simultaneous_audio`). When disabled, playback stops immediately and the pending queue is cleared, but the **translation session is not interrupted**.
- `isTranslationAudioPlaybackEnabled()`: returns whether real-time translation downlink playback is enabled. Returns `true` if the AI client is not initialized.

### 7.4 Image translation APIs
- `getImageTransLangList(serviceType)`: fetches the supported language list for the given image translation provider. `serviceType` can be `VOLC_ENGINE`, `ALIYUN`, `MICROSOFT`, or `OPEN_AI`.
- `imageTrans(targetImage, sourceLanguage, targetLanguage)`: uploads an image and requests image translation. Results are returned via `AgentEvent.ImageTransResult` / `AgentEvent.ImageTransFailEvent`.

### 7.5 Voice room and audio/video call APIs

> **Permissions**: Calls need microphone (and camera for video calls). Request `android.permission.RECORD_AUDIO`; video calls also require `android.permission.CAMERA`.

- `getVoiceRoomParams(lang, target, type, appId, mac)`: fetches Zego voice room parameters.
  `type = 1` means video call, `type = 2` means voice call.
- `startCall(appID, token, roomID, streamId, userID, isVideo, local, remote)`: starts an audio/video call.
- `updateLocalView(view)`: updates the local preview `TextureView`.
- `updateRemoteView(view)`: updates the remote `TextureView`.
- `endCall()`: ends the call.
- `muteMicrophone(mute)`: mutes / unmutes the microphone.
- `enableSpeaker(enable)`: enables / disables speaker playback.
- `muteVideo(mute)`: disables / enables local video capture.
- `switchCamera(useFront)`: switches between front and rear camera.
- `setPlayVolume(volume)`: sets call playback volume.

### 7.6 AI chat reply audio playback toggle

Applies to AI assistant voice chat (TTS / `agent_audio`): lets users temporarily turn SDK auto-playback of reply audio on or off during an active conversation, without changing `AiAgentConfig.enableDefaultPlayAgentAudio`.

**Behavior**:

| Item | Description |
|------|-------------|
| Scope | SDK auto-playback of TTS binary stream and `agent_audio` |
| Relation to init config | Still constrained by `enableDefaultPlayAgentAudio = false`; the runtime toggle cannot enable playback when that init flag is `false` |
| When disabled | `AgentAudioEvent.AgentAudioSend` is still delivered; audio file writes are unaffected |
| When disabled during playback | If a TTS stream is active, playback stops immediately and the pending queue is cleared, but the **AI chat session is not interrupted** |

- `setAgentAudioPlaybackEnabled(enabled)`: enables or disables SDK auto-playback of AI chat downlink audio.
- `isAgentAudioPlaybackEnabled()`: returns whether AI chat downlink playback is enabled. Returns `true` if the AI client is not initialized.

```kotlin
val aiClient = AiAssistantClient.getInstance()

// User turns off AI reply playback (takes effect immediately)
aiClient.setAgentAudioPlaybackEnabled(false)

// Query current state
val playbackEnabled = aiClient.isAgentAudioPlaybackEnabled()

// Re-enable SDK auto-playback
aiClient.setAgentAudioPlaybackEnabled(true)
```

Demo: `AiAssistantScreen` provides a bottom “Reply audio: On / Off” toggle, mapped to `AiAssistantViewModel.toggleAgentAudioPlayback()`.

## 8. AI Translation

Please refer to the Demo `translate` implementation.

> **Permission**: Request `android.permission.RECORD_AUDIO` before capturing microphone audio for translation. Prompt the user if permission is denied.

### Language list (voice translation)

Voice, dialog, and simultaneous interpretation use **language IDs (`langType`, Int)**, not locale strings. The SDK does not expose an online API to fetch the voice-translation language table; the host app maintains the list (display names + `langType` for API calls).

Demo approach: load from `assets/languages.json`. Example entry:

```json
{
  "name": "Chinese",
  "nameEn": "Chinese",
  "langType": 140,
  "code": "zh-CN"
}
```

```kotlin
// Load local language table (same as Demo)
val languages: List<Language> /* parse languages.json */

val srcLangType = languages.find { it.code == "zh-CN" }?.langType ?: 140
val targetLangType = languages.find { it.code == "en-US" }?.langType ?: 47

aiClient.startAiTranslation(
    from = srcLangType,
    toList = listOf(targetLangType),
    reqId = System.currentTimeMillis(),
    audioFormat = GlassesConstant.AI_TRANSLATION_AUDIO_FORMAT_RAW_PCM,
)
aiClient.startReceivingAudio(
    mode = GlassesConstant.AI_ASSISTANT_TYPE_LISTEN_MODE_TRANSLATION,
    language = srcLangType, // source language ID, same as from
)
```

Common `mode` values:

| Constant | Scenario |
|----------|----------|
| `AI_ASSISTANT_TYPE_LISTEN_MODE_TRANSLATION` | Dialog translation |
| `AI_ASSISTANT_TYPE_LISTEN_MODE_SIMULTANEOUS_INTERPRETATION` | Real-time simultaneous interpretation |

> For image translation languages, use `getImageTransLangList` — see [7.3](#73-ai-translation-apis) and [7.4](#74-image-translation-apis).

When the app actively captures microphone audio on the phone side for translation, the recommended call order is:

```kotlin
val aiClient = AiAssistantClient.getInstance()
val mode = GlassesConstant.AI_ASSISTANT_TYPE_LISTEN_MODE_TRANSLATION
val requestId = System.currentTimeMillis()

aiClient.startAiTranslation(
    from = 140,
    toList = listOf(47),
    reqId = requestId,
    audioFormat = GlassesConstant.AI_TRANSLATION_AUDIO_FORMAT_RAW_PCM
)

aiClient.startReceivingAudio(mode, 140)

audioRecorderPcmFlow.collect { pcm ->
    aiClient.sendReceivingAudioData(mode = mode, byteArray = pcm)
}

aiClient.stopReceivingAudio(mode)
```

Notes:
- `startAiTranslation(...)`: create the translation session before sending audio.
- `startReceivingAudio(mode, language)`: enter translation listening state; `language` is the source language.
- `sendReceivingAudioData(...)`: continuously sends 16 kHz mono PCM raw data.
- `pauseListening()`: pause without ending the whole session, mainly for simultaneous interpretation scenarios.
- `stopReceivingAudio(mode)`: normally end the current translation session.
- `cancelReceivingAudio()`: abort the current audio input flow abnormally.

#### Real-time translation audio playback toggle

Applies to `AI_ASSISTANT_TYPE_LISTEN_MODE_SIMULTANEOUS_INTERPRETATION` (real-time simultaneous interpretation): lets users temporarily turn SDK auto-playback of translated audio on or off during an active session, without changing `AiAgentConfig.enableDefaultPlaySimultaneousAudio`.

**Behavior**:

| Item | Description |
|------|-------------|
| Scope | SDK auto-playback of simultaneous-interpretation downlink `simultaneous_audio` |
| Relation to init config | Still constrained by `enableDefaultPlaySimultaneousAudio = false`; the runtime toggle cannot enable playback when that init flag is `false` |
| When disabled | `AgentAudioEvent.TranslationAudioSend` is still delivered |

```kotlin
val aiClient = AiAssistantClient.getInstance()

// User turns off translated audio playback (takes effect immediately)
aiClient.setTranslationAudioPlaybackEnabled(false)

// Query current state
val playbackEnabled = aiClient.isTranslationAudioPlaybackEnabled()

// Start a new real-time translation session
aiClient.startAiTranslation(
    from = 140,
    toList = listOf(47),
    reqId = System.currentTimeMillis(),
    audioFormat = GlassesConstant.AI_TRANSLATION_AUDIO_FORMAT_RAW_PCM,
)
aiClient.startReceivingAudio(
    GlassesConstant.AI_ASSISTANT_TYPE_LISTEN_MODE_SIMULTANEOUS_INTERPRETATION,
    language = 140,
)
```

Demo: the real-time translation bottom controls in `TranslatorScreen` include a “Translation playback: On / Off” toggle, mapped to `TranslatorViewModel.toggleTranslationAudioPlayback()`.

**Real-time translation record-and-play (echo and routing)**: Under `AI_ASSISTANT_TYPE_LISTEN_MODE_SIMULTANEOUS_INTERPRETATION`, the SDK branches automatically by output device (`TranslationSimultaneousAudioPolicy`):
- **Bluetooth A2DP / wired headset**: `MODE_NORMAL` + `VOICE_RECOGNITION`; downlink prefers A2DP when `simultaneousInterpretationPlaybackPreferA2dp = true`.
- **Phone speaker**: `MODE_IN_COMMUNICATION` + `VOICE_COMMUNICATION`; the app calls `bindSimultaneousInterpretationCaptureSession(audioSessionId)` to share the session with the SDK, and enables hardware `AcousticEchoCanceler` to reduce translated audio being picked up by the microphone.

Demo: `AiAssistantClient.resolveSimultaneousInterpretationAudioPolicy()` → `StreamAudioRecorder.start(simultaneousPolicy=...)`; on speaker, bind the session; on pause/end, call `clearSimultaneousInterpretationCaptureSession()`.

**Event subscription** (`AiAssistantClient.aiAgentEventFlow()`):

| Type | Event | Description |
|------|-------|-------------|
| `AiTranslationEvent` | `AiTranslationResult` | Translated text |
| | `Failed` | Translation error |
| `AgentAudioEvent` | `TranslationAudioStart` / `TranslationAudioSend` / `TranslationAudioStop` | Translation audio (PCM 16k mono) |
| `AgentEvent` | `AiAssistantConnectState` | AI service connection state |

### Custom LLM (implemented by the host app)
To enable custom mode, please contact the development team.

- `GlassesManage.startAiAssistant`: start recording
  - `AudioStateEvent.ReceivingAudioData`: continuous audio input
- `GlassesManage.stopAiAssistant()`: stop recording
- `GlassesManage.interruptAiAssistant()`: interrupt recording
- `GlassesManage.takePicture(true)`: AI image recognition (`takePhotoOnly = true` sends image to the app; `false` saves on glasses — see [11.8 Device-side capture](#8-device-side-capture-and-photo))
  - callback events: `CmdResultEvent.ImageData` / `CmdResultEvent.ImageFile`

---

## 9. Live Streaming

Glasses push an RTSP stream; the app subscribes to `LiveEvent` for the URL, then previews locally or re-pushes to a third-party platform (Demo supports Douyin live).

**Prerequisites**: BLE connected; some channels require Wi-Fi / location permissions (see Demo live page). Call `getDeviceSupportedFeatures()` first to confirm live streaming support.

```kotlin
viewModelScope.launch {
    GlassesManage.eventFlow().collect { event ->
        when (event) {
            is LiveEvent.LiveSuccess -> {
                val rtspUrl = event.rtsp  // glasses RTSP URL
            }
            is LiveEvent.Failed -> {
                // event.reason / event.code
            }
            LiveEvent.RespStop -> {
                // live streaming stopped
            }
            else -> Unit
        }
    }
}

GlassesManage.startLiveStreaming(
    LiveStreamingConfig(
        videoPictureWidth = 1280,
        videoPictureHeight = 720,
        fps = 30,
        bps = 1_000_000,
        liveChannel = GlassesConstant.LiveChannel.WIFI_STATION,
    )
)

// Re-push RTSP to a third-party endpoint (e.g. RTMP); channel-dependent
GlassesManage.startPushLiveStreaming("rtmp://your-push-url")

GlassesManage.stopLiveStreaming()
```

| API | Description |
|-----|-------------|
| `startLiveStreaming(liveStreamingConfig)` | Start glasses-side live streaming |
| `startPushLiveStreaming(liveUrl)` | Push the stream to a third-party URL (e.g. RTMP) |
| `stopLiveStreaming()` | Stop live streaming |

**`LiveStreamingConfig` parameters**:

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `videoPictureWidth` | `Int` | `1280` | Video width |
| `videoPictureHeight` | `Int` | `720` | Video height |
| `fps` | `Int` | `30` | Frame rate |
| `bps` | `Int` | `1000000` | Bitrate (bps) |
| `liveChannel` | `GlassesConstant.LiveChannel` | `WIFI_AP` | Live streaming transport channel |

**`liveChannel` values**:

| Enum | Description |
|------|-------------|
| `WIFI_AP` | Wi-Fi hotspot mode |
| `WIFI_STATION` | Wi-Fi Station mode |
| `BT` | Bluetooth channel |

**Callback events (`LiveEvent`)**:

| Event | Description |
|-------|-------------|
| `LiveSuccess` | Stream started; includes RTSP URL |
| `Failed` | Stream failed |
| `RespStop` | Stream stopped |

> Demo: `LiveViewModel` + `LiveScreen`. Capabilities and parameter handling vary by channel (LY / RTK, etc.) and firmware — confirm `SdkConfig.channel` before integration.

---

## 10. SDK Flow Events

> **Connection state**: prefer `GlassesManage.connectionStateFlow()` for BLE/BT (see [section 5.2](#52-subscribe-ble--bt-state-recommended)). This section documents other `eventFlow` events: scan, sync, OTA, commands, etc.

### Common - `CmdResultEvent`
- For device settings, device status, media files, battery, and button actions, observe `CmdResultEvent` subclasses.

### 1. Device Scan - `ScanStateEvent`
- `DeviceFound`: returns `ScanResult`
- `ScanFinished`: scan completed
- `Error`: scan error

### 2. Device Connection

**Recommended**: subscribe to `GlassesManage.connectionStateFlow()` — see [section 5.2](#52-subscribe-ble--bt-state-recommended).

**Raw `eventFlow` events** (advanced):

| Type | Event | Description |
|------|------|------|
| `ConnectionStateEvent` | `Connecting` | BLE connecting |
| | `Connected` | Connected (`isOtaMode = true` skips BT pairing) |
| | `Disconnected` | Disconnected |
| | `Failed` | Connection failed |
| `BtConnectEvent` | `Bonding` / `Bonded` / `BondFailed` | Classic BT pairing (LY, etc.; SDK-driven) |
| | `A2dpConnected` / `HfpConnected`, etc. | Audio profile changes |

Manual BT reconnect: `GlassesManage.reconnectBluetooth()` — see [section 5.3](#53-manual-bt-reconnect).

### 3. Audio Stream - `AudioStateEvent`
- Refer to the Demo

### 4. Media Sync - `FileSyncEvent`
- `ConnectSuccess`: Wi-Fi connected
- `DownloadProgress`: download progress
- `DownloadSuccess`: sync success
- `Failed`: sync failed

### 5. AI Assistant - `AgentEvent`
- `AgentEvent.AiAssistantConnectState`: AI service connection state
- `AgentEvent.AiAssistantResult`: AI chat result
- `AgentEvent.AiScheduleResult`: MCP schedule result
- `AgentEvent.ImageTransLangListResult`: image translation language list
- `AgentEvent.ImageTransResult`: image translation result
- `AgentEvent.ImageTransFailEvent`: image translation failure
- `AgentEvent.VoiceRoomParamsEvent` / `VoiceRoomParamsFailEvent`: voice room parameter result
- `AgentEvent.CallConnected` / `CallDisconnected`: call connected / disconnected
- `AgentEvent.RemoteVideoStateEvent`: remote video mute state
- `AgentEvent.RemoteLanguageEvent`: remote language change
- `AgentEvent.ReconnectRequired`: business layer should trigger reconnect
- `AgentEvent.DeviceAiServiceError`: device-side AI service error
---

### 6. AI Translation - `AiTranslationEvent`
- `AiTranslationResult`: translated text result
- `Failed`: error

### 7. AI Audio Stream - `AgentAudioEvent`
- `AgentAudioStart` / `AgentAudioSend` / `AgentAudioStop`: AI chat audio stream
- `TranslationAudioStart` / `TranslationAudioSend` / `TranslationAudioStop`: AI translation audio stream

---

### 8. OTA Upgrade - `OTAEvent`
- `Start`: OTA started
- `Progress`: OTA progress
- `Success`: OTA success
- `Failed`: OTA failed
- `Cancelled`: OTA cancelled
- `Idle`: idle state
- `DeviceRebooting`: device is rebooting

---

### 9. Device Action State - `ActionSync`
- `ActionSync(type, state)`: device action state sync; `type` is `GlassesConstant.ActionSyncType`, and `state` indicates whether the action is on/triggered (`true` on one-shot actions means this trigger fired).
- Call `GlassesManage.getActionState()` to query actively; the device may also push updates through `eventFlow`.

**`ActionSyncType` enum**:

| Enum | `index` | Description |
|------|---------|-------------|
| `TAKE_PHOTO` | 0 | Take photo |
| `RECORD_AUDIO` | 1 | Audio recording |
| `RECORD_VIDEO` | 2 | Video recording |
| `VOLUME_UP` | 3 | Volume up (one-shot) |
| `VOLUME_DOWN` | 4 | Volume down (one-shot) |
| `NOD` | 5 | Nod (one-shot) |
| `SHAKE_HEAD` | 6 | Shake head (one-shot) |
| `MUSIC` | 7 | Music playback state |
| `WEAR` | 8 | Wear detection |
| `IMPORTING` | 9 | Media import in progress |
| `SINGLE_TOUCH` | 1000 | Single tap (SDK-synthesized, not a BLE index) |

```kotlin
when (event) {
    is CmdResultEvent.ActionSync -> when (event.type) {
        GlassesConstant.ActionSyncType.TAKE_PHOTO ->
            sdkRepository.updateTakePhotoStatus(event.state)
        GlassesConstant.ActionSyncType.RECORD_AUDIO ->
            sdkRepository.updateRecordAudioStatus(event.state)
        GlassesConstant.ActionSyncType.RECORD_VIDEO ->
            sdkRepository.updateRecordVideoStatus(event.state)
        GlassesConstant.ActionSyncType.WEAR ->
            sdkRepository.updateWearingStatus(event.state)
        GlassesConstant.ActionSyncType.MUSIC -> {
            if (isAiTranslatingSceneSuppressMusic) {
                pauseMusicForAiTranslatingScene()
            }
        }
        GlassesConstant.ActionSyncType.VOLUME_UP,
        GlassesConstant.ActionSyncType.VOLUME_DOWN,
        GlassesConstant.ActionSyncType.NOD,
        GlassesConstant.ActionSyncType.SHAKE_HEAD,
        GlassesConstant.ActionSyncType.IMPORTING -> Unit
        GlassesConstant.ActionSyncType.SINGLE_TOUCH -> { /* single tap */ }
    }
}
```

### 10. Live Streaming - `LiveEvent`
- `LiveSuccess`: stream started, includes RTSP URL
- `Failed`: stream failed
- `RespStop`: stream stopped

### 11. SDK Global Errors - `SdkErrorEvent`
- `GlobalError`: global SDK errors such as not initialized (error code `1001`)

---

## 11. Device Settings
The SDK exposes device setting read/write APIs through `GlassesManage`, including LED, gestures, wear detection, volume, time sync, and more.
**Result callbacks**: collect `GlassesEvent` from `GlassesManage.eventFlow()` and focus on `CmdResultEvent` subclasses, consistent with [Section 10](#10-sdk-flow-events).

---

### 1. Get consolidated device settings
Actively fetch the current aggregated device-side settings:

```kotlin
GlassesManage.getDeviceSettingsState()
```

**Callback event**: `CmdResultEvent.DeviceSettingsStateEvent`
**Payload** `DeviceSettingsStateDTO` fields:
- `ledBrightness`: `LyCmdConstant.LedBrightnessLevel?` (LED brightness level)
- `recordDuration`: `Int?` (video recording duration)
- `systemVolume` / `mediaVolume` / `callVolume`: `Int?` (system / media / call volume)
- `wearDetectionEnabled`: `LyCmdConstant.WearDetectionState?` (wear detection)
- `voiceCommandEnabled`: `Boolean?` (voice command related state)
- `gestureSettings`: `Map<GestureType, GestureAction>?` (shortcut action mapped to each gesture)
- `burstPhotoCount`: `Int?` (burst photo count)
- `orientation`: `LyCmdConstant.ScreenOrientation?` (screen orientation)

---

### 2. Update individual device settings
The APIs below send write commands. On success, you will usually receive `DeviceSettingsStateEvent` or `Success` again, depending on firmware and strategy. Always use the actual `eventFlow` callbacks as the source of truth.

| API | Description |
|-----|------|
| `setLedBrightness(level: LyCmdConstant.LedBrightnessLevel)` | LED brightness (`LOW` / `MEDIUM` / `HIGH`) |
| `setVideoDuration(times: Int)` | Video duration |
| `setWearDetection(state: LyCmdConstant.WearDetectionState)` | Wear detection (`OFF` / `ON`) |
| `setGestureShortcut(gesture: LyCmdConstant.GestureType, action: LyCmdConstant.GestureAction)` | Set a single gesture shortcut |
| `resetGestureShortcuts()` | Restore gesture shortcuts to defaults |
| `setScreenOrientation(orientation: LyCmdConstant.ScreenOrientation)` | Photo / video orientation (`PORTRAIT` / `LANDSCAPE`) |
| `setOfflineVoiceLanguage(language: Int)` | Offline voice language; on LY logs `0` is usually Chinese and `1` English, but final behavior depends on firmware |
| `setVolume(type: LyCmdConstant.AudioVolumeType, volume: Int)` | Set volume by type (`SYSTEM` / `MEDIA` / `CALL`) |
| `setTime()` | Sync phone time to glasses |
| `setVoiceWakeUp(localOfflineEnabled: Boolean, opusPushEnabled: Boolean)` | Voice wake-up related switches: `true` enables the corresponding capability (local offline wake-up / Opus uplink) |

```kotlin
GlassesManage.setLedBrightness(LyCmdConstant.LedBrightnessLevel.MEDIUM)
GlassesManage.setWearDetection(LyCmdConstant.WearDetectionState.ON)
GlassesManage.setGestureShortcut(
    LyCmdConstant.GestureType.SINGLE_TAP,
    LyCmdConstant.GestureAction.PLAY_PAUSE
)
GlassesManage.setScreenOrientation(LyCmdConstant.ScreenOrientation.LANDSCAPE)
```

---

### 3. Version, features, battery, and storage

```kotlin
// Firmware / middleware version / hardware (on LY, second item in response is often ISP version; DTO field is wifiVersion)
GlassesManage.requestDeviceVersionInfo()
// Event: CmdResultEvent.DeviceVersionInfoEvent(data: DeviceVersionInfoDTO)

// Device feature flags (live streaming, quick volume, watermark, wear detection, orientation, etc.)
GlassesManage.getDeviceSupportedFeatures()
// Event: CmdResultEvent.DeviceSupportedFeatures(featuresConfigInfo: GlassesFeaturesConfigInfo)

// Battery level (active query or device report)
GlassesManage.getBatteryLevel()
// Event: CmdResultEvent.DevicePower(value, isCharging)

// Storage query (command sent to device; event packaging varies by channel)
GlassesManage.getDeviceStorage()
```

---

### 4. Volume and voice wake-up state

```kotlin
GlassesManage.getVolume()
// Event: CmdResultEvent.DeviceVolumeState(systemVolume, mediaVolume, callVolume)

GlassesManage.upVolume()
GlassesManage.downVolume()
// Device-side volume shortcuts (check getDeviceSupportedFeatures first)

GlassesManage.getVoiceWakeUp()
// Event: CmdResultEvent.VoiceCommandDisableState(
//     localOfflineVoiceDisabled, opusStreamPushDisabled)
```

---

### 5. Media and call control

```kotlin
GlassesManage.controlMusic(Boolean)  // true play / false pause (firmware-dependent)
GlassesManage.switchMusic(LyCmdConstant.MusicSwitchAction.PREVIOUS) // previous track
GlassesManage.switchMusic(LyCmdConstant.MusicSwitchAction.NEXT)     // next track

GlassesManage.answerPhoneCall()
GlassesManage.hangUpPhoneCall()
```

---

### 6. System maintenance

```kotlin
GlassesManage.rebootDevice()
GlassesManage.restoreFactorySettings()
```

---

### 7. Optional state queries often shown on the settings page
The following APIs are more about device state but are often shown alongside settings:

```kotlin
GlassesManage.getMediaFileCount()   // CmdResultEvent.MediaFileCount
GlassesManage.getActionState()      // CmdResultEvent.ActionSync (type: ActionSyncType)
```

---

### 8. Device-side capture and photo

Control glasses-side audio recording, video recording, and photo capture (BLE must be connected). Capabilities vary by channel and firmware.

| API | Description |
|-----|-------------|
| `startDeviceRecording()` | Start glasses-side audio recording |
| `stopDeviceRecording()` | Stop glasses-side audio recording |
| `startDeviceVideoRecording()` | Start glasses-side video recording |
| `stopDeviceVideoRecording()` | Stop glasses-side video recording |
| `takePicture(takePhotoOnly: Boolean)` | Take a photo. `true`: AI recognition, image sent to app; `false`: save on glasses storage |

```kotlin
GlassesManage.startDeviceRecording()
GlassesManage.stopDeviceRecording()

GlassesManage.startDeviceVideoRecording()
GlassesManage.stopDeviceVideoRecording()

GlassesManage.takePicture(takePhotoOnly = true)   // AI recognition
GlassesManage.takePicture(takePhotoOnly = false)  // save on glasses
```

**Callback events**:
- Photo / recognition: `CmdResultEvent.ImageData` (raw bytes) or `CmdResultEvent.ImageFile` (local file path, channel-dependent)
- Custom LLM recording: see [Custom LLM](#custom-llm-implemented-by-the-host-app) and `AudioStateEvent`

---

## 12. OTA Upgrade

BLE must be connected first. Progress is delivered via `OTAEvent` on `GlassesManage.eventFlow()` (see [Section 10](#8-ota-upgrade---otaevent)).

```kotlin
GlassesManage.startOTA(
    path = "/path/to/firmware.bin",
    type = GlassesConstant.OtaType.FIRMWARE,
    version = "",  // optional target version for WIFI_ISP upgrade
)
```

| Parameter | Type | Description |
|-----------|------|-------------|
| `path` | `String` | Absolute path to the local firmware file |
| `type` | `GlassesConstant.OtaType` | OTA type |
| `version` | `String` | Optional, default `""`; target version for `WIFI_ISP` upgrade |

**`OtaType` values**:

| Enum | Description |
|------|-------------|
| `FIRMWARE` | Main firmware OTA |
| `WIFI_ISP` | Wi-Fi / ISP module OTA |

---

## 13. Error Codes

### SDK base errors (1000 ~ 1001)
| Error Code | Name | Description |
|:-------:|:------|:------|
| 1001 | ERROR_CODE_SDK_NOT_INITIALIZED | SDK is not initialized |

### Image transfer errors (2001 - 2011)
| Error Code | Name | Description |
|:-------:|:------|:------|
| 2001 | ERROR_CODE_IMAGE_PACKET_TOO_SHORT | Packet too short |
| 2002 | ERROR_CODE_IMAGE_INVALID_HEADER | Invalid header |
| 2003 | ERROR_CODE_IMAGE_INVALID_FOOTER | Invalid footer |
| 2004 | ERROR_CODE_IMAGE_CRC_FAILURE | CRC validation failed |
| 2005 | ERROR_CODE_IMAGE_NO_HEADER_RECEIVED | Data packet received before file header |
| 2006 | ERROR_CODE_IMAGE_INCOMPLETE | File reception incomplete |
| 2007 | ERROR_CODE_IMAGE_TIMEOUT | Receive timeout |
| 2008 | ERROR_CODE_IMAGE_UNKNOWN_CMD | Unknown image command |
| 2009 | ERROR_CODE_IMAGE_INVALID_DATA_PACKET | Invalid data packet |
| 2010 | ERROR_CODE_IMAGE_SAVE | Failed to save image |
| 2011 | ERROR_CODE_IMAGE_RECOGNITION | Image recognition failed |

### Wi-Fi connection errors (3001 - 3004)
| Error Code | Name | Description |
|:-------:|:------|:------|
| 3001 | ERROR_CODE_WIFI_CONNECT_TIMEOUT | Wi-Fi connection flow timeout |
| 3002 | ERROR_CODE_WIFI_DEVICE_DISCOVERY_TIMEOUT | Wi-Fi device discovery timeout |
| 3003 | ERROR_CODE_WIFI_NEGOTIATION_TIMEOUT | Wi-Fi negotiation timeout |
| 3004 | ERROR_CODE_WIFI_UNKNOWN_ERROR | Unknown Wi-Fi error |

### File download errors (3101 - 3105)
| Error Code | Name | Description |
|:-------:|:------|:------|
| 3101 | ERROR_CODE_DOWNLOAD_GET_FILE_LIST_FAILED | Failed to get file list |
| 3102 | ERROR_CODE_DOWNLOAD_FILE_NOT_FOUND | Target file not found |
| 3103 | ERROR_CODE_DOWNLOAD_FAILED | File download failed |
| 3104 | ERROR_CODE_DOWNLOAD_NETWORK_ERROR | Network error |
| 3105 | ERROR_CODE_DOWNLOAD_DELETE | Failed to delete file |

> For OTA error codes, please refer to the [official OTA documentation](https://doc.zh-jieli.com/Apps/Android/ota/zh-cn/master/development/interface_desc.html#id7).
