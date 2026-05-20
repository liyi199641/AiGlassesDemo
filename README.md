# LinWear Ai Glasses SDK 文档（中文版）

---

## 📚 目录 (TOC)
- [1. 添加权限](#1-添加权限)
- [2. 添加依赖（必须）](#2-添加依赖必须)
- [3. SDK 初始化](#3-sdk-初始化)
- [4. 搜索设备](#4-搜索设备)
- [5. 连接设备](#5-连接设备)
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
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
<uses-permission android:name="android.permission.CHANGE_WIFI_STATE" />
<uses-permission
android:name="android.permission.NEARBY_WIFI_DEVICES"
android:usesPermissionFlags="neverForLocation"
tools:targetApi="33" />
```

---

## **2. 添加依赖（必须）**
可参考 Demo，Demo 为 `toml` 集成方式。
直接集成方式：

```gradle
implementation("com.fission.wear.glasses:sdk:1.0.2")
implementation("io.reactivex.rxjava3:rxjava:3.1.6")
```

必需依赖项：
- settings.gradle 添加： maven { url = uri("https://repo.repsy.io/mvn/linwear/android") }
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
参考 Demo 实现方式（包含 Rx 全局异常捕获）。日志可不用保存在本地。

主要方法：
- `Utils.init()`
- `GlassesManage.initialize()`
- `AiAssistantWsInstanceManager.getInstance().initializeAiClient()`

---

## **4. 搜索设备**
App 可以自行实现搜索逻辑，也可以使用 SDK 自带方法：
```kotlin
GlassesManage.startScanBleDevices()
```

---

## **5. 连接设备**
```kotlin
GlassesManage.connect()
```
BleComConfig 参数新增是否为OTA模式

---

## **6. 同步文件**
```kotlin
GlassesManage.syncAllMediaFile()
```

---

## **7. AI 助手功能**
AI 功能包括 **语音对话、图像识别、翻译** 等。可选择两种方式：

### ✅ SDK 内部大模型
```kotlin
//初始化：
  AiAssistantClient.getInstance().initializeAiClient(AiAgentConfig(
     context,
     channel,
     aiModelTyp = AiModelVendor.DEFAULT,
     serverEnvironment = GlassesConstant.ServerEnvironment.DEV,
     enableDefaultPlaySimultaneousAudio = true,//默认SDK处理播放同声传译音频流，false时否可订阅AgentAudioEvent自行处理
     enableDefaultPlayAgentAudio = true,//默认SDK处理播放Agent音频流,false时可订阅AgentAudioEvent自行处理
     translationAudioStorageDirName = GlassesConstant.DEFAULT_TRANS_AUDIO_FILES_STORAGE_DIR //翻译文件存储目录文件夹名
  ))

//不启用SDK播放音频，则订阅接收音频流自行处理，音频流参数：PCM ，16000 ，单声道：
AiAssistantClient.getInstance().aiAgentEventFlow().collect { events->
  when(events){
    is AgentAudioEvent.AgentAudioStart -> Log.d("","AI聊天语音开始")
    is AgentAudioEvent.AgentAudioSend -> Log.d("","AI聊天语音流")
    is AgentAudioEvent.AgentAudioStop -> Log.d("","AI聊天语音结束")
    is AgentAudioEvent.TranslationAudioStart -> Log.d("","AI翻译语音开始")
    is AgentAudioEvent.TranslationAudioSend -> Log.d("","AI翻译语音流")
    is AgentAudioEvent.TranslationAudioStop -> Log.d("","AI翻译语音结束")
    else                           -> {}
  }
}
  
//AI服务连接状态,聊天,识图等订阅 AgentEvent ；翻译订阅：AiTranslationEvent
  AiAssistantClient.getInstance().aiAgentEventFlow().collect { events->
    when(events){
      is AgentEvent.AiAssistantConnectState ->{

      }
      else                           -> {}
    }
  }
```

## **8. AI 翻译**
请参考Demo中translate 实现流程
- **startAiTranslation(fromLanguage: Int, toLanguageList: List<Int>,requestId: Long)**：初始化需要翻译的语音
- **startReceivingAudio(mode: String,language: Int)**：切换AI助手为翻译模式
- **sendReceivingAudioData(byteArray: ByteArray)**：发送录音数据。录音数据参数必须设置为频率16000，pcm流
- **stopReceivingAudio(mode: String)**：停止录音后 必须发送stop
- **翻译回调查看flow流，翻译Event部分**

### ✅ 自定义大模型（App 自己实现）
请参考 Demo 中的 `AudioStateEvent` 实现。  
`GlassesManage.initialize()` 如需开启自定义模式 请联系开发人员。

- **StartRecording**：开始录音
- **ReceivingAudioData**：持续接收录音数据
- **GlassesManage.takePicture()**：AI 识图
  - 回调事件：`CmdResultEvent.ImageData`
- **GlassesManage.stopVadAudio()**：停止录音
---


## **9. 直播**



## **10. SDK Flow 流监听**

### **通用 - CmdResultEvent**
- `CallConnected`& `CallDisconnected` 接听&挂断音视频


### **① 搜索设备 - ScanStateEvent**
- `DeviceFound`：返回 `ScanResult`
- `ScanFinished`：扫描完成
- `Error`：扫描异常

### **② 连接设备 - ConnectionStateEvent**
- `Connecting`：连接中
- `Connected`：已连接
- `Disconnected`：断开连接

### **③ 音频流 - AudioStateEvent**
- 参考 Demo

### **④ 同步媒体文件 - FileSyncEvent**
- `ConnectSuccess`：连接 Wi-Fi 成功
- `DownloadProgress`：下载进度
- `DownloadSuccess`：同步成功
- `Failed`：同步失败

### **⑤ AI 助手 - AiAssistantEvent**
- `AiAssistantResult`：大模型返回结果
- `Failed`：错误
---

### **⑥ AI 翻译 - AiTranslationEvent**
- `AiTranslationResult`：大模型返回翻译结果
- `Failed`：错误

---
### **⑦ OTA 升级 - OTAEvent**
- `Start`：开始升级
- `Progress`：升级进度
- `Success`：升级成功
- `Failed`：升级失败
- `Cancelled`：升级已取消
- `Idle`：空闲状态
- `DeviceRebooting`：设备重启中

---

### **⑧ 眼镜动作状态 - ActionSync**
- `ActionSync`：眼镜动作状态 ACTION_INDEX_TAKE_PHOTO 等，可主动获取。
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
GlassesManage.controlMusic(enable: Boolean)  // true 播放 / false 暂停（以固件为准）
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
GlassesManage.getActionState()      // CmdResultEvent.ActionSync（与眼镜动作同步）
```

---

## **12. OTA 升级**
- 方法：
```kotlin
fun startOTA(firmwareFilePath: String, otaType: GlassesConstant.OtaType)
```
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

