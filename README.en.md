# KaraokeLink

[ไทย](README.md) · [English](README.en.md) · [简体中文](README.zh-CN.md) · [Tiếng Việt](README.vi.md) · [Bahasa Indonesia](README.id.md)

Turn phones into wireless microphones and play karaoke on another phone over the same Wi-Fi. One player supports **two microphones at once**. Use the same APK on all phones. Requires Android 9 or later.

**[Download the latest release](https://github.com/saycodedevelop/FREEMIC/releases/latest)** · [Multilingual changelog](CHANGELOG.md)

## Screenshots

![Choose player or microphone](docs/screenshots/home.png)

[Microphone](docs/screenshots/microphone.png) · [Mixer](docs/screenshots/mixer.png) · [Setup guide](docs/screenshots/getting-started.png)

Android-rendered previews use simulated state and Thai text. [User guide (Thai)](docs/USER_GUIDE.md).

## Get started

1. Install the APK on the player and each microphone phone; connect all to the same Wi-Fi.
2. On the phone connected to the speaker, choose **Open music player**, then search for a song or paste an HTTPS link.
3. On each singing phone, choose **Use this phone as a mic**, grant microphone/notification permissions, and select the same player.

Skip the first-run guide or reopen it using **?** on any screen.

## Find the player and connect two microphones

Discovery uses Android NSD/mDNS alongside UDP broadcast and confirms the player with unicast probes. Results show the manufacturer/model and IP. Two connected microphones appear as **2/2**; stopping one leaves the other connected.

If discovery fails, tap the player status to find its IP, then choose **Connect by IP** on the microphone. Guest Wi-Fi, AP/client isolation, some hotspots, and VPNs can block communication. Devices must be allowed to contact each other.

## Screen-off microphone

A connected microphone keeps sending audio with its screen off or another app open. Stop from the notification or the in-app **Stop microphone** button. Recording never restarts automatically after reboot or process termination.

The player pauses audio when backgrounded or its screen is off; keep its player screen open.

## Sound and network

- Each microphone has gain and effects. Noise gate defaults to off and can be enabled in advanced settings.
- Packet reordering, adaptive buffering, short-loss fading, and clock-drift compensation stabilize audio.
- Wired or USB speakers suit live singing; Bluetooth adds device-dependent delay and may stutter.
- HTML5 music volume works only on supported sites. Android mixes the music and microphone audio.
- RTT and queue statistics do not measure mouth-to-speaker latency. Added delay cannot remove Bluetooth delay.
- LAN microphone traffic is currently unencrypted; use a trusted network.

## Languages

Thai, English, Simplified Chinese, Vietnamese, and Indonesian are included. The app follows the phone language, with English as fallback. Choose **Language** on Home or use Android 13+ app-language settings. Change language before starting a session. Release notes include all five languages.

## Installation and development

Official assets are `KaraokeLink-<version>.apk`, signed with a permanent release key, and `SHA256SUMS.txt`. Old previews use a different signing key and require one uninstall; later official releases can update in place.

Use JDK 17 or 21, Android SDK 35, NDK 27.0.12077973, and CMake 3.22.1. Set `JAVA_HOME` and `ANDROID_HOME`, then run:

```powershell
.\gradlew.bat :app:assembleDebug :app:lintDebug :app:testDebugUnitTest
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.

[Architecture (Thai/English)](docs/ARCHITECTURE.md) · [LAN protocol (Thai)](docs/PROTOCOL.md) · [Device testing (Thai/English)](docs/TESTING.md) · [Release process (English)](docs/RELEASING.md)
