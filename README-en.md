# LinWear Ai Glasses SDK Documentation

> [中文版](README.md)

---

## Table of Contents
- [1. Permissions](#1-permissions)
- [2. Dependencies (Required)](#2-dependencies-required)
  - [2.0+ (recommended)](#20-recommended-modular)
  - [Upgrading from 1.x to 2.0+](#upgrading-from-1x-to-20)
- [3. SDK Initialization](#3-sdk-initialization)
  - [GlassesManage API support by channel](#glassesmanage-api-support-by-channel)
- [4. Scan Devices](#4-scan-devices)
- [5. Connect Device](#5-connect-device)
  - [5.1 Connect / Disconnect BLE](#51-connect--disconnect-ble)
  - [5.2 Subscribe BLE + BT State (Recommended)](#52-subscribe-ble--bt-state-recommended)
  - [5.3 Manual BT Reconnect (LY / TB)](#53-manual-bt-reconnect-ly--tb)
- [6. File Sync](#6-file-sync)
- [7. AI Assistant](#7-ai-assistant)
- [8. AI Translation](#8-ai-translation)
- [9. Live Streaming](#9-live-streaming)
  - [9.6 Live Experience Config (Douyin Key / Package Name / Signing)](#96-live-experience-config-douyin-key--package-name--signing)
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
<!-- Live streaming front desk service -->
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

---

## 2. Dependencies (Required)

### 2.0+ (recommended, modular)

Always add `sdk-core`, then optional scheme modules (one or more). `SdkConfig.channel` must match the modules you include.

```gradle
// Required
implementation("com.fission.wear.glasses:sdk-core:last_version")
implementation("io.reactivex.rxjava3:rxjava:3.1.6")

// Optional (one or more)
implementation("com.fission.wear.glasses:sdk-ly:last_version")    // LY
implementation("com.fission.wear.glasses:sdk-rtk:last_version")   // RTK
implementation("com.fission.wear.glasses:sdk-tb:last_version")  // TB
```

> **Minimum Android version**: Android 7.0 (API 24). Your app's `minSdk` must be at least 24.

### Upgrading from 1.x to 2.0+

1. **Replace dependencies**: Remove `com.fission.wear.glasses:sdk` and add `sdk-core` plus the scheme modules you need (`sdk-ly` / `sdk-rtk` / `sdk-tb`).
2. **Remove Jieli libs**: Delete any manually bundled Jieli OTA artifacts (e.g. `jl_bt_ota_*.aar` / `jl_bt_ota_*.jar` under `app/libs`, or via `files()` / `flatDir`).
3. **LyCmdConstant**: Migrate all usages to `GlassesConstant`.
4. **Do not mix**: Never depend on both the 1.x monolithic `sdk` and 2.0+ modules.


### Repositories

Add to `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://repo.repsy.io/mvn/linwear/android") }
        maven { url = uri("https://maven.zego.im") }
        // For sdk-tb, also add
        maven { url = uri("https://maven.topstepht.com/repository/maven-public/") }
    }
}
```


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
        // productSeries = GlassesConstant.ProductSeries.T, // Required for T-series hardware; omit for S (default)
        // deviceLensType = GlassesConstant.LensType.WIDE_ANGLE, // RTK wide-angle lens; omit for flat-angle (default)
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
| `productSeries` | `GlassesConstant.ProductSeries` | No | `S` | Glasses product series. Affects supported media-sync Wi-Fi modes and ISP OTA result parsing. **Must match the connected hardware series.** |
| `deviceLensType` | `GlassesConstant.LensType` | No | `LensType.FLAT_ANGLE` | Lens type. On the RTK channel, wide-angle enables JPG distortion correction; flat-angle skips it. Only takes effect when passed to `initialize`. |

**`channel` values**:

| Enum | Description |
|------|-------------|
| `ChannelType.TB` | TB platform |
| `ChannelType.LY` | LY platform (Demo default) |
| `ChannelType.RTK` | RTK platform |
| `ChannelType.QC` | QC platform |

**`productSeries` values**:

| Enum | Description | Supported Wi-Fi sync modes | Default Wi-Fi mode |
|------|-------------|---------------------------|--------------------|
| `ProductSeries.S` | S series (default) | `AP_MODE`, `P2P_MODE` | `AP_MODE` |
| `ProductSeries.T` | T series | `AP_MODE` | `AP_MODE` |

**`deviceLensType` values**:

| Enum | Description | RTK JPG distortion correction |
|------|-------------|------------------------------|
| `LensType.FLAT_ANGLE` | Flat-angle lens (default) | Off |
| `LensType.WIDE_ANGLE` | Wide-angle lens | On |

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
| `enableDefaultPlaySimultaneousAudio` | `Boolean` | No | `true` | Whether the SDK auto-plays real-time simultaneous-interpretation downlink PCM (`simultaneous_audio`). When `false`, the SDK does not auto-play. The runtime toggle `setTranslationAudioPlaybackEnabled` still applies when init allows it (see [section 8](#real-time-translation-audio-playback-toggle)). |
| `enableDefaultPlayAgentAudio` | `Boolean` | No | `true` | Whether the SDK auto-plays AI assistant (Agent) downlink PCM. When `false`, the SDK does not auto-play. The runtime toggle `setAgentAudioPlaybackEnabled` still applies when init allows it (see [section 7.6](#76-ai-chat-reply-audio-playback-toggle)). |
| `translationAudioStorageDirName` | `String` | No | `"transAudioFiles"` | Subdirectory name under `context.filesDir` for translation/dialog recording files. |
| `aiDialogueLanguage` | `Int` | No | `140` | Source language ID (`langType`) for AI dialogue when the glasses button starts recording. Default `140` (Chinese). Can be changed at runtime with `setAiDialogueLanguage` (see [section 7.7](#77-ai-dialogue-source-language)). |

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
| `CUSTOM`   | Custom environment (configure `baseUrl` and `wsUrl` separately) |

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
| `reconnectBluetooth()` | Manually reconnect Bluetooth audio. Requires BLE connected; **LY skips automatically in OTA mode**. |
| `syncAllMediaFile(wifiMode: WifiMode? = null)` | Starts media sync. Defaults to `AP_MODE` when `wifiMode` is `null`. For `AP_MODE`, the SDK picks the AP connection method from `productSeries`. Emits `FileSyncEvent.Failed` if the mode is not supported for the current series. |

Notes:

- `Utils.init()` comes from UtilCodex, not the SDK. File logging is optional (enabled in Demo).
- Preset env: use method A plus `AiAgentConfig.serverEnvironment`.
- Custom URLs: use method B, or pass `customServerEnvironment` in `AiAgentConfig` (it **overrides** `serverEnvironment`); do not configure both redundantly.
- If you switch environments after the AI service is connected, call `connectAiAssistant(...)` or `manualReconnect()` again.
- Demo “Custom environment” maps to `GlassesConstant.ServerEnvironment.CUSTOM` with separate `baseUrl` and `wsUrl`.

### GlassesManage API support by channel

`GlassesManage` exposes one API surface; behavior depends on the strategy for `SdkConfig.channel`. Unsupported calls are usually **no-ops** (no event / log only). Check this matrix before integrating.

Legend: ✓ supported · △ partial / differs by channel · — not supported (empty or stub)

> **QC** is omitted below. **TB** capabilities differ from LY/RTK — verify the matrix for your channel.

#### Lifecycle / scan / connect

| API | LY | RTK | TB | Notes |
|-----|:--:|:---:|:--:|-------|
| `initialize` / `isDebug` / `setProductSeries` | ✓ | ✓ | ✓ | Shared |
| `aiUplinkProfile` | ✓ | ✓ | ✓ | Channel AI uplink profile |
| `eventFlow` / `connectionStateFlow` / `currentConnectionState` | ✓ | ✓ | ✓ | Shared |
| `startScanBleDevices` / `stopScanBleDevices` | ✓ | ✓ | ✓ | |
| `connect` / `disConnect` | ✓ | ✓ | ✓ | Unpair clears bonding (including classic Bluetooth) |
| `reconnectBluetooth` | ✓ | — | △ | LY: audio reconnect; TB: retry when connection is abnormal |

#### OTA

| API | LY | RTK | TB | Notes |
|-----|:--:|:---:|:--:|-------|
| `startOTA` | ✓ | — | ✓ | LY/TB: `FIRMWARE` / `WIFI_ISP`; use RTK API below on RTK |
| `startRtkOta` | — | ✓ | — | **RTK only**: dual-channel BT + Wi‑Fi upgrade |

#### Live / preview

| API | LY | RTK | TB | Notes |
|-----|::|:---:|:--:|-------|
| `startLiveStreaming` | ✓ | ✓ | — | RTK: optional `LiveStreamingConfig.notificationConfig` for foreground notification |
| `stopLiveStreaming` | ✓ | ✓ | — | |
| `startPushLiveStreaming` | — | ✓ | — | |
| `setLivePreviewMicState` | — | ✓ | — | |
| `setLivePreviewRotation` | — | ✓ | — | |

#### Media / capture / sync

| API | LY | RTK | TB | Notes |
|-----|:--:|:---:|:--:|-------|
| `takePicture` | ✓ | ✓ | ✓ | |
| `setLifePhotoConfig` | — | ✓ | — | **RTK only**: life-photo resolution / JPEG / rotation |
| `setWifiApConfig` | — | ✓ | — | **RTK only**: SoftAP SSID / password |
| `startDeviceRecording` / `stopDeviceRecording` | ✓ | ✓ | ✓ | Audio |
| `startDeviceVideoRecording` / `stopDeviceVideoRecording` | ✓ | ✓ | ✓ | Video |
| `getMediaFileCount` | ✓ | ✓ | ✓ | |
| `syncAllMediaFile` | ✓ | △ | ✓ | RTK **SoftAP only**; `wifiMode` may be ignored on TB/LY/RTK; event fields differ — see [§6](#6-file-sync) |

#### AI assistant

| API | LY | RTK | TB | Notes |
|-----|:--:|:---:|:--:|-------|
| `startAiAssistant` / `stopAiAssistant` / `interruptAiAssistant` | ✓ | ✓ | ✓ | |

#### Device info / power / time

| API | LY | RTK | TB | Notes |
|-----|:--:|:---:|:--:|-------|
| `getBatteryLevel` / `getActionState` / `getDeviceStorage` | ✓ | ✓ | ✓ | |
| `requestDeviceVersionInfo` | ✓ | ✓ | ✓ | |
| `rebootDevice` / `restoreFactorySettings` | ✓ | ✓ | ✓ | |
| `setTime` | ✓ | — | ✓ | |

#### Settings

| API | LY | RTK | TB | Notes |
|-----|:--:|:---:|:--:|-------|
| `setLedBrightness` / `setVideoDuration` | ✓ | ✓ | ✓ | TB: `setVideoDuration` accepts **seconds**, sent to device as **minutes** |
| `setVoiceDuration` | — | ✓ | ✓ | TB: accepts **seconds**, sent to device as **minutes** |
| `setGestureShortcut` / `resetGestureShortcuts` | ✓ | — | — | |
| `setWearDetection` / `setScreenOrientation` | ✓ | ✓ | ✓ | |
| `getDeviceSettingsState` | ✓ | ✓ | ✓ | |
| `setOfflineVoiceLanguage` | ✓ | — | ✓ | |
| `getVoiceWakeUp` | ✓ | ✓ | ✓ | |
| `setVoiceWakeUp` | ✓ | △ | △ | RTK/TB: local offline wake flag only |
| `getDeviceSupportedFeatures` | ✓ | — | — | |

#### Volume / music / calls

| API | LY | RTK | TB | Notes |
|-----|:--:|:---:|:--:|-------|
| `setVolume` / `getVolume` | ✓ | — | ✓ | |
| `upVolume` / `downVolume` | ✓ | ✓ | ✓ | |
| `controlMusic` / `switchMusic` | ✓ | — | ✓ | |
| `answerPhoneCall` / `hangUpPhoneCall` | ✓ | — | — | |

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

> `unpair = true` (default): disconnect and remove the device from the phone’s Bluetooth paired list. Requires `BLUETOOTH_CONNECT` on Android 12+.

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

// Read current snapshot
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

### 5.3 Manual BT Reconnect (LY / TB)

**LY channel** — when `btState` is `FAILED` or `DISCONNECTED` while BLE remains `CONNECTED`:

```kotlin
GlassesManage.reconnectBluetooth()
```

Notes:

- Checks current system BT state first; skips if already connected or connecting
- **No-op in OTA mode (`isOtaMode = true`)**
- Requires `BLUETOOTH_CONNECT` on Android 12+

**TB channel** — when BLE is still connected but Bluetooth state is abnormal, call the same API to retry the connection.

---

## 6. File Sync

Sync media files from glasses to the phone (BLE must be connected first). **Subscribe to** `GlassesManage.eventFlow()` first, then call `syncAllMediaFile()`. Progress and results are delivered via `FileSyncEvent`.

> **AI service**: Media sync requires joining the glasses Wi-Fi hotspot. The SDK **pauses the AI service** before sync and **automatically resumes it** after `FileSyncEvent.BatchDownloadFinished` or `FileSyncEvent.Failed`. See [7.1.1 AI service during Wi-Fi usage](#711-ai-service-during-wi-fi-usage).

```kotlin
// 1. Subscribe to sync events (register once in Application / ViewModel init)
viewModelScope.launch {
    GlassesManage.eventFlow().collect { event ->
        when (event) {
            is FileSyncEvent.ConnectSuccess -> {
                // Wi-Fi linked; download starting (both LY and RTK)
            }

            is FileSyncEvent.ThumbnailsReady -> {
                // LY: thumbnail grid ready for preview UI
                val total = event.totalFileCount
                event.thumbnails.forEach { thumb ->
                    // thumb.fpath / thumb.index / thumb.thumbnailUrl
                }
            }

            is FileSyncEvent.DownloadProgress -> {
                // curFileIndex is 0-based
                val percent = event.progress          // 0~100
                val index = event.curFileIndex
                val total = event.totalFileCount
                val speed = event.speed
            }

            is FileSyncEvent.DownloadSuccess -> {
                val localPath = event.filePath
                val fpath = event.fpath               // LY: matches ThumbnailItem.fpath
                val remoteUrl = event.remoteUrl       // LY remote URL
                val size = event.fileSizeInBytes
                val modifiedTime = event.fileModifiedTime
                // RTK: fpath / remoteUrl / fileModifiedTime may be empty; use filePath
            }

            is FileSyncEvent.DownloadSkipped -> {
                // LY: invalid file skipped; not counted in successCount
                val fpath = event.fpath
            }

            is FileSyncEvent.BatchDownloadFinished -> {
                // Batch complete; successCount is the actual download count
                val success = event.successCount
                val total = event.totalFileCount
            }

            is FileSyncEvent.Failed -> {
                val reason = event.reason
                val code = event.code
            }

            else -> Unit
        }
    }
}

// 2. Start sync (see LY / RTK sections below for channel-specific calls)
GlassesManage.syncAllMediaFile()
```

### LY platform

On the LY channel, only `AP_MODE` and `P2P_MODE` are exposed. For `AP_MODE`, the SDK selects the AP connection method from `productSeries`:

```kotlin
// Default AP_MODE
GlassesManage.syncAllMediaFile()

// S series: explicit P2P
GlassesManage.syncAllMediaFile(GlassesConstant.WifiMode.P2P_MODE)
```

**Wi-Fi sync modes (`GlassesConstant.WifiMode`)**

| Enum | Description | Applicable series |
|------|-------------|-------------------|
| `AP_MODE` | AP hotspot (SDK auto-selects connection method by series) | S, T |
| `P2P_MODE` | Wi-Fi Direct | S |

**Series capability reference**

| Series | Init example | Default sync call |
|--------|--------------|-------------------|
| S (default) | `productSeries = GlassesConstant.ProductSeries.S` can be omitted | `GlassesManage.syncAllMediaFile()` |
| T | `productSeries = GlassesConstant.ProductSeries.T` | `GlassesManage.syncAllMediaFile()` |

### RTK platform

On the RTK channel, media sync **only supports AP (SoftAP)**. `P2P_MODE` is not supported. Example:

```kotlin
GlassesManage.syncAllMediaFile()

// Or specify AP mode explicitly
GlassesManage.syncAllMediaFile(GlassesConstant.WifiMode.AP_MODE)
```

**Change SoftAP SSID / password (RTK only)**:

```kotlin
GlassesManage.setWifiApConfig(
    WifiApConfig(
        ssid = "MyGlassAP",       // ASCII, 1–32 bytes
        password = "rtkaiglass",  // ASCII, 8–64 bytes
    ),
)
// Callback: CmdResultEvent.WifiApConfigResult(success)
```

> ASCII only; lengths are counted in ASCII bytes. Invalid input fails the request. No-op on LY / TB.

Whether RTK JPG distortion correction runs is controlled by `SdkConfig.deviceLensType` (see [SdkConfig parameters](#sdkconfig-parameters)).

**Callback events (`FileSyncEvent`)**

| Event | Description | LY | RTK |
|-------|-------------|:--:|:---:|
| `ConnectSuccess` | Wi-Fi link to glasses established | ✓ | ✓ |
| `ThumbnailsReady` | All thumbnail URLs ready (`thumbnails` + `totalFileCount`) | ✓ | — |
| `DownloadProgress` | Per-file progress (`progress` / `curFileIndex` / `totalFileCount` / `speed`) | ✓ | ✓ |
| `DownloadSuccess` | File saved locally (`filePath` / `fpath` / `fileSizeInBytes`, etc.) | ✓ | ✓ |
| `DownloadSkipped` | Single invalid file skipped; not counted as success | ✓ | — |
| `BatchDownloadFinished` | Batch complete (`successCount` / `totalFileCount`) | ✓ | ✓ |
| `Failed` | Sync failed (`reason` / `code`); RTK AP enable/join failures use `3501` / `3504`, etc. — see [35xx](#rtk-softap-shared-errors-3501---3505) | ✓ | ✓ |

`ThumbnailsReady.thumbnails` items are `ThumbnailItem` (`fpath` / `index` / `thumbnailUrl`); match them to `DownloadSuccess.fpath` for preview UI. RTK does not emit `ThumbnailsReady` or `DownloadSkipped`; `DownloadSuccess.remoteUrl`, `fpath`, and `fileModifiedTime` may be empty on RTK.

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
        translationAudioStorageDirName = GlassesConstant.DEFAULT_TRANS_AUDIO_FILES_STORAGE_DIR,
        aiDialogueLanguage = 140,                  // AI dialogue source langType; default 140 (Chinese)
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

// Subscribe to AI dialogue in-progress state
viewModelScope.launch {
    aiClient.aiDialogueInProgressFlow().collect { inProgress ->
        // Update UI: in-dialogue indicator, disable duplicate triggers, etc.
    }
}

// Subscribe to AI events
aiClient.aiAgentEventFlow().collect { event ->
    when (event) {
        is AgentEvent.AiAssistantConnectState -> Unit
        is AgentEvent.AiAssistantResult -> {
            val msg = event.data  // AiChatMessageDTO
            // msg.question / msg.answer — streaming text
            // msg.answerAudioPath — local WAV path for TTS audio
            // msg.isFinished — whether this round is complete
        }
        is AiTranslationEvent.AiTranslationResult -> Unit
        else -> Unit
    }
}
```

The following public methods are exposed by `AiAssistantClient` for host apps.

### 7.1 Lifecycle and connection
- `AiAssistantClient.getInstance()`: returns the singleton instance.
- `applyServerEnvironmentToGlobals(env, localWsUrl)`: syncs a preset AI service environment. In `CUSTOM` mode, `localWsUrl` can override the default AI service address. Callable before or after `initializeAiClient`; if the AI service is already connected, call `connectAiAssistant(...)` or `manualReconnect()` again after switching environments.
- `applyServerEnvironmentToGlobals(serverConfig)`: syncs a custom AI service environment, allowing the host app to pass `baseUrl` and `wsUrl` directly.
- `initializeAiClient(config: AiAgentConfig)`: initializes the AI client runtime and creates the dependencies required by the AI service, image translation, and calling. Repeated calls clear the previous connection first, but **do not reconnect automatically**. Call `connectAiAssistant(...)` again if needed. If `customServerEnvironment` is provided, it takes precedence.
- `connectAiAssistant(deviceId, deviceName, deviceModel, clientId, sk)`: establishes the AI assistant connection. Usually called after the device is connected and auth parameters are available.
- `disconnect()`: disconnects the AI service, ends the call, clears image translation state, and cancels internal coroutines. Recommended when leaving the page or disconnecting the device.
- `manualReconnect()`: manually triggers AI service reconnect. Call it after receiving `AgentEvent.ReconnectRequired`.

#### 7.1.1 AI service during Wi-Fi usage

Media sync, OTA, and live streaming require the phone to join the glasses Wi-Fi hotspot (SoftAP or P2P), which conflicts with the AI service. **LY / RTK / TB** handle this uniformly — **no manual pause/resume in the app**:

| Phase | SDK behavior |
|-------|----------------|
| Start | **Pause the AI service** (end the current session and disconnect; brief network settle) |
| End (success or failure) | On `FileSyncEvent.BatchDownloadFinished`, `OTAEvent.Success`, `LiveEvent.RespStop`, or failure events such as `FileSyncEvent.Failed` / `OTAEvent.Failed` / `LiveEvent.Failed` → **automatically resume the AI service** |
| Glasses BLE disconnect | Clear the pause flag; **do not** resume AI (handled by `disconnect()` etc.) |

**GlassesManage APIs that auto pause / resume the AI service**:

| API | LY | RTK | TB |
|-----|----|----|-----|
| `syncAllMediaFile()` | ✓ | ✓ | ✓ |
| `startOTA()` / `startRtkOta()` | ✓ | ✓ | ✓ (FIRMWARE) |
| `startLiveStreaming()` | ✓ | ✓ | ✓ |
| `stopLiveStreaming()` | ✓ | ✓ | ✓ |

**If your app runs custom Wi-Fi–exclusive flows**, you can control pause/resume manually:

| When | Description | API |
|------|-------------|-----|
| Before | Pause AI service | `AiAssistantClient.beginWifiExclusiveSession()` |
| After | Resume AI service | `AiAssistantClient.endWifiExclusiveSession()` |

```kotlin
val ai = AiAssistantClient.getInstance()
// Before custom Wi-Fi work — pause AI service
runBlocking { ai.beginWifiExclusiveSession() }
// ... your Wi-Fi flow ...
// When done — resume AI service
ai.endWifiExclusiveSession()
```

> If `connectAiAssistant(...)` was never called, resume has no session to reconnect.  
> Legacy `suspendConnectionTemporarily()` / `resumeSuspendedConnection()` still work; they pause / resume the AI service respectively.

### 7.2 Event subscription
- `aiAgentEventFlow(): Flow<AiAgentBase>`: unified AI event stream.
- `aiDialogueInProgressFlow(): StateFlow<Boolean>`: AI dialogue in progress (glasses recording / awaiting reply / TTS delivery). `true` when the device starts recording or TTS `start` is received; `false` on TTS `stop`, device cancel/interrupt, or disconnect. Use for in-dialogue UI indicators or to prevent duplicate triggers.

  Demo: `AiAssistantViewModel.observeAiDialogueState()` subscribes to this Flow and updates UI state.

- Possible event types:
  `AgentEvent` (connection state, chat result, image translation result, call state, etc.),
  `AiTranslationEvent` (translation text result / failure),
  `LocalVadEvent` (local VAD state).

> **`AgentAudioEvent` is deprecated**: this event type has been removed from the SDK and is no longer delivered via `aiAgentEventFlow()`. Downlink PCM is played inside the SDK (controlled by `enableDefaultPlaySimultaneousAudio` / `enableDefaultPlayAgentAudio` and the runtime playback toggles).

#### **AiChatMessageDTO** (`AgentEvent.AiAssistantResult` payload)

`AgentEvent.AiAssistantResult.data` is an `AiChatMessageDTO`. It may be emitted multiple times per dialogue round (streaming text / final result).

| Field | Type | Description |
|-------|------|-------------|
| `id` | `String?` | Message ID (delivered when STT / TTS audio completes; used to correlate Q&A within the same round) |
| `question` | `Any?` | User input: text, image path, etc. |
| `answer` | `Any?` | AI reply text (streamed incrementally) |
| `questionType` | `AiContentType` | Question content type |
| `answerType` | `AiContentType` | Answer content type |
| `answerAudioPath` | `String?` | Local WAV path for this round's TTS audio (on TTS `stop`, or on interrupt/new round) |
| `isFinished` | `Boolean` | Whether this round is complete (`true` on STT recognized, TTS `stop`, image recognition done, etc.) |

`AiContentType` enum: `NONE`, `TEXT`, `IMAGE_PATH`, `IMAGE_FILE`, `AUDIO_DATA`.

If SDK simultaneous-interpretation playback is enabled at init time but you need an in-session toggle for translated audio during a single real-time translation session, use `setTranslationAudioPlaybackEnabled` / `isTranslationAudioPlaybackEnabled` (see [Real-time translation audio playback toggle](#real-time-translation-audio-playback-toggle) in section 8).

If SDK agent audio playback is enabled at init time but you need an in-session toggle for AI reply audio during a chat, use `setAgentAudioPlaybackEnabled` / `isAgentAudioPlaybackEnabled` (see [section 7.6](#76-ai-chat-reply-audio-playback-toggle)).

### 7.3 AI translation APIs

> **Permission**: When the app captures phone microphone audio for `startReceivingAudio` / `sendReceivingAudioData`, request and hold `android.permission.RECORD_AUDIO`.

#### Language list

| Scenario | How to get languages | Notes |
|----------|----------------------|-------|
| **Voice / dialog / simultaneous translation** | Integer `langType` | No dedicated SDK HTTP API for a voice-translation language list. Pass `from`, `language`, and `toList` as backend language IDs (Demo defaults: source `140`, target `47`). Maintain display names locally; see Demo `assets/languages.json` (`name`, `nameEn`, `langType`, `code`). |
| **Image translation** | `getImageTransLangList(serviceType)` | Fetches supported languages per provider via `AgentEvent.ImageTransLangListResult` (includes `requestId`). |

**Image translation — fetch language list**

> **requestId correlation**: `getImageTransLangList`, `imageTrans`, and `getVoiceRoomParams` are HTTP-style requests that deliver results asynchronously via `aiAgentEventFlow()`. After multiple calls or leaving and re-entering a screen, you may receive a stale callback from a previous request. Save the `requestId` returned by the API and verify `event.requestId` in the callback; the SDK also cancels the previous in-flight request of the same type when a new one is started.

```kotlin
val aiClient = AiAssistantClient.getInstance()
var pendingLangListRequestId: Long? = null

viewModelScope.launch {
    aiClient.aiAgentEventFlow().collect { event ->
        when (event) {
            is AgentEvent.ImageTransLangListResult -> {
                if (event.requestId != pendingLangListRequestId) return@collect
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
// Returns Long requestId for this call; returns 0 if the AI client is not initialized
pendingLangListRequestId =
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
- `getImageTransLangList(serviceType): Long`: fetches the supported language list for the given image translation provider. `serviceType` can be `VOLC_ENGINE`, `ALIYUN`, `MICROSOFT`, or `OPEN_AI`. Returns the `requestId` for this call; result is delivered via `AgentEvent.ImageTransLangListResult(requestId, languageList)`.
- `imageTrans(targetImage, sourceLanguage, targetLanguage): Long`: uploads an image and requests image translation. Returns the `requestId` for this call; results are delivered via `AgentEvent.ImageTransResult(requestId, imageBase64)` / `AgentEvent.ImageTransFailEvent(requestId, code, msg)`.

```kotlin
var pendingImageTransRequestId: Long? = null

pendingImageTransRequestId = aiClient.imageTrans(
    targetImage = imageFile,
    sourceLanguage = srcLangType,
    targetLanguage = targetLangType,
)

// In aiAgentEventFlow():
is AgentEvent.ImageTransResult -> {
    if (event.requestId != pendingImageTransRequestId) return@collect
    // handle translation result
}
is AgentEvent.ImageTransFailEvent -> {
    if (event.requestId != pendingImageTransRequestId) return@collect
    // handle failure
}
```

### 7.5 Voice room and audio/video call APIs

> **Permissions**: Calls need microphone (and camera for video calls). Request `android.permission.RECORD_AUDIO`; video calls also require `android.permission.CAMERA`.

- `getVoiceRoomParams(lang, target, type, appId, mac): Long`: fetches Zego voice room parameters. Returns the `requestId` for this call; results are delivered via `AgentEvent.VoiceRoomParamsEvent(requestId, params)` / `AgentEvent.VoiceRoomParamsFailEvent(requestId, code, msg)`. Verify `event.requestId` before calling `startCall(...)`.
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
| When disabled | Auto-playback stops; audio file writes are unaffected (`AgentAudioEvent` is deprecated — no PCM event callback) |
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

### 7.7 AI dialogue source language

When the glasses button starts AI dialogue recording, the SDK uses the configured source language ID (`langType`) for `startReceivingAudio`. Set it at init via `AiAgentConfig.aiDialogueLanguage`, or change it at runtime.

| Item | Description |
|------|-------------|
| Scope | Glasses-side AI dialogue (button recording); does **not** affect App-driven translation / simultaneous interpretation |
| Value | Backend language ID (`langType`), e.g. `140` (Chinese); see Demo `assets/languages.json` |
| When it applies | `setAiDialogueLanguage` updates the config immediately; if the AI service is connected, the WebSocket is disconnected, released, and reconnected so the server re-initializes via HELLO, and local session state is cleared. **Listening is blocked until rebuild finishes and HELLO completes** (glasses button recording is stopped); query with `isListenBlockedByWsReconnect()` |

- `setAiDialogueLanguage(language)`: updates the AI dialogue source language without calling `initializeAiClient` again; when already connected, releases and rebuilds the WS (also resets local listen/TTS/image-recognition state), and blocks `startReceivingAudio` / glasses listen until ready.
- `getAiDialogueLanguage()`: returns the current AI dialogue source language; returns `140` if the AI client is not initialized.
- `isListenBlockedByWsReconnect()`: whether a language-change WS rebuild is still in progress (HELLO not ready); do not start listening while `true`.

```kotlin
val aiClient = AiAssistantClient.getInstance()

// Set at initialization
aiClient.initializeAiClient(
    AiAgentConfig(
        context = context,
        channel = channel,
        aiDialogueLanguage = 140, // Chinese
    )
)

// Change at runtime (e.g. user picks a dialogue language in settings)
aiClient.setAiDialogueLanguage(47) // English, etc.

val currentLang = aiClient.getAiDialogueLanguage()
```

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
| When disabled | Auto-playback stops (`AgentAudioEvent` is deprecated — no PCM event callback) |

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

The glasses start RTSP streaming; the phone app subscribes to `LiveEvent` for the URL, previews locally, or re-pushes to a third-party platform (Demo supports Douyin live).

> **AI service**: The SDK **pauses the AI service** before live start and **automatically resumes it** after `stopLiveStreaming()`, live failure (`LiveEvent.Failed` / `PreviewFailed` / `Disconnected`), or stop (`RespStop`). See [7.1.1](#711-ai-service-during-wi-fi-usage).

**Prerequisites** (`startLiveStreaming` validates in SDK; failures emit `LiveEvent.Failed`):

- Glasses **BLE connected** (3201)
- Phone **Wi‑Fi enabled** (3210; AP mode joins glasses hotspot)
- Phone **cellular data enabled** (3214; checks the system toggle, not the current Internet connection; used for third-party platform API / RTMP push)

Some channels also require Wi‑Fi / location permissions (see the Demo live page).

```kotlin
viewModelScope.launch {
    GlassesManage.eventFlow().collect { event ->
        when (event) {
            is LiveEvent.RespSuccess -> {
                val rtspUrl = event.rtsp  // Glasses streaming started; RTSP URL
            }
            is LiveEvent.Failed -> {
                // Pre-broadcast failure (before preview); branch on event.code for UI
                val code = event.code
                val reason = event.reason
            }
            is LiveEvent.PreviewFailed -> {
                // RTK preview start failed; branch on event.code for UI
            }
            is LiveEvent.Disconnected -> {
                // Abnormal disconnect during preview/push; branch on event.code for UI
            }
            LiveEvent.RespStop -> {
                // Live stopped
            }
            LiveEvent.StoppedByNotification -> {
                //End live broadcast from notification bar
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
        maxQp = 0,
        minQp = 0,
        videoBitRateMode = GlassesConstant.VideoBitRateMode.VBR,
        previewView = previewView,
        mode = LiveStreamingMode.PREVIEW,
        // RTK optional: live foreground-service notification (R.string / R.drawable only)
        notificationConfig = LiveStreamingNotificationConfig(
            startTitleRes = R.string.live_notification_start_title,
            startContentRes = R.string.live_notification_start_content,
            networkDisconnectedTitleRes = R.string.live_notification_net_disconnect_title,
            networkDisconnectedContentRes = R.string.live_notification_net_disconnect_content,
            stopActionRes = R.string.live_notification_stop_action,
            smallIconRes = R.drawable.ic_live_notification,
        ),
    )
)

// After preview is ready, push to a third-party platform (e.g. RTMP URL from Douyin)
GlassesManage.startPushLiveStreaming("rtmp://your-push-url")

GlassesManage.stopLiveStreaming()
```

| API | Description |
|-----|-------------|
| `startLiveStreaming(liveStreamingConfig)` | Start glasses-side live (Wi-Fi AP); bind `previewView` and start local preview |
| `startPushLiveStreaming(liveUrl)` | After preview is ready, push RTSP to an RTMP URL returned by the third-party platform |
| `stopLiveStreaming()` | Stop preview/push, release player, resume AI service |
| `ensureGlassesWifiApConnected(callback)` | **WiFi reconnect only**: force phone back to glasses AP after `RespSuccess`; do not use in normal start flow |

**`LiveStreamingConfig` parameters**:

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `videoPictureWidth` | `Int` | `1280` | Video width |
| `videoPictureHeight` | `Int` | `720` | Video height |
| `fps` | `Int` | `30` | Frame rate |
| `bps` | `Int` | `1000000` | Bitrate (bps) |
| `maxQp` | `Int` | `0` | Max video QP (RTK); `0` = device default |
| `minQp` | `Int` | `0` | Min video QP (RTK); `0` = device default |
| `videoBitRateMode` | `VideoBitRateMode` | `VBR` | Bitrate mode: `CBR` constant / `VBR` variable (RTK) |
| `pushUrl` | `String?` | `null` | RTMP push URL; `null` with `mode = PREVIEW` means preview only |
| `previewView` | `RTKVideoView?` | `null` | RTK preview view |
| `mode` | `LiveStreamingMode` | `PREVIEW` | Live mode: `PREVIEW` preview only / `PUSH` push only / `PREVIEW_PUSH` preview + push |
| `notificationConfig` | `LiveStreamingNotificationConfig?` | `null` | **RTK**: live foreground-service notification; `null` uses RTK defaults |

**`LiveStreamingNotificationConfig` fields (`notificationConfig`, RTK smartwear ≥ 1.8.70)**:

| Parameter | Type | Description |
|-----------|------|-------------|
| `startTitleRes` | `@StringRes Int` | Notification title when the live service starts; `0` (`RES_ID_UNSET`) uses RTK default |
| `startContentRes` | `@StringRes Int` | Notification body when the live service starts |
| `networkDisconnectedTitleRes` | `@StringRes Int` | Notification title when the network disconnects |
| `networkDisconnectedContentRes` | `@StringRes Int` | Notification body when the network disconnects |
| `stopActionRes` | `@StringRes Int` | Notification action label to stop live streaming |
| `smallIconRes` | `@DrawableRes Int` | Notification small icon |

> Notification strings and icons accept **resource IDs only**, not runtime `String` or `Drawable` objects.

**Callback events (`LiveEvent`)**:

| Event | Fields | Description |
|-------|--------|-------------|
| `RespSuccess` | `rtsp` | Glasses streaming started with RTSP URL |
| `PreviewStarted` | — | Preview ready (RTK may callback twice: AP connected / stream started) |
| `PreviewFailed` | `reason`, `code` | RTK preview player failed |
| `Failed` | `reason`, `code` | Pre-broadcast failure (before preview) |
| `Disconnected` | `reason`, `code` | Abnormal disconnect during preview or push |
| `RespStop` | — | Live stopped |

**Error codes and default text (for App UI)**:

- Live session errors: `GlassesConstant.ERROR_CODE_LIVE_*` (3201–3214, excluding AP link)
- **RTK enable AP / join hotspot** (shared by OTA, live, media sync): `GlassesConstant.ERROR_CODE_RTK_*` (3501–3505); aliases in `RtkSoftApErrors` / `LiveStreamErrors.CODE_HOTSPOT_*`, `CODE_WIFI_JOIN_*`
- Default messages: `LiveStreamErrors.defaultReason(code)`, `RtkSoftApErrors.defaultReason(code)`
- **Apps should branch on `code` first**; `reason` is the SDK default message (fallback / logging)

```kotlin
import com.fission.wear.glasses.sdk.live.LiveStreamErrors

when (event) {
    is LiveEvent.Disconnected -> {
        when (event.code) {
            LiveStreamErrors.CODE_WIFI_JOIN_REJECTED -> showWifiJoinRejectedDialog()
            LiveStreamErrors.CODE_PHONE_WIFI_OFF -> showPhoneWifiOffHint()
            else -> showMessage(
                appMessageFor(event.code) ?: LiveStreamErrors.defaultReason(event.code).ifBlank { event.reason }
            )
        }
    }
    else -> Unit
}
```

See [Section 13 · RTK SoftAP shared (3501–3505)](#rtk-softap-shared-errors-3501---3505) and [Live streaming errors (3201–3214)](#live-streaming-errors-3201---3214) for the full tables.

> Demo: `LiveViewModel` + `LiveScreen`. Live capabilities and parameter parsing vary by channel (LY / RTK / TB, etc.) and firmware version. Confirm `SdkConfig.channel` before integration.

### **9.6 Live Experience Config (Douyin Key / Package Name / Signing)**

To integrate Douyin live streaming, the Demo (`app` module) must use the **application package name, signing keystore (jks) and Douyin appId / appName** that match the registration on the Douyin Open Platform. To make switching between customers/channels easy, all these parameters are centralized in a local config file `douyin.properties` at the project root — **switch by editing the config, no code changes required**.

**Config file**: `douyin.properties` at the project root (includes field descriptions — edit it directly to apply).

**Steps**:

1. Edit `douyin.properties`: set `CONFIG_ENABLED` to `true` and fill in the values for your customer/channel:

   | Key | Description | Example |
   |-----|-------------|---------|
   | `CONFIG_ENABLED` | Master switch; when `false` or the file is missing, no signing is configured and Studio's default `debug.keystore` is used | `true` |
   | `APPLICATION_ID` | Application package name; must match the Douyin console registration | `com.xxx.xxx.xxx` |
   | `KEY_STORE_FILE` | jks path, relative to project root | `key/xxx.jks` |
   | `KEY_STORE_PASSWORD` | Keystore password | — |
   | `KEY_ALIAS` | Key alias | — |
   | `KEY_PASSWORD` | Key password | — |
   | `DOUYIN_APP_ID` | Douyin Open Platform appId (ClientKey) | `1032728` |
   | `DOUYIN_APP_NAME` | App name (must match the Douyin console) | `LwGlass` |
   | `DOUYIN_CLIENT_KEY` | ClientKey from the Douyin Open Platform; used by the `DouYinEntryActivity` auth callback | — |
   | `DOUYIN_CLIENT_SECRET` | ClientSecret from the Douyin Open Platform; used by the `DouYinEntryActivity` auth callback | — |

2. Place your `.jks` file at the path specified by `KEY_STORE_FILE`.
3. Gradle Sync / rebuild to apply.

> After filling in package name, keystore passwords, Douyin keys, etc., do not commit `douyin.properties` to version control.

**Where each config takes effect**:

| Config | Effective location |
|--------|--------------------|
| `CONFIG_ENABLED` | Master switch; when off, none of the configs below apply, and both debug / release are signed with the default `debug.keystore` |
| `APPLICATION_ID` / `KEY_*` | `signingConfigs` and `defaultConfig.applicationId` in `app/build.gradle.kts` |
| `DOUYIN_APP_ID` / `DOUYIN_APP_NAME` | Injected into `lib_core` `BuildConfig`, used by `BroadcastInitConfig.Builder` in `DouYinRepository.initDouyinSdk` |
| `DOUYIN_CLIENT_KEY` / `DOUYIN_CLIENT_SECRET` | Injected into `app` `BuildConfig`, used by the auth callback `DouYinEntryActivity` |

---

## 10. SDK Flow Events

> **Connection state**: prefer `GlassesManage.connectionStateFlow()` for BLE/BT (see [section 5.2](#52-subscribe-ble--bt-state-recommended)). This section documents other `eventFlow` events: scan, sync, OTA, commands, etc.

### Common - `CmdResultEvent`
- For device settings, device status, media files, battery, and button actions, observe `CmdResultEvent` subclasses.
- RTK life-photo config: `CmdResultEvent.LifePhotoConfigResult(success)` (see [11.8](#8-device-side-capture-and-photo))
- RTK SoftAP SSID/password: `CmdResultEvent.WifiApConfigResult(success)` (see [§6 RTK](#rtk-platform))

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

Manual BT reconnect (**LY / TB**): see [section 5.3](#53-manual-bt-reconnect-ly--tb).

### 3. Audio Stream - `AudioStateEvent`
- Refer to the Demo

### 4. Media Sync - `FileSyncEvent`
- `ConnectSuccess`: Wi-Fi connected
- `ThumbnailsReady(thumbnails, totalFileCount)`: thumbnail list ready (LY); `thumbnails` is `ThumbnailItem(fpath, index, thumbnailUrl)`
- `DownloadProgress(progress, curFileIndex, totalFileCount, speed)`: download progress
- `DownloadSuccess(filePath, curFileIndex, totalFileCount, remoteUrl, fileSizeInBytes, fileModifiedTime, fpath)`: single file downloaded
- `DownloadSkipped(curFileIndex, totalFileCount, fpath)`: single invalid file skipped (LY)
- `BatchDownloadFinished(successCount, totalFileCount)`: batch download finished
- `Failed(reason, code)`: sync failed

### 5. AI Assistant - `AgentEvent`
- `AgentEvent.AiAssistantConnectState`: AI service connection state
- `AgentEvent.AiAssistantResult`: AI chat result; payload is `AiChatMessageDTO` (see [section 7.2](#72-event-subscription))
- `AgentEvent.AiScheduleResult`: MCP schedule result
- `AgentEvent.ImageTransLangListResult(requestId, languageList)`: image translation language list
- `AgentEvent.ImageTransResult(requestId, imageBase64)`: image translation result
- `AgentEvent.ImageTransFailEvent(requestId, code, msg)`: image translation failure
- `AgentEvent.VoiceRoomParamsEvent(requestId, params)` / `VoiceRoomParamsFailEvent(requestId, code, msg)`: voice room parameter result
- `AgentEvent.CallConnected` / `CallDisconnected`: call connected / disconnected
- `AgentEvent.RemoteVideoStateEvent`: remote video mute state
- `AgentEvent.RemoteLanguageEvent`: remote language change
- `AgentEvent.ReconnectRequired`: business layer should trigger reconnect
- `AgentEvent.DeviceAiServiceError`: device-side AI service error
---

### 6. AI Translation - `AiTranslationEvent`
- `AiTranslationResult`: translated text result
- `Failed`: error

### 7. AI Audio Stream - `AgentAudioEvent` (deprecated)

> **`AgentAudioEvent` is deprecated and removed from the SDK**. It is no longer delivered via `aiAgentEventFlow()`. Downlink audio is played inside the SDK, controlled by `enableDefaultPlaySimultaneousAudio` / `enableDefaultPlayAgentAudio` and `setTranslationAudioPlaybackEnabled` / `setAgentAudioPlaybackEnabled`. Text and business results continue via `AgentEvent` / `AiTranslationEvent`.

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
- `RespSuccess(rtsp)`: Glasses streaming started with RTSP URL
- `PreviewStarted`: RTK local preview/push player started (see [Section 9](#9-live-streaming))
- `PreviewFailed(reason, code)`: RTK preview player failed
- `Failed(reason, code)`: Pre-broadcast failure
- `Disconnected(reason, code)`: Abnormal disconnect during preview or push
- `RespStop`: Live stopped (normal stop)
- `WifiApReady(ssid, password)`: Glasses AP credentials (WiFi reconnect)

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
- `recordDuration`: `Int?` (video recording duration, **seconds**)
- `audioRecordDuration`: `Int?` (audio recording duration, **seconds**)
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
| `setVideoDuration(times: Int)` | Video duration limit (**seconds**) |
| `setVoiceDuration(times: Int)` | Audio recording duration limit (**seconds**) |
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

**TB channel notes**:

- `setVideoDuration` / `setVoiceDuration` use **seconds** as input, same as LY/RTK.
- TB devices store duration in **minutes**; the SDK **rounds up** seconds to minutes when sending (values under 1 minute are sent as 1 minute).
- When reading via `getDeviceSettingsState()`, `recordDuration` / `audioRecordDuration` are still returned in **seconds**.

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
| `setLifePhotoConfig(config: LifePhotoConfig)` | **RTK only**: configure life-photo params (resolution / JPEG quality / rotation). Async result: `CmdResultEvent.LifePhotoConfigResult` |

```kotlin
GlassesManage.startDeviceRecording()
GlassesManage.stopDeviceRecording()

GlassesManage.startDeviceVideoRecording()
GlassesManage.stopDeviceVideoRecording()

GlassesManage.takePicture(takePhotoOnly = true)   // AI recognition
GlassesManage.takePicture(takePhotoOnly = false)  // save on glasses

// RTK: configure life-photo params (call before takePicture(false) as needed)
GlassesManage.setLifePhotoConfig(
    LifePhotoConfig(
        photoWidth = 2560,
        photoHeight = 1440,
        jpegQuality = LifePhotoConfig.JPEG_QUALITY_MAX, // 1–9
        rotationDegrees = 0, // 0 / 90 / 180 / 270
    ),
)
```

**Callback events**:
- Photo / recognition: `CmdResultEvent.ImageData` (raw bytes) or `CmdResultEvent.ImageFile` (local file path, channel-dependent)
- Life-photo config (RTK): `CmdResultEvent.LifePhotoConfigResult(success)`
- Custom LLM recording: see [Custom LLM](#custom-llm-implemented-by-the-host-app) and `AudioStateEvent`

> Resolution must be supported by the sensor; `jpegQuality` is 1–9; `rotationDegrees` only 0/90/180/270. No-op on LY / TB.

---

## 12. OTA Upgrade

BLE must be connected first. Progress is delivered via `OTAEvent` on `GlassesManage.eventFlow()` (see [Section 10](#8-ota-upgrade---otaevent)).

> **AI service**: The SDK **pauses the AI service** before OTA and **automatically resumes it** after `OTAEvent.Success` / `Failed` / `Cancelled`. Same behavior as media sync and live when Wi-Fi is in use. See [7.1.1](#711-ai-service-during-wi-fi-usage).

```kotlin
GlassesManage.startOTA(
    path = "/path/to/firmware.bin",
    type = GlassesConstant.OtaType.FIRMWARE,
    version = "",  // see channel notes below
)
```

| Parameter | Type | Description |
|-----------|------|-------------|
| `path` | `String` | Absolute path to the local firmware file |
| `type` | `GlassesConstant.OtaType` | OTA type |
| `version` | `String` | Optional, default `""`; **TB BLE OTA** requires a four-segment target version (see TB below); LY `WIFI_ISP` may pass target version |

**`OtaType` values**:

| Enum | Description |
|------|-------------|
| `FIRMWARE` | Main firmware OTA (LY BLE / TB BLE) |
| `WIFI_ISP` | Wi-Fi / ISP module OTA (LY / TB WiFi package) |

### TB channel

On TB, use `GlassesManage.startOTA(...)` for BLE and WiFi upgrades:

| `OtaType` | Description | `version` |
|-----------|-------------|-----------|
| `FIRMWARE` | Bluetrum BLE OTA (`.bin`, etc.) | **Required four segments**, e.g. `1.0.0.5` (each segment 0–255); used for OTA handshake |
| `WIFI_ISP` | Allwinner WiFi OTA | No `version` needed |

```kotlin
// BLE firmware
GlassesManage.startOTA(
    path = "/path/to/firmware_v1.0.0.5.bin",
    type = GlassesConstant.OtaType.FIRMWARE,
    version = "1.0.0.5",
)

// WiFi firmware
GlassesManage.startOTA(
    path = "/path/to/wifi_firmware.bin",
    type = GlassesConstant.OtaType.WIFI_ISP,
)
```

> Demo: `UpdateScreen` shows current device version; for BLE upgrade ensure the firmware version field is a four-segment target (pad a fourth segment if the filename only has three).

### RTK channel

On the RTK channel, use `GlassesManage.startRtkOta(...)`. `GlassesManage.startOTA(...)` is a **no-op** on RTK — do not call it.

```kotlin
GlassesManage.startRtkOta(
    btPath = "/path/to/bt_firmware.bin",       // null or empty = skip BT upgrade
    btVersion = "1.0.0.1",                     // BT firmware version (four segments)
    wifiZipPath = "/path/to/wifi_firmware.zip" // null or empty = skip WiFi upgrade; zip must contain ota.json
)
```

| Parameter | Type | Description |
|-----------|------|-------------|
| `btPath` | `String?` | Absolute path to the BT firmware file; `null` or empty skips BT |
| `btVersion` | `String?` | BT firmware version (four segments, e.g. `1.0.0.1`); required with `btPath` |
| `wifiZipPath` | `String?` | WiFi firmware zip path (must contain `ota.json` and declared bin files); `null` or empty skips WiFi |

Provide at least BT or WiFi; both may be upgraded in one call. For BT-only, pass `null` for `wifiZipPath`; for WiFi-only, pass `null` for `btPath` / `btVersion`.

**Upgrade flow** (single SoftAP session — not separate BLE-then-WiFi OTA):

1. Connect to the glasses SoftAP
2. If BT is included: push the BT bin (no `OTAEvent.Progress` during this phase)
3. If WiFi is included: SDK extracts the zip and pushes each WiFi package per `ota.json` (`OTAStage.VERIFY` progress)
4. DFU unified activation (`OTAStage.OTA` progress)
5. Results are delivered via `OTAEvent` on `GlassesManage.eventFlow()` (see [Section 10](#8-ota-upgrade---otaevent))

**RTK OTA error codes**: package / DFU — [36xx](#rtk-ota--dfu-errors-3601---3608); SoftAP enable / hotspot join — [35xx](#rtk-softap-shared-errors-3501---3505) (shared with live and media sync). Default messages: `RtkOtaErrors.defaultReason(code)`, `RtkSoftApErrors.defaultReason(code)`.

Demo: `UpdateViewModel.startRtkOtaUpgrade()` supports selecting a BT bin, a WiFi zip, or both.

## 13. Error Codes

Error codes are defined in `GlassesConstant`. Read them from `event.code` or `Failed(reason, code)` in callbacks.

> `ERROR_CODE_SYNC_BLE_NOT_CONNECTED` (1010) is deprecated; use `ERROR_CODE_BLE_NOT_CONNECTED`.

### SDK base errors (1001 ~ 1010)
| Error Code | Name | Description |
|:-------:|:------|:------|
| 1001 | ERROR_CODE_SDK_NOT_INITIALIZED | SDK is not initialized |
| 1010 | ERROR_CODE_BLE_NOT_CONNECTED | BLE is not connected (shared by OTA, media sync, and other BLE-required operations) |

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

### Wi-Fi connection errors (3001 - 3008)
| Error Code | Name | Description |
|:-------:|:------|:------|
| 3001 | ERROR_CODE_WIFI_CONNECT_TIMEOUT | Wi-Fi connection flow timeout |
| 3002 | ERROR_CODE_WIFI_DEVICE_DISCOVERY_TIMEOUT | Wi-Fi device discovery timeout |
| 3003 | ERROR_CODE_WIFI_NEGOTIATION_TIMEOUT | Wi-Fi negotiation timeout |
| 3004 | ERROR_CODE_WIFI_UNKNOWN_ERROR | Unknown Wi-Fi error |
| 3005 | ERROR_CODE_WIFI_OPEN_ERROR | Failed to enable Wi-Fi AP hotspot (**LY channel**; RTK see 3501) |
| 3006 | ERROR_CODE_WIFI_NO_PERMISSION | Missing Wi-Fi permissions: `ACCESS_FINE_LOCATION` not granted; on Android 13 (API 33)+ also requires `NEARBY_WIFI_DEVICES` |
| 3007 | ERROR_CODE_WIFI_NO_OPEN_LOCATION | Location services not enabled |
| 3008 | ERROR_CODE_WIFI_CLOSED | Wi-Fi is disabled |

### File download errors (3101 - 3105)
| Error Code | Name | Description |
|:-------:|:------|:------|
| 3101 | ERROR_CODE_DOWNLOAD_GET_FILE_LIST_FAILED | Failed to get file list |
| 3102 | ERROR_CODE_DOWNLOAD_FILE_NOT_FOUND | Target file not found |
| 3103 | ERROR_CODE_DOWNLOAD_FAILED | File download failed |
| 3104 | ERROR_CODE_DOWNLOAD_NETWORK_ERROR | Network error |
| 3105 | ERROR_CODE_DOWNLOAD_DELETE | Failed to delete file |

### RTK SoftAP shared errors (3501 - 3505)

On the **RTK channel**, OTA, live streaming, and media sync share these codes for the **glasses SoftAP enable → phone joins hotspot** phase (`code` on `FileSyncEvent.Failed` / `LiveEvent.Failed` / `OTAEvent.Failed`).

Default messages and Wi‑Fi join failure classification: `com.fission.wear.glasses.sdk.rtk.RtkSoftApErrors`. Live aliases: `LiveStreamErrors.CODE_HOTSPOT_*`, `CODE_WIFI_JOIN_*` (all map to this range).

| Error Code | Name | Description |
|:-------:|:------|:------|
| 3501 | ERROR_CODE_RTK_AP_ENABLE_FAILED | Failed to enable glasses SoftAP |
| 3502 | ERROR_CODE_RTK_AP_INFO_UNAVAILABLE | Glasses AP info (SSID/password) unavailable |
| 3503 | ERROR_CODE_RTK_WIFI_JOIN_REJECTED | User cancelled/rejected system Wi‑Fi dialog for glasses AP |
| 3504 | ERROR_CODE_RTK_WIFI_JOIN_FAILED | Failed to join glasses AP (not an explicit user cancel) |
| 3505 | ERROR_CODE_RTK_WIFI_JOIN_TIMEOUT | Timeout joining glasses AP |

### RTK OTA / DFU errors (3601 - 3608)

**RTK channel** only — `GlassesManage.startRtkOta(...)` / `OTAEvent.Failed` (separate from LY 40xx ISP OTA). Default messages: `RtkOtaErrors.defaultReason(code)`.

| Error Code | Name | Description |
|:-------:|:------|:------|
| 3601 | ERROR_CODE_RTK_OTA_PACKAGE_INVALID | Invalid package, version, or `ota.json` |
| 3602 | ERROR_CODE_RTK_OTA_BT_FILE_NOT_FOUND | BT firmware file not found |
| 3603 | ERROR_CODE_RTK_OTA_DEVICE_ADDRESS_EMPTY | Device address empty |
| 3604 | ERROR_CODE_RTK_OTA_DFU_CONNECT_FAILED | DFU connect to device failed |
| 3605 | ERROR_CODE_RTK_OTA_DFU_PREPARE_FAILED | DFU device prepare failed or timed out |
| 3606 | ERROR_CODE_RTK_OTA_PUSH_FAILED | Failed to push firmware to glasses |
| 3607 | ERROR_CODE_RTK_OTA_ACTIVATE_FAILED | Firmware activate (`startOtaProcedure`) failed |
| 3608 | ERROR_CODE_RTK_OTA_DFU_PROCEDURE_FAILED | DFU procedure failed (vendor detail in `reason`) |

### LY OTA upgrade errors (4001 - 4005)

**LY channel** ISP / firmware OTA; RTK uses 36xx above.
| Error Code | Name | Description |
|:-------:|:------|:------|
| 4001 | ERROR_CODE_OTA_FILE_NOT_FOUND | OTA firmware file does not exist or path is empty |
| 4002 | ERROR_CODE_OTA_HANDSHAKE_FAILED | ISP TCP OTA handshake failed |
| 4003 | ERROR_CODE_OTA_TRANSFER_FAILED | ISP TCP OTA transfer failed |
| 4004 | ERROR_CODE_OTA_UPGRADE_FAILED | ISP upgrade failed (device returned Upgrade_err) |
| 4005 | ERROR_CODE_OTA_FILE_OR_VERSION_INVALID | Invalid OTA resource or version (LY ISP OTA) |

### AI assistant errors (500001 - 500003)
| Error Code | Name | Description |
|:-------:|:------|:------|
| 500001 | AIErrorCode.DUPLICATE_CONNECTION | Duplicate connection |
| 500002 | AIErrorCode.DEVICE_NOT_AUTHORIZED | Device not authorized |
| 500003 | AIErrorCode.SERVER_KEY_ERROR | Server key error |

### Live streaming errors (3201 - 3214)

Delivered via `LiveEvent.Failed` / `PreviewFailed` / `Disconnected` **`code`**. Customize UI by code; SDK default text: `LiveStreamErrors.defaultReason(code)`.

**RTK AP enable / hotspot join** is not in this table — see [RTK SoftAP shared (3501–3505)](#rtk-softap-shared-errors-3501---3505).

| Error Code | Name | Typical scenario |
|:-------:|:------|:------|
| 3201 | ERROR_CODE_LIVE_DEVICE_NOT_CONNECTED | Glasses BLE not connected |
| 3202 | ERROR_CODE_LIVE_DEVICE_NOT_READY | Glasses init not complete |
| 3207 | ERROR_CODE_LIVE_PREVIEW_START_FAILED | RTK preview player start failed |
| 3208 | ERROR_CODE_LIVE_GLASSES_START_FAILED | Glasses `startLiveStreaming` returned false |
| 3209 | ERROR_CODE_LIVE_INTERRUPTED | **During push** — session interrupted |
| 3210 | ERROR_CODE_LIVE_PHONE_WIFI_OFF | Phone Wi‑Fi turned off |
| 3211 | ERROR_CODE_LIVE_GLASSES_AP_LINK_LOST | Link to glasses AP lost |
| 3212 | ERROR_CODE_LIVE_GLASSES_AP_CLOSED | Glasses AP closed |
| 3213 | ERROR_CODE_LIVE_GLASSES_DISCONNECTED | Glasses BLE disconnected |
| 3214 | ERROR_CODE_LIVE_CELLULAR_UNAVAILABLE | Phone cellular data toggle off |

**Phase distinction (important)**:

- **Start preconditions** (`startLiveStreaming` entry): `3201`, `3210`, `3214`
- **Connect/preview phase** (not yet pushing): RTK often `3501`–`3505` (AP link), `3207`–`3208`
- **During push**: `3209`, `3210`–`3213`; `3209` must **not** be used for “user cancelled Wi‑Fi join”

**Related Wi‑Fi codes**: LY media sync / OTA — [3001–3008](#wi-fi-connection-errors-3001---3008); RTK join glasses AP — [3501–3505](#rtk-softap-shared-errors-3501---3505).

> For Jieli OTA in-progress error codes, please refer to the [official OTA documentation](https://doc.zh-jieli.com/Apps/Android/ota/zh-cn/master/development/interface_desc.html#id7).
