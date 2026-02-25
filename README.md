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
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
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
implementation("com.fission.wear.glasses:sdk:1.0.9")
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
---

## **3. SDK 初始化**
参考 Demo 实现方式（包含 Rx 全局异常捕获）。日志可不用保存在本地。

主要方法：
- `Utils.init()`
- `GlassesManage.initialize()`
- `GlassesManage.updateEnvironment(env)`

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
GlassesManage.connectAiAssistant()
```

### ✅ 自定义大模型（App 自己实现）
请参考 Demo 中的 `AudioStateEvent` 实现。  
`GlassesManage.initialize()` 如需开启自定义模式 请联系开发人员。

- **StartRecording**：开始录音
- **ReceivingAudioData**：持续接收录音数据
- **GlassesManage.takePicture()**：AI 识图
  - 回调事件：`CmdResultEvent.ImageData`
- **GlassesManage.stopVadAudio()**：停止录音

---

## **8. AI 翻译**
请参考Demo中translate 实现流程
- **startAiTranslation(fromLanguage: Int, toLanguageList: List<Int>,requestId: Long)**：初始化需要翻译的语音
- **startReceivingAudio(mode: String,language: Int)**：切换AI助手为翻译模式
- **sendReceivingAudioData(byteArray: ByteArray)**：发送录音数据。录音数据参数必须设置为频率16000，pcm流
- **stopReceivingAudio(mode: String)**：停止录音后 必须发送stop
- **翻译回调查看flow流，翻译Event部分**


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
SDK 提供了读取和修改眼镜多种参数的功能，如 LED 亮度、手势快捷方式、佩戴检测等。

### **① 获取所有设备设置**
一次性获取设备当前的所有设置状态。  
返回事件：`CmdResultEvent.DeviceSettingsStateEvent`

**参数**: `data: DeviceSettingsStateDTO`  
包含内容：
- `ledBrightness` (LED 亮度)
- `recordDuration` (录像时长)
- `wearDetectionEnabled` (佩戴检测开关)
- `voiceCommandEnabled` (语音指令开关)
- `gestureSettings` (手势设置 Map)
- `burstPhotoCount` (连拍张数)
- `orientation` (屏幕方向)

### **② 修改单项设备设置**
```kotlin
GlassesManage.setLedBrightness()
GlassesManage.setWearDetection()
GlassesManage.setGestureAction()
```

### **③ 获取设备版本信息**
```kotlin
GlassesManage.getDeviceVersionInfo()
```
### **④ 设备重启**
```kotlin
GlassesManage.rebootDevice()
```

### **⑤ 设备恢复出厂设置**
```kotlin
GlassesManage.restoreFactorySettings()
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

