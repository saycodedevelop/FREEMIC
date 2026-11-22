# KaraokeLink

[ไทย](README.md) · [English](README.en.md) · [简体中文](README.zh-CN.md) · [Tiếng Việt](README.vi.md) · [Bahasa Indonesia](README.id.md)

将手机变成无线麦克风，在另一部手机上播放卡拉 OK 音乐。所有设备连接同一 Wi-Fi，一台播放器可同时连接 **两台麦克风手机**。各手机安装同一个 APK，支持 Android 9 及以上版本。

**[下载最新版本](https://github.com/saycodedevelop/FREEMIC/releases/latest)** · [多语言更新日志](CHANGELOG.md)

## 界面预览

![选择播放器或麦克风](docs/screenshots/home.png)

[麦克风](docs/screenshots/microphone.png) · [混音面板](docs/screenshots/mixer.png) · [入门指南](docs/screenshots/getting-started.png)

使用模拟状态在 Android 上渲染的预览；截图文字为泰语。[用户指南（泰语）](docs/USER_GUIDE.md)。

## 开始使用

1. 在播放器和每台麦克风手机上安装 APK，并连接同一 Wi-Fi。
2. 在连接音箱的手机上选择 **打开音乐播放器**，搜索歌曲或粘贴 HTTPS 链接。
3. 在每台用于唱歌的手机上选择 **将此手机用作麦克风**，允许麦克风和通知权限，然后选择同一播放器。

首次使用指南可以跳过，也可以通过任意界面的 **?** 再次打开。

## 查找播放器并连接两台麦克风

应用同时使用 Android NSD/mDNS 和 UDP 广播搜索，并通过单播探测确认播放器。结果显示品牌、型号和 IP。连接两台麦克风后显示 **2/2**；停止其中一台不会中断另一台。

如果找不到播放器，点击播放器状态查看 IP，再在麦克风手机上选择 **通过 IP 连接**。访客 Wi-Fi、AP/客户端隔离、部分热点及 VPN 可能阻止设备通信，网络必须允许设备互相访问。

## 麦克风熄屏后继续唱歌

麦克风连接后，熄屏或切换应用仍可继续传送声音。通过通知或应用中的 **停止麦克风** 按钮停止。重启手机或进程结束后不会自动重新录音。

播放器在进入后台或熄屏时会暂停声音，请保持播放器界面打开。

## 音质和网络

- 每台麦克风都有增益和音效设置。噪声门默认关闭，需要时可在高级设置中开启。
- 支持数据包重排、自适应缓冲、短暂丢包时的淡出及音频时钟漂移补偿。
- 现场演唱建议使用有线或 USB 音箱。蓝牙延迟取决于设备，也可能出现卡顿。
- HTML5 音乐音量调节仅适用于支持的网站。Android 混合音乐和麦克风声音。
- RTT 和队列数据不代表从嘴到音箱的延迟。增加延迟无法消除蓝牙延迟。
- 局域网麦克风流量目前未加密，请使用可信网络。

## 语言

支持泰语、英语、简体中文、越南语和印度尼西亚语。默认跟随手机语言，不支持时使用英语。可在主页选择 **语言**，或使用 Android 13 及以上版本的应用语言设置。请在开始会话前切换语言。发布说明包含全部五种语言。

## 安装和开发

正式文件为使用固定发布密钥签名的 `KaraokeLink-<version>.apk` 和 `SHA256SUMS.txt`。旧预览版签名不同，需要先卸载一次；之后可覆盖更新正式版。

使用 JDK 17 或 21、Android SDK 35、NDK 27.0.12077973 和 CMake 3.22.1。设置 `JAVA_HOME` 和 `ANDROID_HOME` 后运行：

```powershell
.\gradlew.bat :app:assembleDebug :app:lintDebug :app:testDebugUnitTest
```

调试 APK：`app/build/outputs/apk/debug/app-debug.apk`。

[架构（泰语/英语）](docs/ARCHITECTURE.md) · [局域网协议（泰语）](docs/PROTOCOL.md) · [真机测试（泰语/英语）](docs/TESTING.md) · [发布流程（英语）](docs/RELEASING.md)
