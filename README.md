# LinWear Ai Glasses SDK 文档（中文版）

---

## 📚 目录 (TOC)
- [1. 添加权限](#1-添加权限)
- [2. 添加依赖（必须）](#2-添加依赖必须)
- [3. SDK 初始化](#3-sdk-初始化)
- [4. 搜索设备](#4-搜索设备)
- [5. 连接设备](#5-连接设备)
  - [5.1 连接 / 断开 BLE](#51-连接--断开-ble)
  - [5.2 订阅 BLE + BT 连接状态（推荐）](#52-订阅-ble--bt-连接状态推荐)
  - [5.3 手动重连 BT](#53-手动重连-bt)
- [6. 同步文件](#6-同步文件)
- [7. AI 助手功能](#7-ai-助手功能)
- [8. AI 翻译](#8-ai-翻译)
- [9. 直播](#9-直播)
- [10. SDK Flow 流监听](#10-sdk-flow-流监听)
- [11. 眼镜设置功能](#11-眼镜设置功能)
- [12. OTA 升级](#12-ota-升级)
- [13. 错误码说明](#13-错误码说明)

---

## **1. 添加权限**
```xml
<uses-permission android:name="android.permission.INTERNET"/>
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"/>
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"/>
<!-- 蓝牙连接 -->
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION"/>
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<!-- 媒体文件同步 -->
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE"/>
<uses-permission android:name="android.permission.CHANGE_WIFI_STATE"/>
<uses-permission
android:name="android.permission.NEARBY_WIFI_DEVICES"
android:usesPermissionFlags="neverForLocation"
tools:targetApi="33" />
<!-- AI 翻译、音视频通话（手机端采集） -->
<uses-permission android:name="android.permission.RECORD_AUDIO"/>
<!-- 视频通话 -->
<uses-permission android:name="android.permission.CAMERA"/>
```

---

## **2. 添加依赖（必须）**
```gradle
implementation("com.fission.wear.glasses:sdk:lastVersion")
implementation("io.reactivex.rxjava3:rxjava:3.1.6")
```

必需依赖项：
- settings.gradle 添加： maven { url = uri("https://repo.repsy.io/mvn/linwear/android") } 
- settings.gradle 添加： maven { url = uri("https://maven.zego.im") }
- 导入app/libs下的 aar/jar
- RxJava3
- RxAndroid
- RxAndroidBle
- OkHttp
- Retrofit
- UtilCodex
- 详情参考settings.gradle

### 宿主 App 的 SO 冲突处理
SDK 内部已经对 `libc++_shared.so` 做了一层库侧兜底，但 Android 的 Native Library 冲突最终仍发生在宿主 `app` 的 APK/AAB 打包阶段。

如果宿主工程同时依赖了其他也携带 `libc++_shared.so` 的库，请在宿主 `app/build.gradle(.kts)` 中添加：

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

## **3. SDK 初始化**

| 模块 | 入口 | 职责 |
|------|------|------|
| 眼镜 SDK | `GlassesManage.initialize(SdkConfig)` | BLE 扫描/连接、设备指令、OTA、媒体同步等 |
| AI 客户端 | `AiAssistantClient.getInstance().initializeAiClient(AiAgentConfig)` | AI 服务、语音助手、翻译、图片翻译、即构通话等 |

参考 Demo（`LinWearApplication` + `AppStartupReconnectManager`），推荐顺序如下：

1. `Utils.init(application)` — UtilCodex 工具库（必须）
2. （可选）`RxJavaPlugins.setErrorHandler { ... }` — Rx 全局异常兜底，见 Demo
3. （可选）`AiAssistantClient.getInstance().applyServerEnvironmentToGlobals(...)` — 设置 AI 服务环境（可在 `initializeAiClient` 前后调用）
4. `GlassesManage.initialize(SdkConfig(...))` — 仅**首次**生效，重复调用会被忽略
5. `AiAssistantClient.getInstance().initializeAiClient(AiAgentConfig(...))` — 切换环境或重配 AI 时需再次调用；**不会**自动重连 AI 服务
6. 眼镜 BLE 连接成功并取得鉴权参数后，调用 `connectAiAssistant(...)`（见第 7 节）

```kotlin
// Application.onCreate 或进入眼镜业务前
Utils.init(this)

// 方式 A：预置环境（DEV / TEST / CHINA 等）
AiAssistantClient.getInstance().applyServerEnvironmentToGlobals(
    GlassesConstant.ServerEnvironment.DEV
)

// 方式 B：自定义 HTTP / AI 服务（自定义环境）
AiAssistantClient.getInstance().applyServerEnvironmentToGlobals(
    AiServerEnvironmentConfig(
        baseUrl = "https://your-http-host/",
        wsUrl = "wss://your-ws-host"
    )
)

// 眼镜 SDK（channel 需与眼镜方案一致：TB / LY / RTK）
GlassesManage.initialize(
    SdkConfig(
        isDebug = BuildConfig.DEBUG,
        context = applicationContext,
        channel = GlassesConstant.ChannelType.LY,
        logLevel = LogUtils.V,
    )
)

// AI 运行时（channel 建议与 SdkConfig 保持一致）
AiAssistantClient.getInstance().initializeAiClient(
    AiAgentConfig(
        context = applicationContext,
        channel = GlassesConstant.ChannelType.LY,
        serverEnvironment = GlassesConstant.ServerEnvironment.DEV,
    )
)
```

### **SdkConfig 参数说明**

`GlassesManage.initialize(SdkConfig(...))` 使用以下配置项：

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|:----:|--------|------|
| `isDebug` | `Boolean` | 是 | — | 是否为调试模式。建议传入 `BuildConfig.DEBUG`，与宿主 App 构建类型保持一致。 |
| `context` | `Context` | 是 | — | 应用上下文，SDK 内部会取 `applicationContext` 使用。 |
| `channel` | `GlassesConstant.ChannelType` | 是 | — | 眼镜硬件/协议渠道，决定 BLE 指令策略与能力差异。**必须与所连接眼镜方案一致**。 |
| `logLevel` | `Int` | 否 | `LogUtils.V` | SDK 日志输出级别，使用 UtilCodex `LogUtils` 常量：`V`（最详细）→ `D` → `I` → `W` → `E`（最精简）。 |
| `mediaFilesStorageDirName` | `String` | 否 | `"mediaFiles"` | 从眼镜同步的媒体文件保存目录名，位于 `context.filesDir` 下。 |
| `aiImageRecognitionStorageDirName` | `String` | 否 | `"tempImages"` | AI 识图临时图片保存目录名，位于 `context.filesDir` 下。 |

**`channel` 可选值**：

| 枚举  | 说明 |
|------|------|
| `ChannelType.TB` | TB 方案 |
| `ChannelType.LY` | LY 方案（Demo 默认） |
| `ChannelType.RTK` | RTK 方案 |
| `ChannelType.QC` | QC 方案 |

> `GlassesManage.initialize` 仅**首次**调用生效；重复调用会被忽略，后续无法通过再次 `initialize` 修改 `SdkConfig`。

---

### **AiAgentConfig 参数说明**

`AiAssistantClient.getInstance().initializeAiClient(AiAgentConfig(...))` 使用以下配置项：

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|:----:|--------|------|
| `context` | `Context` | 是 | — | 应用上下文，用于创建即构通话、图片翻译等组件。 |
| `channel` | `GlassesConstant.ChannelType` | 是 | — | AI 业务渠道，**建议与 `SdkConfig.channel` 保持一致**。 |
| `aiModelType` | `GlassesConstant.AiModelVendor` | 否 | `DEFAULT` | 大模型供应商标识，影响 AI 对话/翻译等请求路由。 |
| `serverEnvironment` | `GlassesConstant.ServerEnvironment` | 否 | `DEV` | 预置 AI 服务环境（HTTP `baseUrl` + AI 服务 `wsUrl`）。当 `customServerEnvironment` 非空时被忽略。 |
| `customServerEnvironment` | `AiServerEnvironmentConfig?` | 否 | `null` | 自定义 AI 服务地址；**优先级高于** `serverEnvironment`。 |
| `enableDefaultPlaySimultaneousAudio` | `Boolean` | 否 | `true` | 是否由 SDK 自动播放实时同传（`simultaneous_audio`）下行 PCM 音频。设为 `false` 时，需自行订阅 `AgentAudioEvent.TranslationAudioSend` 处理播放；运行时仍可用 `setTranslationAudioPlaybackEnabled` 控制（见 [第 8 节](#实时翻译译文播放开关)）。 |
| `enableDefaultPlayAgentAudio` | `Boolean` | 否 | `true` | 是否由 SDK 自动播放 AI 助手对话（Agent）下行 PCM 音频。设为 `false` 时，需自行订阅 `AgentAudioEvent.AgentAudioSend` 处理播放；运行时仍可用 `setAgentAudioPlaybackEnabled` 控制（见 [7.6](#76-ai-对话回复音频播放开关)）。 |
| `translationAudioStorageDirName` | `String` | 否 | `"transAudioFiles"` | 翻译/对话模式录音文件保存目录名，位于 `context.filesDir` 下。 |

**`aiModelType` 可选值**：

| 枚举  | 说明 |
|------|------|
| `AiModelVendor.DEFAULT` | 按后台默认配置 |
| `AiModelVendor.QWEN` | 通义千问 |
| `AiModelVendor.GPT_5O_MINI` | GPT-5o mini |
| `AiModelVendor.KIMI_V2` | Kimi v2 |

**`serverEnvironment` 预置环境**：

| 枚举         | 说明 |
|------------|------|
| `DEV`      | 开发环境 |
| `TEST`     | 测试 / 预发布环境 |
| `CHINA`    | 正式（中国） |
| `EUROPE`   | 正式（欧洲） |
| `SINGAPORE` | 正式（新加坡） |

**`customServerEnvironment`（`AiServerEnvironmentConfig`）字段**：

| 字段 | 类型 | 说明 |
|------|------|------|
| `baseUrl` | `String` | AI HTTP 服务根地址，如 `https://your-http-host/` |
| `wsUrl` | `String` | AI 服务地址，如 `wss://your-ws-host` |

> `initializeAiClient` 每次调用会先清理旧 AI 服务连接并重建依赖，但**不会自动重连**；环境切换后需再次调用 `connectAiAssistant(...)` 或 `manualReconnect()`。

### **GlassesManage 基础 API**

| API | 说明 |
|-----|------|
| `eventFlow(): Flow<GlassesEvent>` | 眼镜 SDK 统一事件流。扫描、文件同步、OTA、直播、设备指令等结果均通过此 Flow 回调，请在 Application 或页面生命周期内订阅。 |
| `connectionStateFlow(): StateFlow<GlassesConnectionState>` | **BLE + BT 聚合连接状态**（推荐）。SDK 内部维护，订阅即可展示连接/配对/音频状态；应用重启后会自动同步系统 BT 状态。 |
| `currentConnectionState(): GlassesConnectionState` | 读取当前 BLE/BT 连接状态快照。 |
| `reconnectBluetooth()` | 手动重连 BT（BREDR 配对 / A2DP·HFP）。需 BLE 已连接；**OTA 模式下自动跳过**。 |

说明：

- `Utils.init()` 来自 UtilCodex，不是 SDK 自带方法；日志是否写文件由宿主自行配置（Demo 开启了本地日志，非必须）。
- 预置环境：方式 A + `AiAgentConfig.serverEnvironment` 即可。
- 自定义地址：使用方式 B，或在 `AiAgentConfig` 中传入 `customServerEnvironment`（**优先于** `serverEnvironment`）；不必两处重复配置。
- 若在已连接 AI 服务后切换环境，需再次调用 `connectAiAssistant(...)` 或 `manualReconnect()`。
- Demo 中的「自定义环境」对应 `GlassesConstant.ServerEnvironment.LOCAL`，需分别配置 `baseUrl` 与 `wsUrl`。

---

## **4. 搜索设备**

App 可自行实现扫描，也可使用 SDK 方法。扫描结果通过 `GlassesManage.eventFlow()` 回调（`ScanStateEvent`）。

```kotlin
// 建议在 Application 或页面生命周期内统一订阅
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
                // 本轮扫描结束（超时或主动 stop）
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

// 停止扫描并解绑扫描 Service
GlassesManage.stopScanBleDevices(context)
```

**回调事件（`ScanStateEvent`）**

| 事件 | 说明 |
|------|------|
| `DeviceFound` | 发现设备，载荷为 `ScanResult`（含 MAC、名称、RSSI） |
| `ScanFinished` | 扫描结束 |
| `Error` | 扫描异常 |

> Android 12+ 需申请 `BLUETOOTH_SCAN`；定位相关权限见 [第 1 节](#1-添加权限)。

---

## **5. 连接设备**

连接眼镜 BLE；**BLE/BT 连接状态请订阅 `connectionStateFlow()`**（见下文）。`eventFlow()` 中的 `ConnectionStateEvent` / `BtConnectEvent` 仍可用于高级场景或已有逻辑，但 UI 展示与重连判断建议统一使用 `GlassesConnectionState`。

### **5.1 连接 / 断开 BLE**

```kotlin
GlassesManage.connect(
    BleComConfig(
        context = context,
        mac = macAddress,
        isOtaMode = false, // true：OTA 模式，SDK 不会发起 BT 配对/重连
    )
)

// 断开并释放 SDK 内部资源
GlassesManage.disConnect(unpair = true)
```

### **5.2 订阅 BLE + BT 连接状态（推荐）**

SDK 内部通过 `GlassesConnectionStateManager` 汇总 BLE 与经典蓝牙（BREDR / A2DP·HFP）状态；BT 状态会在 BLE 连接成功、A2DP Profile 就绪、应用重启重连后**自动同步**，上层无需手动调用同步接口。

```kotlin
viewModelScope.launch {
    GlassesManage.connectionStateFlow().collect { state ->
        when (state.bleState) {
            SdkBleConnectionState.IDLE -> { /* 未连接 */ }
            SdkBleConnectionState.CONNECTING -> { /* BLE 连接中 */ }
            SdkBleConnectionState.CONNECTED -> {
                // BLE 已连接，可发设备指令、建 AI 会话
                if (state.isBtAudioConnected) { /* A2DP 音频已连接 */ }
            }
            SdkBleConnectionState.DISCONNECTED -> { /* BLE 已断开 */ }
            SdkBleConnectionState.FAILED -> { /* BLE 连接失败：state.bleErrorMessage */ }
        }

        when (state.btState) {
            SdkBtConnectionState.IDLE -> { /* 未开始 BT 流程（如 OTA 模式） */ }
            SdkBtConnectionState.BONDING -> { /* BT 配对中 */ }
            SdkBtConnectionState.CONNECTING -> { /* 配对完成，连接 A2DP/HFP 中 */ }
            SdkBtConnectionState.CONNECTED -> { /* BT 音频已连接 */ }
            SdkBtConnectionState.FAILED -> { /* 配对/连接失败：state.btFailureReason */ }
            SdkBtConnectionState.DISCONNECTED -> { /* 音频 Profile 已断开，可手动重连 */ }
        }

        state.deviceName
        state.deviceAddress
    }
}

// 读取当前快照
val snapshot = GlassesManage.currentConnectionState()
```

**`GlassesConnectionState` 字段**

| 字段 | 类型 | 说明 |
|------|------|------|
| `bleState` | `SdkBleConnectionState` | BLE 连接状态 |
| `btState` | `SdkBtConnectionState` | 经典蓝牙 / 音频 Profile 状态（LY 等方案） |
| `deviceName` / `deviceAddress` | `String?` | 当前设备信息 |
| `btFailureReason` | `String?` | BT 配对或连接失败原因 |
| `bleErrorMessage` | `String?` | BLE 连接失败原因 |
| `isBleConnected` | `Boolean` | BLE 是否已连接 |
| `isBtAudioConnected` | `Boolean` | A2DP 是否已连接 |

**状态枚举**

| BLE (`SdkBleConnectionState`) | 说明 |
|------|------|
| `IDLE` | 空闲 |
| `CONNECTING` | 连接中 |
| `CONNECTED` | 已连接 |
| `DISCONNECTED` | 已断开 |
| `FAILED` | 连接失败 |

| BT (`SdkBtConnectionState`) | 说明 |
|------|------|
| `IDLE` | 未开始（含 OTA 模式） |
| `BONDING` | 配对中 |
| `CONNECTING` | 连接音频 Profile 中 |
| `CONNECTED` | 音频已连接 |
| `FAILED` | 配对/连接失败 |
| `DISCONNECTED` | 音频已断开 |

### **5.3 手动重连 BT**

当 `btState` 为 `FAILED` 或 `DISCONNECTED` 且 BLE 仍为 `CONNECTED` 时，可调用：

```kotlin
GlassesManage.reconnectBluetooth()
```

行为说明：

- 重连前会检查当前 BT 实际连接状态，已连接或连接中会跳过重复操作
- **OTA 模式（`isOtaMode = true`）下不会执行 BT 重连**
- 需已申请 `BLUETOOTH_CONNECT`（Android 12+）

---

## **6. 同步文件**

将眼镜内媒体文件同步到手机，需先完成 BLE 连接。同步过程通过 `GlassesManage.eventFlow()` 回调（`FileSyncEvent`）。

```kotlin
viewModelScope.launch {
    GlassesManage.eventFlow().collect { event ->
        when (event) {
            is FileSyncEvent.ConnectSuccess -> { /* Wi-Fi 已连通，开始拉取 */ }
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

// wifiMode：AP_MODE（热点）或 P2P_MODE（Wi-Fi Direct），按眼镜固件能力选择
GlassesManage.syncAllMediaFile(GlassesConstant.WifiMode.P2P_MODE)
```

**回调事件（`FileSyncEvent`）**

| 事件 | 说明 |
|------|------|
| `ConnectSuccess` | 手机与眼镜 Wi-Fi 通道建立成功 |
| `DownloadProgress` | 单文件下载进度（含序号、速率） |
| `DownloadSuccess` | 单文件下载完成（含本地路径） |
| `Failed` | 同步失败（含错误码） |

> 需定位、Wi-Fi 相关权限，见 [第 1 节](#1-添加权限) 与 Demo 中媒体同步页申请逻辑。

---

## **7. AI 助手功能**
AI 功能包括 **语音对话、图像识别、翻译** 等。可选择两种方式：

### ✅ SDK 内部大模型
```kotlin
val aiClient = AiAssistantClient.getInstance()

// 可选：通过公开 API 自定义环境地址（可在 initializeAiClient 前后调用）
aiClient.applyServerEnvironmentToGlobals(
    AiServerEnvironmentConfig(
        baseUrl = "https://your-http-host/",
        wsUrl = "wss://your-ws-host"
    )
)

// 初始化 AI 运行时
aiClient.initializeAiClient(
    AiAgentConfig(
        context = context,
        channel = channel,
        aiModelType = GlassesConstant.AiModelVendor.DEFAULT,
        serverEnvironment = GlassesConstant.ServerEnvironment.DEV,
        // 传入后会优先使用该自定义环境；不传则走 serverEnvironment
        customServerEnvironment = AiServerEnvironmentConfig(
            baseUrl = "https://your-http-host/",
            wsUrl = "wss://your-ws-host"
        ),
        enableDefaultPlaySimultaneousAudio = true, // true: SDK 自动播放同传音频
        enableDefaultPlayAgentAudio = true,        // true: SDK 自动播放 Agent 音频
        translationAudioStorageDirName = GlassesConstant.DEFAULT_TRANS_AUDIO_FILES_STORAGE_DIR
    )
)

// 连接 AI 服务（需在 initializeAiClient 之后调用）
aiClient.connectAiAssistant(
    deviceId = deviceId,
    deviceName = deviceName,
    deviceModel = deviceModel,
    clientId = clientId,
    sk = sk
)

// 统一订阅 AI 事件
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

`AiAssistantClient` 对宿主 App 暴露的公开方法如下。

### **7.1 生命周期与连接**
- `AiAssistantClient.getInstance()`：获取单例入口。
- `applyServerEnvironmentToGlobals(env, localWsUrl)`：同步预置 AI 服务环境；`LOCAL` 环境下可通过 `localWsUrl` 覆盖默认 AI 服务地址。可在 `initializeAiClient` 前后调用；已连接 AI 服务后切换环境需再次 `connectAiAssistant(...)` 或 `manualReconnect()`。
- `applyServerEnvironmentToGlobals(serverConfig)`：同步自定义 AI 服务环境，支持上层直接传入 `baseUrl` 和 `wsUrl`。
- `initializeAiClient(config: AiAgentConfig)`：初始化 AI 客户端运行时，创建 AI 服务 / 图片翻译 / 通话所需依赖。重复调用会先清理旧连接，但**不会自动重连**，需要之后再调用 `connectAiAssistant(...)`。如传入 `customServerEnvironment`，会优先使用该自定义环境。
- `connectAiAssistant(deviceId, deviceName, deviceModel, clientId, sk)`：建立 AI 助手连接。通常在设备连接完成并拿到鉴权参数后调用。
- `disconnect()`：断开 AI 服务、结束通话、清理图片翻译与内部协程。页面退出或设备断开时建议调用。
- `manualReconnect()`：手动触发 AI 服务重连。收到 `AgentEvent.ReconnectRequired` 后可调用。

### **7.2 事件订阅**
- `aiAgentEventFlow(): Flow<AiAgentBase>`：统一输出 AI 相关事件。
- 可能收到的事件类型：
  `AgentEvent`（连接状态、聊天结果、识图 / 图片翻译结果、音视频通话状态等）、
  `AiTranslationEvent`（翻译文本结果 / 失败）、
  `AgentAudioEvent`（AI 聊天音频流 / 翻译音频流）、
  `LocalVadEvent`（本地 VAD 状态）。

如果不启用 SDK 默认音频播放（`enableDefaultPlaySimultaneousAudio = false` 或 `enableDefaultPlayAgentAudio = false`），可自行订阅 `AgentAudioEvent` 处理 PCM 音频流。当前回调音频参数为 **PCM / 16000Hz / 单声道**。

若初始化时仍允许 SDK 播放同传音频，但需要在**单次实时翻译会话内**让用户开关译文播放，可使用 `setTranslationAudioPlaybackEnabled` / `isTranslationAudioPlaybackEnabled`（见 [8. 实时翻译译文播放开关](#实时翻译译文播放开关)）。

若初始化时仍允许 SDK 播放 AI 对话音频，但需要在**对话进行中**让用户开关回复播放，可使用 `setAgentAudioPlaybackEnabled` / `isAgentAudioPlaybackEnabled`（见 [7.6](#76-ai-对话回复音频播放开关)）。

### **7.3 AI 翻译相关方法**

> **权限**：App 侧使用手机麦克风采集并调用 `startReceivingAudio` / `sendReceivingAudioData` 时，需向用户申请并持有 `android.permission.RECORD_AUDIO`（录音权限）。未授权会导致无法采集上行音频。

#### 语种获取

| 场景 | 获取方式 | 说明 |
|------|----------|------|
| **语音 / 对话 / 同传翻译** | 使用整数 `langType` | SDK **未提供**单独的「翻译语种列表」HTTP 接口；`startAiTranslation(from, toList, ...)`、`startReceivingAudio(mode, language)` 中的 `from` / `language` / `toList` 均为后台约定的语种 ID（如 Demo 默认源语 `140`、目标语 `47`）。语种名称与列表由宿主维护，可参考 Demo `assets/languages.json`（字段：`name`、`nameEn`、`langType`、`code`）。 |
| **图片翻译** | `getImageTransLangList(serviceType)` | 按服务商拉取支持语种，结果见 `AgentEvent.ImageTransLangListResult`。 |

**图片翻译 — 获取语种列表**

```kotlin
val aiClient = AiAssistantClient.getInstance()

viewModelScope.launch {
    aiClient.aiAgentEventFlow().collect { event ->
        when (event) {
            is AgentEvent.ImageTransLangListResult -> {
                event.languageList.forEach { lang ->
                    // lang.langType   — 语种 ID（用于 imageTrans 入参）
                    // lang.name       — 中文名
                    // lang.nameEn     — 英文名
                    // lang.code       — 如 zh-CN
                    // lang.supportSource / lang.supportTarget — 是否可作源/目标语
                }
            }
            else -> Unit
        }
    }
}

// serviceType：VOLC_ENGINE(1) / ALIYUN(2) / MICROSOFT(3) / OPEN_AI(4)
aiClient.getImageTransLangList(GlassesConstant.ImageTranslateServerType.VOLC_ENGINE)
```

`LanguageResult` 字段：`name`、`nameEn`、`langType`、`code`、`supportSource`、`supportTarget`。

- `startAiTranslation(from, toList, reqId, audioFormat)`：创建一次翻译会话。App 自己采集手机麦克风时，`audioFormat` 使用 `GlassesConstant.AI_TRANSLATION_AUDIO_FORMAT_RAW_PCM`。
- `startReceivingAudio(mode, language)`：开始向 AI 服务发送录音。常用 `mode`：
  `GlassesConstant.AI_ASSISTANT_TYPE_LISTEN_MODE_TRANSLATION`（对话翻译）、
  `GlassesConstant.AI_ASSISTANT_TYPE_LISTEN_MODE_SIMULTANEOUS_INTERPRETATION`（实时同传）。
- `sendReceivingAudioData(mode, byteArray)`：持续发送 16k、单声道 PCM；SDK 会在内部完成降噪、AGC 等上行预处理，宿主无需自行处理。
- `pauseListening()`：暂停当前监听，适合实时同传的“暂停但不结束会话”场景。
- `stopReceivingAudio(mode)`：发送 stop 并结束当前模式的录音流程。
- `cancelReceivingAudio()`：直接中断当前录音 / AI 收音流程。
- `setTranslationAudioPlaybackEnabled(enabled)`：启用或禁用**实时翻译**下行音频的 SDK 自动播放（主要作用于同传 `simultaneous_audio`）。禁用后会立即停止当前播放并清空待播队列，但**不会**中断翻译会话。
- `isTranslationAudioPlaybackEnabled()`：查询当前是否启用实时翻译下行播放。未初始化 AI 客户端时返回 `true`。

### **7.4 图片翻译相关方法**
- `getImageTransLangList(serviceType)`：获取指定图片翻译服务商支持的语言列表。`serviceType` 可选 `VOLC_ENGINE`、`ALIYUN`、`MICROSOFT`、`OPEN_AI`。
- `imageTrans(targetImage, sourceLanguage, targetLanguage)`：上传图片并请求图片翻译，结果通过 `AgentEvent.ImageTransResult` / `AgentEvent.ImageTransFailEvent` 返回。

### **7.5 语音房间 / 音视频通话相关方法**

> **权限**：音视频通话需使用麦克风（及视频通话时的相机），请申请 `android.permission.RECORD_AUDIO`；视频通话另需 `android.permission.CAMERA`。

- `getVoiceRoomParams(lang, target, type, appId, mac)`：获取即构语音房间参数。
  `type = 1` 表示视频通话，`type = 2` 表示语音通话。
- `startCall(appID, token, roomID, streamId, userID, isVideo, local, remote)`：开始音视频通话。
- `updateLocalView(view)`：更新本地预览 `TextureView`。
- `updateRemoteView(view)`：更新远端画面 `TextureView`。
- `endCall()`：挂断通话。
- `muteMicrophone(mute)`：静音 / 取消静音麦克风。
- `enableSpeaker(enable)`：切换扬声器播放。
- `muteVideo(mute)`：关闭 / 打开本地视频采集。
- `switchCamera(useFront)`：切换前后摄像头。
- `setPlayVolume(volume)`：设置通话播放音量。

### **7.6 AI 对话回复音频播放开关**

适用于 AI 助手语音对话（TTS / `agent_audio`）场景：在对话进行中允许用户临时关闭 / 打开 SDK 自动播放回复音频，无需修改 `AiAgentConfig.enableDefaultPlayAgentAudio`。

**行为说明**：

| 项 | 说明 |
|----|------|
| 控制范围 | TTS 二进制流与 `agent_audio` 的 SDK 自动播放 |
| 与初始化配置关系 | 仍受 `enableDefaultPlayAgentAudio = false` 约束；该配置为 `false` 时，运行时开关无法开启播放 |
| 禁用后 | 仍收到 `AgentAudioEvent.AgentAudioSend`；音频文件写入不受影响 |
| 禁用时机 | 若当前有 TTS 流在播，会立即停止播放并清空待播队列，但**不会**中断 AI 对话会话 |

- `setAgentAudioPlaybackEnabled(enabled)`：启用或禁用 AI 对话下行音频的 SDK 自动播放。
- `isAgentAudioPlaybackEnabled()`：查询当前是否启用 AI 对话下行播放。未初始化 AI 客户端时返回 `true`。

```kotlin
val aiClient = AiAssistantClient.getInstance()

// 用户关闭 AI 回复播放（立即生效）
aiClient.setAgentAudioPlaybackEnabled(false)

// 查询当前状态
val playbackEnabled = aiClient.isAgentAudioPlaybackEnabled()

// 恢复 SDK 自动播放
aiClient.setAgentAudioPlaybackEnabled(true)
```

Demo：`AiAssistantScreen` 底部提供「回复播放：开 / 关」切换按钮，对应 `AiAssistantViewModel.toggleAgentAudioPlayback()`。

## **8. AI 翻译**

请参考 Demo 中 `translate` 相关实现。

> **权限**：使用手机麦克风做翻译前，请申请 `android.permission.RECORD_AUDIO`。可在进入翻译页时请求权限，拒绝后应提示用户无法录音。

### 语种获取（语音翻译）

语音翻译、对话翻译、实时同传均使用 **语种 ID（`langType`，Int）**，不是 locale 字符串。SDK 不提供在线拉取语音翻译语种表的 API，接入方需自行维护语种列表（名称展示 + `langType` 传参）。

Demo 做法：从 `assets/languages.json` 加载列表，结构示例：

```json
{
  "name": "中文",
  "nameEn": "Chinese",
  "langType": 140,
  "code": "zh-CN"
}
```

```kotlin
// 读取本地语种表（与 Demo 一致）
val languages: List<Language> /* 解析 languages.json */

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
    language = srcLangType, // 源语 ID，与 from 一致
)
```

常用 `mode`：

| 常量 | 场景 |
|------|------|
| `AI_ASSISTANT_TYPE_LISTEN_MODE_TRANSLATION` | 对话翻译 |
| `AI_ASSISTANT_TYPE_LISTEN_MODE_SIMULTANEOUS_INTERPRETATION` | 实时同传 |

> 图片翻译的语种请走 `getImageTransLangList`，见 [7.3](#73-ai-翻译相关方法) 与 [7.4](#74-图片翻译相关方法)。

App 侧主动采集手机麦克风做翻译时，建议按下面流程调用：

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

调用说明：
- `startAiTranslation(...)`：先创建翻译会话，再开始送音频。
- `startReceivingAudio(mode, language)`：进入翻译监听状态，`language` 为源语言。
- `sendReceivingAudioData(...)`：持续发送 16k、单声道 PCM 原始数据
- `pauseListening()`：实时同传场景中，暂停但不结束整场会话。
- `stopReceivingAudio(mode)`：正常结束当前翻译会话。
- `cancelReceivingAudio()`：异常中断当前收音流程。

#### 实时翻译译文播放开关

适用于 `AI_ASSISTANT_TYPE_LISTEN_MODE_SIMULTANEOUS_INTERPRETATION`（实时同传）场景：在会话进行中允许用户临时关闭 / 打开 SDK 自动播放译文，无需修改 `AiAgentConfig.enableDefaultPlaySimultaneousAudio`。

**行为说明**：

| 项 | 说明 |
|----|------|
| 控制范围 | 同传下行 `simultaneous_audio` 的 SDK 自动播放 |
| 与初始化配置关系 | 仍受 `enableDefaultPlaySimultaneousAudio = false` 约束；该配置为 `false` 时，运行时开关无法开启播放 |
| 禁用后 | 仍收到 `AgentAudioEvent.TranslationAudioSend`； |

```kotlin
val aiClient = AiAssistantClient.getInstance()

// 用户关闭译文播放（立即生效）
aiClient.setTranslationAudioPlaybackEnabled(false)

// 查询当前状态
val playbackEnabled = aiClient.isTranslationAudioPlaybackEnabled()

// 开始新一轮实时翻译
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

Demo：`TranslatorScreen` 实时翻译底部控制区提供「译文播放：开 / 关」切换按钮，对应 `TranslatorViewModel.toggleTranslationAudioPlayback()`。

**实时翻译边录边播（回声与路由）**：SDK 在 `AI_ASSISTANT_TYPE_LISTEN_MODE_SIMULTANEOUS_INTERPRETATION` 下按输出设备自动分支（`TranslationSimultaneousAudioPolicy`）：
- **蓝牙 A2DP / 有线耳机**：`MODE_NORMAL` + `VOICE_RECOGNITION`，下行优先 A2DP（`simultaneousInterpretationPlaybackPreferA2dp = true` 时）。
- **手机扬声器**：`MODE_IN_COMMUNICATION` + `VOICE_COMMUNICATION`，App 调用 `bindSimultaneousInterpretationCaptureSession(audioSessionId)` 与 SDK共享 session，并启用硬件 `AcousticEchoCanceler`，减轻译文被麦克风回录。

Demo：`AiAssistantClient.resolveSimultaneousInterpretationAudioPolicy()` → `StreamAudioRecorder.start(simultaneousPolicy=...)`，扬声器场景下绑定 session；暂停/结束录音时 `clearSimultaneousInterpretationCaptureSession()`。

**回调订阅**（`AiAssistantClient.aiAgentEventFlow()`）：

| 类型 | 事件 | 说明 |
|------|------|------|
| `AiTranslationEvent` | `AiTranslationResult` | 翻译文本结果 |
| | `Failed` | 翻译失败 |
| `AgentAudioEvent` | `TranslationAudioStart` / `TranslationAudioSend` / `TranslationAudioStop` | 翻译下行音频流（PCM 16k 单声道） |
| `AgentEvent` | `AiAssistantConnectState` | AI 服务连接状态 |

### ✅ 自定义大模型（App 自己实现）
如需开启自定义模式 请联系开发人员。

- **GlassesManage.startAiAssistant**：开始录音 
  - AudioStateEvent.ReceivingAudioData：持续接收录音数据
- **GlassesManage.stopAiAssistant()**：停止录音
- **GlassesManage.interruptAiAssistant()**：打断录音
- **GlassesManage.takePicture(true)**：AI 识图（`takePhotoOnly = true` 时图片回传 App；`false` 时保存到眼镜，见 [11.8 设备侧采集与拍照](#8️⃣-设备侧采集与拍照)）
  - 回调事件：`CmdResultEvent.ImageData` / `CmdResultEvent.ImageFile`
---


## **9. 直播**

眼镜端发起 RTSP 推流，手机 App 订阅 `LiveEvent` 获取地址后可本地预览，或二次推流到第三方平台（Demo 支持抖音直播）。

**前置条件**：BLE 已连接；部分渠道需 Wi-Fi / 定位相关权限（参考 Demo 直播页）。建议先调用 `getDeviceSupportedFeatures()` 确认设备是否支持直播。

```kotlin
viewModelScope.launch {
    GlassesManage.eventFlow().collect { event ->
        when (event) {
            is LiveEvent.LiveSuccess -> {
                val rtspUrl = event.rtsp  // 眼镜 RTSP 地址
            }
            is LiveEvent.Failed -> {
                // event.reason / event.code
            }
            LiveEvent.RespStop -> {
                // 直播已停止
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

// 将 RTSP 流转推到第三方（如 RTMP）；具体实现因渠道而异
GlassesManage.startPushLiveStreaming("rtmp://your-push-url")

GlassesManage.stopLiveStreaming()
```

| API | 说明 |
|-----|------|
| `startLiveStreaming(liveStreamingConfig)` | 启动眼镜端直播推流 |
| `startPushLiveStreaming(liveUrl)` | 将直播流推送到第三方地址（如 RTMP URL） |
| `stopLiveStreaming()` | 停止直播 |

**`LiveStreamingConfig` 参数**：

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `videoPictureWidth` | `Int` | `1280` | 视频宽度 |
| `videoPictureHeight` | `Int` | `720` | 视频高度 |
| `fps` | `Int` | `30` | 帧率 |
| `bps` | `Int` | `1000000` | 码率（bps） |
| `liveChannel` | `GlassesConstant.LiveChannel` | `WIFI_AP` | 直播传输通道 |

**`liveChannel` 可选值**：

| 枚举 | 说明 |
|------|------|
| `WIFI_AP` | Wi-Fi 热点模式 |
| `WIFI_STATION` | Wi-Fi Station 模式 |
| `BT` | 蓝牙通道 |

**回调事件（`LiveEvent`）**：

| 事件 | 说明 |
|------|------|
| `LiveSuccess` | 推流成功，含 RTSP 地址 |
| `Failed` | 推流失败 |
| `RespStop` | 直播已停止 |

> Demo：`LiveViewModel` + `LiveScreen`。直播能力与参数解析因渠道（LY / RTK 等）及固件版本而异，接入前请确认 `SdkConfig.channel`。

---

## **10. SDK Flow 流监听**

> **连接状态**：BLE/BT 请优先使用 `GlassesManage.connectionStateFlow()`（见 [第 5.2 节](#52-订阅-ble--bt-连接状态推荐)），本节 `eventFlow` 主要覆盖扫描、同步、OTA、指令等业务事件。

### **通用 - CmdResultEvent**
- 设备设置、设备状态、媒体文件、电量、按键动作等结果请关注 `CmdResultEvent` 子类


### **① 搜索设备 - ScanStateEvent**
- `DeviceFound`：返回 `ScanResult`
- `ScanFinished`：扫描完成
- `Error`：扫描异常

### **② 连接设备**

**推荐**：订阅 `GlassesManage.connectionStateFlow()`，字段见 [第 5.2 节](#52-订阅-ble--bt-连接状态推荐)。

**`eventFlow` 原始事件**（高级用法）：

| 类型 | 事件 | 说明 |
|------|------|------|
| `ConnectionStateEvent` | `Connecting` | BLE 连接中 |
| | `Connected` | 已连接（`isOtaMode` 为 true 时不走 BT 配对） |
| | `Disconnected` | 已断开 |
| | `Failed` | 连接失败 |
| `BtConnectEvent` | `Bonding` / `Bonded` / `BondFailed` | 经典蓝牙配对（LY 等方案，SDK 内部驱动） |
| | `A2dpConnected` / `HfpConnected` 等 | 音频 Profile 状态变化 |

手动重连 BT：`GlassesManage.reconnectBluetooth()`（见 [第 5.3 节](#53-手动重连-bt)）。

### **③ 音频流 - AudioStateEvent**
- 参考 Demo

### **④ 同步媒体文件 - FileSyncEvent**
- `ConnectSuccess`：连接 Wi-Fi 成功
- `DownloadProgress`：下载进度
- `DownloadSuccess`：同步成功
- `Failed`：同步失败

### **⑤ AI 助手 - AgentEvent**
- `AgentEvent.AiAssistantConnectState`：AI 服务连接状态
- `AgentEvent.AiAssistantResult`：AI 聊天结果
- `AgentEvent.AiScheduleResult`：日程类 MCP 返回
- `AgentEvent.ImageTransLangListResult`：图片翻译语言列表
- `AgentEvent.ImageTransResult`：图片翻译结果
- `AgentEvent.ImageTransFailEvent`：图片翻译失败
- `AgentEvent.VoiceRoomParamsEvent` / `VoiceRoomParamsFailEvent`：语音房间参数获取结果
- `AgentEvent.CallConnected` / `CallDisconnected`：通话接通 / 断开
- `AgentEvent.RemoteVideoStateEvent`：远端视频开关状态
- `AgentEvent.RemoteLanguageEvent`：远端语种变化
- `AgentEvent.ReconnectRequired`：需要业务侧主动重连
- `AgentEvent.DeviceAiServiceError`：设备侧 AI 服务错误
---

### **⑥ AI 翻译 - AiTranslationEvent**
- `AiTranslationResult`：大模型返回翻译结果
- `Failed`：错误

### **⑦ AI 音频流 - AgentAudioEvent**
- `AgentAudioStart` / `AgentAudioSend` / `AgentAudioStop`：AI 聊天音频流
- `TranslationAudioStart` / `TranslationAudioSend` / `TranslationAudioStop`：AI 翻译音频流

---
### **⑧ OTA 升级 - OTAEvent**
- `Start`：开始升级
- `Progress`：升级进度
- `Success`：升级成功
- `Failed`：升级失败
- `Cancelled`：升级已取消
- `Idle`：空闲状态
- `DeviceRebooting`：设备重启中

---

### **⑨ 眼镜动作状态 - ActionSync**
- `ActionSync(type, state)`：眼镜动作状态同步；`type` 为 `GlassesConstant.ActionSyncType`，`state` 表示该动作是否处于开启/触发状态（一次性动作为 `true` 表示本次触发）。
- 可通过 `GlassesManage.getActionState()` 主动向设备拉取；设备状态变化时也会经 `eventFlow` 推送。

**`ActionSyncType` 枚举**：

| 枚举 | `index` | 说明 |
|------|---------|------|
| `TAKE_PHOTO` | 0 | 拍照 |
| `RECORD_AUDIO` | 1 | 录音 |
| `RECORD_VIDEO` | 2 | 录像 |
| `VOLUME_UP` | 3 | 音量增加（一次性） |
| `VOLUME_DOWN` | 4 | 音量减少（一次性） |
| `NOD` | 5 | 点头（一次性） |
| `SHAKE_HEAD` | 6 | 摇头（一次性） |
| `MUSIC` | 7 | 音乐播放状态 |
| `WEAR` | 8 | 佩戴检测 |
| `IMPORTING` | 9 | 媒体文件导入中 |
| `SINGLE_TOUCH` | 1000 | 单点触控（SDK 合成，非 BLE 索引） |

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
        GlassesConstant.ActionSyncType.SINGLE_TOUCH -> { /* 单点触控 */ }
    }
}
```

### **⑩ 直播 - LiveEvent**
- `LiveSuccess`：推流成功，含 RTSP 地址
- `Failed`：推流失败
- `RespStop`：直播已停止

### **⑪ SDK 全局错误 - SdkErrorEvent**
- `GlobalError`：SDK 未初始化等全局错误（如错误码 `1001`）

---

## **11. 眼镜设置功能**
SDK 通过 `GlassesManage` 提供眼镜参数读取与修改（LED、手势、佩戴检测、音量、时间等）。  
**结果回传**：请在 `GlassesManage.eventFlow()` 中收集 `GlassesEvent`，关注 `CmdResultEvent` 子类（与 [第 10 节](#10-sdk-flow-流监听) 一致）。

---

### **1️⃣ 获取设备综合设置**
主动拉取当前设备侧汇总状态：

```kotlin
GlassesManage.getDeviceSettingsState()
```

**回调事件**：`CmdResultEvent.DeviceSettingsStateEvent`  
**载荷** `DeviceSettingsStateDTO` 字段说明：
- `ledBrightness`：`LyCmdConstant.LedBrightnessLevel?`（LED 亮度档位）
- `recordDuration`：`Int?`（录像时长）
- `systemVolume` / `mediaVolume` / `callVolume`：`Int?`（系统 / 媒体 / 通话音量）
- `wearDetectionEnabled`：`LyCmdConstant.WearDetectionState?`（佩戴检测）
- `voiceCommandEnabled`：`Boolean?`（语音指令相关状态）
- `gestureSettings`：`Map<GestureType, GestureAction>?`（各手势绑定的快捷动作）
- `burstPhotoCount`：`Int?`（连拍张数）
- `orientation`：`LyCmdConstant.ScreenOrientation?`（横竖屏）

---

### **2️⃣ 修改单项设备设置**
以下为写指令，成功后通常会再次收到 `DeviceSettingsStateEvent` 或 `Success`（视固件与策略而定），请以 `eventFlow` 实际事件为准。

| API | 说明 |
|-----|------|
| `setLedBrightness(level: LyCmdConstant.LedBrightnessLevel)` | LED 亮度（`LOW` / `MEDIUM` / `HIGH`） |
| `setVideoDuration(times: Int)` | 录像时长 |
| `setWearDetection(state: LyCmdConstant.WearDetectionState)` | 佩戴检测（`OFF` / `ON`） |
| `setGestureShortcut(gesture: LyCmdConstant.GestureType, action: LyCmdConstant.GestureAction)` | 单条手势快捷方式 |
| `resetGestureShortcuts()` | 恢复手势快捷方式为默认 |
| `setScreenOrientation(orientation: LyCmdConstant.ScreenOrientation)` | 拍照 / 录像画面方向（`PORTRAIT` / `LANDSCAPE`） |
| `setOfflineVoiceLanguage(language: Int)` | 离线语音词条语言；LY 侧日志约定 `0` 中文、`1` 英文（具体以固件为准） |
| `setVolume(type: LyCmdConstant.AudioVolumeType, volume: Int)` | 按类型设置音量（`SYSTEM` / `MEDIA` / `CALL`） |
| `setTime()` | 同步手机时间到眼镜 |
| `setVoiceWakeUp(localOfflineEnabled: Boolean, opusPushEnabled: Boolean)` | 语音唤醒相关开关：`true` 表示开启对应能力（本地离线唤醒 / Opus 流上行） |

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

### **3️⃣ 版本、能力、电量与存储**

```kotlin
// 固件 / 中间版本字段 / 硬件（LY 回包解析后第二项常为 ISP 版本，DTO 字段名为 wifiVersion）
GlassesManage.requestDeviceVersionInfo()
// 对应事件：CmdResultEvent.DeviceVersionInfoEvent(data: DeviceVersionInfoDTO)

// 设备能力位（直播、快捷音量、水印、佩戴检测、横竖屏等）
GlassesManage.getDeviceSupportedFeatures()
// 对应事件：CmdResultEvent.DeviceSupportedFeatures(featuresConfigInfo: GlassesFeaturesConfigInfo)

// 电量（主动查询或设备上报）
GlassesManage.getBatteryLevel()
// 对应事件：CmdResultEvent.DevicePower(value, isCharging)

// 存储空间查询（向设备下发指令；各渠道策略是否封装回包事件以实际工程为准）
GlassesManage.getDeviceStorage()
```

---

### **4️⃣ 音量与语音唤醒状态**

```kotlin
GlassesManage.getVolume()
// 对应事件：CmdResultEvent.DeviceVolumeState(systemVolume, mediaVolume, callVolume)

GlassesManage.upVolume()
GlassesManage.downVolume()
// 设备侧快捷加减音量（是否支持可先 getDeviceSupportedFeatures）

GlassesManage.getVoiceWakeUp()
// 对应事件：CmdResultEvent.VoiceCommandDisableState(
//     localOfflineVoiceDisabled, opusStreamPushDisabled)
```

---

### **5️⃣ 媒体与通话控制**

```kotlin
GlassesManage.controlMusic(Boolean)  // true 播放 / false 暂停（以固件为准）
GlassesManage.switchMusic(LyCmdConstant.MusicSwitchAction.PREVIOUS) // 上一曲
GlassesManage.switchMusic(LyCmdConstant.MusicSwitchAction.NEXT)     // 下一曲

GlassesManage.answerPhoneCall()
GlassesManage.hangUpPhoneCall()
```

---

### **6️⃣ 系统维护**

```kotlin
GlassesManage.rebootDevice()
GlassesManage.restoreFactorySettings()
```

---

### **7️⃣ 与「设置」相关的状态查询（可选）**
以下接口更偏设备状态，但常与设置页一同展示：

```kotlin
GlassesManage.getMediaFileCount()   // CmdResultEvent.MediaFileCount
GlassesManage.getActionState()      // CmdResultEvent.ActionSync（type: ActionSyncType）
```

---

### **8️⃣ 设备侧采集与拍照**

控制眼镜端录音、录像与拍照（需 BLE 已连接）。具体能力因渠道与固件而异。

| API | 说明 |
|-----|------|
| `startDeviceRecording()` | 开始眼镜端录音 |
| `stopDeviceRecording()` | 停止眼镜端录音 |
| `startDeviceVideoRecording()` | 开始眼镜端录像 |
| `stopDeviceVideoRecording()` | 停止眼镜端录像 |
| `takePicture(takePhotoOnly: Boolean)` | 拍照。`true`：AI 识图，图片回传 App；`false`：保存到眼镜本地存储 |

```kotlin
GlassesManage.startDeviceRecording()
GlassesManage.stopDeviceRecording()

GlassesManage.startDeviceVideoRecording()
GlassesManage.stopDeviceVideoRecording()

GlassesManage.takePicture(takePhotoOnly = true)   // AI 识图
GlassesManage.takePicture(takePhotoOnly = false)  // 保存到眼镜
```

**回调事件**：
- 拍照 / 识图：`CmdResultEvent.ImageData`（原始字节）或 `CmdResultEvent.ImageFile`（本地文件路径，视渠道而定）
- 自定义大模型录音：见 [8. 自定义大模型](#-自定义大模型app-自己实现) 中的 `AudioStateEvent`

---

## **12. OTA 升级**

需先完成 BLE 连接。升级过程通过 `GlassesManage.eventFlow()` 回调 `OTAEvent`（见 [第 10 节](#-ota-升级---otaevent)）。

```kotlin
GlassesManage.startOTA(
    path = "/path/to/firmware.bin",
    type = GlassesConstant.OtaType.FIRMWARE,
    version = "",  // WIFI_ISP 升级时可传目标版本号
)
```

| 参数 | 类型 | 说明 |
|------|------|------|
| `path` | `String` | 本地固件文件绝对路径 |
| `type` | `GlassesConstant.OtaType` | OTA 类型 |
| `version` | `String` | 可选，默认 `""`；`WIFI_ISP` 升级时可传目标版本号 |

**`OtaType` 可选值**：

| 枚举 | 说明 |
|------|------|
| `FIRMWARE` | 主固件 OTA |
| `WIFI_ISP` | Wi-Fi / ISP 模块 OTA |

---

## **13. 错误码说明**

### ⚠️ SDK 基础错误（1000 ~ 1001）
| 错误码 | 名称 | 描述 |
|:-------:|:------|:------|
| 1001 | ERROR_CODE_SDK_NOT_INITIALIZED | SDK 未初始化 |

### 🖼️ 图片传输错误（2001 - 2011）
| 错误码 | 名称 | 描述 |
|:-------:|:------|:------|
| 2001 | ERROR_CODE_IMAGE_PACKET_TOO_SHORT | 包长度过短 |
| 2002 | ERROR_CODE_IMAGE_INVALID_HEADER | 包头错误 |
| 2003 | ERROR_CODE_IMAGE_INVALID_FOOTER | 包尾错误 |
| 2004 | ERROR_CODE_IMAGE_CRC_FAILURE | CRC 校验失败 |
| 2005 | ERROR_CODE_IMAGE_NO_HEADER_RECEIVED | 未收到文件头就收到了数据包 |
| 2006 | ERROR_CODE_IMAGE_INCOMPLETE | 文件接收不完整 |
| 2007 | ERROR_CODE_IMAGE_TIMEOUT | 接收超时 |
| 2008 | ERROR_CODE_IMAGE_UNKNOWN_CMD | 未知图片指令 |
| 2009 | ERROR_CODE_IMAGE_INVALID_DATA_PACKET | 无效的数据包 |
| 2010 | ERROR_CODE_IMAGE_SAVE | 图片保存失败 |
| 2011 | ERROR_CODE_IMAGE_RECOGNITION | 图片识别失败 |

### 📶 Wi-Fi 连接错误（3001 - 3004）
| 错误码 | 名称 | 描述 |
|:-------:|:------|:------|
| 3001 | ERROR_CODE_WIFI_CONNECT_TIMEOUT | 连接 Wi-Fi 超时 |
| 3002 | ERROR_CODE_WIFI_DEVICE_DISCOVERY_TIMEOUT | 发现设备超时 |
| 3003 | ERROR_CODE_WIFI_NEGOTIATION_TIMEOUT | 协商超时 |
| 3004 | ERROR_CODE_WIFI_UNKNOWN_ERROR | 未知错误 |

### 📂 文件下载错误（3101 - 3105）
| 错误码 | 名称 | 描述 |
|:-------:|:------|:------|
| 3101 | ERROR_CODE_DOWNLOAD_GET_FILE_LIST_FAILED | 获取文件列表失败 |
| 3102 | ERROR_CODE_DOWNLOAD_FILE_NOT_FOUND | 文件未找到 |
| 3103 | ERROR_CODE_DOWNLOAD_FAILED | 文件下载失败 |
| 3104 | ERROR_CODE_DOWNLOAD_NETWORK_ERROR | 网络错误 |
| 3105 | ERROR_CODE_DOWNLOAD_DELETE | 文件删除失败 |

> OTA 错误码说明请参考：[**官方文档 OTA 错误码**](https://doc.zh-jieli.com/Apps/Android/ota/zh-cn/master/development/interface_desc.html#id7)

