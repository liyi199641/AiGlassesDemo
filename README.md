# LinWear Ai Glasses SDK 文档（中文版）

> [English](README-en.md) · [AI 功能文档](AI-README.md)

---

## 📚 目录 (TOC)
- [1. 添加权限](#1-添加权限)
- [2. 添加依赖（必须）](#2-添加依赖必须)
  - [2.0+（推荐）](#20推荐按方案拆分)
  - [从 1.x 升级到 2.0+](#从-1x-升级到-20)
- [3. SDK 初始化](#3-sdk-初始化)
  - [GlassesManage API 方案支持](#glassesmanage-api-方案支持)
- [4. 搜索设备](#4-搜索设备)
- [5. 连接设备](#5-连接设备)
  - [5.1 连接 / 断开 BLE](#51-连接--断开-ble)
  - [5.2 订阅 BLE + BT 连接状态（推荐）](#52-订阅-ble--bt-连接状态推荐)
  - [5.3 手动重连 BT（LY / TB）](#53-手动重连-btly--tb)
- [6. 同步文件](#6-同步文件)
- [7. AI 功能](#7-ai-功能) → 详见 [docs/AI-README.md](AI-README.md)
- [8. 直播](#8-直播)
  - [8.6 直播体验配置（抖音 Key / 包名 / 签名）](#86-直播体验配置抖音-key--包名--签名)
- [9. SDK Flow 流监听](#9-sdk-flow-流监听)
- [10. 眼镜设置功能](#10-眼镜设置功能)
- [11. OTA 升级](#11-ota-升级)
- [12. 错误码说明](#12-错误码说明)

> **AI 能力**（助手 / 翻译 / 图片翻译 / 通话等）已独立成册：[**AI 功能使用文档**](AI-README.md) · [English](AI-README-en.md)

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
<!-- 直播前台服务，WIFI保活 -->
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
```

---

## **2. 添加依赖（必须）**

### 2.0+（推荐，按方案拆分）

必选 `sdk-core`，再按设备方案引入对应模块（可多选）。`SdkConfig.channel` 须与已引入的方案一致。

```gradle
// 必选
implementation("com.fission.wear.glasses:sdk-core:last_version")
implementation("io.reactivex.rxjava3:rxjava:3.1.6")

// 按需（可多选）
implementation("com.fission.wear.glasses:sdk-ly:last_version")    // LY
implementation("com.fission.wear.glasses:sdk-rtk:last_version")   // RTK
implementation("com.fission.wear.glasses:sdk-tb:last_version")  // TB
```

> **最低系统版本**：Android 7.0（API 24）。宿主 App 的 `minSdk` 不得低于 24。

### 从 1.x 升级到 2.0+

1. **替换依赖**：移除 `com.fission.wear.glasses:sdk`，改为 `sdk-core` + 对应方案模块（`sdk-ly` / `sdk-rtk` / `sdk-tb`）。
2. **删除杰理库**：曾在 `app/libs` 或通过 `files()` / `flatDir` 等方式手动引入杰理 OTA 库（如 `jl_bt_ota_*.aar`、`jl_bt_ota_*.jar`），请**全部删除**。
3. **LyCmdConstant**：统一迁移到GlassesConstant
4. **不要混用**：1.x 单体包与 2.0+ 模块请勿同时依赖。


### 仓库配置

在 `settings.gradle.kts` 中添加：

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://repo.repsy.io/mvn/linwear/android") }
        maven { url = uri("https://maven.zego.im") }
        // 引入 sdk-tb 时还需
        maven { url = uri("https://maven.topstepht.com/repository/maven-public/") }
    }
}
```


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
6. 眼镜 BLE 连接成功并取得鉴权参数后，调用 `connectAiAssistant(...)`（见 [AI 文档](AI-README.md)）

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
        // productSeries = GlassesConstant.ProductSeries.T, // T 系列眼镜需显式指定；S 系列可省略
        // deviceLensType = GlassesConstant.LensType.WIDE_ANGLE, // RTK 广角镜头需显式指定；平角可省略
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

| 参数 | 类型 | 必填 | 默认值 | 说明                                                                          |
|------|------|:----:|--------|-----------------------------------------------------------------------------|
| `isDebug` | `Boolean` | 是 | — | 是否为调试模式。建议传入 `BuildConfig.DEBUG`，与宿主 App 构建类型保持一致。                          |
| `context` | `Context` | 是 | — | 应用上下文，SDK 内部会取 `applicationContext` 使用。                                     |
| `channel` | `GlassesConstant.ChannelType` | 是 | — | 眼镜硬件/协议渠道，决定 BLE 指令策略与能力差异。**必须与所连接眼镜方案一致**。                                |
| `logLevel` | `Int` | 否 | `LogUtils.V` | SDK 日志输出级别，使用 UtilCodex `LogUtils` 常量：`V`（最详细）→ `D` → `I` → `W` → `E`（最精简）。 |
| `mediaFilesStorageDirName` | `String` | 否 | `"mediaFiles"` | 从眼镜同步的媒体文件保存目录名，位于应用外部存储沙盒 `getExternalFilesDir(null)` 下（不可用时回退 `filesDir`）。                                    |
| `aiImageRecognitionStorageDirName` | `String` | 否 | `"tempImages"` | AI 识图临时图片保存目录名，位于应用外部存储沙盒 `getExternalFilesDir(null)` 下（不可用时回退 `filesDir`）。                                     |
| `productSeries` | `GlassesConstant.ProductSeries` | 否 | `S` | LY方案：眼镜产品系列，影响媒体同步 Wi-Fi 模式能力与 ISP OTA 结果解析。**须与所连接眼镜硬件系列一致**。              |
| `deviceLensType` | `GlassesConstant.LensType` | 否 | `LensType.FLAT_ANGLE` | 镜头类型。RTK 渠道同步 JPG 时，广角开启畸变校正，平角跳过。仅在 `initialize` 时传入生效。                    |

**`channel` 可选值**：

| 枚举  | 说明 |
|------|------|
| `ChannelType.TB` | TB 方案 |
| `ChannelType.LY` | LY 方案（Demo 默认） |
| `ChannelType.RTK` | RTK 方案 |
| `ChannelType.QC` | QC 方案 |

**`productSeries` 可选值**：

| 枚举 | 说明 | 支持的 Wi-Fi 同步模式 | 默认 Wi-Fi 模式 |
|------|------|----------------------|----------------|
| `ProductSeries.S` | S 系列（默认） | `AP_MODE`、`P2P_MODE` | `AP_MODE` |
| `ProductSeries.T` | T 系列 | `AP_MODE` | `AP_MODE` |

**`deviceLensType` 可选值**：

| 枚举 | 说明 | RTK JPG 畸变校正 |
|------|------|-----------------|
| `LensType.FLAT_ANGLE` | 平角镜头（默认） | 关闭 |
| `LensType.WIDE_ANGLE` | 广角镜头 | 开启 |

> `GlassesManage.initialize` 仅**首次**调用生效；重复调用会被忽略，后续无法通过再次 `initialize` 修改 `SdkConfig`。

---

### **AiAgentConfig 参数说明**

AI 客户端配置（`initializeAiClient(AiAgentConfig)`）的完整参数表、环境切换与连接说明，请参阅 **[AI 功能使用文档 §3](AI-README.md#3-ai-初始化与配置)**。

---

### **GlassesManage 基础 API**

| API | 说明 |
|-----|------|
| `eventFlow(): Flow<GlassesEvent>` | 眼镜 SDK 统一事件流。扫描、文件同步、OTA、直播、设备指令等结果均通过此 Flow 回调，请在 Application 或页面生命周期内订阅。 |
| `connectionStateFlow(): StateFlow<GlassesConnectionState>` | **BLE + BT 聚合连接状态**（推荐）。SDK 内部维护，订阅即可展示连接/配对/音频状态；应用重启后会自动同步系统 BT 状态。 |
| `currentConnectionState(): GlassesConnectionState` | 读取当前 BLE/BT 连接状态快照。 |
| `reconnectBluetooth()` | 手动重连蓝牙音频。需 BLE 已连接；**OTA 模式下 LY 会自动跳过**。 |
| `syncAllMediaFile(wifiMode: WifiMode? = null)` | 发起媒体同步。`wifiMode` 为 `null` 时默认 `AP_MODE`；指定 `AP_MODE` 时 SDK 按 `productSeries` 自动选择对应 AP 连接方式。模式不在系列支持范围内时，将通过 `FileSyncEvent.Failed` 回调。 |

说明：

- `Utils.init()` 来自 UtilCodex，不是 SDK 自带方法；日志是否写文件由宿主自行配置（Demo 开启了本地日志，非必须）。
- 预置环境：方式 A + `AiAgentConfig.serverEnvironment` 即可。
- 自定义地址：使用方式 B，或在 `AiAgentConfig` 中传入 `customServerEnvironment`（**优先于** `serverEnvironment`）；不必两处重复配置。
- 若在已连接 AI 服务后切换环境，需再次调用 `connectAiAssistant(...)` 或 `manualReconnect()`。
- Demo 中的「自定义环境」对应 `GlassesConstant.ServerEnvironment.CUSTOM`，需分别配置 `baseUrl` 与 `wsUrl`。

### **GlassesManage API 方案支持**

`GlassesManage` 对外 API 统一，能力由 `SdkConfig.channel` 对应的策略实现。调用不支持的 API 通常为**空操作**（无事件 / 仅打日志），接入前请按渠道核对。

图例：✓ 支持 · △ 部分支持 / 有差异 · — 不支持（空实现或占位）

> **QC** 未在下表展开。TB 方案能力与 LY/RTK 存在差异，接入前请按渠道核对下表。

#### 生命周期 / 扫描 / 连接

| API | LY | RTK | TB | 说明            |
|-----|:--:|:---:|:--:|---------------|
| `initialize` / `isDebug` / `setProductSeries` | ✓ | ✓ | ✓ | 全渠道共用         |
| `eventFlow` / `connectionStateFlow` / `currentConnectionState` | ✓ | ✓ | ✓ | 全渠道共用         |
| `startScanBleDevices` / `stopScanBleDevices` | ✓ | ✓ | ✓ | 全渠道共用         |
| `connect` / `disConnect` | ✓ | ✓ | ✓ | 解绑时清除配对信息（含经典蓝牙） |
| `reconnectBluetooth` | ✓ | — | △ | LY：音频重连；TB：连接异常时重试 |

#### OTA

| API | LY | RTK | TB | 说明                             |
|-----|:--:|:--:|:--:|--------------------------------|
| `startOTA` | ✓ | — | ✓ | LY/TB：`FIRMWARE` / `WIFI_ISP`；RTK 请用下方接口 |
| `startRtkOta` | — |✓ | —  | **仅 RTK**：BT + Wi‑Fi 双通道升级     |

#### 直播 / 预览

| API | LY | RTK | TB | 说明 |
|-----|:--:|:---:|:--:|----|
| `startLiveStreaming` | ✓ | ✓ | — | RTK 可通过 `LiveStreamingConfig.notificationConfig` 自定义前台服务通知栏 |
| `stopLiveStreaming` | ✓ | ✓ | — | |
| `startPushLiveStreaming` | — | ✓ | — | |
| `setLivePreviewMicState` | — | ✓ | — | |
| `setLivePreviewRotation` | — | ✓ | — | |

#### 媒体 / 拍摄 / 同步

| API | LY | RTK | TB | 说明 |
|-----|:--:|:---:|:--:|------|
| `takePicture` | ✓ | ✓ | ✓ | |
| `setLifePhotoConfig` | — | ✓ | — | **仅 RTK**：高清拍照分辨率 / JPEG / 旋转 |
| `setWifiApConfig` | — | ✓ | — | **仅 RTK**：修改 SoftAP 名称与密码 |
| `startDeviceRecording` / `stopDeviceRecording` | ✓ | ✓ | ✓ | 录音 |
| `startDeviceVideoRecording` / `stopDeviceVideoRecording` | ✓ | ✓ | ✓ | 录像 |
| `getMediaFileCount` | ✓ | ✓ | ✓ | |
| `syncAllMediaFile` | ✓ | △ | ✓ | RTK **仅 SoftAP**；TB/LY/RTK 的 `wifiMode` 实参可能被忽略；事件字段差异见 [§6](#6-同步文件) |

#### AI 助手

| API | LY | RTK | TB | 说明 |
|-----|:--:|:---:|:--:|------|
| `startAiAssistant` / `stopAiAssistant` / `interruptAiAssistant` | ✓ | ✓ | ✓ | |

#### 设备信息 / 电源 / 时间

| API | LY | RTK | TB | 说明 |
|-----|:--:|:---:|:--:|------|
| `getBatteryLevel` / `getActionState` / `getDeviceStorage` | ✓ | ✓ | ✓ | |
| `requestDeviceVersionInfo` | ✓ | ✓ | ✓ | |
| `rebootDevice` / `restoreFactorySettings` | ✓ | ✓ | ✓ | |
| `setTime` | ✓ | — | ✓ | |

#### 设置项

| API | LY | RTK | TB | 说明                    |
|-----|:--:|:---:|:--:|-----------------------|
| `setLedBrightness` / `setVideoDuration` | ✓ | ✓ | ✓ | TB：`setVideoDuration` 入参为**秒**，下发时换算为**分钟** |
| `setVoiceDuration` | — | ✓ | ✓ | TB：入参为**秒**，下发时换算为**分钟** |
| `setGestureShortcut` / `resetGestureShortcuts` | ✓ | — | — |                       |
| `setWearDetection` / `setScreenOrientation` | ✓ | ✓ | ✓ |                       |
| `getDeviceSettingsState` | ✓ | ✓ | ✓ |                       |
| `setOfflineVoiceLanguage` | ✓ | — | ✓ |                       |
| `getVoiceWakeUp` | ✓ | ✓ | ✓ |                       |
| `setVoiceWakeUp` | ✓ | △ | △ | RTK/TB 仅使用本地离线唤醒开关    |
| `getDeviceSupportedFeatures` | ✓ | — | — |                       |

#### 音量 / 音乐 / 通话

| API | LY | RTK | TB | 说明 |
|-----|:--:|:---:|:--:|------|
| `setVolume` / `getVolume` | ✓ | — | ✓ | |
| `upVolume` / `downVolume` | ✓ | ✓ | ✓ | |
| `controlMusic` / `switchMusic` | ✓ | — | ✓ | |
| `answerPhoneCall` / `hangUpPhoneCall` | ✓ | — | — | |

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

> `unpair = true`（默认）：断开连接，并清除手机系统蓝牙中的配对记录。需已申请 `BLUETOOTH_CONNECT`（Android 12+）。

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

### **5.3 手动重连 BT（LY / TB）**

**LY 渠道**：当 `btState` 为 `FAILED` 或 `DISCONNECTED` 且 BLE 仍为 `CONNECTED` 时，可调用：

```kotlin
GlassesManage.reconnectBluetooth()
```

行为说明：

- 重连前会检查当前 BT 实际连接状态，已连接或连接中会跳过重复操作
- **OTA 模式（`isOtaMode = true`）下不会执行 BT 重连**
- 需已申请 `BLUETOOTH_CONNECT`（Android 12+）

**TB 渠道**：BLE 仍连接但蓝牙状态异常时，可同样调用上述 API 尝试恢复连接。

---

## **6. 同步文件**

将眼镜内媒体文件同步到手机，需先完成 BLE 连接。建议**先订阅** `GlassesManage.eventFlow()`，再调用 `syncAllMediaFile()`；进度与结果通过 `FileSyncEvent` 回调。

> **AI 服务**：媒体同步需手机连接眼镜 Wi-Fi 热点，SDK 会在同步开始前**暂停 AI 服务**，在 `FileSyncEvent.BatchDownloadFinished` 或 `FileSyncEvent.Failed` 后**自动恢复 AI 服务**。详见 [AI 文档 §4.1](AI-README.md#41-占用-wi-fi-时的-ai-服务)。

```kotlin
// 1. 订阅同步事件（建议在 Application / ViewModel 初始化时注册一次）
viewModelScope.launch {
    GlassesManage.eventFlow().collect { event ->
        when (event) {
            is FileSyncEvent.ConnectSuccess -> {
                // Wi-Fi 已连通，开始拉取（LY / RTK 均会回调）
            }

            is FileSyncEvent.ThumbnailsReady -> {
                // LY：缩略图列表就绪，可展示预览网格
                val total = event.totalFileCount
                event.thumbnails.forEach { thumb ->
                    // thumb.fpath / thumb.index / thumb.thumbnailUrl
                }
            }

            is FileSyncEvent.DownloadProgress -> {
                // curFileIndex 为 0-based
                val percent = event.progress          // 0~100
                val index = event.curFileIndex
                val total = event.totalFileCount
                val speed = event.speed
            }

            is FileSyncEvent.DownloadSuccess -> {
                val localPath = event.filePath
                val fpath = event.fpath               // LY：与 ThumbnailItem.fpath 对应
                val remoteUrl = event.remoteUrl       // LY 远程地址
                val size = event.fileSizeInBytes
                val modifiedTime = event.fileModifiedTime
                // RTK：fpath / remoteUrl / fileModifiedTime 可能为空，以 filePath 为准
            }

            is FileSyncEvent.DownloadSkipped -> {
                // LY：单文件无效被跳过，不计入 successCount
                val fpath = event.fpath
            }

            is FileSyncEvent.BatchDownloadFinished -> {
                // 整批结束；以 successCount 为实际成功数
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

// 2. 发起同步（按渠道见下方 LY / RTK 说明）
GlassesManage.syncAllMediaFile()
```

### **LY 方案**

```kotlin
// 默认 AP_MODE
GlassesManage.syncAllMediaFile()

// S 系列可显式指定 P2P
GlassesManage.syncAllMediaFile(GlassesConstant.WifiMode.P2P_MODE)
```

**Wi-Fi 同步模式（`GlassesConstant.WifiMode`）**

| 枚举 | 说明 | 适用系列 |
|------|------|----------|
| `AP_MODE` | AP 热点模式（SDK 按系列自动选择连接方式） | S、T |
| `P2P_MODE` | Wi-Fi Direct | S |

**系列与能力对照**

| 系列 | 初始化示例 | 默认同步调用 |
|------|-----------|-------------|
| S（默认） | `productSeries = GlassesConstant.ProductSeries.S` 可省略 | `GlassesManage.syncAllMediaFile()` |
| T | `productSeries = GlassesConstant.ProductSeries.T` | `GlassesManage.syncAllMediaFile()` |

### **RTK 方案**

RTK 渠道**仅支持 AP（SoftAP）** 同步，不支持 `P2P_MODE`。示例：

```kotlin
GlassesManage.syncAllMediaFile()

// 或显式指定 AP 模式
GlassesManage.syncAllMediaFile(GlassesConstant.WifiMode.AP_MODE)
```

**修改 SoftAP 名称与密码（仅 RTK）**：

```kotlin
GlassesManage.setWifiApConfig(
    WifiApConfig(
        ssid = "MyGlassAP",       // ASCII，1–32 字节
        password = "rtkaiglass",  // ASCII，8–64 字节
    ),
)
// 回调：CmdResultEvent.WifiApConfigResult(success)
```

> 仅支持 ASCII；长度按 ASCII 字节计。不满足约束时下发失败。LY / TB 为空操作。

RTK 同步 JPG 时是否执行畸变校正，由 `SdkConfig.deviceLensType` 决定（见 [SdkConfig 参数说明](#sdkconfig-参数说明)）。

**回调事件（`FileSyncEvent`）**

| 事件 | 说明 | LY | RTK |
|------|------|:--:|:---:|
| `ConnectSuccess` | 手机与眼镜 Wi-Fi 通道建立成功 | ✓ | ✓ |
| `ThumbnailsReady` | 全部缩略图 URL 就绪（`thumbnails` + `totalFileCount`） | ✓ | — |
| `DownloadProgress` | 单文件下载进度（`progress` / `curFileIndex` / `totalFileCount` / `speed`） | ✓ | ✓ |
| `DownloadSuccess` | 单文件下载完成（`filePath` / `fpath` / `fileSizeInBytes` 等） | ✓ | ✓ |
| `DownloadSkipped` | 单文件无效被跳过，不计入成功数 | ✓ | — |
| `BatchDownloadFinished` | 整批处理结束（`successCount` / `totalFileCount`） | ✓ | ✓ |
| `Failed` | 同步失败（`reason` / `code`）；RTK 开启 AP / 连热点失败为 `3501` / `3504` 等，见 [35xx](#-rtk-softap-共用错误3501---3505) | ✓ | ✓ |

`ThumbnailsReady.thumbnails` 元素类型为 `ThumbnailItem`（`fpath` / `index` / `thumbnailUrl`），可与 `DownloadSuccess.fpath` 关联预览项。RTK 方案不回调 `ThumbnailsReady`、`DownloadSkipped`；`DownloadSuccess` 中 `remoteUrl`、`fpath`、`fileModifiedTime` 可能为空。

> 需定位、Wi-Fi 相关权限，见 [第 1 节](#1-添加权限) 与 Demo 中媒体同步页申请逻辑。

---

## **7. AI 功能**

AI 能力已按功能模块单独整理（含事件与回调 `data` 用法），请参阅：

- 中文：[**AI-README.md**](AI-README.md) — AI 对话 / AI 翻译（对话+实时）/ 音视频通话翻译 / 图片翻译
- English：[**AI-README-en.md**](AI-README-en.md)

主文档仅保留设备侧相关交叉引用（如占用 Wi-Fi 时自动暂停 AI、自定义大模型下的 `GlassesManage.startAiAssistant` / `takePicture` 等），实现细节以 AI 文档为准。

---

## **8. 直播**

眼镜端发起 RTSP 推流，手机 App 订阅 `LiveEvent` 获取地址后可本地预览，或二次推流到第三方平台（Demo 支持抖音直播）。

> **AI 服务**：开播前 SDK 会**暂停 AI 服务**；`stopLiveStreaming()`、直播失败（`LiveEvent.Failed` / `PreviewFailed` / `Disconnected`）或停止（`RespStop`）后**自动恢复 AI 服务**。详见 [AI 文档 §4.1](AI-README.md#41-占用-wi-fi-时的-ai-服务)。

**前置条件**（`startLiveStreaming` 会在 SDK 内校验，不满足则回调 `LiveEvent.Failed`）：

- 眼镜 **BLE 已连接**（3201）
- 手机 **Wi‑Fi 已开启**（3210；AP 模式需连眼镜热点）
- 手机 **蜂窝数据已开启**（3214；检测系统开关，非当前 Internet 连接；用于第三方平台 API / RTMP 推流）

部分渠道还需 Wi‑Fi / 定位相关权限（参考 Demo 直播页）。

> **系统网络切换提醒（重要）**  
> 直播依赖手机连接眼镜 Wi‑Fi 热点（SoftAP）拉流，同时可能经蜂窝访问第三方平台。若系统开启智能网络切换，Android 可能在直播过程中主动切网，导致预览/推流断连。  
> 请引导用户（或在接入说明中写明）关闭手机上类似设置，例如：  
> - **WLAN+** / **智能多网络切换** / **智能双通道**  
> - **WLAN 安全检测** / **WLAN 助理**  
> - **WLAN 不可上网时自动切换到移动数据** / **网络加速** 等  
> 具体名称因厂商（华为 / 小米 / OPPO / vivo / 三星等）与系统版本而异，设置路径通常在「WLAN / 移动网络 / 更多连接」相关菜单。开播前建议确认上述能力已关闭，以降低系统主动切网导致的断播概率。

```kotlin
viewModelScope.launch {
    GlassesManage.eventFlow().collect { event ->
        when (event) {
            is LiveEvent.RespSuccess -> {
                val rtspUrl = event.rtsp  // 眼镜开启推流成功，RTSP 地址
            }
            is LiveEvent.Failed -> {
                // 开播前置失败（未进入预览）；优先按 event.code 定制 UI
                val code = event.code
                val reason = event.reason
            }
            is LiveEvent.PreviewFailed -> {
                // RTK 预览启动失败；优先按 event.code 定制 UI
            }
            is LiveEvent.Disconnected -> {
                // 预览/推流异常断连；优先按 event.code 定制 UI
            }
            LiveEvent.RespStop -> {
                // 直播已停止
            }
            LiveEvent.StoppedByNotification -> {
                //通知栏结束直播    
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
        // RTK 可选：直播前台服务通知栏（仅 R.string / R.drawable 资源 ID）
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

// 预览就绪后，将直播流推到第三方（如抖音返回的 RTMP 地址）
GlassesManage.startPushLiveStreaming("rtmp://your-push-url")

GlassesManage.stopLiveStreaming()
```

| API | 说明 |
|-----|------|
| `startLiveStreaming(liveStreamingConfig)` | 启动眼镜端直播（Wi-Fi AP）；绑定 `previewView` 并开启本地预览 |
| `startPushLiveStreaming(liveUrl)` | 预览就绪后，将 RTSP 流推到第三方返回的 RTMP 地址 |
| `stopLiveStreaming()` | 停止预览/推流，释放播放器，恢复 AI 服务 |
| `ensureGlassesWifiApConnected(callback)` | **仅 WiFi 重连**：`RespSuccess` 后强制手机重连眼镜热点；正常开播流程无需调用 |

**`LiveStreamingConfig` 参数**：

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `videoPictureWidth` | `Int` | `1280` | 视频宽度 |
| `videoPictureHeight` | `Int` | `720` | 视频高度 |
| `fps` | `Int` | `30` | 帧率 |
| `bps` | `Int` | `1000000` | 码率（bps） |
| `maxQp` | `Int` | `0` | 视频编码最大 QP（RTK）；`0` 表示设备默认 |
| `minQp` | `Int` | `0` | 视频编码最小 QP（RTK）；`0` 表示设备默认 |
| `videoBitRateMode` | `VideoBitRateMode` | `VBR` | 码率模式：`CBR` 恒定码率 / `VBR` 可变码率（RTK） |
| `pushUrl` | `String?` | `null` | RTMP 推流地址；`null` 且 `mode` 为 `PREVIEW` 时仅预览 |
| `previewView` | `RTKVideoView?` | `null` | RTK 预览视图 |
| `mode` | `LiveStreamingMode` | `PREVIEW` | 直播模式：`PREVIEW` 仅预览 / `PUSH` 仅推流 / `PREVIEW_PUSH` 预览+推流 |
| `notificationConfig` | `LiveStreamingNotificationConfig?` | `null` | **RTK**：直播前台服务通知栏；`null` 使用 RTK 默认 |

**`LiveStreamingNotificationConfig` 字段（`notificationConfig`，RTK smartwear ≥ 1.8.70）**：

| 参数 | 类型 | 说明 |
|------|------|------|
| `startTitleRes` | `@StringRes Int` | 直播服务启动时通知标题；`0`（`RES_ID_UNSET`）表示使用 RTK 默认 |
| `startContentRes` | `@StringRes Int` | 直播服务启动时通知正文 |
| `networkDisconnectedTitleRes` | `@StringRes Int` | 网络断开时通知标题 |
| `networkDisconnectedContentRes` | `@StringRes Int` | 网络断开时通知正文 |
| `pushFailedTitleRes` | `@StringRes Int` | 推流失败（如直播间被平台关闭）时通知标题（RTK smartwear ≥ 1.8.73） |
| `pushFailedContentRes` | `@StringRes Int` | 推流失败时通知正文（RTK smartwear ≥ 1.8.73） |
| `stopActionRes` | `@StringRes Int` | 通知栏停止直播操作按钮文案 |
| `smallIconRes` | `@DrawableRes Int` | 通知小图标 |

> 通知栏文案与图标**仅支持资源 ID**，不支持运行时 `String` 或 `Drawable` 对象。

**回调事件（`LiveEvent`）**：

| 事件 | 字段 | 说明 |
|------|------|------|
| `RespSuccess` | `rtsp` | 眼镜开启推流成功，含 RTSP 地址 |
| `PreviewStarted` | — | 预览就绪（RTK 可能回调两次：连 AP / 出流） |
| `PreviewFailed` | `reason`, `code` | RTK 预览播放器启动失败 |
| `Failed` | `reason`, `code` | 开播前置失败（未进入预览） |
| `Disconnected` | `reason`, `code` | 预览或推流阶段异常断连 |
| `RespStop` | — | 直播已停止 |

**错误码与默认文案（App 定制 UI）**：

- 直播会话错误：`GlassesConstant.ERROR_CODE_LIVE_*`（3201–3214，不含 AP 链路）
- **RTK 开启 AP / 连热点**（与 OTA、媒体同步共用）：`GlassesConstant.ERROR_CODE_RTK_*`（3501–3505），别名见 `RtkSoftApErrors` / `LiveStreamErrors.CODE_HOTSPOT_*`、`CODE_WIFI_JOIN_*`
- 默认说明：`LiveStreamErrors.defaultReason(code)`、`RtkSoftApErrors.defaultReason(code)`
- **App 应优先根据 `code` 展示文案**；`reason` 为 SDK 默认说明，可作兜底或日志

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

完整错误码表见 [第 12 节 · RTK SoftAP 共用（3501–3505）](#-rtk-softap-共用错误3501---3505) 与 [直播错误（3201–3214）](#-直播错误3201---3214)。

> Demo：`LiveViewModel` + `LiveScreen`。直播能力与参数解析因渠道（LY / RTK / TB 等）及固件版本而异，接入前请确认 `SdkConfig.channel`。

### **8.6 直播体验配置（抖音 Key / 包名 / 签名）**

Demo（`app` 模块）对接抖音直播时，需使用与抖音开放平台登记一致的 **应用包名、签名（jks）以及抖音 appId / appName**。为方便在不同客户/渠道间快速切换，这些参数已统一抽离到根目录的本地配置文件 `douyin.properties`，**改配置即可切换，无需改动任何代码**。

**配置文件**：项目根目录的 `douyin.properties`（含各字段说明，直接编辑即可生效）。

**使用步骤**：

1. 编辑 `douyin.properties`，将 `CONFIG_ENABLED` 设为 `true`，并填入对应客户/渠道的值：

   | Key | 说明 | 示例 |
   |-----|------|------|
   | `CONFIG_ENABLED` | 总开关；`false` 或文件缺失时不配置签名，用 Studio 默认 `debug.keystore` 打包 | `true` |
   | `APPLICATION_ID` | 应用包名，需与抖音后台登记一致 | `com.xxx.xxx.xxx` |
   | `KEY_STORE_FILE` | jks 签名文件路径（相对项目根目录） | `key/xxx.jks` |
   | `KEY_STORE_PASSWORD` | keystore 密码 | — |
   | `KEY_ALIAS` | 签名别名 | — |
   | `KEY_PASSWORD` | 别名对应密码 | — |
   | `DOUYIN_APP_ID` | 抖音开放平台 appId（ClientKey） | `1032728` |
   | `DOUYIN_APP_NAME` | 应用名称（与抖音后台一致） | `LwGlass` |
   | `DOUYIN_CLIENT_KEY` | 抖音开放平台申请的 ClientKey，`DouYinEntryActivity` 授权回调使用 | — |
   | `DOUYIN_CLIENT_SECRET` | 抖音开放平台申请的 ClientSecret，`DouYinEntryActivity` 授权回调使用 | — |

2. 将 `.jks` 签名文件放到 `KEY_STORE_FILE` 指定路径。
3. Gradle Sync / 重新构建即可生效。

> 填写包名、签名密码、抖音 Key 等敏感信息后，请勿将 `douyin.properties` 提交到版本库。

**配置生效位置**：

| 配置项 | 生效位置 |
|--------|----------|
| `CONFIG_ENABLED` | 总开关；关闭时下方配置均不生效，debug / release 均用默认 `debug.keystore` 签名 |
| `APPLICATION_ID` / `KEY_*` | `app/build.gradle.kts` 的 `signingConfigs` 与 `defaultConfig.applicationId` |
| `DOUYIN_APP_ID` / `DOUYIN_APP_NAME` | 注入 `lib_core` 的 `BuildConfig`，由 `DouYinRepository.initDouyinSdk` 中 `BroadcastInitConfig.Builder` 使用 |
| `DOUYIN_CLIENT_KEY` / `DOUYIN_CLIENT_SECRET` | 注入 `app` 的 `BuildConfig`，由授权回调 `DouYinEntryActivity` 使用 |

---

## **9. SDK Flow 流监听**

> **连接状态**：BLE/BT 请优先使用 `GlassesManage.connectionStateFlow()`（见 [第 5.2 节](#52-订阅-ble--bt-连接状态推荐)），本节 `eventFlow` 主要覆盖扫描、同步、OTA、指令等业务事件。

### **通用 - CmdResultEvent**
- 设备设置、设备状态、媒体文件、电量、按键动作等结果请关注 `CmdResultEvent` 子类
- RTK 高清拍照参数：`CmdResultEvent.LifePhotoConfigResult(success)`（见 [10.8](#8️⃣-设备侧采集与拍照)）
- RTK SoftAP 名称/密码：`CmdResultEvent.WifiApConfigResult(success)`（见下方 [§6 RTK](#rtk-方案)）


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

手动重连 BT：**LY / TB** 见 [第 5.3 节](#53-手动重连-btly--tb)。

### **③ 音频流 - AudioStateEvent**
- 自定义大模型请参阅 **[眼镜原始音频流说明](Glasses-Raw-Audio-Stream.md)**（LY / TB / RTK 原始格式、采样率、声道、帧大小）
- 亦可参考 Demo

### **④ 同步媒体文件 - FileSyncEvent**
- `ConnectSuccess`：Wi-Fi 连接成功
- `ThumbnailsReady(thumbnails, totalFileCount)`：缩略图列表就绪（LY）；`thumbnails` 为 `ThumbnailItem(fpath, index, thumbnailUrl)`
- `DownloadProgress(progress, curFileIndex, totalFileCount, speed)`：下载进度
- `DownloadSuccess(filePath, curFileIndex, totalFileCount, remoteUrl, fileSizeInBytes, fileModifiedTime, fpath)`：单文件下载成功
- `DownloadSkipped(curFileIndex, totalFileCount, fpath)`：单文件无效跳过（LY）
- `BatchDownloadFinished(successCount, totalFileCount)`：整批下载结束
- `Failed(reason, code)`：同步失败

### **⑤～⑦ AI 相关事件**

AI 助手 / 翻译 / 音频事件（`AgentEvent`、`AiTranslationEvent` 等）通过 `AiAssistantClient.aiAgentEventFlow()` 订阅，详见 **[AI 文档 §10](AI-README.md#10-ai-事件订阅汇总)**。

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
- `RespSuccess(rtsp)`：眼镜开启推流成功，含 RTSP 地址
- `PreviewStarted`：预览就绪
- `PreviewFailed(reason, code)`：预览启动失败
- `Failed(reason, code)`：开播前置失败
- `Disconnected(reason, code)`：预览/推流异常断连
- `RespStop`：直播已停止
- `WifiApReady(ssid, password)`：眼镜热点凭证（WiFi 重连用）

### **⑪ SDK 全局错误 - SdkErrorEvent**
- `GlobalError`：SDK 未初始化等全局错误（如错误码 `1001`）

---

## **10. 眼镜设置功能**
SDK 通过 `GlassesManage` 提供眼镜参数读取与修改（LED、手势、佩戴检测、音量、时间等）。  
**结果回传**：请在 `GlassesManage.eventFlow()` 中收集 `GlassesEvent`，关注 `CmdResultEvent` 子类（与 [第 9 节](#9-sdk-flow-流监听) 一致）。

---

### **1️⃣ 获取设备综合设置**
主动拉取当前设备侧汇总状态：

```kotlin
GlassesManage.getDeviceSettingsState()
```

**回调事件**：`CmdResultEvent.DeviceSettingsStateEvent`  
**载荷** `DeviceSettingsStateDTO` 字段说明：
- `ledBrightness`：`LyCmdConstant.LedBrightnessLevel?`（LED 亮度档位）
- `recordDuration`：`Int?`（录像时长，**秒**）
- `audioRecordDuration`：`Int?`（录音时长，**秒**）
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
| `setVideoDuration(times: Int)` | 录像时长上限（**秒**） |
| `setVoiceDuration(times: Int)` | 录音时长上限（**秒**） |
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

**TB 方案说明**：

- `setVideoDuration` / `setVoiceDuration` 的入参与 LY/RTK 一致，单位为**秒**。
- TB 设备侧以**分钟**存储时长，SDK 下发时会将秒数**向上取整**为分钟（不足 1 分钟按 1 分钟计）。
- 读取 `getDeviceSettingsState()` 时，`recordDuration` / `audioRecordDuration` 仍会以**秒**返回（SDK 已做换算）。

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
| `setLifePhotoConfig(config: LifePhotoConfig)` | **仅 RTK**：配置高清拍照参数（分辨率 / JPEG 质量 / 旋转）。异步结果见 `CmdResultEvent.LifePhotoConfigResult` |

```kotlin
GlassesManage.startDeviceRecording()
GlassesManage.stopDeviceRecording()

GlassesManage.startDeviceVideoRecording()
GlassesManage.stopDeviceVideoRecording()

GlassesManage.takePicture(takePhotoOnly = true)   // AI 识图
GlassesManage.takePicture(takePhotoOnly = false)  // 保存到眼镜

// RTK：配置高清拍照参数（须在 takePicture(false) 前按需调用）
GlassesManage.setLifePhotoConfig(
    LifePhotoConfig(
        photoWidth = 2560,
        photoHeight = 1440,
        jpegQuality = LifePhotoConfig.JPEG_QUALITY_MAX, // 1–9
        rotationDegrees = 0, // 0 / 90 / 180 / 270
    ),
)
```

**回调事件**：
- 拍照 / 识图：`CmdResultEvent.ImageData`（原始字节）或 `CmdResultEvent.ImageFile`（本地文件路径，视渠道而定）
- 高清拍照参数（RTK）：`CmdResultEvent.LifePhotoConfigResult(success)`
- 自定义大模型录音：见 [眼镜原始音频流说明](Glasses-Raw-Audio-Stream.md)（LY / TB / RTK 格式、采样率、帧大小）与 `AudioStateEvent`

> 分辨率须为传感器实际支持值；`jpegQuality` 范围 1–9；`rotationDegrees` 仅 0/90/180/270。LY / TB 调用为空操作。

---

## **11. OTA 升级**

需先完成 BLE 连接。升级过程通过 `GlassesManage.eventFlow()` 回调 `OTAEvent`（见 [第 9 节](#-ota-升级---otaevent)）。

> **AI 服务**：OTA 开始前 SDK 会**暂停 AI 服务**；收到 `OTAEvent.Success` / `Failed` / `Cancelled` 后**自动恢复 AI 服务**。与媒体同步、直播等占用 Wi-Fi 的流程相同，详见 [AI 文档 §4.1](AI-README.md#41-占用-wi-fi-时的-ai-服务)。

```kotlin
GlassesManage.startOTA(
    path = "/path/to/firmware.bin",
    type = GlassesConstant.OtaType.FIRMWARE,
    version = "",  // 见下方各渠道说明
)
```

| 参数 | 类型 | 说明 |
|------|------|------|
| `path` | `String` | 本地固件文件绝对路径 |
| `type` | `GlassesConstant.OtaType` | OTA 类型 |
| `version` | `String` | 可选，默认 `""`；**TB BLE OTA** 需四段式目标版本（见下方 TB 说明）；LY `WIFI_ISP` 可传目标版本号 |

**`OtaType` 可选值**：

| 枚举 | 说明 |
|------|------|
| `FIRMWARE` | 主固件 OTA（LY BLE / TB BLE） |
| `WIFI_ISP` | Wi-Fi / ISP 模块 OTA（LY / TB WiFi 包） |

### TB 方案

TB 渠道使用 `GlassesManage.startOTA(...)`，支持 BLE 与 WiFi 两种升级：

| `OtaType` | 说明 | `version` |
|-----------|------|-----------|
| `FIRMWARE` |  BLE OTA（`.bin` 等） | **必填四段式**，如 `1.0.0.5`（每段 0–255）；用于 OTA 握手校验 |
| `WIFI_ISP` |  WiFi OTA | 无需传 `version` |

```kotlin
// BLE 固件
GlassesManage.startOTA(
    path = "/path/to/firmware_v1.0.0.5.bin",
    type = GlassesConstant.OtaType.FIRMWARE,
    version = "1.0.0.5",
)

// WiFi 固件
GlassesManage.startOTA(
    path = "/path/to/wifi_firmware.bin",
    type = GlassesConstant.OtaType.WIFI_ISP,
)
```

> Demo：`UpdateScreen` 会展示当前设备版本；BLE 升级时请确认「固件版本」为四段式目标版本（文件名解析出的三段式需手动补第四段）。

### RTK 方案

RTK 渠道请使用 `GlassesManage.startRtkOta(...)`；`GlassesManage.startOTA(...)` 在 RTK 下**无实际操作**，请勿调用。

```kotlin
GlassesManage.startRtkOta(
    btPath = "/path/to/bt_firmware.bin",       // null 或空 = 跳过 BT 升级
    btVersion = "1.0.0.1",                     // BT 固件版本号（四段式）
    wifiZipPath = "/path/to/wifi_firmware.zip" // null 或空 = 跳过 WiFi 升级；压缩包内需含 ota.json
)
```

| 参数 | 类型 | 说明 |
|------|------|------|
| `btPath` | `String?` | BT 固件文件绝对路径；`null` 或空字符串则跳过 BT |
| `btVersion` | `String?` | BT 固件版本号（四段式，如 `1.0.0.1`）；与 `btPath` 配套 |
| `wifiZipPath` | `String?` | WiFi 固件压缩包路径（内含 `ota.json` 及声明的 bin 文件）；`null` 或空则跳过 WiFi |

BT 与 WiFi 至少传入一种；也可同时升级。仅升 BT 时 `wifiZipPath` 传 `null`；仅升 WiFi 时 `btPath` / `btVersion` 传 `null`。

**升级流程**（一次 SoftAP 会话，非 BLE 与 WiFi 两段独立 OTA）：

1. 连接眼镜 SoftAP
2. 若含 BT：推送 BT bin（此阶段不汇报 `OTAEvent.Progress`）
3. 若含 WiFi：SDK 解压压缩包，按 `ota.json` 依次推送各 WiFi 包（`OTAStage.VERIFY` 进度）
4. DFU 统一激活（`OTAStage.OTA` 进度）
5. 结果经 `GlassesManage.eventFlow()` 回调 `OTAEvent`（见 [第 9 节](#-ota-升级---otaevent)）

**RTK OTA 错误码**：包校验 / DFU 流程见 [36xx](#-rtk-ota--dfu-错误3601---3608)；开启 SoftAP / 连热点见 [35xx](#-rtk-softap-共用错误3501---3505)（与直播、媒体同步共用）。默认文案：`RtkOtaErrors.defaultReason(code)`、`RtkSoftApErrors.defaultReason(code)`。

Demo：`UpdateViewModel.startRtkOtaUpgrade()` 支持分别选择 BT bin 与 WiFi zip，或二者同时升级。

## **12. 错误码说明**

错误码定义于 `GlassesConstant`；业务回调中通过 `event.code` 或 `Failed(reason, code)` 获取。

> `ERROR_CODE_SYNC_BLE_NOT_CONNECTED`（1010）已废弃，请使用 `ERROR_CODE_BLE_NOT_CONNECTED`。

### ⚠️ SDK 基础错误（1001 ~ 1010）
| 错误码 | 名称 | 描述 |
|:-------:|:------|:------|
| 1001 | ERROR_CODE_SDK_NOT_INITIALIZED | SDK 未初始化 |
| 1010 | ERROR_CODE_BLE_NOT_CONNECTED | BLE 未连接（OTA、媒体同步等需 BLE 已连接的操作通用） |

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

### 📶 Wi-Fi 连接错误（3001 - 3008）
| 错误码 | 名称 | 描述 |
|:-------:|:------|:------|
| 3001 | ERROR_CODE_WIFI_CONNECT_TIMEOUT | 连接 Wi-Fi 超时 |
| 3002 | ERROR_CODE_WIFI_DEVICE_DISCOVERY_TIMEOUT | 发现设备超时 |
| 3003 | ERROR_CODE_WIFI_NEGOTIATION_TIMEOUT | 协商超时 |
| 3004 | ERROR_CODE_WIFI_UNKNOWN_ERROR | 未知错误 |
| 3005 | ERROR_CODE_WIFI_OPEN_ERROR | 开启 Wi-Fi AP 热点失败（**LY 方案**；RTK 见 3501） |
| 3006 | ERROR_CODE_WIFI_NO_PERMISSION | 缺少 Wi-Fi 连接权限：未授予 `ACCESS_FINE_LOCATION`；Android 13（API 33）及以上还需 `NEARBY_WIFI_DEVICES` |
| 3007 | ERROR_CODE_WIFI_NO_OPEN_LOCATION | 位置服务未开启 |
| 3008 | ERROR_CODE_WIFI_CLOSED | Wi-Fi 已关闭 |

### 📂 文件下载错误（3101 - 3105）
| 错误码 | 名称 | 描述 |
|:-------:|:------|:------|
| 3101 | ERROR_CODE_DOWNLOAD_GET_FILE_LIST_FAILED | 获取文件列表失败 |
| 3102 | ERROR_CODE_DOWNLOAD_FILE_NOT_FOUND | 文件未找到 |
| 3103 | ERROR_CODE_DOWNLOAD_FAILED | 文件下载失败 |
| 3104 | ERROR_CODE_DOWNLOAD_NETWORK_ERROR | 网络错误 |
| 3105 | ERROR_CODE_DOWNLOAD_DELETE | 文件删除失败 |

### 📡 RTK SoftAP 共用错误（3501 - 3505）

**RTK 渠道**在 OTA、直播、媒体同步中，「眼镜开启 SoftAP → 手机连接热点」阶段统一使用下列错误码（`FileSyncEvent.Failed` / `LiveEvent.Failed` / `OTAEvent.Failed` 的 `code` 字段）。

默认文案与 Wi‑Fi 连 AP 失败分类：`com.fission.wear.glasses.sdk.rtk.RtkSoftApErrors`；直播侧别名：`LiveStreamErrors.CODE_HOTSPOT_*`、`CODE_WIFI_JOIN_*`（均指向本段）。

| 错误码 | 名称 | 描述 |
|:-------:|:------|:------|
| 3501 | ERROR_CODE_RTK_AP_ENABLE_FAILED | 眼镜 SoftAP 开启失败 |
| 3502 | ERROR_CODE_RTK_AP_INFO_UNAVAILABLE | 眼镜 AP 信息（SSID/密码）不可用 |
| 3503 | ERROR_CODE_RTK_WIFI_JOIN_REJECTED | 用户在系统 Wi‑Fi 弹窗取消/拒绝连接眼镜热点 |
| 3504 | ERROR_CODE_RTK_WIFI_JOIN_FAILED | 未能连上眼镜热点（非用户明确取消） |
| 3505 | ERROR_CODE_RTK_WIFI_JOIN_TIMEOUT | 连接眼镜热点超时 |

### 🔄 RTK OTA / DFU 错误（3601 - 3608）

**RTK 渠道** `GlassesManage.startRtkOta(...)` / `OTAEvent.Failed` 专用（与 LY 的 40xx ISP OTA 独立编号）。默认文案：`RtkOtaErrors.defaultReason(code)`。

| 错误码 | 名称 | 描述 |
|:-------:|:------|:------|
| 3601 | ERROR_CODE_RTK_OTA_PACKAGE_INVALID | 升级包、版本号或 `ota.json` 无效 |
| 3602 | ERROR_CODE_RTK_OTA_BT_FILE_NOT_FOUND | BT 固件文件不存在 |
| 3603 | ERROR_CODE_RTK_OTA_DEVICE_ADDRESS_EMPTY | 设备地址为空 |
| 3604 | ERROR_CODE_RTK_OTA_DFU_CONNECT_FAILED | DFU 连接设备失败 |
| 3605 | ERROR_CODE_RTK_OTA_DFU_PREPARE_FAILED | DFU 设备准备失败或超时 |
| 3606 | ERROR_CODE_RTK_OTA_PUSH_FAILED | 固件推送到眼镜失败 |
| 3607 | ERROR_CODE_RTK_OTA_ACTIVATE_FAILED | 固件激活（`startOtaProcedure`）失败 |
| 3608 | ERROR_CODE_RTK_OTA_DFU_PROCEDURE_FAILED | DFU 升级过程失败（厂商细节见 `reason`） |

### 🔄 LY OTA 升级错误（4001 - 4005）

**LY 方案** ISP / 主固件 OTA；RTK 请使用上节 36xx。
| 错误码 | 名称 | 描述 |
|:-------:|:------|:------|
| 4001 | ERROR_CODE_OTA_FILE_NOT_FOUND | OTA 固件文件不存在或路径为空 |
| 4002 | ERROR_CODE_OTA_HANDSHAKE_FAILED | ISP TCP OTA 握手失败 |
| 4003 | ERROR_CODE_OTA_TRANSFER_FAILED | ISP TCP OTA 传输失败 |
| 4004 | ERROR_CODE_OTA_UPGRADE_FAILED | ISP 升级失败（设备返回 Upgrade_err） |
| 4005 | ERROR_CODE_OTA_FILE_OR_VERSION_INVALID | OTA 资源或版本号无效（LY ISP OTA） |

### 🤖 AI 助手错误（500001 - 500003）

详见 **[AI 文档 §11](AI-README.md#11-错误码)**。

### 📺 直播错误（3201 - 3214）

通过 `LiveEvent.Failed` / `PreviewFailed` / `Disconnected` 的 `code` 字段回调。App 可按码定制 UI；SDK 默认说明见 `LiveStreamErrors.defaultReason(code)`。

**RTK 开启 AP / 连热点**不在本表，见 [RTK SoftAP 共用（3501–3505）](#-rtk-softap-共用错误3501---3505)。

| 错误码 | 名称 | 典型场景 |
|:-------:|:------|:------|
| 3201 | ERROR_CODE_LIVE_DEVICE_NOT_CONNECTED | 眼镜 BLE 未连接 |
| 3202 | ERROR_CODE_LIVE_DEVICE_NOT_READY | 眼镜初始化未完成 |
| 3207 | ERROR_CODE_LIVE_PREVIEW_START_FAILED | RTK 预览播放器启动失败 |
| 3208 | ERROR_CODE_LIVE_GLASSES_START_FAILED | 眼镜侧 `startLiveStreaming` 返回失败 |
| 3209 | ERROR_CODE_LIVE_INTERRUPTED | **推流进行中**会话异常中断 |
| 3210 | ERROR_CODE_LIVE_PHONE_WIFI_OFF | 手机 Wi‑Fi 已关闭 |
| 3211 | ERROR_CODE_LIVE_GLASSES_AP_LINK_LOST | 与眼镜热点连接断开 |
| 3212 | ERROR_CODE_LIVE_GLASSES_AP_CLOSED | 眼镜热点已关闭 |
| 3213 | ERROR_CODE_LIVE_GLASSES_DISCONNECTED | 眼镜 BLE 断开 |
| 3214 | ERROR_CODE_LIVE_CELLULAR_UNAVAILABLE | 手机蜂窝数据开关未开启 |

**阶段区分（重要）**：

- **开播前置**（`startLiveStreaming` 入口）：`3201`、`3210`、`3214`
- **连接/预览阶段**（未正式推流）：RTK 常见 `3501`–`3505`（AP 链路）、`3207`–`3208`
- **推流进行中**：常见 `3209`、`3210`–`3213`；`3209` 不应再用于「用户取消连 Wi‑Fi」

**相关 Wi‑Fi 码**：LY 媒体同步/OTA 见 [3001–3008](#-wi-fi-连接错误3001---3008)；RTK 连眼镜 AP 见 [3501–3505](#-rtk-softap-共用错误3501---3505)。

> 杰理 OTA 升级过程中的错误码说明请参考：[**官方文档 OTA 错误码**](https://doc.zh-jieli.com/Apps/Android/ota/zh-cn/master/development/interface_desc.html#id7)

