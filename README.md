# MiMo 语音合成 App

基于 [MiMo V2.5 TTS](https://platform.xiaomimimo.com/) API 的安卓语音合成应用，支持预置音色、音色克隆和音色设计三种模式。

## 快速开始

### 直接安装（推荐）

项目根目录下包含预编译的 APK 文件，可直接安装到安卓手机使用：

```
mimo-tts-app.apk
```

**安装方式：**
1. 将 `mimo-tts-app.apk` 传输到安卓手机
2. 在手机上打开文件管理器，点击 APK 文件
3. 允许"安装未知来源应用"权限
4. 按提示完成安装

**系统要求：** Android 8.0 (API 26) 及以上

### 从源码构建

需要 Android Studio + JDK 17，打开 `app5/` 目录作为 Gradle 项目即可编译。

## 功能特性

| 功能 | 说明 |
|------|------|
| **预置音色** | 8 种内置音色：冰糖、茉莉、苏打、白桦、Mia、Chloe、Milo、Dean |
| **音色克隆** | 上传或录制参考音频，克隆任意说话人的音色 |
| **音色设计** | 通过文字描述创造独特的声音 |
| **风格控制** | 用自然语言描述期望的语气、语速、情感等 |
| **录音功能** | 应用内直接录制参考音频 |
| **音频保存** | 生成的语音可保存为 WAV 文件到本地 |

## 使用说明

### 1. 配置 API

首次使用需在**设置**页面填写：

- **API 地址**：默认 `https://api.xiaomimimo.com/v1`（已预填）
- **API Key**：必须填写，在 [MiMo 开放平台](https://platform.xiaomimimo.com/) 获取

### 2. 选择 TTS 模式

| 模式 | 模型 | 说明 |
|------|------|------|
| 预置音色 | `mimo-v2.5-tts` | 从 8 种内置音色中选择，可选填风格描述 |
| 音色克隆 | `mimo-v2.5-tts-voiceclone` | 需在主页上传或录制参考音频 |
| 音色设计 | `mimo-v2.5-tts-voicedesign` | 通过文字描述设计独特音色 |

### 3. 风格控制（可选）

在设置的「风格控制」输入框中，用自然语言描述期望效果，例如：

- "用温柔的语气，缓慢地朗读"
- "充满激情，语速较快"
- "像新闻播报员一样正式"

### 4. 生成语音

1. 在主页输入要合成的文字
2. 点击「生成语音」
3. 生成完成后可播放或保存

## 技术细节

### API 调用

**预置音色 / 音色克隆模式：**

```
POST /chat/completions
Content-Type: application/json
Authorization: Bearer <API_KEY>

{
  "model": "mimo-v2.5-tts",
  "messages": [
    {
      "role": "user",
      "content": "风格描述（可选）"
    },
    {
      "role": "assistant",
      "content": "要合成的文字"
    }
  ],
  "audio": {
    "format": "wav",
    "voice": "茉莉"
  }
}
```

**音色设计模式**（不支持 `voice` 字段、预置音色、音色克隆、唱歌模式）：

```
POST /chat/completions
Content-Type: application/json
Authorization: Bearer <API_KEY>

{
  "model": "mimo-v2.5-tts-voicedesign",
  "messages": [
    {
      "role": "user",
      "content": "Give me a young male tone."
    },
    {
      "role": "assistant",
      "content": "Yes, I had a sandwich."
    }
  ],
  "audio": {
    "format": "wav"
  }
}
```

响应格式为标准 Chat Completion，音频数据在 `choices[0].message.audio.data` 中以 base64 编码返回。

### 项目结构

```
app5/
├── mimo-tts-app.apk                  # 预编译 APK
├── build.gradle                      # 根级构建配置
├── settings.gradle
├── gradle.properties
└── app/
    ├── build.gradle                   # 包名: com.mimo.tts
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/com/mimo/tts/
        │   ├── Constants.java         # 常量定义（音色、模式、默认值）
        │   ├── TTSApi.java            # API 请求/响应处理
        │   ├── AudioPlayer.java       # WAV 音频播放
        │   ├── MainActivity.java      # 主界面
        │   ├── SettingsActivity.java  # 设置界面
        │   └── AppApplication.java    # Application
        └── res/
            ├── layout/                # 界面布局
            ├── drawable/              # 图标和背景
            └── values/                # 字符串、颜色、主题
```

### 依赖

| 库 | 版本 | 用途 |
|----|------|------|
| OkHttp | 4.11.0 | HTTP 请求 |
| Gson | 2.10.1 | JSON 解析 |
| AndroidX AppCompat | 1.6.1 | 兼容性支持 |
| Material Components | 1.9.0 | Material Design 组件 |
| ConstraintLayout | 2.1.4 | 布局 |

## 许可

本项目仅供学习和研究使用。MiMo API 的使用需遵守 [小米 MiMo 服务条款](https://platform.xiaomimimo.com/)。
